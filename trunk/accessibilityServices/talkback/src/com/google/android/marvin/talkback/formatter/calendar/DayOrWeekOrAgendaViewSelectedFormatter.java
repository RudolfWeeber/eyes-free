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
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;

import java.util.List;

/**
 * This class is a custom formatter for
 * {@link AccessibilityEvent#TYPE_VIEW_SELECTED} events from the Day/Weekly view
 * of the Google Calendar application.
 */
public final class DayOrWeekOrAgendaViewSelectedFormatter implements AccessibilityEventFormatter {
    private static final int SDK_INT = Build.VERSION.SDK_INT;
    private static final int GINGERBREAD = 9;
    private static final int HONEYCOMB = 10;

    private static String CLASS_NAME_AGENDA_VIEW = "";

    static {
        switch (SDK_INT) {
            case GINGERBREAD:
                CLASS_NAME_AGENDA_VIEW = "com.android.calendar.AgendaListView";
                break;
            case HONEYCOMB:
                CLASS_NAME_AGENDA_VIEW = "com.android.calendar.agenda.AgendaListView";
                break;
        }
    }

    private static final char SPACE = ' ';
    private static final char PERIOD = '.';
    private static final char COMMA = ',';

    private static final String CLASS_NAME_DAY_VIEW = "com.android.calendar.DayView";

    public static final Uri CONTENT_URI_CALENDARS = Uri
            .parse("content://com.android.calendar/calendars");

    private static final String COLOR = "color";
    private static final String DISPLAY_NAME = "displayName";
    private static final String SELECTED = "selected";

    private static final int COLUMN_INDEX_COLOR = 0;
    private static final int COLUMN_INDEX_DISPLAY_NAME = 1;

    private static final String CALENDAR_EVENT_COLOR = "color";
    private static final String CALENDAR_EVENT_TITLE = "title";
    private static final String CALENDAR_EVENT_LOCATION = "location";
    private static final String CALENDAR_EVENT_START_MILLIS = "startMillis";
    private static final String CALENDAR_EVENT_END_MILLIS = "endMillis";

    private static final String[] PROJECTION = new String[] {
            COLOR, DISPLAY_NAME
    };

    private final SparseArray<String> mColorToDisplayNameMap = new SparseArray<String>();

    private String mLastTimeFragment;

    private String mLastDateFragment;

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        String className = event.getClassName().toString();
        if (CLASS_NAME_AGENDA_VIEW.equals(className)) {
            formatAgendaViewSelected(event, context, utterance);
        } else {
            formatDayOrWeekViewSelected(event, context, utterance);
        }

        return true;
    }

    /**
     * Formats utterance for announcing DayView or WeekView selection events.
     *
     * @param event The processed {@link AccessibilityEvent}.
     * @param context For accessing resources.
     * @param utterance The utterance to format.
     */
    private void formatDayOrWeekViewSelected(AccessibilityEvent event, Context context,
            Utterance utterance) {
        final SpannableStringBuilder textBuilder = new SpannableStringBuilder();
        appendSelectedRange(context, event, textBuilder);
        appendSelectedEventIndexAnouncement(context, event, textBuilder);
        appendSelectedEventDetails(context, event, textBuilder);

        if (!TextUtils.isEmpty(textBuilder)) {
            utterance.addSpoken(textBuilder);
        }
    }

    /**
     * Formats utterance for announcing AgendaView selection events.
     *
     * @param event The processed {@link AccessibilityEvent}.
     * @param context For accessing resources.
     * @param utterance The utterance to format.
     */
    private void formatAgendaViewSelected(AccessibilityEvent event, Context context,
            Utterance utterance) {
        final SpannableStringBuilder textBuilder = new SpannableStringBuilder();
        appendDisplayName(context, event, textBuilder);
        appendEventText(event, textBuilder);

        if (!TextUtils.isEmpty(textBuilder)) {
            utterance.addSpoken(textBuilder);
        }
    }

    /**
     * Appends the event text.
     *
     * @param event The event being processed.
     * @param textBuilder The builder to which to append the announcement.
     */
    private void appendEventText(AccessibilityEvent event, SpannableStringBuilder textBuilder) {
        List<CharSequence> text = event.getText();
        for (CharSequence subText : text) {
            textBuilder.append(subText);
            textBuilder.append(SPACE);
        }
    }

    /**
     * Appends announcement for the selected time range.
     *
     * @param context For accessing resources.
     * @param event The event being processed.
     * @param textBuilder The builder to which to append the announcement.
     */
    private void appendSelectedRange(Context context, AccessibilityEvent event,
            SpannableStringBuilder textBuilder) {
        String eventText = event.getText().get(0).toString();
        if (TextUtils.isEmpty(eventText)) {
            return;
        }
        String className = event.getClassName().toString();
        if (CLASS_NAME_DAY_VIEW.equals(className)) {
            String timeFragment = eventText;
            switch (SDK_INT) {
                case GINGERBREAD:
                    if (!timeFragment.equals(mLastTimeFragment)) {
                        mLastTimeFragment = timeFragment;
                        textBuilder.append(eventText);
                        textBuilder.append(PERIOD);
                    }
                    break;
                case HONEYCOMB:
                    String dateFragment = null;
                    int firstCommaIndex = eventText.indexOf(COMMA);
                    if (firstCommaIndex > -1) {
                        timeFragment = eventText.substring(0, firstCommaIndex);
                        dateFragment = eventText.substring(firstCommaIndex + 1);
                    }
                    if (!timeFragment.equals(mLastTimeFragment)) {
                        mLastTimeFragment = timeFragment;
                        textBuilder.append(timeFragment);
                        if (dateFragment == null) {
                            textBuilder.append(PERIOD);
                        } else {
                            textBuilder.append(COMMA);
                        }
                    }
                    if (dateFragment != null) {
                        textBuilder.append(dateFragment);
                        textBuilder.append(PERIOD);
                    }
                    break;
            }
        } else {
            int firstCommaIndex = eventText.indexOf(',');
            String timeFragment = eventText.substring(0, firstCommaIndex);
            String dateFragment = eventText.substring(firstCommaIndex + 1);
            if (!dateFragment.equals(mLastDateFragment)) {
                mLastDateFragment = dateFragment;
                textBuilder.append(dateFragment);
                int todayEventCount = event.getAddedCount();
                if (todayEventCount > 0) {
                    textBuilder.append(COMMA);
                    textBuilder.append(SPACE);
                    appendEventCountAnnouncement(context, event, textBuilder);
                }
                textBuilder.append(PERIOD);
                textBuilder.append(SPACE);
            }
            if (!timeFragment.equals(mLastTimeFragment)) {
                mLastTimeFragment = timeFragment;
                textBuilder.append(timeFragment);
                if (event.getItemCount() > 0) {
                    textBuilder.append(COMMA);
                    textBuilder.append(SPACE);
                }
            }
        }
    }

    /**
     * Appends announcement for the index of the selected event in case of
     * multiple events in the selected time range.
     *
     * @param context For accessing resources.
     * @param event The event being processed.
     * @param textBuilder The builder to which to append the announcement.
     */
    private void appendSelectedEventIndexAnouncement(Context context, AccessibilityEvent event,
            SpannableStringBuilder textBuilder) {
        int eventCount = event.getItemCount();
        if (eventCount > 1) {
            textBuilder.append(SPACE);
            appendEventCountAnnouncement(context, event, textBuilder);
            textBuilder.append(PERIOD);
            textBuilder.append(SPACE);
        }
    }

    /**
     * Appends announcement for event count.
     *
     * @param context For accessing resources.
     * @param event The processed event.
     * @param textBuilder The builder to which to append the announcement.
     */
    private void appendEventCountAnnouncement(Context context, AccessibilityEvent event,
            SpannableStringBuilder textBuilder) {
        int eventIndex = event.getCurrentItemIndex() + 1;
        int eventCount = event.getItemCount();
        textBuilder.append(context.getString(R.string.template_announce_item_index, eventIndex,
                eventCount));
        textBuilder.append(SPACE);
        textBuilder.append(context.getResources().getQuantityString(R.plurals.plural_event,
                eventCount));
    }

    /**
     * Appends announcement for the selected event details i.e. event content.
     *
     * @param context For accessing resources.
     * @param event The event being processed.
     * @param textBuilder The builder to which to append the announcement.
     */
    @SuppressWarnings("deprecation")
    private void appendSelectedEventDetails(Context context, AccessibilityEvent event,
            SpannableStringBuilder textBuilder) {
        Bundle parcelableData = (Bundle) event.getParcelableData();
        if (parcelableData == null) {
            return;
        }

        // append account description if more than one account is shown
        appendDisplayName(context, event, textBuilder);

        // append the event title
        CharSequence title = parcelableData.getCharSequence(CALENDAR_EVENT_TITLE);
        if (!TextUtils.isEmpty(title)) {
            textBuilder.append(SPACE);
            textBuilder.append(title);
            textBuilder.append(PERIOD);
        }

        // append time
        long startMillis = parcelableData.getLong(CALENDAR_EVENT_START_MILLIS);
        if (startMillis > 0) {
            long endMillis = parcelableData.getLong(CALENDAR_EVENT_END_MILLIS);
            int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL
                    | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
            if (DateFormat.is24HourFormat(context)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            String timeRange = DateUtils.formatDateRange(context, startMillis, endMillis, flags);
            textBuilder.append(SPACE);
            textBuilder.append(timeRange);
            textBuilder.append(PERIOD);
        }

        // append the event location
        CharSequence location = parcelableData.getCharSequence(CALENDAR_EVENT_LOCATION);
        if (!TextUtils.isEmpty(location)) {
            textBuilder.append(SPACE);
            textBuilder.append(location);
            textBuilder.append(PERIOD);
        }
    }

    /**
     * Appends the display name from whose calendar the event comes.
     *
     * @param context For accessing resources.
     * @param event The event being processed.
     * @param textBuilder The builder to which to append the announcement.
     */
    private void appendDisplayName(Context context, AccessibilityEvent event,
            SpannableStringBuilder textBuilder) {
        Bundle parcelableData = (Bundle) event.getParcelableData();
        if (parcelableData == null) {
            return;
        }
        // append account description if more than one account is shown
        int color = parcelableData.getInt(CALENDAR_EVENT_COLOR);
        String accountDescription = mColorToDisplayNameMap.get(color);
        if (accountDescription == null) {
            reloadColorToDisplayNameMap(context);
        }
        if (mColorToDisplayNameMap.size() > 1) {
            accountDescription = mColorToDisplayNameMap.get(color);
            if (accountDescription != null) {
                textBuilder.append(context.getString(R.string.value_owner));
                textBuilder.append(COMMA);
                textBuilder.append(SPACE);
                textBuilder.append(accountDescription);
                textBuilder.append(PERIOD);
            }
        }
    }

    /**
     * Reloads the color to display name map used to announce the event owner.
     *
     * @param context For accessing resources.
     */
    private void reloadColorToDisplayNameMap(Context context) {
        Cursor cursor = context.getContentResolver().query(CONTENT_URI_CALENDARS, PROJECTION,
                SELECTED + "=?", new String[] {
                    "1"
                }, null);
        if (cursor == null) {
            return;
        }
        while (!cursor.isLast()) {
            cursor.moveToNext();
            int color = cursor.getInt(COLUMN_INDEX_COLOR);
            String dispayName = cursor.getString(COLUMN_INDEX_DISPLAY_NAME);
            mColorToDisplayNameMap.put(color, dispayName);
        }
        cursor.close();
    }
}
