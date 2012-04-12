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
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.Utterance;
import com.googlecode.eyesfree.utils.LogUtils;

import org.w3c.dom.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * This class is a {@link EventSpeechRule} processor responsible for loading from
 * speech strategy XML files sets of {@link EventSpeechRule}s used for processing
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
public class EventSpeechRuleProcessor {

    /**
     * Constant used for storing all speech rules that either do not
     * define a filter package or have custom filters.
     */
    private static final String UNDEFINED_PACKAGE_NAME = "undefined_package_name";

    /**
     * Mutex lock for accessing the speech rules.
     */
    private final Object mLock = new Object();

    /**
     * Context for accessing resources.
     */
    private final Context mContext;

    /**
     * Mapping from package name to speech rules for that package.
     */
    private HashMap<String, ArrayList<EventSpeechRule>> mPackageNameToSpeechRulesMap = new HashMap<String, ArrayList<EventSpeechRule>>();

    /**
     * Creates a new instance.
     */
    public EventSpeechRuleProcessor(Context context) {
        mContext = context;
    }

    /**
     * Loads a speech strategy from a given <code>resourceId</code> to handle
     * events from all packages and use the resources of the TalkBack context.
     */
    public void addSpeechStrategy(int resourceId) {
        addSpeechStrategy(mContext, null, null, resourceId);
    }

    /**
     * Removes the speech rules defined by a speech strategy with the
     * given <code>resourceId</code> 
     */
    void removeSpeechStrategy(int resourceId) {
        String speechStrategy = mContext.getResources().getResourceName(resourceId);
        removeSpeechStrategy(speechStrategy);
    }

    /**
     * Loads a speech strategy from a given <code>file</code>.
     */
    public void addSpeechStrategy(File file) {
        String speechStrategy = file.toURI().toString();
        try {
            InputStream inputStream = new FileInputStream(file);
            addSpeechStrategy(mContext, speechStrategy, null, null, inputStream);
        } catch (FileNotFoundException fnfe) {
            LogUtils.log(EventSpeechRuleProcessor.class, Log.ERROR, "Error loading speech strategy: "
                    + "%s\n%s", speechStrategy, fnfe.toString());
        }
    }

    /**
     * Removes the speech rules defined by a speech strategy in the
     * given <code>file</code> 
     */
    public void removeSpeechStrategy(File file) {
        String speechStrategy = file.toURI().toString();
        removeSpeechStrategy(speechStrategy);
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
        String speechStrategy = context.getResources().getResourceName(resourceId);
        InputStream inputStream = context.getResources().openRawResource(resourceId);
        addSpeechStrategy(context, speechStrategy, targetPackage, publicSourceDir, inputStream);
    }

    /**
     * Loads a <code>speechStrategy</code> from a given <code>inputStream</code> to handle
     * events from the specified <code>targetPackage</code> and use the resources
     * from a given <code>context</code>. If the target package is
     * <code>null</code> the rules of the loaded speech strategy are appended to
     * the default speech rules. While for loading of resources is used the provided
     * context instance, for loading plug-in classes (custom Filters and Formatters)
     * from the <code>publicSourceDir</code> (specifies the location of the APK that
     * defines them) is used the TalkBack {@link ClassLoader}.
     */
    private void addSpeechStrategy(Context context, String speechStrategy, String targetPackage,
            String publicSourceDir, InputStream inputStream) {
        Document document = parseSpeechStrategy(context, inputStream);
        ArrayList<EventSpeechRule> speechRules = EventSpeechRule.createSpeechRules(context, speechStrategy,
                targetPackage, publicSourceDir, document);
        synchronized (mLock) {
            for (int i = 0, count = speechRules.size(); i < count; i++) {
                EventSpeechRule speechRule = speechRules.get(i);
                addSpeechRuleLocked(speechRule);
            }
        }
        LogUtils.log(EventSpeechRuleProcessor.class, Log.INFO, "%d speech rules appended from: %s",
                speechRules.size(), speechStrategy);
    }

    /**
     * Removes the speech rules defined by a given <code>speechStrategy</code>
     */
    private void removeSpeechStrategy(String speechStrategy) {
        int removedCount = 0;
        for (String key : mPackageNameToSpeechRulesMap.keySet()) {
            ArrayList<EventSpeechRule> speechRules = mPackageNameToSpeechRulesMap.get(key);
            Iterator<EventSpeechRule> speechRulesIterator = speechRules.iterator();
            while (speechRulesIterator.hasNext()) {
                EventSpeechRule speechRule = speechRulesIterator.next();
                if (speechStrategy.equals(speechRule.getSpeechStrategy())) {
                    speechRulesIterator.remove();
                    removedCount++;
                }
            }
        }
        LogUtils.log(EventSpeechRuleProcessor.class, Log.DEBUG, "%d speech rules removed from: %s",
                removedCount, speechStrategy);
    }

    /**
     * Adds a <code>speechRule</code>.
     */
    private boolean addSpeechRuleLocked(EventSpeechRule speechRule) {
        String packageName = speechRule.getPackageName();
        ArrayList<EventSpeechRule> packageSpeechRules = mPackageNameToSpeechRulesMap.get(packageName);
        if (packageSpeechRules == null) {
            packageSpeechRules = new ArrayList<EventSpeechRule>();
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
            ArrayList<EventSpeechRule> speechRules = mPackageNameToSpeechRulesMap.remove(packageName);
            if (speechRules != null) {
                LogUtils.log(EventSpeechRuleProcessor.class, Log.INFO, "%d speech rules removed",
                        speechRules.size());
            }
            return (speechRules != null);
        }
    }

    /**
     * Processes an <code>event</code> by sequentially trying to apply all
     * {@link EventSpeechRule}s in the order they are defined for the package source
     * of the event. If no package specific rules exist the default speech rules
     * are examined in the same manner. If a rule is successfully applied the
     * result is used to populate an <code>utterance</code>. In other words, the
     * first matching rule wins. Optionally <code>filterArguments</code> and
     * <code>formatterArguments</code> can be provided.
     * 
     * @return True if the event was processed false otherwise.
     */
    public boolean processEvent(AccessibilityEvent event, Utterance utterance) {
        synchronized (mLock) {
            // Try package specific speech rules first.
            ArrayList<EventSpeechRule> speechRules = mPackageNameToSpeechRulesMap
                    .get(event.getPackageName());
            
            if (speechRules != null
                    && processEvent(speechRules, event, utterance)) {
                return true;
            }

            // Package specific rule not found; try undefined package ones.
            speechRules = mPackageNameToSpeechRulesMap.get(UNDEFINED_PACKAGE_NAME);
            if (speechRules != null
                    && processEvent(speechRules, event, utterance)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Processes an <code>event</code> by sequentially trying to apply all
     * <code>speechRules</code> in the order they are defined for the package
     * source of the event. If no package specific rules exist the default
     * speech rules are examined in the same manner. If a rule is successfully
     * applied the result is used to populate an <code>utterance</code>. In
     * other words, the first matching rule wins. Optionally
     * <code>filterArguments</code> and <code>formatterArguments</code> can be
     * provided.
     * 
     * @return {@code true} if the event was processed, {@code false} otherwise.
     */
    private boolean processEvent(ArrayList<EventSpeechRule> speechRules, AccessibilityEvent event,
            Utterance utterance) {
        for (int i = 0, count = speechRules.size(); i < count; i++) {
            // We should never crash because of a bug in speech rules.
            EventSpeechRule speechRule = speechRules.get(i);
            try {
                if (speechRule.apply(event, utterance)) {
                    if (Log.VERBOSE >= LogUtils.LOG_LEVEL) {
                        LogUtils.log(EventSpeechRuleProcessor.class, Log.VERBOSE, "Processed event using "
                                + "rule:\n%s", speechRule.asXmlString());
                    }
                    return true;
                }
            } catch (Throwable t) {
                LogUtils.log(EventSpeechRuleProcessor.class, Log.ERROR, "Error while processing "
                        + "rule:\n%s", speechRule.asXmlString());
                t.printStackTrace();
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
     * @param inputStream An {@link InputStream} to the speech strategy XML file.
     * @return The parsed {@link Document} or <code>null</code> if an error
     *         occurred.
     */
    private Document parseSpeechStrategy(Context context, InputStream inputStream) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            return builder.parse(inputStream);
        } catch (Exception e) {
            LogUtils.log(EventSpeechRuleProcessor.class, Log.ERROR, "Could not open speechstrategy "
                    + "xml file\n%s", e.toString());
        }
        return null;
    }
}
