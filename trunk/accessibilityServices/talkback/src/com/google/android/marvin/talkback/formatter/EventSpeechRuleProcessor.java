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

import android.content.res.Resources;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.TalkBackService;
import com.google.android.marvin.talkback.Utterance;
import com.googlecode.eyesfree.utils.LogUtils;

import org.w3c.dom.Document;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * This class is a {@link EventSpeechRule} processor responsible for loading
 * from speech strategy XML files sets of {@link EventSpeechRule}s used for
 * processing {@link AccessibilityEvent}s such that utterances are generated.
 * <p>
 * Speech strategies can be registered for handling events from a given package
 * or their rules to be appended to the default speech rules which are examined
 * as fall-back if no package specific ones have matched the event. The rules
 * are processed in the order they are defined and in case a rule is
 * successfully applied i.e. an utterance is formatted, processing stops. In
 * other words, the first applicable speech rule wins.
 * </p>
 *
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public class EventSpeechRuleProcessor {

    /**
     * Constant used for storing all speech rules that either do not define a
     * filter package or have custom filters.
     */
    private static final String UNDEFINED_PACKAGE_NAME = "undefined_package_name";

    /** Context for accessing resources. */
    private final TalkBackService mContext;

    /** Mapping from package name to speech rules for that package. */
    private final Map<String, List<EventSpeechRule>>
            mPackageNameToSpeechRulesMap = new HashMap<String, List<EventSpeechRule>>();

    /** A lazily-constructed shared instance of a document builder. */
    private DocumentBuilder mDocumentBuilder;

    /**
     * Creates a new instance of a speech rule processor.
     *
     * @param context The service context.
     */
    public EventSpeechRuleProcessor(TalkBackService context) {
        mContext = context;
    }

    /**
     * Processes an <code>event</code> by sequentially trying to apply all
     * {@link EventSpeechRule}s in the order they are defined for the package
     * source of the event. If no package specific rules exist the default
     * speech rules are examined in the same manner. If a rule is successfully
     * applied the result is used to populate an <code>utterance</code>. In other words,
     * the first matching rule wins. Optionally <code>filterArguments</code> and <code>formatterArguments</code>
     * can be provided.
     *
     * @return True if the event was processed false otherwise.
     */
    public boolean processEvent(AccessibilityEvent event, Utterance utterance) {
        synchronized (mPackageNameToSpeechRulesMap) {
            // Try package specific speech rules first.
            List<EventSpeechRule> speechRules = mPackageNameToSpeechRulesMap
                    .get(event.getPackageName());

            if ((speechRules != null) && processEvent(speechRules, event, utterance)) {
                return true;
            }

            // Package specific rule not found; try undefined package ones.
            speechRules = mPackageNameToSpeechRulesMap.get(UNDEFINED_PACKAGE_NAME);

            if ((speechRules != null) && processEvent(speechRules, event, utterance)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Loads a speech strategy from a given <code>resourceId</code> to handle events from
     * the specified <code>targetPackage</code> and use the resources from a given <code>context
     * </code>. If the target package is <code>null</code> the rules of the loaded
     * speech strategy are appended to the default speech rules. While for
     * loading of resources is used the provided context instance, for loading
     * plug-in classes (custom Filters and Formatters) the <code>publicSourceDir</code> which
     * specifies the location of the APK that defines them is used to enabled
     * using the TalkBack {@link ClassLoader}.
     */
    public void addSpeechStrategy(int resourceId) {
        final Resources res = mContext.getResources();
        final String speechStrategy = res.getResourceName(resourceId);
        final InputStream inputStream = res.openRawResource(resourceId);
        final Document document = parseSpeechStrategy(inputStream);
        final ArrayList<EventSpeechRule> speechRules = EventSpeechRule.createSpeechRules(
                mContext, document);

        final int added = addSpeechStrategy(speechRules);

        LogUtils.log(EventSpeechRuleProcessor.class, Log.INFO, "%d speech rules appended from: %s",
                added, speechStrategy);
    }

    /**
     * Loads speech rules from a list.
     *
     * @return The number of rules that were loaded successfully.
     */
    public int addSpeechStrategy(Iterable<EventSpeechRule> speechRules) {
        int count = 0;

        synchronized (mPackageNameToSpeechRulesMap) {
            for (EventSpeechRule speechRule : speechRules) {
                if (addSpeechRuleLocked(speechRule)) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Adds a <code>speechRule</code>.
     */
    private boolean addSpeechRuleLocked(EventSpeechRule speechRule) {
        final String packageName = speechRule.getPackageName();

        List<EventSpeechRule> packageSpeechRules = mPackageNameToSpeechRulesMap.get(packageName);

        if (packageSpeechRules == null) {
            packageSpeechRules = new LinkedList<EventSpeechRule>();
            mPackageNameToSpeechRulesMap.put(packageName, packageSpeechRules);
        }

        return packageSpeechRules.add(speechRule);
    }

    /**
     * Processes an <code>event</code> by sequentially trying to apply all <code>speechRules
     * </code> in the order they are defined for the package source of the
     * event. If no package specific rules exist the default speech rules are
     * examined in the same manner. If a rule is successfully applied the result
     * is used to populate an <code>utterance</code>. In other words, the first matching
     * rule wins. Optionally <code>filterArguments</code> and <code>formatterArguments</code> can be provided.
     *
     * @return {@code true} if the event was processed, {@code false} otherwise.
     */
    private boolean processEvent(List<EventSpeechRule> speechRules, AccessibilityEvent event,
            Utterance utterance) {
        for (EventSpeechRule speechRule : speechRules) {
            // We should never crash because of a bug in speech rules.
            try {
                if (speechRule.applyFilter(event)) {
                    if (speechRule.applyFormatter(event, utterance)) {
                        LogUtils.log(EventSpeechRuleProcessor.class, Log.VERBOSE,
                                "Processed event using rule:\n%s", speechRule);
                        return true;
                    } else {
                        LogUtils.log(EventSpeechRuleProcessor.class, Log.VERBOSE,
                                "The \"%s\" filter accepted the event, but the \"%s\" "
                                        + "formatter indicated the event should be dropped.",
                                speechRule.getFilter().getClass().getSimpleName(),
                                speechRule.getFormatter().getClass().getSimpleName());
                        return false;
                    }
                }
            } catch (Exception e) {
                LogUtils.log(EventSpeechRuleProcessor.class, Log.ERROR,
                        "Error while processing rule:\n%s", speechRule);
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * Parses a speech strategy XML file specified by <code>resourceId</code> and returns
     * a <code>document</code>. If an error occurs during the parsing, it is logged and
     * <code>null</code> is returned.
     *
     * @param inputStream An {@link InputStream} to the speech strategy XML
     *            file.
     * @return The parsed {@link Document} or <code>null</code> if an error
     *         occurred.
     */
    private Document parseSpeechStrategy(InputStream inputStream) {
        try {
            final DocumentBuilder builder = getDocumentBuilder();
            return builder.parse(inputStream);
        } catch (Exception e) {
            LogUtils.log(EventSpeechRuleProcessor.class, Log.ERROR,
                    "Could not open speechstrategy xml file\n%s", e.toString());
        }

        return null;
    }

    /**
     * @return A lazily-constructed shared instance of a document builder.
     * @throws ParserConfigurationException
     */
    private DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
        if (mDocumentBuilder == null) {
            mDocumentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        }

        return mDocumentBuilder;
    }
}
