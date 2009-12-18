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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /**
     *  reusable builder to avoid object creation
     */
    private static final StringBuilder sTempBuilder = new StringBuilder();

    /**
     *  standard, reusable string formatter for populating utterance template
     */
    private static final java.util.Formatter sStirngFormatter = new java.util.Formatter();

    /**
     *  Mapping from class names to classes form outside package - serves as cache for performance
     */
    private static final HashMap<String, Class<?>> sClassNameToOutsidePackageClassMap = new HashMap<String, Class<?>>();

    /**
     * The context in which the service operates.
     */
    private static Context sContext;

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
        sQueueModeNameToQueueModeMap.put("QUEUE", 1);
        sQueueModeNameToQueueModeMap.put("INTERRUPT", 2);
        sQueueModeNameToQueueModeMap.put("COMPUTE_FROM_EVENT_CONTEXT", 3);
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
     * Creates a new speech rule from a given DOM {@link Node}.
     */
    private SpeechRule(Node node, int ruleIndex) {
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
                pupulateMetadata(child);
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
     * Applies this rule to an {@link AccessibilityEvent}. If the event is
     * accepted by the {@link Filter} the rule's {@link Formatter} is used to
     * populate a formatted {@link Utterance}.
     *
     * @param event The event to which to apply the rule.
     * @param utterance Utterance to populate if the event is accepted.
     * @return True if the event matched the filter, false otherwise.
     */
    public boolean apply(AccessibilityEvent event, Utterance utterance) {
        // no filter matches all events
        // no formatter drops the event on the floor
        boolean matched = (mFilter == null || mFilter.accept(event));
        boolean hasFormatter = (mFormatter != null);

        if (matched) {
            utterance.getMetadata().putAll(mMetadata);
            if (hasFormatter) {
                mFormatter.format(event, utterance);
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
    private void pupulateMetadata(Node node) {
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
                PROPERTY_CONTENT_DESCRIPTION.equals(propertyName));
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

        return new DefaultFilter(node);
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
     * @param expectedClass The class the instance must belong to.
     * @return New instance if succeeded, null otherwise.
     */
    @SuppressWarnings("unchecked")
    // the possible ClassCastException is handled by the method
    private <T> T createNewInstance(String className, Class<T> expectedClass) {
        try {
            Class<T> clazz = (Class<T>) sContext.getClassLoader().loadClass(className);
            return clazz.newInstance();
        } catch (ClassNotFoundException cnfe) {
            Log.e(LOG_TAG, "Rule: #" + mRuleIndex + ". Could not load class: '" + className
                    + "'. Possibly a typo or the class is not in the TalkBack package");
        } catch (InstantiationException ie) {
            Log.e(LOG_TAG, "Rule: #" + mRuleIndex + ". Could not instantiate class: '" + className
                    + "'");
        } catch (IllegalAccessException iae) {
            Log.e(LOG_TAG, "Rule: #" + mRuleIndex + ". Could not instantiate class: '" + className
                    + "'. Possibly you do not have a public no arguments constructor.");
        } catch (ClassCastException cce) {
            Log.e(LOG_TAG, "Rule: #" + mRuleIndex + ". Could not instantiate class: '" + className
                    + "'. The class is not instanceof: '" + expectedClass + "'.");
        }

        return null;
    }

    /**
     * Factory method that creates all speech rules from the DOM representation
     * of a speechstrategy.xml. This class does not verify if the
     * <code>document</code> is well-formed and it is responsibility of the
     * client to do that.
     * 
     * @param document The parsed XML.
     * @param context A {@link Context} instance.
     * @return Speech rules if such were defined in the document or an empty
     *         list if no rules are defined or the document is null.
     */
    public static ArrayList<SpeechRule> createSpeechRules(Document document, Context context) {
        ArrayList<SpeechRule> speechRules = new ArrayList<SpeechRule>();

        if (document != null) {
            sContext = context;

            NodeList children = document.getDocumentElement().getChildNodes();
            for (int i = 0, count = children.getLength(); i < count; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    speechRules.add(new SpeechRule(child, i));
                }
            }
            Log.d(LOG_TAG, speechRules.size() + " speech rules loaded");
        }

        return speechRules;
    }

    /**
     * Returns the text content of a <code>node</code> after it has been
     * localized.
     */
    static String getLocalizedTextContent(Node node) {
        String textContent = getTextContent(node);
        return getStringResource(textContent);
    }

    /**
     * Returns a string resource given its <code>resourceIndetifier</code>. </p>
     * Note: The resource identifier format is: @<package name>:string/<resource
     * name>
     * 
     * @param resourceIdentifier The identifier of the resource to get.
     * @return The localized text content.
     */
    static String getStringResource(String resourceIdentifier) {
        if (resourceIdentifier.startsWith("@")) {
            int id = sContext.getResources().getIdentifier(resourceIdentifier.substring(1), null,
                    null);
            return sContext.getString(id);
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
     * @param event The event.
     * @return The event text.
     */
    static StringBuilder getEventText(AccessibilityEvent event) {
        StringBuilder aggregator = new StringBuilder();
        for (CharSequence text : event.getText()) {
            aggregator.append(text);
            aggregator.append(SPACE);
        }
        if (aggregator.length() > 0) {
            aggregator.deleteCharAt(aggregator.length() - 1);
        }
        return aggregator;
    }

    /**
     * Represents a default filter determining if the rule applies to a given
     * {@link AccessibilityEvent}.
     */
    static class DefaultFilter implements Filter {

        private final Properties mFilterProperties = new Properties();

        DefaultFilter(Node node) {
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
        public boolean accept(AccessibilityEvent event) {
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
            if (!acceptProperty(PROPERTY_TEXT, getEventText(event).toString())) {
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

            return true;
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
            String eventPackageName = event.getPackageName().toString();

            Class<?> filteringClass = loadOrGetCachedClass(filteringClassName,
                    filteringPackageName);
            Class<?> eventClass = loadOrGetCachedClass(event.getClassName().toString(), event
                    .getPackageName().toString());

            if (filteringClass == null || eventClass == null) {
                return false;
            } else {
                return (filteringClass.isAssignableFrom(eventClass));
            }
        }

        /**
         * Returns a class by given <code>className</code>. The loading proceeds
         * as follows: </br> 1. Try to load with the current context class
         * loader (it caches loaded classes). </br> 2. If (1) fails try if we
         * have loaded the class before and return it if that is the cases.
         * </br> 3. If (2) failed, try to create a package context and load the
         * class. </p> Note: If the package name is null and an attempt for
         * loading of a package context is required the it is extracted from the
         * class name.
         * 
         * @param className The name of the class to load.
         * @param packageName The name of the package to which the class
         *            belongs.
         * @return The class if loaded successfully, null otherwise.
         */
        private static Class<?> loadOrGetCachedClass(String className, String packageName) {
            try {
                // try the current ClassLoader first
                return sContext.getClassLoader().loadClass(className);
            } catch (ClassNotFoundException cnfe) {
                // do we have a cached class
                Class<?> clazz = sClassNameToOutsidePackageClassMap.get(className);
                if (clazz != null) {
                    return clazz;
                }
                // no package - get it from the class name
                if (packageName == null) {
                    int lastDotIndex = className.lastIndexOf(".");
                    if (lastDotIndex > -1) {
                        packageName = className.substring(0, lastDotIndex);
                    } else {
                        return null;
                    }
                }
                // all failed - try via creating a package context
                try {
                    int flags = (Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                    Context context = sContext.getApplicationContext().createPackageContext(
                            packageName, flags);
                    clazz = context.getClassLoader().loadClass(className);
                    sClassNameToOutsidePackageClassMap.put(className, clazz);
                    return clazz;
                } catch (NameNotFoundException nnfe) {
                    Log.e(LOG_TAG, "Error during loading an event source class: " + className + " "
                            + nnfe);
                } catch (ClassNotFoundException cnfe2) {
                    Log.e(LOG_TAG, "Error during loading an event source class: " + className + " "
                            + cnfe);
                }

                return null;
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
            String template = null;
            mSelectors = new ArrayList<StringPair>();

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
                    template = getLocalizedTextContent(child);
                } else if (NODE_NAME_PROPERTY.equals(unqualifiedName)
                        || NODE_NAME_SPLIT.equals(unqualifiedName)) {
                    mSelectors.add(new StringPair(unqualifiedName, getLocalizedTextContent(child)));
                } else {
                    mSelectors.add(new StringPair(unqualifiedName, getTextContent(child)));
                }
            }

            mTemplate = template;
        }

        @Override
        public void format(AccessibilityEvent event, Utterance utterance) {
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
                    arguments[i] = getPropertyValue(selectorValue, event);
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
            StringBuilder text = getEventText(event);
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
            String text = getEventText(event).toString();
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
                return getEventText(event); // special case
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

