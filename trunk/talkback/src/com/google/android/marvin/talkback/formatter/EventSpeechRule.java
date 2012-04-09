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

package com.google.android.marvin.talkback.formatter;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.AccessibilityEventUtils;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.utils.ClassLoadingManager;
import com.google.android.marvin.utils.NodeUtils;
import com.google.android.marvin.utils.PackageManagerUtils;
import com.googlecode.eyesfree.utils.LogUtils;

import dalvik.system.DexFile;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.MissingFormatArgumentException;
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
public class EventSpeechRule {

    // string constants
    private static final String SPACE = " ";
    private static final String COLON = ":";

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
    private static final String PROPERTY_TO_INDEX = "toIndex";
    private static final String PROPERTY_SCROLLABLE = "scrollable";
    private static final String PROPERTY_SCROLL_X = "scrollX";
    private static final String PROPERTY_SCROLL_Y = "scrollY";
    private static final String PROPERTY_RECORD_COUNT = "recordCount";
    private static final String PROPERTY_CHECKED = "checked";
    private static final String PROPERTY_ENABLED = "enabled";
    private static final String PROPERTY_FULL_SCREEN = "fullScreen";
    private static final String PROPERTY_PASSWORD = "password";
    private static final String PROPERTY_ADDED_COUNT = "addedCount";
    private static final String PROPERTY_REMOVED_COUNT = "removedCount";
    private static final String PROPERTY_QUEUING = "queuing";
    private static final String PROPERTY_EARCON = "earcon";
    private static final String PROPERTY_VIBRATION = "vibration";
    private static final String PROPERTY_CUSTOM_EARCON = "customEarcon";
    private static final String PROPERTY_CUSTOM_VIBRATION = "customVibration";
    private static final String PROPERTY_VERSION_CODE = "versionCode";
    private static final String PROPERTY_VERSION_NAME = "versionName";
    private static final String PROPERTY_PLATFORM_RELEASE = "platformRelease";
    private static final String PROPERTY_PLATFORM_SDK = "platformSdk";

    /**
     * Constant used for storing all speech rules that either do not define a
     * filter package or have custom filters.
     */
    private static final String UNDEFINED_PACKAGE_NAME = "undefined_package_name";

    /** Regular expression pattern for resource identifiers. */
    private static final Pattern mResourceIdentifier = Pattern.compile("@([\\w\\.]+:)?\\w+/\\w+");

    /** Reusable builder to avoid object creation. */
    private static final StringBuilder sTempBuilder = new StringBuilder();

    /** Mapping from event type name to its type. */
    private static final HashMap<String, Integer> sEventTypeNameToValueMap =
            new HashMap<String, Integer>();
    static {
        sEventTypeNameToValueMap.put("TYPE_VIEW_CLICKED", AccessibilityEvent.TYPE_VIEW_CLICKED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_LONG_CLICKED",
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_SELECTED", AccessibilityEvent.TYPE_VIEW_SELECTED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_FOCUSED", AccessibilityEvent.TYPE_VIEW_FOCUSED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_TEXT_CHANGED",
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
        sEventTypeNameToValueMap.put("TYPE_WINDOW_STATE_CHANGED",
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        sEventTypeNameToValueMap.put("TYPE_NOTIFICATION_STATE_CHANGED",
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_HOVER_ENTER",
                AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER);
        sEventTypeNameToValueMap.put("TYPE_VIEW_HOVER_EXIT",
                AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT);
        sEventTypeNameToValueMap.put("TYPE_TOUCH_EXPLORATION_GESTURE_START",
                AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_START);
        sEventTypeNameToValueMap.put("TYPE_TOUCH_EXPLORATION_GESTURE_END",
                AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_END);
        sEventTypeNameToValueMap.put("TYPE_WINDOW_CONTENT_CHANGED",
                AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_SCROLLED",
                AccessibilityEventCompat.TYPE_VIEW_SCROLLED);
        sEventTypeNameToValueMap.put("TYPE_VIEW_TEXT_SELECTION_CHANGED",
                AccessibilityEventCompat.TYPE_VIEW_TEXT_SELECTION_CHANGED);
    }

    /**
     * Mapping from queue mode names to queue modes.
     */
    private static final HashMap<String, Integer> sQueueModeNameToQueueModeMap =
            new HashMap<String, Integer>();
    static {
        sQueueModeNameToQueueModeMap.put("INTERRUPT", 0);
        sQueueModeNameToQueueModeMap.put("QUEUE", 1);
        sQueueModeNameToQueueModeMap.put("UNINTERRUPTIBLE", 2);
    }

    /**
     * Meta-data of how the utterance should be spoken. It is a key value
     * mapping to enable extending the meta-data specification.
     */
    private final Bundle mMetadata = new Bundle();

    /**
     * List of earcons resource identifiers that should be played with an
     * utterance.
     */
    private final List<Integer> mEarcons = new LinkedList<Integer>();

    /**
     * List of vibration pattern resource identifiers that should be played with
     * an utterance.
     */
    private final List<Integer> mVibrationPatterns = new LinkedList<Integer>();

    /**
     * List of resource identifiers for preference keys that correspond to
     * custom earcons that should be played with an utterance.
     */
    private final List<Integer> mCustomEarcons = new LinkedList<Integer>();

    /**
     * List of resource identifiers for preference keys that correspond to
     * custom vibration patterns that should be played with an utterance.
     */
    private final List<Integer> mCustomVibrations = new LinkedList<Integer>();

    /** Mapping from property name to property matcher. */
    private final HashMap<String, PropertyMatcher> mPropertyMatchers =
            new HashMap<String, PropertyMatcher>();

    /** Filter for matching an event. */
    private final AccessibilityEventFilter mFilter;

    /** Formatter for building an utterance. */
    private final AccessibilityEventFormatter mFormatter;

    /** The index of this rule within the global rule list. */
    private final int mRuleIndex;

    /** The context in which this speech rule operates. */
    private final Context mContext;

    /**
     * The speech strategy defined the rule.
     * <p>
     * Note: This is either a resource name or a URI.
     * </p>
     */
    private final String mSpeechStrategy;

    /**
     * The package targeted by this rule.
     */
    private String mPackageName;

    /**
     * The location of the APK from which to load classes. This is required
     * since we need to load the plug-in classes through the TalkBack class
     * loader.
     */
    private final String mPublicSourceDir;

    /**
     * The DOM node that defines this speech rule.
     */
    private final Node mNode;

    /** Lazily populated XML representation of this node. */
    private String mCachedXmlString;

    /**
     * Creates a new speech rule that loads resources form the given
     * <code>context</code> and classes from the APK specified by the
     * <code>publicSourceDird</code>. The rule is defined in the
     * <code>speechStrategy</code> and is targeted to the
     * <code>packageName</code>. If the former is not provided resources are
     * loaded form the TalkBack context. If the latter is not provided class are
     * loaded from the current TalkBack APK. The speech rule content is loaded
     * from a DOM <code>node</code> and a <code>ruleIndex</code> is assigned to
     * the rule.
     *
     * @throws IllegalStateException If the tries to load custom
     *             filter/formatter while <code>customInstancesSupported</code>
     *             is false;
     */
    private EventSpeechRule(Context context, String speechStrategy, String packageName,
            String publicSourceDird, Node node, int ruleIndex) {
        mContext = context;
        mSpeechStrategy = speechStrategy;
        mPackageName = packageName != null ? packageName : UNDEFINED_PACKAGE_NAME;
        mPublicSourceDir = publicSourceDird;
        mNode = node;

        AccessibilityEventFilter filter = null;
        AccessibilityEventFormatter formatter = null;

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
     * @return The XML representation of this rule.
     */
    public String asXmlString() {
        if (mCachedXmlString == null) {
            mCachedXmlString = NodeUtils.asXmlString(mNode);
        }

        return mCachedXmlString;
    }

    /**
     * @return The speech strategy that defined this rule.
     */
    public String getSpeechStrategy() {
        return mSpeechStrategy;
    }

    /**
     * @return The package targeted by this rule.
     */
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * @return This rule's filter.
     */
    public AccessibilityEventFilter getFilter() {
        return mFilter;
    }

    /**
     * @return This rule's formatter.
     */
    public AccessibilityEventFormatter getFormatter() {
        return mFormatter;
    }

    /**
     * Applies this rule to an {@link AccessibilityEvent}. If the event is
     * accepted by the {@link AccessibilityEventFilter} the rule's {@link AccessibilityEventFormatter} is used to
     * populate a formatted {@link Utterance}.
     *
     * @param event The event to which to apply the rule.
     * @param utterance Utterance to populate if the event is accepted.
     * @return True if the event matched the filter, false otherwise.
     */
    public boolean apply(AccessibilityEvent event, Utterance utterance) {
        // No filter matches all events.
        if (mFilter != null && !mFilter.accept(event, mContext)) {
            return false;
        }

        // No formatter means no utterance text.
        if (mFormatter != null && !mFormatter.format(event, mContext, utterance)) {
            return false;
        }

        utterance.getMetadata().putAll(mMetadata);
        utterance.getEarcons().addAll(mEarcons);
        utterance.getVibrationPatterns().addAll(mVibrationPatterns);
        utterance.getCustomEarcons().addAll(mCustomEarcons);
        utterance.getCustomVibrations().addAll(mCustomVibrations);

        return true;
    }

    /**
     * Populates the meta-data which determines how an {@link Utterance}
     * formatted by this rule should be announced.
     *
     * @param node The meta-data node to parse.
     */
    private void populateMetadata(Node node) {
        final NodeList metadata = node.getChildNodes();
        final int count = metadata.getLength();

        for (int i = 0; i < count; i++) {
            final Node child = metadata.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            final String unqualifiedName = getUnqualifiedNodeName(child);
            final String textContent = getTextContent(child);

            if (PROPERTY_QUEUING.equals(unqualifiedName)) {
                final int mode = sQueueModeNameToQueueModeMap.get(textContent);
                mMetadata.putInt(unqualifiedName, mode);
            } else if (PROPERTY_EARCON.equals(unqualifiedName)) {
                final int resId = getResourceIdentifierContent(mContext, textContent);
                mEarcons.add(resId);
            } else if (PROPERTY_VIBRATION.equals(unqualifiedName)) {
                final int resId = getResourceIdentifierContent(mContext, textContent);
                mVibrationPatterns.add(resId);
            } else if (PROPERTY_CUSTOM_EARCON.equals(unqualifiedName)) {
                mCustomEarcons.add(getResourceIdentifierContent(mContext, textContent));
            } else if (PROPERTY_CUSTOM_VIBRATION.equals(unqualifiedName)) {
                mCustomVibrations.add(getResourceIdentifierContent(mContext, textContent));
            } else {
                final String value = (String) parsePropertyValue(unqualifiedName, textContent);
                mMetadata.putString(unqualifiedName, value);
            }
        }
    }

    /**
     * Parses a property according to its expected type. Parsing failures are
     * logged and null is returned.
     *
     * @param name The property name.
     * @param value The property value.
     * @return The parsed value or null if parse error occurs.
     */
    private static Comparable<?> parsePropertyValue(String name, String value) {
        if (PROPERTY_EVENT_TYPE.equals(name)) {
            return sEventTypeNameToValueMap.get(value);
        }
        if (isIntegerProperty(name)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException nfe) {
                LogUtils.log(EventSpeechRule.class, Log.WARN, "Property: '%s' not interger. Ignoring!",
                        name);
                return null;
            }
        } else if (isFloatProperty(name)) {
            try {
                return Float.parseFloat(value);
            } catch (NumberFormatException nfe) {
                LogUtils.log(EventSpeechRule.class, Log.WARN, "Property: '%s' not float. Ignoring!",
                        name);
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
    private static boolean isIntegerProperty(String propertyName) {
        return (PROPERTY_EVENT_TYPE.equals(propertyName)
                || PROPERTY_ITEM_COUNT.equals(propertyName)
                || PROPERTY_CURRENT_ITEM_INDEX.equals(propertyName)
                || PROPERTY_FROM_INDEX.equals(propertyName)
                || PROPERTY_TO_INDEX.equals(propertyName) || PROPERTY_SCROLL_X.equals(propertyName)
                || PROPERTY_SCROLL_Y.equals(propertyName)
                || PROPERTY_RECORD_COUNT.equals(propertyName)
                || PROPERTY_ADDED_COUNT.equals(propertyName)
                || PROPERTY_REMOVED_COUNT.equals(propertyName) || PROPERTY_QUEUING
                    .equals(propertyName))
                || PROPERTY_VERSION_CODE.equals(propertyName)
                || PROPERTY_PLATFORM_SDK.equals(propertyName);
    }

    /**
     * Returns if a property is a float.
     *
     * @param propertyName The property name.
     * @return True if the property is a float, false otherwise.
     */
    private static boolean isFloatProperty(String propertyName) {
        return PROPERTY_EVENT_TIME.equals(propertyName);
    }

    /**
     * Returns if a property is a string.
     *
     * @param propertyName The property name.
     * @return True if the property is a string, false otherwise.
     */
    private static boolean isStringProperty(String propertyName) {
        return (PROPERTY_PACKAGE_NAME.equals(propertyName)
                || PROPERTY_CLASS_NAME.equals(propertyName) || PROPERTY_TEXT.equals(propertyName)
                || PROPERTY_BEFORE_TEXT.equals(propertyName)
                || PROPERTY_CONTENT_DESCRIPTION.equals(propertyName)
                || PROPERTY_VERSION_NAME.equals(propertyName)
                || PROPERTY_PLATFORM_RELEASE.equals(propertyName));
    }

    /**
     * Returns if a property is a boolean.
     *
     * @param propertyName The property name.
     * @return True if the property is a boolean, false otherwise.
     */
    private static boolean isBooleanProperty(String propertyName) {
        return (PROPERTY_CHECKED.equals(propertyName) || PROPERTY_ENABLED.equals(propertyName)
                || PROPERTY_FULL_SCREEN.equals(propertyName)
                || PROPERTY_SCROLLABLE.equals(propertyName) || PROPERTY_PASSWORD
                    .equals(propertyName));
    }

    /**
     * Create a {@link AccessibilityEventFilter} given a DOM <code>node</code>.
     *
     * @param node The node.
     * @return The created filter.
     */
    private AccessibilityEventFilter createFilter(Node node) {
        NodeList children = node.getChildNodes();

        // do we have a custom filter
        for (int i = 0, count = children.getLength(); i < count; i++) {
            Node child = children.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String nodeName = getUnqualifiedNodeName(child);
            if (NODE_NAME_CUSTOM.equals(nodeName)) {
                return createNewInstance(getTextContent(child), AccessibilityEventFilter.class);
            }
        }

        return new DefaultFilter(mContext, node);
    }

    /**
     * Creates a {@link AccessibilityEventFormatter} given a DOM <code>node</code>.
     *
     * @param node The node.
     * @return The created formatter.
     */
    private AccessibilityEventFormatter createFormatter(Node node) {
        NodeList children = node.getChildNodes();

        for (int i = 0, count = children.getLength(); i < count; i++) {
            Node child = children.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String nodeName = getUnqualifiedNodeName(child);
            if (NODE_NAME_CUSTOM.equals(nodeName)) {
                return createNewInstance(getTextContent(child), AccessibilityEventFormatter.class);
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
            private
            <T> T createNewInstance(String className, Class<T> expectedClass) {
        try {
            Class<T> clazz = null;
            // if we are loaded by the context class loader => use the latter
            if (mContext.getClassLoader() == this.getClass().getClassLoader()) {
                clazz = (Class<T>) mContext.getClassLoader().loadClass(className);
            } else {
                // It is important to load the plug-in classes via the TalkBack
                // ClassLoader to achieve interoperability of the classes of the
                // different APKs. Form VM perspective a class loaded by two
                // different class loaders is considered as two separate
                // classes.
                DexFile dexFile = new DexFile(new File(mPublicSourceDir));
                ClassLoader classLoader = mContext.getClassLoader();
                clazz = dexFile.loadClass(className, classLoader);
            }
            return clazz.newInstance();
        } catch (Exception e) {
            LogUtils.log(EventSpeechRule.class, Log.ERROR, "Rule: #%d. Could not load class: '%s'.",
                    mRuleIndex, className, e.toString());
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
     * @param speechStrategy The speech strategy that defined the rules.
     * @param targetPackage The package targeted by the rules.
     * @param publicSourceDir The location of the plug-in APK for loading
     *            classes.
     * @param document The parsed XML.
     * @return The list of loaded speech rules.
     */
    public static ArrayList<EventSpeechRule> createSpeechRules(Context context, String speechStrategy,
            String targetPackage, String publicSourceDir, Document document)
            throws IllegalStateException {
        ArrayList<EventSpeechRule> speechRules = new ArrayList<EventSpeechRule>();

        if (document == null || context == null) {
            return speechRules;
        }

        NodeList children = document.getDocumentElement().getChildNodes();
        for (int i = 0, count = children.getLength(); i < count; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                try {
                    speechRules.add(new EventSpeechRule(context, speechStrategy, targetPackage,
                            publicSourceDir, child, i));
                } catch (Exception e) {
                    LogUtils.log(EventSpeechRule.class, Log.WARN,
                            "Failed loading speech rule: %s %s", getTextContent(child),
                            e.toString());
                }
            }
        }

        return speechRules;
    }

    /**
     * Returns from the given <code>context</code> the text content of a
     * <code>node</code> after it has been localized.
     */
    private static String getLocalizedTextContent(Context context, Node node) {
        final String textContent = getTextContent(node);
        final int resId = getResourceIdentifierContent(context, textContent);

        if (resId > 0) {
            return context.getString(resId);
        }

        return textContent;
    }

    /**
     * Returns a resource identifier from the given <code>context</code> for the
     * text content of a <code>node</code>.
     * <p>
     * Note: The resource identifier format is: @&lt;package
     * name&gt;:&lt;type&gt;/&lt;resource name&gt;
     * </p>
     *
     * @param context The parent context.
     * @param resName A valid resource name.
     * @return A resource identifier, or {@code -1} if the resource name is invalid.
     */
    private static int getResourceIdentifierContent(Context context, String resName) {
        if (resName == null) {
            return -1;
        }

        final Matcher matcher =  mResourceIdentifier.matcher(resName);

        if (!matcher.matches()) {
            return -1;
        }

        final Resources res = context.getResources();
        final String defaultPackage = (matcher.groupCount() < 2) ? context.getPackageName() : null;
        final int resId = res.getIdentifier(resName.substring(1), null, defaultPackage);

        if (resId == 0) {
            LogUtils.log(EventSpeechRule.class, Log.ERROR, "Failed to load resource: %s", resName);
        }

        return resId;
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
     * Represents a default filter determining if the rule applies to a given
     * {@link AccessibilityEvent}.
     */
    private class DefaultFilter implements AccessibilityEventFilter {
        public DefaultFilter(Context context, Node node) {
            NodeList properties = node.getChildNodes();

            for (int i = 0, count = properties.getLength(); i < count; i++) {
                Node child = properties.item(i);

                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                String unqualifiedName = getUnqualifiedNodeName(child);
                String textContent = getTextContent(child);
                PropertyMatcher propertyMatcher =
                        new PropertyMatcher(context, unqualifiedName, textContent);
                mPropertyMatchers.put(unqualifiedName, propertyMatcher);

                // if the speech rule specifies a target package we use that
                // value
                // rather the one passed as an argument to the rule constructor
                if (PROPERTY_PACKAGE_NAME.equals(unqualifiedName)) {
                    mPackageName = textContent;
                }
            }
        }

        @Override
        public boolean accept(AccessibilityEvent event, Context context) {
            final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);

            // the order here matters and is from most frequently used to less
            PropertyMatcher eventTypeMatcher = mPropertyMatchers.get(PROPERTY_EVENT_TYPE);
            if (eventTypeMatcher != null) {
                int eventType = event.getEventType();
                if (!eventTypeMatcher.accept(eventType)) {
                    return false;
                }
            }
            PropertyMatcher packageNameMatcher = mPropertyMatchers.get(PROPERTY_PACKAGE_NAME);
            if (packageNameMatcher != null) {
                CharSequence packageName = event.getPackageName();
                if (!packageNameMatcher.accept(packageName)) {
                    return false;
                }
            }
            // special case
            PropertyMatcher classNameMatcher = mPropertyMatchers.get(PROPERTY_CLASS_NAME);
            if (classNameMatcher != null) {
                CharSequence eventClass = event.getClassName();
                CharSequence eventPackage = event.getPackageName();
                String filteringPackage = null;
                if (packageNameMatcher != null) {
                    filteringPackage = (String) packageNameMatcher.getAcceptedValues()[0];
                }
                if (!classNameMatcher.accept(eventClass, eventPackage, filteringPackage)) {
                    return false;
                }
            }
            PropertyMatcher textMatcher = mPropertyMatchers.get(PROPERTY_TEXT);
            if (textMatcher != null) {
                CharSequence eventText = AccessibilityEventUtils.getEventAggregateText(event);
                if (!textMatcher.accept(eventText)) {
                    return false;
                }
            }
            PropertyMatcher beforeTextMatcher = mPropertyMatchers.get(PROPERTY_BEFORE_TEXT);
            if (beforeTextMatcher != null) {
                CharSequence beforeText = event.getBeforeText();
                if (!beforeTextMatcher.accept(beforeText)) {
                    return false;
                }
            }
            PropertyMatcher contentDescriptionMatcher =
                    mPropertyMatchers.get(PROPERTY_CONTENT_DESCRIPTION);
            if (contentDescriptionMatcher != null) {
                CharSequence contentDescription = event.getContentDescription();
                if (!contentDescriptionMatcher.accept(contentDescription)) {
                    return false;
                }
            }
            PropertyMatcher eventTimeMatcher = mPropertyMatchers.get(PROPERTY_EVENT_TIME);
            if (eventTimeMatcher != null) {
                long eventTime = event.getEventTime();
                if (!eventTimeMatcher.accept(eventTime)) {
                    return false;
                }
            }
            PropertyMatcher itemCountMatcher = mPropertyMatchers.get(PROPERTY_ITEM_COUNT);
            if (itemCountMatcher != null) {
                int itemCount = event.getItemCount();
                if (!itemCountMatcher.accept(itemCount)) {
                    return false;
                }
            }
            PropertyMatcher currentItemIndexMatcher =
                    mPropertyMatchers.get(PROPERTY_CURRENT_ITEM_INDEX);
            if (currentItemIndexMatcher != null) {
                int currentItemIndex = event.getCurrentItemIndex();
                if (!currentItemIndexMatcher.accept(currentItemIndex)) {
                    return false;
                }
            }
            PropertyMatcher fromIndexMatcher = mPropertyMatchers.get(PROPERTY_FROM_INDEX);
            if (fromIndexMatcher != null) {
                int fromIndex = event.getFromIndex();
                if (!fromIndexMatcher.accept(fromIndex)) {
                    return false;
                }
            }
            PropertyMatcher toIndexMatcher = mPropertyMatchers.get(PROPERTY_TO_INDEX);
            if (toIndexMatcher != null) {
                int toIndex = record.getToIndex();
                if (!toIndexMatcher.accept(toIndex)) {
                    return false;
                }
            }
            PropertyMatcher scrollableMatcher = mPropertyMatchers.get(PROPERTY_SCROLLABLE);
            if (scrollableMatcher != null) {
                boolean scrollable = record.isScrollable();
                if (!scrollableMatcher.accept(scrollable)) {
                    return false;
                }
            }
            PropertyMatcher scrollXMatcher = mPropertyMatchers.get(PROPERTY_SCROLL_X);
            if (scrollXMatcher != null) {
                int scrollX = record.getScrollX();
                if (!scrollXMatcher.accept(scrollX)) {
                    return false;
                }
            }
            PropertyMatcher scrollYMatcher = mPropertyMatchers.get(PROPERTY_SCROLL_Y);
            if (scrollYMatcher != null) {
                int scrollY = record.getScrollY();
                if (!scrollYMatcher.accept(scrollY)) {
                    return false;
                }
            }
            PropertyMatcher recordCountMatcher = mPropertyMatchers.get(PROPERTY_RECORD_COUNT);
            if (recordCountMatcher != null) {
                int recordCount = AccessibilityEventCompat.getRecordCount(event);
                if (!recordCountMatcher.accept(recordCount)) {
                    return false;
                }
            }
            PropertyMatcher isCheckedMatcher = mPropertyMatchers.get(PROPERTY_CHECKED);
            if (isCheckedMatcher != null) {
                boolean isChecked = event.isChecked();
                if (!isCheckedMatcher.accept(isChecked)) {
                    return false;
                }
            }
            PropertyMatcher isEnabledMatcher = mPropertyMatchers.get(PROPERTY_ENABLED);
            if (isEnabledMatcher != null) {
                boolean isEnabled = event.isEnabled();
                if (!isEnabledMatcher.accept(isEnabled)) {
                    return false;
                }
            }
            PropertyMatcher isFullScreenMatcher = mPropertyMatchers.get(PROPERTY_FULL_SCREEN);
            if (isFullScreenMatcher != null) {
                boolean isFullScreen = event.isFullScreen();
                if (!isFullScreenMatcher.accept(isFullScreen)) {
                    return false;
                }
            }
            PropertyMatcher isPasswordMatcher = mPropertyMatchers.get(PROPERTY_PASSWORD);
            if (isPasswordMatcher != null) {
                boolean isPassword = event.isPassword();
                if (!isPasswordMatcher.accept(isPassword)) {
                    return false;
                }
            }
            PropertyMatcher addedCountMatcher = mPropertyMatchers.get(PROPERTY_ADDED_COUNT);
            if (addedCountMatcher != null) {
                int addedCount = event.getAddedCount();
                if (!addedCountMatcher.accept(addedCount)) {
                    return false;
                }
            }
            PropertyMatcher removedCountMatcher = mPropertyMatchers.get(PROPERTY_REMOVED_COUNT);
            if (removedCountMatcher != null) {
                int removedCount = event.getRemovedCount();
                if (!removedCountMatcher.accept(removedCount)) {
                    return false;
                }
            }
            PropertyMatcher versionCodeMatcher = mPropertyMatchers.get(PROPERTY_VERSION_CODE);
            if (versionCodeMatcher != null) {
                String packageName = event.getPackageName().toString();
                int versionCode = PackageManagerUtils.getVersionCode(context, packageName);
                if (!versionCodeMatcher.accept(versionCode)) {
                    return false;
                }
            }
            PropertyMatcher versionNameMatcher = mPropertyMatchers.get(PROPERTY_VERSION_NAME);
            if (versionNameMatcher != null) {
                String packageName = event.getPackageName().toString();
                String versionName = PackageManagerUtils.getVersionName(context, packageName);
                if (!versionNameMatcher.accept(versionName)) {
                    return false;
                }
            }
            PropertyMatcher platformReleaseMatcher =
                    mPropertyMatchers.get(PROPERTY_PLATFORM_RELEASE);
            if (platformReleaseMatcher != null) {
                String platformRelease = Build.VERSION.RELEASE;
                if (!platformReleaseMatcher.accept(platformRelease)) {
                    return false;
                }
            }
            PropertyMatcher platformSdkMatcher = mPropertyMatchers.get(PROPERTY_PLATFORM_SDK);
            if (platformSdkMatcher != null) {
                int platformSdk = Build.VERSION.SDK_INT;
                if (!platformSdkMatcher.accept(platformSdk)) {
                    return false;
                }
            }
            return true;
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
    private class DefaultFormatter implements AccessibilityEventFormatter {
        private static final String NODE_NAME_TEMPLATE = "template";
        private static final String NODE_NAME_PROPERTY = "property";

        /** Optional template to populate with selected values. */
        private final String mTemplate;

        private final List<Pair<String, String>> mSelectors;

        /**
         * Creates a new formatter from a given DOM {@link Node}.
         *
         * @param node The node.
         */
        public DefaultFormatter(Node node) {
            mSelectors = new ArrayList<Pair<String, String>>();
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
                } else if (NODE_NAME_PROPERTY.equals(unqualifiedName)) {
                    mSelectors.add(new Pair<String, String>(unqualifiedName,
                            getLocalizedTextContent(mContext, child)));
                } else {
                    mSelectors
                            .add(new Pair<String, String>(unqualifiedName, getTextContent(child)));
                }
            }

            mTemplate = template;
        }

        @Override
        public boolean format(AccessibilityEvent event, Context context, Utterance utterance) {
            List<Pair<String, String>> selectors = mSelectors;
            Object[] arguments = new Object[selectors.size()];

            for (int i = 0, count = selectors.size(); i < count; i++) {
                Pair<String, String> selector = selectors.get(i);
                String selectorType = selector.first;
                String selectorValue = selector.second;

                if (NODE_NAME_PROPERTY.equals(selectorType)) {
                    Object propertyValue = getPropertyValue(selectorValue, event);
                    arguments[i] = propertyValue != null ? propertyValue : "";
                } else {
                    throw new IllegalArgumentException("Unknown selector type: [" + selector.first
                            + ", " + selector.second + "]");
                }
            }

            utterance.getEarcons().addAll(mEarcons);
            utterance.getVibrationPatterns().addAll(mVibrationPatterns);

            formatTemplateOrAppendSpaceSeparatedValueIfNoTemplate(utterance, arguments);

            return true;
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
            final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);

            // no reflection to avoid performance hit
            if (PROPERTY_EVENT_TYPE.equals(property)) {
                return event.getEventType();
            } else if (PROPERTY_PACKAGE_NAME.equals(property)) {
                return event.getPackageName();
            } else if (PROPERTY_CLASS_NAME.equals(property)) {
                return event.getClassName();
            } else if (PROPERTY_TEXT.equals(property)) {
                return AccessibilityEventUtils.getEventText(event);
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
            } else if (PROPERTY_TO_INDEX.equals(property)) {
                return record.getToIndex();
            } else if (PROPERTY_SCROLLABLE.equals(property)) {
                return record.isScrollable();
            } else if (PROPERTY_SCROLL_X.equals(property)) {
                return record.getScrollX();
            } else if (PROPERTY_SCROLL_Y.equals(property)) {
                return record.getScrollY();
            } else if (PROPERTY_RECORD_COUNT.equals(property)) {
                return AccessibilityEventCompat.getRecordCount(event);
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
         * {@link EventSpeechRule} with the <code>arguments</code> in case such a
         * template was provided. If no template was provided the arguments are
         * concatenated using space as delimiter. The <code>utterance</code> is
         * populated with the generated text.
         *
         * @param utterance The builder to which to append the utterance.
         * @param arguments The formatting arguments.
         */
        private void formatTemplateOrAppendSpaceSeparatedValueIfNoTemplate(Utterance utterance,
                Object[] arguments) {
            final StringBuilder utteranceText = utterance.getText();

            if (mTemplate != null) {
                try {
                    final String formatted = String.format(mTemplate, arguments);
                    utteranceText.append(formatted);
                } catch (MissingFormatArgumentException mfae) {
                    LogUtils.log(DefaultFormatter.class, Log.ERROR, "Speech rule: '%d' has "
                            + "inconsistency between template: '%s' and arguments: '%s'. "
                            + "Possibliy #template arguments does not " + "match #parameters. %s",
                            mRuleIndex, mTemplate, arguments, mfae.toString());
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
    }

    /**
     * Helper class for managing pairs of objects.
     */
    private static class Pair<A, B> {
        public final A first;
        public final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }

    /**
     * Helper class for matching properties.
     */
    private static class PropertyMatcher {

        /**
         * Match type if a property is equal to an expected value.
         */
        private static final int TYPE_EQUALS = 0;

        /**
         * Match type if a numeric property is less than or equal to an expected
         * value.
         */
        private static final int TYPE_LESS_THAN_OR_EQUAL = 1;

        /**
         * Match type if a numeric property is greater than or equal to an
         * expected value.
         */
        private static final int TYPE_GREATER_THAN_OR_EQUAL = 2;

        /**
         * Match type if a numeric property is less than an expected value.
         */
        private static final int TYPE_LESS_THAN = 3;

        /**
         * Match type if a numeric property is greater than an expected value.
         */
        private static final int TYPE_GREATER_THAN = 4;

        /**
         * Match type if a numeric property is equal to one of several expected
         * values.
         */
        private static final int TYPE_OR = 5;

        /**
         * String for a regex pattern than matches float numbers.
         */
        private static final String PATTERN_STRING_FLOAT =
                "(\\s)*([+-])?((\\d)+(\\.(\\d)+)?|\\.(\\d)+)(\\s)*";

        /**
         * Pattern to match a less than or equal a numeric value constraint.
         */
        private static final Pattern PATTERN_LESS_THAN_OR_EQUAL = Pattern.compile("(\\s)*<="
                + PATTERN_STRING_FLOAT);

        /**
         * Pattern to match a greater than or equal a numeric value constraint.
         */
        private static final Pattern PATTERN_GREATER_THAN_OR_EQUAL = Pattern.compile("(\\s)*>="
                + PATTERN_STRING_FLOAT);

        /**
         * Pattern to match a less than a numeric value constraint.
         */
        private static final Pattern PATTERN_LESS_THAN = Pattern.compile("(\\s)*<"
                + PATTERN_STRING_FLOAT);

        /**
         * Pattern to match a greater than a numeric value constraint.
         */
        private static final Pattern PATTERN_GREATER_THAN = Pattern.compile("(\\s)*>"
                + PATTERN_STRING_FLOAT);

        /**
         * Pattern to match an or constraint.
         */
        private static final Pattern PATTERN_OR = Pattern.compile("(.)+\\|\\|(.)+(\\|\\|(.)+)*");

        /**
         * Pattern for splitting an or constraint into simple equal constraints.
         */
        private static final Pattern PATTERN_SPLIT_OR = Pattern.compile("(\\s)*\\|\\|(\\s)*");

        /**
         * The less than or equal string.
         */
        private static final String LESS_THAN_OR_EQUAL = "<=";

        /**
         * The greater than or equal string.
         */
        private static final String GREATER_THAN_OR_EQUAL = ">=";

        /**
         * The less than string.
         */
        private static final String LESS_THAN = "<";

        /**
         * The greater than string.
         */
        private static final String GREATER_THAN = ">";

        /**
         * The name of the property matched by this instance.
         */
        private String mPropertyName;

        /**
         * The type of matching to be performed.
         */
        private int mType;

        /**
         * The values accepted by this matcher.
         */
        private Object[] mAcceptedValues;

        /**
         * Context handled for accessing resources.
         */
        private Context mContext;

        /**
         * Creates a new instance.
         *
         * @param context The Context for accessing resources.
         * @param propertyName The name of the matched property.
         * @param acceptedValue The not parsed accepted value.
         */
        public PropertyMatcher(Context context, String propertyName, String acceptedValue) {
            mContext = context;
            mPropertyName = propertyName;
            if (acceptedValue == null) {
                return;
            }
            if ((isIntegerProperty(propertyName) || isFloatProperty(propertyName))
                    && PATTERN_LESS_THAN_OR_EQUAL.matcher(acceptedValue).matches()) {
                mType = TYPE_LESS_THAN_OR_EQUAL;
                int fromIndex = acceptedValue.indexOf(LESS_THAN_OR_EQUAL);
                String valueString = acceptedValue.substring(fromIndex + 2).trim();
                mAcceptedValues = new Object[] {
                    parsePropertyValue(propertyName, valueString)
                };
            } else if ((isIntegerProperty(propertyName) || isFloatProperty(propertyName))
                    && PATTERN_GREATER_THAN_OR_EQUAL.matcher(acceptedValue).matches()) {
                mType = TYPE_GREATER_THAN_OR_EQUAL;
                int fromIndex = acceptedValue.indexOf(GREATER_THAN_OR_EQUAL);
                String valueString = acceptedValue.substring(fromIndex + 2).trim();
                mAcceptedValues = new Object[] {
                    parsePropertyValue(propertyName, valueString)
                };
            } else if ((isIntegerProperty(propertyName) || isFloatProperty(propertyName))
                    && PATTERN_LESS_THAN.matcher(acceptedValue).matches()) {
                mType = TYPE_LESS_THAN;
                int fromIndex = acceptedValue.indexOf(LESS_THAN);
                String valueString = acceptedValue.substring(fromIndex + 1).trim();
                mAcceptedValues = new Object[] {
                    parsePropertyValue(propertyName, valueString)
                };
            } else if ((isIntegerProperty(propertyName) || isFloatProperty(propertyName))
                    && PATTERN_GREATER_THAN.matcher(acceptedValue).matches()) {
                mType = TYPE_GREATER_THAN;
                int fromIndex = acceptedValue.indexOf(GREATER_THAN);
                String valueString = acceptedValue.substring(fromIndex + 1).trim();
                mAcceptedValues = new Object[] {
                    parsePropertyValue(propertyName, valueString)
                };
            } else if (PATTERN_OR.matcher(acceptedValue).matches()) {
                mType = TYPE_OR;
                String[] acceptedValues = PATTERN_SPLIT_OR.split(acceptedValue);
                mAcceptedValues = new Object[acceptedValues.length];
                for (int i = 0, count = acceptedValues.length; i < count; i++) {
                    mAcceptedValues[i] = parsePropertyValue(propertyName, acceptedValues[i]);
                }
            } else {
                mType = TYPE_EQUALS;
                mAcceptedValues = new Object[] {
                    parsePropertyValue(propertyName, acceptedValue)
                };
            }
        }

        /**
         * @return The values accepted by this matcher.
         */
        public Object[] getAcceptedValues() {
            return mAcceptedValues;
        }

        /**
         * @return True if the given <code>value</code> with specified
         *         <code>arguments</code> is accepted by this matcher.
         */
        public boolean accept(Object value, Object... arguments) {
            if (mAcceptedValues == null) {
                return true;
            }
            if (value == null) {
                if (isStringProperty(mPropertyName)) {
                    value = "";
                } else {
                    return false;
                }
            }
            if (PROPERTY_CLASS_NAME.equals(mPropertyName)) {
                String eventClassName = (String) value;
                String eventPackageName = (String) arguments[0];
                String filteringPackageName = (String) arguments[1];
                return acceptClassNameProperty(eventClassName, eventPackageName,
                        filteringPackageName);
            } else {
                return acceptProperty(value);
            }
        }

        /**
         * @return True if this matcher accepts a <code>className</code> from
         *         the given <code>packageName</code> while comparing it against
         *         the filtered event (#PROPERTY_CLASS_NAME) from the
         *         <code>filteredPackageName</code>.
         */
        private boolean acceptClassNameProperty(String eventClassName, String eventPackageName,
                String filteringPackageName) {
            for (Object acceptedValue : mAcceptedValues) {
                String filteringClassName = (String) acceptedValue;
                // try a shortcut for efficiency
                if (filteringClassName.equals(eventClassName)) {
                    return true;
                }
                Class<?> filteringClass =
                        ClassLoadingManager.getInstance().loadOrGetCachedClass(mContext,
                                filteringClassName, filteringPackageName);
                Class<?> eventClass =
                        ClassLoadingManager.getInstance().loadOrGetCachedClass(mContext,
                                eventClassName.toString(), eventPackageName);
                if (filteringClass != null && eventClass != null) {
                    return (filteringClass.isAssignableFrom(eventClass));
                }
            }
            return false;
        }

        /**
         * @return True if this matcher accepts the given <code>value</code> for
         *         the property it matches.
         */
        private boolean acceptProperty(Object value) {
            if (mType == TYPE_LESS_THAN_OR_EQUAL) {
                if (isIntegerProperty(mPropertyName)) {
                    return ((Integer) value) <= ((Integer) mAcceptedValues[0]);
                } else {
                    return ((Float) value) <= ((Float) mAcceptedValues[0]);
                }
            } else if (mType == TYPE_GREATER_THAN_OR_EQUAL) {
                if (isIntegerProperty(mPropertyName)) {
                    return ((Integer) value) >= ((Integer) mAcceptedValues[0]);
                } else {
                    return ((Float) value) >= ((Float) mAcceptedValues[0]);
                }
            } else if (mType == TYPE_LESS_THAN) {
                if (isIntegerProperty(mPropertyName)) {
                    return ((Integer) value) < ((Integer) mAcceptedValues[0]);
                } else {
                    return ((Float) value) < ((Float) mAcceptedValues[0]);
                }
            } else if (mType == TYPE_GREATER_THAN) {
                if (isIntegerProperty(mPropertyName)) {
                    return ((Integer) value) > ((Integer) mAcceptedValues[0]);
                } else {
                    return ((Float) value) > ((Float) mAcceptedValues[0]);
                }
            } else {
                // Convert CharSequences to Strings
                if (value instanceof CharSequence) {
                    value = value.toString();
                }
                for (Object acceptedValue : mAcceptedValues) {
                    if (value.equals(acceptedValue)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    /**
     * This interface defines the contract for writing filters. A filter either
     * accepts or rejects an {@link AccessibilityEvent}.
     *
     * @author svetoslavganov@google.com (Svetoslav Ganov)
     */
    public interface AccessibilityEventFilter {

        /**
         * Check if the filter accepts a given <code>event</code>.
         *
         * @param event The event.
         * @param context The context to be used for loading resources etc.
         * @return True if the event is accepted, false otherwise.
         */
        boolean accept(AccessibilityEvent event, Context context);
    }

    /**
     * This interface defines the contract for writing formatters. A formatter
     * populates a formatted {@link Utterance} from an
     * {@link AccessibilityEvent}.
     *
     * @author svetoslavganov@google.com (Svetoslav Ganov)
     */
    public interface AccessibilityEventFormatter {
        /**
         * Formats an <code>utterance</code> form given <code>event</code>.
         *
         * @param event The event.
         * @param context The context to be used for loading resources etc.
         * @param utterance The utterance instance to populate.
         * @return {@code true} if the formatter produced output.
         */
        public boolean format(AccessibilityEvent event, Context context, Utterance utterance);
    }
}
