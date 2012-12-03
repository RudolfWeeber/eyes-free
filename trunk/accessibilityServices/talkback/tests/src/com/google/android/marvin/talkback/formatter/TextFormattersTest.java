/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.SpeechCleanupUtils;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.test.TextActivity;

/**
 * This class is a test case for the {@link TextFormatters} class.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class TextFormattersTest extends ActivityInstrumentationTestCase2<TextActivity> {
    private CachingDelegate mDelegate;
    private EventSpeechRuleProcessor mProcessor;
    private Instrumentation mInstrumentation;

    public TextFormattersTest() {
        super(TextActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        setActivityInitialTouchMode(false);

        mDelegate = new CachingDelegate();

        getActivity().getWindow().getDecorView().setAccessibilityDelegate(mDelegate);

        mProcessor = new EventSpeechRuleProcessor(getActivity());
        mProcessor.addSpeechStrategy(com.google.android.marvin.talkback.R.raw.speechstrategy);

        mInstrumentation = getInstrumentation();
    }

    @Override
    protected void tearDown() throws Exception {
        mDelegate.clearCache();

        super.tearDown();
    }

    public void testPreconditions() {
        final AccessibilityManager accessibilityManager = (AccessibilityManager) getActivity()
                .getSystemService(Context.ACCESSIBILITY_SERVICE);

        assertTrue("Accessibility must be turned on", accessibilityManager.isEnabled());
    }

    public void testChangedTextFormatter_deleteCharacter() throws Exception {
        internalTestChangedTextFormatter_deleteCharacter("hello");
        internalTestChangedTextFormatter_deleteCharacter("hello ");
    }

    private void internalTestChangedTextFormatter_deleteCharacter(String startingText) {
        if (startingText.length() < 1) {
            throw new IllegalArgumentException("This test case can only handle non-empty strings");
        }

        final Activity activity = getActivity();
        final EditText username = (EditText) activity.findViewById(R.id.username);

        activity.runOnUiThread(new ClearTextRunnable(username));
        username.requestFocus();

        mInstrumentation.sendStringSync(startingText);
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL);

        final AccessibilityEvent event = mDelegate.getLastAccessibilityEvent();

        assertNotNull("Last accessibility event must not be null", event);

        final Utterance utterance = Utterance.obtain();
        final boolean processed = mProcessor.processEvent(event, utterance);
        event.recycle();

        assertTrue("The event must match the filter", processed);

        final String text = utterance.getText().toString();
        final String lastChar = startingText.substring(startingText.length() - 1);
        final CharSequence cleanLastChar = SpeechCleanupUtils.cleanUp(activity, lastChar);

        assertTrue("The event text must contain the 'clean' version of the deleted character",
                text.contains(cleanLastChar));
    }

    /**
     * Clears the text of a TextView.
     */
    private class ClearTextRunnable implements Runnable {
        private TextView mTargetView;

        public ClearTextRunnable(TextView targetView) {
            mTargetView = targetView;
        }

        @Override
        public void run() {
            mTargetView.setText("");
        }
    }

    /**
     * Caches the last {@link AccessibilityEvent} sent from within the view
     * hierarchy.
     */
    private class CachingDelegate extends AccessibilityDelegate {
        /** The most recent event. */
        private AccessibilityEvent mCachedEvent;

        @Override
        public boolean onRequestSendAccessibilityEvent(
                ViewGroup host, View child, AccessibilityEvent event) {
            if (mCachedEvent != null) {
                mCachedEvent.recycle();
            }
            mCachedEvent = AccessibilityEvent.obtain(event);

            return super.onRequestSendAccessibilityEvent(host, child, event);
        }

        /**
         * @return A copy of the most recent {@link AccessibilityEvent} sent
         *         from within the view hierarchy. The caller is responsible for
         *         recycling the event using
         *         {@link AccessibilityEvent#recycle()}.
         */
        public AccessibilityEvent getLastAccessibilityEvent() {
            if (mCachedEvent == null) {
                return null;
            }

            return AccessibilityEvent.obtain(mCachedEvent);
        }

        /**
         * Clears any cached events.
         */
        public void clearCache() {
            if (mCachedEvent != null) {
                mCachedEvent.recycle();
                mCachedEvent = null;
            }
        }
    }
}
