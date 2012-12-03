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

import android.test.AndroidTestCase;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.formatter.EventSpeechRule;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * This class is a test case for the {@link EventSpeechRule} class.
 *
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public class SpeechRuleTest extends AndroidTestCase {

    private static final String TEMPLATE_SPEECH_STRATEGY =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "  <ss:speechstrategy " +
            "      xmlns:ss=\"http://www.google.android.marvin.talkback.com/speechstrategy\" " +
            "      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "      xsi:schemaLocation=\"http://www.google.android.marvin.talkback.com/speechstrategy speechstrategy.xsd \">" +
            "%1s" +
            "</ss:speechstrategy>";

    /**
     * Test if the {@link EventSpeechRule} returns an empty list if it is passed
     * a <code>null</code> document.
     */
    public void testCreateSpeechRules_fromNullDocument() throws Exception {
        final ArrayList<EventSpeechRule> speechRules = EventSpeechRule.createSpeechRules(
                getContext(), null, null, null, null);
        assertNotNull("Must always return an instance.", speechRules);
        assertTrue("The list must be empty.", speechRules.isEmpty());
    }

    /**
     * Test if an event is filtered correctly when all its properties are used.
     */
    public void testCreateSpeechRules_filteringByTextProperty() throws Exception {
        // define a speech strategy content
        final String speechStrategyContent =
                "<ss:rule>" +
                "   <ss:filter>" +
                "       <ss:text>first blank second</ss:text>" +
                "   </ss:filter>" +
                "   <ss:formatter>" +
                "       <ss:template>template</ss:template>" +
                "   </ss:formatter>" +
                "</ss:rule>";

        // load the speech rules
        final ArrayList<EventSpeechRule> speechRules =
                loadSpeechRulesAssertingCorrectness(speechStrategyContent, 1);

        // create an event that matches our only rule
        final AccessibilityEvent event = AccessibilityEvent.obtain();
        event.getText().add("first blank second");

        // we expect to match the rule with the event and get an utterance
        final EventSpeechRule speechRule = speechRules.get(0);
        final Utterance utterance = Utterance.obtain();
        final boolean processed = speechRule.apply(event, utterance);

        assertTrue("The event must match the filter", processed);
        assertTrue("An utterance must be produed", !TextUtils.isEmpty(utterance.getText()));
    }

    /**
     * Test if an event is filtered correctly when all its properties are used.
     */
    public void testCreateSpeechRules_filteringByAllEventProperties() throws Exception {

        // define a speech strategy content
        final String speechStrategyContent =
                "<ss:rule>" +
                "   <ss:filter>" +
                "       <ss:addedCount>1</ss:addedCount>" +
                "       <ss:beforeText>beforeText</ss:beforeText>" +
                "       <ss:checked>true</ss:checked>" +
                "       <ss:className>foo.bar.baz.Test</ss:className>" +
                "       <ss:contentDescription>contentDescription</ss:contentDescription>" +
                "       <ss:currentItemIndex>2</ss:currentItemIndex>" +
                "       <ss:enabled>true</ss:enabled>" +
                "       <ss:eventType>TYPE_NOTIFICATION_STATE_CHANGED</ss:eventType>" +
                "       <ss:fromIndex>1</ss:fromIndex>" +
                "       <ss:fullScreen>true</ss:fullScreen>" +
                "       <ss:itemCount>10</ss:itemCount>" +
                "       <ss:packageName>foo.bar.baz</ss:packageName>" +
                "       <ss:password>true</ss:password>" +
                "       <ss:removedCount>2</ss:removedCount>" +
                "       <ss:text>first blank second</ss:text>" +
                "   </ss:filter>" +
                "   <ss:formatter>" +
                "       <ss:template>text template</ss:template>" +
                "   </ss:formatter>" +
                "</ss:rule>";

        // load the speech rules
        final ArrayList<EventSpeechRule> speechRules =
                loadSpeechRulesAssertingCorrectness(speechStrategyContent, 1);

        // create an event that matches our only rule
        final AccessibilityEvent event = AccessibilityEvent
                .obtain();
        event.setAddedCount(1);
        event.setBeforeText("beforeText");
        event.setChecked(true);
        event.setClassName("foo.bar.baz.Test");
        event.setContentDescription("contentDescription");
        event.setCurrentItemIndex(2);
        event.setEnabled(true);
        event.setEventType(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        event.setFromIndex(1);
        event.setFullScreen(true);
        event.setItemCount(10);
        event.setPackageName("foo.bar.baz");
        event.setPassword(true);
        event.setRemovedCount(2);
        event.getText().add("first blank second");

        // we expect to match the rule with the event and get an utterance
        final EventSpeechRule speechRule = speechRules.get(0);
        final Utterance utterance = Utterance.obtain();
        final boolean processed = speechRule.apply(event, utterance);

        assertTrue("The event must match the filter", processed);
        assertTrue("An utterance must be produed", !TextUtils.isEmpty(utterance.getText()));
    }

    /**
     * Test if an event is filtered correctly when the filter specifies a
     * property whose value is <code>null</code>.
     */
    public void testCreateSpeechRules_filteringByNullEventProperty() throws Exception {
        // define a speech strategy content
        final String speechStrategyContent =
                "<ss:rule>" +
                "   <ss:filter>" +
                "       <ss:packageName>foo.bar.baz</ss:packageName>" +
                "   </ss:filter>" +
                "</ss:rule>";

        // load the speech rules
        final ArrayList<EventSpeechRule> speechRules =
                loadSpeechRulesAssertingCorrectness(speechStrategyContent, 1);

        // create an event with a null value for the filtered property
        final AccessibilityEvent event = AccessibilityEvent.obtain();

        // we expect to match the rule with the event and get an utterance
        final EventSpeechRule speechRule = speechRules.get(0);
        final Utterance utterance = Utterance.obtain();
        final boolean processed = speechRule.apply(event, utterance);

        assertTrue("The event must not match the filter", !processed);
        assertTrue("An utterance must not be produced", TextUtils.isEmpty(utterance.getText()));
    }

    /**
     * Test if the utterance for speaking an event is properly constructed if
     * the a speech rule defines a template which is to be populated by event
     * property values.
     */
    public void testCreateSpeechRules_useRuleWithPropertyValuesFormatter() throws Exception {
        // define a speech strategy content
        final String speechStrategyContent =
                "<ss:rule>" +
                "  <ss:formatter>" +
                "    <ss:template>@com.google.android.marvin.talkback:string/template_compound_button_selected</ss:template>" +
                "    <ss:property>packageName</ss:property>" +
                "  </ss:formatter>" +
                "</ss:rule>";

        // load the speech rules
        final ArrayList<EventSpeechRule> speechRules =
                loadSpeechRulesAssertingCorrectness(speechStrategyContent, 1);

        // create an event with minimal data
        final AccessibilityEvent event = AccessibilityEvent.obtain();
        event.setPackageName("foo.bar.baz");

        // we expect to match the rule with the event and get an utterance
        final EventSpeechRule speechRule = speechRules.get(0);
        final Utterance utterance = Utterance.obtain();
        final boolean processed = speechRule.apply(event, utterance);

        assertTrue("The event must match the filter", processed);
        assertTrue("An utterance must be produced", !TextUtils.isEmpty(utterance.getText()));
    }

    /**
     * Test if an event is dropped on the floor if not formatter is specified.
     */
    public void testCreateSpeechRules_dropEventIfNoFormatter() throws Exception {
        // define a speech strategy content
        final String speechStrategyContent =
                "<ss:rule>" +
                "  <ss:filter>" +
                "    <ss:eventType>TYPE_VIEW_CLICKED</ss:eventType>" +
                "  </ss:filter>" +
                "</ss:rule>";

        // load the speech rules
        final ArrayList<EventSpeechRule> speechRules =
                loadSpeechRulesAssertingCorrectness(speechStrategyContent, 1);

        // create an event with a null value for the filtered property
        final AccessibilityEvent event = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_VIEW_CLICKED);

        // we expect to match the rule with the event and get an utterance
        final EventSpeechRule speechRule = speechRules.get(0);
        final Utterance utterance = Utterance.obtain();
        final boolean processed = speechRule.apply(event, utterance);

        // see if the event was matched to the filter
        assertTrue("The event must match the filter", processed);
        // no formatter, so no side effects expected (the event is dropped)
        assertTrue("No utterance must be produced", utterance.getText().length() == 0);
    }

    /**
     * Test if the utterance for speaking an event is properly constructed if
     * the a speech rule defines a template which is to be populated by event
     * property values.
     */
    public void testCreateSpeechRules_multipleRuleParsing() throws Exception {
        // define a speech strategy content
        final String speechStrategyContent =
                "<ss:rule>" +
                "  <ss:formatter>" +
                "    <ss:property>packageName</ss:property>" +
                "  </ss:formatter>" +
                "</ss:rule>" +
                "<ss:rule>" +
                "  <ss:formatter>" +
                "    <ss:property>packageName</ss:property>" +
                "  </ss:formatter>" +
                "</ss:rule>";

        // load the speech rules
        loadSpeechRulesAssertingCorrectness(speechStrategyContent, 2);

        // no other assertions needed

    }

    /**
     * Test if the utterance for speaking an event is properly constructed if a
     * {@link AccessibilityEventFormatter} is being used.
     */
    public void testCreateSpeechRules_customFormatter() throws Exception {
        // define a speech strategy content
        final String speechStrategyContent =
                "<ss:rule>" +
                "  <ss:formatter>" +
                "    <ss:custom>com.google.android.marvin.talkback.formatter.TextFormatters$ChangedTextFormatter</ss:custom>" +
                "  </ss:formatter>" +
                "</ss:rule>";

        // load the speech rules
        loadSpeechRulesAssertingCorrectness(speechStrategyContent, 1);

        // no other assertions needed
    }

    /**
     * Test that the meta-data of a speech rule is properly parsed and passed to
     * a formatted utterance.
     */
    public void testCreateSpeechRules_metadata() throws Exception {
        // define a speech strategy content
        final String speechStrategyContent =
                "<ss:rule>" +
                "  <ss:metadata>" +
                "    <ss:queuing>UNINTERRUPTIBLE</ss:queuing>" +
                "  </ss:metadata>" +
                "</ss:rule>";

        // load the speech rules
        final ArrayList<EventSpeechRule> speechRules =
                loadSpeechRulesAssertingCorrectness(speechStrategyContent, 1);

        // create an event with no properties set
        final AccessibilityEvent event = AccessibilityEvent.obtain();

        // we expect to match the rule with the event and get an utterance
        final EventSpeechRule speechRule = speechRules.get(0);
        final Utterance utterance = Utterance.obtain();
        final boolean processed = speechRule.apply(event, utterance);

        assertTrue("The event must match the filter", processed);
        assertEquals("The meta-data must have its queuing poperty set", 2,
                utterance.getMetadata().get(Utterance.KEY_METADATA_QUEUING));
    }

    /**
     * Loads the {@link EventSpeechRule}s from a document obtained by parsing a
     * XML string generated by populating the {@link #TEMPLATE_SPEECH_STRATEGY}
     * with <code>speechStrategyContent</code>. The method asserts the document is parsed and the
     * parsed speech rules list has <code>expectedSize</code>.
     *
     * @return The parsed speech rules.
     */
    private ArrayList<EventSpeechRule> loadSpeechRulesAssertingCorrectness(
            String speechStrategyContent,
            int expectedSize) throws Exception {
        final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

        // create the speech strategy document
        final String speechStrategy = String.format(
                TEMPLATE_SPEECH_STRATEGY, speechStrategyContent);

        // parse the document
        final Document document = builder.parse(new InputSource(new StringReader(speechStrategy)));
        assertNotNull("Test case setup requires properly parsed document.", document);

        // make sure we have the expected number of rules
        final ArrayList<EventSpeechRule> speechRules = EventSpeechRule.createSpeechRules(
                getContext(), null, null, null, document);
        assertEquals("There must be " + expectedSize + " speech rule(s)", expectedSize,
                speechRules.size());

        return speechRules;
    }
}
