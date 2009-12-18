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

import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * This class is a {@link SpeechRule} processor responsible for loading from an
 * XML file a set of {@link SpeechRule}s and processing
 * {@link AccessibilityEvent}s by trying to apply these rules. The rules are
 * processed in the order they are defined and in case a rule is successfully
 * applied i.e. a formatted utterance is returned, the processing is stopped and
 * this utterance is returned. In other words, the first applicable speech rule
 * wins.
 * 
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public class SpeechRuleProcessor {

    private static final String LOG_TAG = "SpeechRuleProcessor";

    private final ArrayList<SpeechRule> mSpeechRules;

    /**
     * Creates a new instance associated with a <code>context</code> and
     * processing speech rules defined by a speech strategy file with a given
     * <code>speechStrategyResId</code>.
     * 
     * @param context A context instance.
     * @param speechStrategyResId The resource id of the speech strategy XML
     *            file.
     */
    public SpeechRuleProcessor(Context context, int speechStrategyResId) {
        Document document = parseSpeechStrategy(context, speechStrategyResId);
        mSpeechRules = SpeechRule.createSpeechRules(document, context);
    }

    /**
     * Processes an <code>event</code> by sequentially trying to apply all
     * {@link SpeechRule}s in the order they are defined. If rule is
     * successfully applied the result is used to populate an
     * <code>utterance</code>. In other words, the first matching rule
     * wins.
     *
     * @param event The event to process.
     * @param utterance Utterance to populate with the result.
     * @return True if the event was processed, false otherwise.
     */
    public boolean processEvent(AccessibilityEvent event, Utterance utterance) {
        for (SpeechRule speechRule : mSpeechRules) {
            if (speechRule.apply(event, utterance)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses a speech strategy XML file specified by
     * <code>speechStrategyResId</code> and returns a <code>document</code>. If
     * an error occurs during the parsing, it is logged and <code>null</code> is
     * returned.
     * 
     * @param context A {@link Context} instance.
     * @param speechStrategyResId The resource id of the speech strategy XML
     *            file.
     * @return The parsed {@link Document} or <code>null</code> if an error
     *         occurred.
     */
    private Document parseSpeechStrategy(Context context, int speechStrategyResId) {
        try {
            InputStream inputStream = context.getResources().openRawResource(R.raw.speechstrategy);
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            return builder.parse(inputStream);
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "Could not open speechstrategy.xml", ioe);
        } catch (ParserConfigurationException pce) {
            Log.e(LOG_TAG, "Could not open speechstrategy.xml", pce);
        } catch (SAXException se) {
            Log.e(LOG_TAG, "Could not open speechstrategy.xml", se);
        }
        return null;
    }
}
