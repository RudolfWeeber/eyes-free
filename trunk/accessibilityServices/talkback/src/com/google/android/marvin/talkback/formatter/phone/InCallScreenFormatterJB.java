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

package com.google.android.marvin.talkback.formatter.phone;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.google.android.marvin.utils.StringBuilderUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;

import java.util.List;

/**
 * Formatter that returns an utterance to announce the incoming call screen.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
public final class InCallScreenFormatterJB implements AccessibilityEventFormatter {
    private static final int INDEX_UPPER_TITLE = 1;
    private static final int INDEX_SUBTITILE = 2;

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean speakCallerId = SharedPreferencesUtils.getBooleanPref(prefs,
                context.getResources(), R.string.pref_caller_id_key, R.bool.pref_caller_id_default);

        if (!speakCallerId) {
            // Don't speak the caller ID screen.
            return true;
        }

        final List<CharSequence> eventText = event.getText();
        final StringBuilder utteranceText = utterance.getText();

        final CharSequence title = eventText.get(INDEX_UPPER_TITLE);
        final CharSequence subtitle = eventText.get(INDEX_SUBTITILE);

        if (title != null) {
            StringBuilderUtils.appendWithSeparator(utteranceText, title);
        }

        if (subtitle != null) {
            StringBuilderUtils.appendWithSeparator(utteranceText, subtitle);
        }

        return true;
    }
}
