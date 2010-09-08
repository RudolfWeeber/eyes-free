/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.marvin.talkback;

import dalvik.system.DexFile;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.CompoundButton;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a speech rule for an {@link AccessibilityEvent}. The
 * rule has a filter which determines if the rule applies to a given event. If
 * the rule applies to an event the formatter for that rule is used to generate
 * a formatted utterance.
 * 
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public class SpeechRule {

    /**
     *  log tag to associate log messages with this class
     */
    private static final String LOG_TAG = "SpeechRule";

    // string constants

    private static final String SPACE = " ";

    private static final String COLON = ":";

    private static final String EMPTY_STRING = "";

    // node names

    private static final String NODE_NAME_METADATA = "metadata";

    private static final String NODE_NAME_FILTER = "filter";

    private static final String NODE_NAME_FORMATTER = "formatter";

    private static final String NODE_NAME_CUSTOM = "custom";

    // properties
    
    private static final String PROPERTY_EVENT_TYPE = "eventType";

    private static final String PROPERTY_PACKAGE_NAME = "packageName";

    private static final String PROPERTY_CLASS_NAME = "className";

    private static final String PROPERTY_TEXT = "text";

    private static final String PROPERTY_BEFORE_TEXT = "beforeText";

    private static final String PROPERTY_CONTENT_DESCRIPTION = "contentDescription";

    private static final String PROPERTY_EVENT_TIME = "eventTime";

    private static final String PROPERTY_ITEM_COUNT = "itemCount";

    private static final String PROPERTY_CURRENT_ITEM_INDEX = "currentItemIndex";

    private static final String PROPERTY_FROM_INDEX = "fromIndex";

    private static final String PROPERTY_CHECKED = "checked";

    private static final String PROPERTY_ENABLED = "enabled";

    private static final String PROPERTY_FULL_SCREEN = "fullScreen";

    private static final String PROPERTY_PASSWORD = "password";

    private static final String PROPERTY_ADDED_COUNT = "addedCount";

    private static final String PROPERTY_REMOVED_COUNT = "removedCount";

    private static final String PROPERTY_QUEUING = "queuing";

    private static final String PROPERTY_ACTIVITY = "activity";

    /**
     *  reusable builder to avoid object creation
     */
    private static final StringBuilder sTempBuilder = new StringBuilder();

    /**
     *  standard, reusable string formatter for populating utterance template
     */
    private static final java.util.Formatter sStirngFormatter = new java.util.Formatter();

    // this is need as a workaround for the adding of CompoundButton state by the framework
    private static String sValueChecked;
    private static String sValueNotChecked;

    /**
     *  Mapping from event type name to its type.
     */
    private static final HashMap<String, Integer> sEventTypeNameToValueMap = new HashMap<String, Integer>();
    static {
        sEventTypeNameToValueMap.put("TYPE_VIEW_CLICKED", 1);
        sEventTypeNameToValueMap.put("TYPE_VIEW_LONG_CLICKED", 2);
        sEventTypeNameToValueMap.put("TYPE_VIEW_SELECTED", 4);
        sEventTypeNameToValueMap.put("TYPE_VIEW_FOCUSED", 8);
        sEventTypeNameToValueMap.put("TYPE_VIEW_TEXT_CHANGED", 16);
        sEventTypeNameToValueMap.put("TYPE_WINDOW_STATE_CHANGED", 32);
        sEventTypeNameToValueMap.put("TYPE_NOTIFICATION_STATE_CHANGED", 64);
    }

    /**
     *  Mapping from queue mode names to queue modes.
     */
    private static final HashMap<String, Integer> sQueueModeNameToQueueModeMap = new HashMap<String, Integer>();
    static {
        sQueueModeNameToQueueModeMap.put("INTERRUPT", 0);
        sQueueModeNameToQueueModeMap.put("QUEUE", 1);
        sQueueModeNameToQueueModeMap.put("COMPUTE_FROM_EVENT_CONTEXT", 2);
        sQueueModeNameToQueueModeMap.put("UNINTERRUPTIBLE", 3);
    }

    /**
     * Meta-data of how the utterance should be spoken. It is a key
     * value mapping to enable extending the meta-data specification.
     */
    private final HashMap<String, Object> mMetadata = new HashMap<String, Object>();

    /**
     *  filter for matching an event
     */
    private final Filter mFilter;

    /**
     *  formatter for building an utterance
     */
    private final Formatter mFormatter;

    // the index of this rule
    private final int mRuleIndex;

    /**
     * The context in which this speech rule operates.
     */
    private Context mContext;

    /**
     * The location of the APK from which to load classes. This is
     * required since we need to load the plug-in classes through
     * the TalkBack class loader.
     */
    private String mPublicSourceDir;

    /**
     * Creates a new speech rule that loads resources form the given
     * <code>context</code> and classes from the APK specified by the
     * <code>publicSourceDird</code>. If the former is not provided resources
     * are loaded form the TalkBack context. If the latter is not provided
     * class are loaded from the current TalkBack APK. The speech rule content
     * is loaded from a DOM <code>node</code> and a <code>ruleIndex</code>
     * is assigned to the rule.
     * 
     * @throws IllegalStateException If the tries to load custom filter/formatter
     *         while <code>customInstancesSupported</code> is false;
     */
    private SpeechRule(Context context, String publicSourceDird, Node node, int ruleIndex) {
        ensureCompoundButtonWorkaround();

        mContext = context;
        mPublicSourceDir = publicSourceDird;

        Filter filter = null;
        Formatter formatter = null;

        // avoid call to Document#getNodesByTagName, it traverses the entire
        // document
        NodeList children = node.getChildNodes();
        for (int i = 0, count = children.getLength(); i < count; i++) {
            Node child = children.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String nodeName = getUnqualifiedNodeName(child);
            if (NODE_NAME_METADATA.equalsIgnoreCase(nodeName)) {
                populateMetadata(child);
            } else if (NODE_NAME_FILTER.equals(nodeName)) {
                filter = createFilter(child);
            } else if (NODE_NAME_FORMATTER.equals(nodeName)) {
                formatter = createFormatter(child);
            }
        }

        mFilter = filter;
        mFormatter = formatter;
        mRuleIndex = ruleIndex;
    }

    /**
     * @return This rule's filter.
     */
    public Filter getFilter() {
        return mFilter;
    }

    /**
     * @return This rule's formatter.
     */
    public Formatter getFormatter() {
        return mFormatter;
    }

    /**
     * This is a workaround for the addition of checked/not checked
     * message generated by the framework which should actually
     * happen the the accessibility service.
     */
    private void ensureCompoundButtonWorkaround() {
        if (sValueChecked == null || sValueNotChecked == null) {
            Context context = TalkBackService.asContext();
            sValueChecked = context.getString(R.string.value_checked);
            sValueNotChecked = context.getString(R.string.value_not_checked);
        }
    }

    /**
     * Applies this rule to an {@link AccessibilityEvent}. If the event is
     * accepted by the {@link Filter} the rule's {@link Formatter} is used to
     * populate a formatted {@link Utterance}.
     *
     * @param event The event to which to apply the rule.
     * @param activity The class name of the current activity.
     * @param utterance Utterance to populate if the event is accepted.
     * @param filterArgs Addition arguments to the filter.
     * @param formatterArgs Addition arguments to the formatter.
     * @return True if the event matched the filter, false otherwise.
     */
    public boolean apply(AccessibilityEvent event, String activity, Utterance utterance,
            Map<Object, Object> filterArgs, Map<Object, Object> formatterArgs) {
        // no filter matches all events
        // no formatter drops the event on the floor
        boolean matched = (mFilter == null || mFilter.accept(event, mContext, activity,
                filterArgs));
        boolean hasFormatter = (mFormatter != null);

        if (matched) {
            utterance.getMetadata().putAll(mMetadata);
            if (hasFormatter) {
                mFormatter.format(event, mContext, utterance, formatterArgs);
            }
        }

        return matched;
    }

    /**
     * Populates the meta-data which determines how an {@link Utterance}
     * formatted by this rule should be announced.
     *
     * @param node The meta-data node to parse.
     */
    private void populateMetadata(Node node) {
        NodeList metadata = node.getChildNodes();

        for (int i = 0, count = metadata.getLength(); i < count; i++) {
            Node child = metadata.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String unqualifiedName = getUnqualifiedNodeName(child);
            String textContent = getTextContent(child);
            Object parsedValue = null;

            if (PROPERTY_QUEUING.equals(unqualifiedName)) {
                parsedValue = sQueueModeNameToQueueModeMap.get(textContent);
            } else {
                parsedValue = parsePropertyValue(unqualifiedName, textContent);
            }

            mMetadata.put(unqualifiedName, parsedValue);
        }
    }

    /**
     * Parses a property according to its expected type. Parsing failures
     * are logged and null is returned.
     *
     * @param name The property name.
     * @param value The property value.
     * @return The parsed value or null if parse error occurs.
     */
    static Object parsePropertyValue(String name, String value) {
        if (isIntegerProperty(name)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException nfe) {
                Log.w(LOG_TAG, "Property: '" + name + "' not interger. Ignoring!");
                return null;
            }
        } else if (isFloatProperty(name)) {
            try {
                return Float.parseFloat(value);
            } catch (NumberFormatException nfe) {
                Log.w(LOG_TAG, "Property: '" + name + "' not float. Ignoring!");
                return null;
            }
        } else if (isBooleanProperty(name)) {
            return Boolean.parseBoolean(value);
        } else if (isStringProperty(name)) {
            return value;
        } else {
            throw new IllegalArgumentException("Unknown property: " + name);
        }
    }

    /**
     * Returns if a property is an integer.
     *
     * @param propertyName The property name. 
     * @return True if the property is an integer, false otherwise.
     */
    static boolean isIntegerProperty(String propertyName) {
        return (PROPERTY_EVENT_TYPE.equals(propertyName) ||
            PROPERTY_ITEM_COUNT.equals(propertyName) ||
            PROPERTY_CURRENT_ITEM_INDEX.equals(propertyName) ||
            PROPERTY_FROM_INDEX.equals(propertyName) ||
            PROPERTY_ADDED_COUNT.equals(propertyName) ||
            PROPERTY_REMOVED_COUNT.equals(propertyName) ||
            PROPERTY_QUEUING.equals(propertyName));
    }

    /**
     * Returns if a property is a float.
     *
     * @param propertyName The property name. 
     * @return True if the property is a float, false otherwise.
     */
    static boolean isFloatProperty(String propertyName) {
        return PROPERTY_EVENT_TIME.equals(propertyName);
    }

    /**
     * Returns if a property is a string.
     *
     * @param propertyName The property name. 
     * @return True if the property is a string, false otherwise.
     */
    static boolean isStringProperty(String propertyName) {
        return (PROPERTY_PACKAGE_NAME.equals(propertyName) ||
                PROPERTY_CLASS_NAME.equals(propertyName) ||
                PROPERTY_TEXT.equals(propertyName) ||
                PROPERTY_BEFORE_TEXT.equals(propertyName) ||
                PROPERTY_CONTENT_DESCRIPTION.equals(propertyName) ||
                PROPERTY_ACTIVITY.equals(propertyName));
    }

    /**
     * Returns if a property is a boolean.
     *
     * @param propertyName The property name. 
     * @return True if the property is a boolean, false otherwise.
     */
    static boolean isBooleanProperty(String propertyName) {
        return (PROPERTY_CHECKED.equals(propertyName) ||
                PROPERTY_ENABLED.equals(propertyName) ||
                PROPERTY_FULL_SCREEN.equals(propertyName) ||
                PROPERTY_PASSWORD.equals(propertyName));
    }

    /**
     * Create a {@link Filter} given a DOM <code>node</code>.
     * 
     * @param node The node.
     * @return The created filter.
     */
    private Filter createFilter(Node node) {
        NodeList children = node.getChildNodes();

        // do we have a custom filter
        for (int i = 0, count = children.getLength(); i < count; i++) {
            Node child = children.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String nodeName = getUnqualifiedNodeName(child);
            if (NODE_NAME_CUSTOM.equals(nodeName)) {
                return createNewInstance(getTextContent(child), Filter.class);
            }
        }

        return new DefaultFilter(mContext, node);
    }

    /**
     * Creates a {@link Formatter} given a DOM <code>node</code>.
     * 
     * @param node The node.
     * @return The created formatter.
     */
    private Formatter createFormatter(Node node) {
        NodeList children = node.getChildNodes();

        for (int i = 0, count = children.getLength(); i < count; i++) {
            Node child = children.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String nodeName = getUnqualifiedNodeName(child);
            if (NODE_NAME_CUSTOM.equals(nodeName)) {
                return createNewInstance(getTextContent(child), Formatter.class);
            }
        }

        return new DefaultFormatter(node);
    }

    /**
     * Creates a new instance given a <code>className</code> and the
     * <code>expectedClass</code> that instance must belong to.
     *
     * @param className the class name.
     * @return New instance if succeeded, null otherwise.
     */
    @SuppressWarnings("unchecked")
    // the possible ClassCastException is handled by the method
    private <T> T createNewInstance(String className, Class<T> expectedClass) {
        try {
            Class<T> clazz = null;
            // if we are loaded by the context class loader => use the latter
            if (mContext.getClassLoader() == this.getClass().getClassLoader()) {
                clazz = (Class<T>) mContext.getClassLoader().loadClass(className);
            } else {
                // It is important to load the plug-in classes via the TalkBack
                // ClassLoader to achieve interoperability of the classes of the
                // different APKs. Form VM perspective a class loaded by two
                // different class loaders is considered as two separate classes. 
                DexFile dexFile = new DexFile(new File(mPublicSourceDir));
                ClassLoader classLoader = TalkBackService.asContext().getClassLoader();
                clazz = dexFile.loadClass(className, classLoader);
            }
            return clazz.newInstance();           
        } catch (ClassNotFoundException cnfe) {
            Log.e(LOG_TAG, "Rule: #" + mRuleIndex + ". Could not load class: '" + className
                    + "'.", cnfe);
        } catch (InstantiationException ie) {
            Log.e(LOG_TAG, "Rule: #" + mRuleIndex + ". Could not instantiate class: '" + className
                    + "'.", ie);
        } catch (IllegalAccessException iae) {
            Log.e(LOG_TAG, "Rule: #" + mRuleIndex + ". Could not instantiate class: '" + className
                    + "'.", iae);
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "Rule: #" + mRuleIndex + ". Could not instantiate class: '" + className
                    + "'.", ioe);
        } catch (NullPointerException npe) {
            Log.e(LOG_TAG, "Rule: #" + mRuleIndex + ". Could not instantiate class: '" + className
                    + "'.", npe);
        }

        return null;
    }

    /**
     * Factory method that creates all speech rules from the DOM representation
     * of a speechstrategy.xml. This class does not verify if the
     * <code>document</code> is well-formed and it is responsibility of the
     * client to do that.
     *
     * @param context A {@link Context} instance for loading resources.
     * @param publicSourceDir The location of the plug-in APK for loading classes.
     * @param document The parsed XML.
     * @return The list of loaded speech rules.
     */
    public static ArrayList<SpeechRule> createSpeechRules(Context context, String publicSourceDir,
            Document document)  throws IllegalStateException {
        ArrayList<SpeechRule> speechRules = new ArrayList<SpeechRule>();

        if (document == null || context == null) {
            return speechRules;
        }

        NodeList children = document.getDocumentElement().getChildNodes();
        for (int i = 0, count = children.getLength(); i < count; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                try {
                    speechRules.add(new SpeechRule(context, publicSourceDir, child, i));
                } catch (IllegalStateException ise) {
                    Log.w(LOG_TAG, "Failed loading speech rule: " + getTextContent(child), ise);
                }
            }
        }
        Log.d(LOG_TAG, speechRules.size() + " speech rules appended");

        return speechRules;
    }

    /**
     * Returns the form the given <code>context</code> the text content
     * of a <code>node</code> after it has been localized.
     */
    static String getLocalizedTextContent(Context context, Node node) {
        String textContent = getTextContent(node);
        return getStringResource(context, textContent);
    }

    /**
     * Returns a string resource from a <code>context</code> given its
     * <code>resourceIndetifier</code>.
     * <p>
     * Note: The resource identifier format is: @<package name>:string/<resource
     * name>
     *</p>
     */
    static String getStringResource(Context context, String resourceIdentifier) {
        if (resourceIdentifier.startsWith("@")) {
            int id = context.getResources().getIdentifier(resourceIdentifier.substring(1), null,
                    null);
            return context.getString(id);
        }
        return resourceIdentifier;
    }

    /**
     * Returns the text content of a given <code>node</code> by performing a
     * preorder traversal of the tree rooted at that node. </p> Note: Android
     * Java implementation is not compatible with Java 5.0 which provides such a
     * method.
     *
     * @param node The node.
     * @return The text content.
     */
    private static String getTextContent(Node node) {
        StringBuilder builder = sTempBuilder;
        getTextContentRecursive(node, builder);
        String text = builder.toString();
        builder.delete(0, builder.length());
        return text;
    }

    /**
     * Performs a recursive preorder traversal of a DOM tree and aggregating the
     * text content.
     * 
     * @param node The currently explored node.
     * @param builder Builder that aggregates the text content.
     */
    private static void getTextContentRecursive(Node node, StringBuilder builder) {
        NodeList children = node.getChildNodes();
        for (int i = 0, count = children.getLength(); i < count; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                builder.append(child.getNodeValue());
            }
            getTextContentRecursive(child, builder);
        }
    }

    /**
     * Returns the unqualified <code>node</code> name i.e. without the prefix.
     * 
     * @param node The node.
     * @return The unqualified name.
     */
    private static String getUnqualifiedNodeName(Node node) {
        String nodeName = node.getNodeName();
        int colonIndex = nodeName.indexOf(COLON);
        if (colonIndex > -1) {
            nodeName = nodeName.substring(colonIndex + 1);
        }
        return nodeName;
    }

    /**
     * Gets the text of an <code>event</code> by concatenating the text members
     * (regardless of their priority) using space as a delimiter.
     *
     * @param context The context from which to load required resources.
     * @param event The event.
     * @return The event text.
     */
    static StringBuilder getEventText(Context context, AccessibilityEvent event) {
        StringBuilder aggregator = new StringBuilder();
        List<CharSequence> eventText = event.getText();
        if (context == null) {
            String s = "";
        }
        Class<?> eventClass = ClassLoadingManager.getInstance().loadOrGetCachedClass(
                context, event.getClassName().toString(),
                event.getPackageName().toString());

        // here we have a special case since the framework is adding
        // the string for the state of a CompoundButton but we also get the isChecked attribute
        int stateStringIndex = -1;
        if (eventClass != null && CompoundButton.class.isAssignableFrom(eventClass)) {
            for (int i = 0, count = eventText.size(); i < count; i++) {
                CharSequence next = eventText.get(i);
                if (sValueChecked.equals(next) || sValueNotChecked.equals(next)) {
                    stateStringIndex = i;
                    break;
                }
            }
        }

        for (int i = 0, count = eventText.size(); i < count; i++) {
            if (i != stateStringIndex) {
                aggregator.append(eventText.get(i));
                aggregator.append(SPACE);
            }
        }

        if (aggregator.length() > 0) {
            aggregator.deleteCharAt(aggregator.length() - 1);
        } else { // use content description if no text
            CharSequence contentDescription = event.getContentDescription();
            if (contentDescription != null) {
              aggregator.append(contentDescription);
            }
        }
        return aggregator;
    }

    /**
     * Represents a default filter determining if the rule applies to a given
     * {@link AccessibilityEvent}.
     */
    class DefaultFilter implements Filter {

        private final Properties mFilterProperties = new Properties();

        DefaultFilter(Context context, Node node) {
            mContext = context;
            NodeList properties = node.getChildNodes();

            for (int i = 0, count = properties.getLength(); i < count; i++) {
                Node child = properties.item(i);

                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                String unqualifiedName = getUnqualifiedNodeName(child);
                String textContent = getTextContent(child);
                Object parsedValue = null;

                if (PROPERTY_EVENT_TYPE.equals(unqualifiedName)) {
                    parsedValue = sEventTypeNameToValueMap.get(textContent);
                } else {
                    parsedValue = parsePropertyValue(unqualifiedName, textContent);
                }

                mFilterProperties.put(unqualifiedName, parsedValue);
            }
        }

        @Override
        public boolean accept(AccessibilityEvent event, Context context, String activity,
                Object args) {
            // the order here matters and is from most frequently used to less
            if (!acceptProperty(PROPERTY_EVENT_TYPE, event.getEventType())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_PACKAGE_NAME, event.getPackageName())) {
                return false;
            }
            if (!acceptClassNameProperty(event)) { // special case
                return false;
            }
            if (!acceptProperty(PROPERTY_TEXT, getEventText(context, event).toString())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_BEFORE_TEXT, event.getBeforeText())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_CONTENT_DESCRIPTION, event.getContentDescription())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_EVENT_TIME, event.getEventTime())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_ITEM_COUNT, event.getItemCount())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_CURRENT_ITEM_INDEX, event.getCurrentItemIndex())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_FROM_INDEX, event.getFromIndex())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_CHECKED, event.isChecked())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_ENABLED, event.isEnabled())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_FULL_SCREEN, event.isFullScreen())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_PASSWORD, event.isPassword())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_ADDED_COUNT, event.getAddedCount())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_REMOVED_COUNT, event.getRemovedCount())) {
                return false;
            }
            if (!acceptProperty(PROPERTY_ACTIVITY, activity)) {
                return false;
            }

            return true;
        }

        /**
         * @return The package name accepted by this filter or null if no such.
         */
        public String getAcceptedPackageName() {
            return mFilterProperties.getProperty(PROPERTY_PACKAGE_NAME);
        }

        /**
         * Checks if the filter accepts a <code>property</code> with a given
         * <code>value</code>.
         *
         * @param property The property.
         * @param eventValue The value.
         * @return True if the filter accepts the property, false otherwise.
         */
        private boolean acceptProperty(String property, Object eventValue) {
            Object acceptedValue = mFilterProperties.get(property);

            if (acceptedValue == null) {
                return true;
            }

            if (eventValue == null) {
                return false;
            }

            return acceptedValue.equals(eventValue);
        }

        /**
         * Checks if the className filtering property accepts an
         * <code>event</code>. </p> Note: This is a special case because the
         * check is if the event class is a subclass of the filtering class.
         *
         * @param event The event.
         * @return True if the event is accepted, false otherwise.
         */
        private boolean acceptClassNameProperty(AccessibilityEvent event) {
            String filteringClassName = mFilterProperties.getProperty(PROPERTY_CLASS_NAME);
            // no filter condition - we are good
            if (filteringClassName == null) {
                return true;
            }

            // try a shortcut for efficiency
            String eventClassName = event.getClassName().toString();
            if (filteringClassName.equals(eventClassName)) {
                return true;
            }

            String filteringPackageName = mFilterProperties.getProperty(PROPERTY_PACKAGE_NAME);

            Class<?> filteringClass = ClassLoadingManager.getInstance().loadOrGetCachedClass(
                    mContext, filteringClassName, filteringPackageName);
            Class<?> eventClass = ClassLoadingManager.getInstance().loadOrGetCachedClass(mContext,
                    event.getClassName().toString(), event.getPackageName().toString());

            if (filteringClass == null || eventClass == null) {
                return false;
            } else {
                return (filteringClass.isAssignableFrom(eventClass));
            }
        }
    }

    /**
     * This class is default formatter for building utterance for announcing an
     * {@link AccessibilityEvent}. The formatting strategy is to populate a
     * template with event properties or strings selected via a regular
     * expression from the event's text. If no template is provided the
     * properties or regular expression selections are concatenated with space
     * as delimiter.
     */
    class DefaultFormatter implements Formatter {

        // node names

        private static final String NODE_NAME_TEMPLATE = "template";

        private static final String NODE_NAME_SPLIT = "split";

        private static final String NODE_NAME_REGEX = "regex";

        private static final String NODE_NAME_PROPERTY = "property";

        /**
         *  optional template to populate with selected values
         */
        private final String mTemplate;

        private final List<StringPair> mSelectors;

        /**
         * Creates a new formatter from a given DOM {@link Node}.
         *
         * @param node The node.
         */
        DefaultFormatter(Node node) {
            mSelectors = new ArrayList<StringPair>();
            String template = null;

            NodeList children = node.getChildNodes();
            for (int i = 0, count = children.getLength(); i < count; i++) {
                Node child = children.item(i);

                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                String unqualifiedName = getUnqualifiedNodeName(child);
                // some elements contain mandatory reference to a string
                // resource
                if (NODE_NAME_TEMPLATE.equals(unqualifiedName)) {
                    template = getLocalizedTextContent(mContext, child);
                } else if (NODE_NAME_PROPERTY.equals(unqualifiedName)
                        || NODE_NAME_SPLIT.equals(unqualifiedName)) {
                    mSelectors.add(new StringPair(unqualifiedName, getLocalizedTextContent(mContext,
                            child)));
                } else {
                    mSelectors.add(new StringPair(unqualifiedName, getTextContent(child)));
                }
            }

            mTemplate = template;
        }

        @Override
        public void format(AccessibilityEvent event, Context context, Utterance utterance,
                Object args) {
            List<StringPair> selectors = mSelectors;
            Object[] arguments = new Object[selectors.size()];

            for (int i = 0, count = selectors.size(); i < count; i++) {
                StringPair selector = selectors.get(i);
                String selectorType = selector.mFirst;
                String selectorValue = selector.mSecond;

                if (NODE_NAME_SPLIT.equals(selectorType)) {
                    arguments = splitEventText(selectorValue, event);
                    break;
                }

                if (NODE_NAME_PROPERTY.equals(selectorType)) {
                    Object propertyValue = getPropertyValue(selectorValue, event);
                    arguments[i] = propertyValue != null ? propertyValue : "";
                } else if (NODE_NAME_REGEX.equals(selectorType)) {
                    arguments[i] = getEventTextRegExpMatch(event, selectorValue);
                } else {
                    throw new IllegalArgumentException("Unknown selector type: [" + selector.mFirst
                            + selector.mSecond + "]");
                }
            }

            formatTemplateOrAppendSpaceSeparatedValueIfNoTemplate(utterance, arguments);
        }

        /**
         * Returns a substring from a <code>event</code> text that matches a
         * given <code>regExp</code>. If the regular expression does not find a
         * match the empty string is returned.
         * 
         * @param event The event.
         * @param regExp The regular expression.
         * @return The matched string, the empty string otherwise.
         */
        private String getEventTextRegExpMatch(AccessibilityEvent event, String regExp) {
            StringBuilder text = getEventText(mContext, event);
            Pattern pattern = Pattern.compile(regExp);
            Matcher matcher = pattern.matcher(text);

            if (matcher.find()) {
                return text.substring(matcher.start(), matcher.end());
            } else {
                return EMPTY_STRING;
            }
        }

        /**
         * Splits the test of an <code>event</code> via a <code>regExp</code>.
         * 
         * @param event The event.
         * @param regExp the regular expression.
         * @return The split.
         */
        private String[] splitEventText(String regExp, AccessibilityEvent event) {
            String text = getEventText(mContext, event).toString();
            return text.split(regExp);
        }

        /**
         * Returns the value of a given <code>property</code> of an
         * <code>event</code>.
         * 
         * @param property The property
         * @param event The event.
         * @return the value.
         */
        private Object getPropertyValue(String property, AccessibilityEvent event) {
            // no reflection to avoid performance hit
            if (PROPERTY_EVENT_TYPE.equals(property)) {
                return event.getEventType();
            } else if (PROPERTY_PACKAGE_NAME.equals(property)) {
                return event.getPackageName();
            } else if (PROPERTY_CLASS_NAME.equals(property)) {
                return event.getClassName();
            } else if (PROPERTY_TEXT.equals(property)) {
                return getEventText(mContext, event); // special case
            } else if (PROPERTY_BEFORE_TEXT.equals(property)) {
                return event.getBeforeText();
            } else if (PROPERTY_CONTENT_DESCRIPTION.equals(property)) {
                return event.getContentDescription();
            } else if (PROPERTY_EVENT_TIME.equals(property)) {
                return event.getEventTime();
            } else if (PROPERTY_ITEM_COUNT.equals(property)) {
                return event.getItemCount();
            } else if (PROPERTY_CURRENT_ITEM_INDEX.equals(property)) {
                return event.getCurrentItemIndex();
            } else if (PROPERTY_FROM_INDEX.equals(property)) {
                return event.getFromIndex();
            } else if (PROPERTY_CHECKED.equals(property)) {
                return event.isChecked();
            } else if (PROPERTY_ENABLED.equals(property)) {
                return event.isEnabled();
            } else if (PROPERTY_FULL_SCREEN.equals(property)) {
                return event.isFullScreen();
            } else if (PROPERTY_PASSWORD.equals(property)) {
                return event.isPassword();
            } else if (PROPERTY_ADDED_COUNT.equals(property)) {
                return event.getAddedCount();
            } else if (PROPERTY_REMOVED_COUNT.equals(property)) {
                return event.getRemovedCount();
            } else {
                throw new IllegalArgumentException("Unknown property : " + property);
            }
        }

        /**
         * Replaces template parameters in the template for this
         * {@link SpeechRule} with the <code>arguments</code> in case such a
         * template was provided. If no template was provided the arguments are
         * concatenated using space as delimiter. The <code>utterance</code>
         * is populated with the generated text.
         * 
         * @param utterance The builder to which to append the utterance.
         * @param arguments The formatting arguments.
         */
        private void formatTemplateOrAppendSpaceSeparatedValueIfNoTemplate(Utterance utterance,
                Object[] arguments) {
            final StringBuilder utteranceText = utterance.getText();

            if (mTemplate != null) {
                try {
                    sStirngFormatter.format(mTemplate, arguments);
                    StringBuilder formatterBuilder = (StringBuilder) sStirngFormatter.out();
                    utteranceText.append(formatterBuilder);
                    // clear the builder of the formatter
                    formatterBuilder.delete(0, formatterBuilder.length());
                } catch (MissingFormatArgumentException mfae) {
                    Log.e(LOG_TAG, "Speech rule: '" + mRuleIndex + " has inconsistency between "
                            + "template: '" + mTemplate + "' and arguments: '" + arguments
                            + "'. Possibliy #template arguments does not match #parameters", mfae);
                }
            } else {
                for (Object arg : arguments) {
                    utteranceText.append(arg);
                    utteranceText.append(SPACE);
                }
                if (utteranceText.length() > 0) {
                    utteranceText.deleteCharAt(utteranceText.length() - 1);
                }
            }
        }

        /**
         * Utility class to store a pair of elements.
         */
        class StringPair {
            String mFirst;
            String mSecond;

            /**
             * Creates a new instance.
             *
             * @param first The first element of the pair.
             * @param second The second element of the pair.
             */
            StringPair(String first, String second) {
                mFirst = first;
                mSecond = second;
            }
        }
    }  
}
