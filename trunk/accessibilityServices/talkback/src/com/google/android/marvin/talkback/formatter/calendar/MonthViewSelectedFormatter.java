/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.google.android.marvin.talkback.formatter.calendar;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;

/**
 * Formatter for the {@link AccessibilityEvent#TYPE_VIEW_SELECTED} events for
 * the month Calendar view.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public class MonthViewSelectedFormatter implements AccessibilityEventFormatter {
    private static final char COMMA = ',';
    private static final char SPACE = ' ';
    private static final char PERIOD = '.';

    private String mLastDayFragment;

    @Override
    public boolean format(AccessibilityEvent event, Context context, Utterance utterance) {
        StringBuilder textBuilder = utterance.getText();
        String eventText = event.getText().get(0).toString();

        // append day of week
        int firstCommaIndex = eventText.indexOf(',');
        String dayFragment = eventText.substring(0, firstCommaIndex);
        if (!dayFragment.equals(mLastDayFragment)) {
            mLastDayFragment = dayFragment;
            textBuilder.append(dayFragment);
            textBuilder.append(COMMA);
            textBuilder.append(SPACE);
        }

        // append date
        String monthDayFragment = eventText.substring(firstCommaIndex + 1);
        textBuilder.append(monthDayFragment);

        // append event count
        int todayEventCount = event.getItemCount();
        if (todayEventCount > 0) {
            textBuilder.append(COMMA);
            textBuilder.append(SPACE);
            textBuilder.append(todayEventCount);
            textBuilder.append(SPACE);
            textBuilder.append(context.getResources().getQuantityString(R.plurals.plural_event,
                    todayEventCount));
        }
        textBuilder.append(PERIOD);

        return true;
    }
}
