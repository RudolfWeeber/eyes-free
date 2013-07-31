/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.googlecode.eyesfree.utils.StringBuilderUtils;

import java.util.Collections;
import java.util.List;

/**
 * Formatter for touch exploration events from the System UI.
 *
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 * @author alanv@google.com (Alan Viverette)
 */
public class TouchExplorationSystemUiFormatter implements AccessibilityEventFormatter {
    /** The most recently spoken utterance. Used to eliminate duplicates. */
    private final SpannableStringBuilder mLastUtteranceText = new SpannableStringBuilder();

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        final CharSequence recordText = getRecordText(context, event);

        // Don't populate with empty text. This should never happen!
        if (TextUtils.isEmpty(recordText)) {
            return false;
        }

        // Don't speak the same utterance twice.
        if (TextUtils.equals(mLastUtteranceText, recordText)) {
            return false;
        }

        utterance.addSpoken(recordText);
        utterance.addHaptic(R.id.patterns_hover);
        utterance.addAuditory(R.id.sounds_hover);

        mLastUtteranceText.clear();
        mLastUtteranceText.append(recordText);

        return true;
    }

    private CharSequence getRecordText(Context context, AccessibilityEvent event) {
        final SpannableStringBuilder builder = new SpannableStringBuilder();
        final List<CharSequence> entries = AccessibilityEventCompat.getRecord(event, 0).getText();

        // Reverse the entries so that time is read aloud first.
        Collections.reverse(entries);

        for (final CharSequence entry : entries) {
            StringBuilderUtils.appendWithSeparator(builder, entry);
        }

        return builder;
    }
}
