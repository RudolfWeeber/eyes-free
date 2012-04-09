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
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;

import java.util.regex.Pattern;

/**
 * This class is a custom formatter for the Day/Weekly/Monthly
 * view of the Google Calendar application.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 *
 */
public class DayViewWindowStateChangedFormatter implements AccessibilityEventFormatter {
    private static final int SDK_INT = Build.VERSION.SDK_INT;
    private static final int GINGERBREAD = 9;
    private static final int HONEYCOMB = 10;

    private static final char SPACE = ' ';
    private static final char PERIOD = '.';
    private static final char COMMA = ',';

    private static final String KEY_SHOWN_DAY_COUNT = "shownDayCount";

    private static final int WEEK_DAY_COUNT = 7;

    private final Pattern mWeekSplitPattern = Pattern.compile(" \u2013 ");

    @Override
    public boolean format(AccessibilityEvent event, Context context, Utterance utterance) {
        StringBuilder textBuilder = utterance.getText();

        CharSequence eventText = event.getText().get(0).toString();
        switch (SDK_INT) {
            case GINGERBREAD:
                textBuilder.append(context.getString(R.string.template_announce_day, eventText));
                break;
            case HONEYCOMB:
                Bundle bundle = (Bundle) event.getParcelableData();
                int shownDayCount = bundle.getInt(KEY_SHOWN_DAY_COUNT);
                if (shownDayCount == WEEK_DAY_COUNT) {
                    String[] rangeFragments = mWeekSplitPattern.split(eventText);
                    String fromDate = rangeFragments[0];
                    String toDate = rangeFragments[1];
                    textBuilder.append(context.getString(R.string.template_announce_week, fromDate,
                            toDate));
                } else {
                    textBuilder.append(context.getString(R.string.template_announce_day, eventText));
                }
                break;
        }

        int todayEventCount = event.getAddedCount();
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
