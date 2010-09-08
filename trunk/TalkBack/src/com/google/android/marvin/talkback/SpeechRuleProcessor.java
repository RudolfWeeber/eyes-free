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
import org.xml.sax.SAXException;

import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * This class is a {@link SpeechRule} processor responsible for loading from
 * speech strategy XML files sets of {@link SpeechRule}s used for processing
 * {@link AccessibilityEvent}s such that utterances are generated. Speech
 * strategies can be registered for handling events from a given package or
 * their rules to be appended to the default speech rules which are examined as
 * fall-back if no package specific ones have matched the event. The rules are
 * processed in the order they are defined and in case a rule is successfully
 * applied i.e. an utterance is formatted, processing stops. In other words, the
 * first applicable speech rule wins.
 * 
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public class SpeechRuleProcessor {

    /**
     * Constant used for storing all speech rules that either do not
     * define a filter package or have custom filters.
     */
    private static final String UNDEFINED_PACKAGE_NAME = "undefined_package_name";

    /**
     * Tag for logging.
     */
    private static final String LOG_TAG = "SpeechRuleProcessor";

    /**
     * Mutex lock for accessing the speech rules.
     */
    private final Object mLock = new Object();

    /**
     * Mapping from package name to speech rules for that package.
     */
    private HashMap<String, ArrayList<SpeechRule>> mPackageNameToSpeechRulesMap = new HashMap<String, ArrayList<SpeechRule>>();

    /**
     * Creates a new instance.
     */
    SpeechRuleProcessor() {
        /* do nothing - reducing constructor visibility */
    }

    /**
     * Loads a speech strategy from a given <code>resourceId</code> to handle
     * events from all packages and use the resources of the TalkBack context.
     */
    void addSpeechStrategy(int resourceId) {
        addSpeechStrategy(TalkBackService.asContext(), null, null, resourceId);
    }

    /**
     * Loads a speech strategy from a given <code>resourceId</code> to handle
     * events from the specified <code>targetPackage</code> and use the resources
     * from a given <code>context</code>. If the target package is
     * <code>null</code> the rules of the loaded speech strategy are appended to
     * the default speech rules. While for loading of resources is used the provided
     * context instance, for loading plug-in classes (custom Filters and Formatters)
     * the <code>publicSourceDir</code> which specifies the location of the APK that
     * defines them is used to enabled using the TalkBack {@link ClassLoader}.
     */
    void addSpeechStrategy(Context context, String publicSourceDir, String targetPackage,
            int resourceId) {
        Document document = parseSpeechStrategy(context, resourceId);
        ArrayList<SpeechRule> speechRules = SpeechRule.createSpeechRules(context, publicSourceDir,
                document);
        synchronized (mLock) {
            for (int i = 0, count = speechRules.size(); i < count; i++) {
                SpeechRule speechRule = speechRules.get(i);
                addSpeechRule(targetPackage, speechRule);
            }
        }
    }

    /**
     * Adds a <code>speechRule</code> for a given <code>targetPackage</code>
     * If <code>targetPackage</code> is specified the speech rule is added to
     * the list of rules for that package. It is up to the speech rule writer
     * to make sure that the accepted package name is the same as this argument
     * (This is applicable only for plug-ins). If the target package is
     * <code>null</code> and the rule's filter is a {@link SpeechRule.DefaultFilter}
     * it is polled to find the accepted package name (if such). If this fails
     * we fall back to putting the rule to the list of rules with not defined
     * package names as {@link #UNDEFINED_PACKAGE_NAME}.
     */
    private boolean addSpeechRule(String targetPackage, SpeechRule speechRule) {
        String packageName = targetPackage;
        if (packageName == null) {
            Filter filter = speechRule.getFilter(); 
            if (filter instanceof SpeechRule.DefaultFilter) {
                SpeechRule.DefaultFilter defaultFilter = (SpeechRule.DefaultFilter) filter;
                packageName = defaultFilter.getAcceptedPackageName();
            }
        }

        // target package neither specified by the client nor the speech rule
        if (packageName == null) {
            packageName = UNDEFINED_PACKAGE_NAME;
        }

        ArrayList<SpeechRule> packageSpeechRules = mPackageNameToSpeechRulesMap.get(packageName);
        if (packageSpeechRules == null) {
            packageSpeechRules = new ArrayList<SpeechRule>();
            mPackageNameToSpeechRulesMap.put(packageName, packageSpeechRules);
        }

        return packageSpeechRules.add(speechRule);
    }

    /**
     * Removes the speech rules for a given <code>packageName</code>.
     *
     * @return True if speech rules were removed.
     */
    boolean removeSpeechRulesForPackage(String packageName) {
        synchronized (mLock) {
            ArrayList<SpeechRule> speechRules = mPackageNameToSpeechRulesMap.remove(packageName);
            if (speechRules != null) {
                Log.i(LOG_TAG, speechRules.size() + " speech rules removed");
            }
            return (speechRules != null);
        }
    }

    /**
     * Processes an <code>event</code> utilizing the optional
     * <code>activity</code> name by sequentially trying to apply all
     * {@link SpeechRule}s in the order they are defined for the package source
     * of the event. If no package specific rules exist the default speech rules
     * are examined in the same manner. If a rule is successfully applied the
     * result is used to populate an <code>utterance</code>. In other words, the
     * first matching rule wins. Optionally <code>filterArguments</code> and
     * <code>formatterArguments</code> can be provided.
     * 
     * @return True if the event was processed false otherwise.
     */
    boolean processEvent(AccessibilityEvent event, String activity, Utterance utterance,
            Map<Object, Object> filterArguments, Map<Object, Object> formatterArguments) {
        synchronized (mLock) {
            // try package specific speech rules first
            ArrayList<SpeechRule> speechRules = mPackageNameToSpeechRulesMap
                    .get(event.getPackageName());
            if (speechRules != null
                    && processEvent(speechRules, event, activity, utterance, filterArguments,
                            formatterArguments)) {
                return true;
            }
            // package specific rule not found - try undefined package ones
            speechRules = mPackageNameToSpeechRulesMap.get(UNDEFINED_PACKAGE_NAME);
            if (speechRules != null
                    && processEvent(speechRules, event, activity, utterance, filterArguments,
                            formatterArguments)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Processes an <code>event</code> utilizing the optional
     * <code>activity</code> name by sequentially trying to apply all
     * <code>speechRules</code> in the order they are defined for the package
     * source of the event. If no package specific rules exist the default
     * speech rules are examined in the same manner. If a rule is successfully
     * applied the result is used to populate an <code>utterance</code>. In
     * other words, the first matching rule wins. Optionally
     * <code>filterArguments</code> and <code>formatterArguments</code> can
     * be provided. 
     *
     * @return True if the event was processed false otherwise.
     */
    private boolean processEvent(ArrayList<SpeechRule> speechRules, AccessibilityEvent event,
            String activity, Utterance utterance, Map<Object, Object> filterArguments,
            Map<Object, Object> formatterArguments) {
        for (int i = 0, count = speechRules.size(); i < count; i++) {
            if (speechRules.get(i).apply(event, activity, utterance, filterArguments,
                    formatterArguments)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses a speech strategy XML file specified by <code>resourceId</code>
     * and returns a <code>document</code>. If an error occurs during the
     * parsing, it is logged and <code>null</code> is returned.
     * 
     * @param context A {@link Context} instance.
     * @param resourceId The resource id of the speech strategy XML file.
     * @return The parsed {@link Document} or <code>null</code> if an error
     *         occurred.
     */
    private Document parseSpeechStrategy(Context context, int resourceId) {
        try {
            InputStream inputStream = context.getResources().openRawResource(resourceId);
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            return builder.parse(inputStream);
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "Could not open speechstrategy xml file", ioe);
        } catch (ParserConfigurationException pce) {
            Log.e(LOG_TAG, "Could not open speechstrategy xml file", pce);
        } catch (SAXException se) {
            Log.e(LOG_TAG, "Could not open speechstrategy xml file", se);
        }
        return null;
    }
}
