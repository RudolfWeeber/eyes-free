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
import android.os.Bundle;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.Filter;
import com.google.android.marvin.talkback.Formatter;
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.Utils;
import com.google.android.marvin.talkback.Utterance;

import java.util.Collections;
import java.util.List;

/**
 * Formatter for touch exploration events from the System UI.
 *
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public class TouchExplorationSystemUiFormatter implements Filter, Formatter {
    private static final String SYSTEM_UI_PACKAGE_NAME = "com.android.systemui";
    private static final String RECORD_SEPARATOR = " ";

    private final StringBuilder mLastUtteranceText = new StringBuilder();

    @Override
    public boolean accept(AccessibilityEvent event, Context context, Bundle args) {
        return (event.getEventType() == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                && SYSTEM_UI_PACKAGE_NAME.equals(event.getPackageName())
                && (event.getRecordCount() > 0);
    }

    @Override
    public boolean format(AccessibilityEvent event, Context context, Utterance utterance, Bundle args) {
        final StringBuilder utteranceText = utterance.getText();

        if (event.getRecordCount() > 0) {
            final List<CharSequence> entries = event.getRecord(0).getText();

            Collections.reverse(entries);

            for (final CharSequence entry : entries) {
                utteranceText.append(entry);
                utteranceText.append(RECORD_SEPARATOR);
            }
        } else {
            final CharSequence eventText = Utils.getEventText(context, event);

            utteranceText.append(eventText);
        }

        if (TextUtils.isEmpty(utteranceText)) {
            return false;
        }

        if (TextUtils.equals(mLastUtteranceText, utteranceText)) {
            utteranceText.setLength(0);
            return false;
        }

        utterance.getVibrationPatterns().add(R.array.view_hovered_pattern);
        utterance.getEarcons().add(R.raw.hover);

        mLastUtteranceText.setLength(0);
        mLastUtteranceText.append(utteranceText);
        
        return true;
    }
}
