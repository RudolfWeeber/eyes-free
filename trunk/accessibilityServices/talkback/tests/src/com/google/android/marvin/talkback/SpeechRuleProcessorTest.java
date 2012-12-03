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
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.formatter.EventSpeechRule;
import com.google.android.marvin.talkback.formatter.EventSpeechRuleProcessor;

/**
 * This class is a test case for the {@link EventSpeechRuleProcessor} class. We
 * test if the processor correctly interacts with the {@link EventSpeechRule}s
 * it manages and the these rule are throughly tested by {@link SpeechRuleTest}.
 * 
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public class SpeechRuleProcessorTest extends AndroidTestCase {

    /**
     * Test event processing by loading a speech strategy file and processing
     * two events each matching different rule.
     * 
     * @throws Exception If an error occurs during text execution.
     */
    public void testProcessEvent() throws Exception {
        // load the TalkBack speech strategy
        final EventSpeechRuleProcessor processor = new EventSpeechRuleProcessor(getContext());
        processor.addSpeechStrategy(com.google.android.marvin.talkback.R.raw.speechstrategy);

        // create a click event
        final AccessibilityEvent clickedEvent = AccessibilityEvent
                .obtain(AccessibilityEvent.TYPE_VIEW_CLICKED);
        clickedEvent.setPackageName("foo.bar.baz");
        clickedEvent.setClassName("foo.bar.baz.Boo");

        final Utterance clickUtterance = Utterance.obtain();
        final boolean clickProcesseed = processor.processEvent(clickedEvent, clickUtterance);

        // see if the event was matched to the filter
        assertTrue("The event must match the filter", clickProcesseed);

        // just make sure something is produced - specific cases in
        // SpeechRuleTest
        assertTrue("An utterance must be produced", clickUtterance.getText().length() > 0);

        // create an focus
        final AccessibilityEvent notificationEvent = AccessibilityEvent
                .obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        notificationEvent.setPackageName("foo.bar.baz");
        notificationEvent.setClassName("foo.bar.baz.Boo");

        final Utterance focusUtterance = Utterance.obtain();
        final boolean focusProcesseed = processor.processEvent(clickedEvent, focusUtterance);

        // see if the event was matched to the filter
        assertTrue("The event must match the filter", focusProcesseed);

        // just make sure something is produced - specific cases in
        // SpeechRuleTest
        assertTrue("An utterance must be produced", focusUtterance.getText().length() > 0);
    }
}
