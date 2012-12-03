
package com.google.android.marvin.talkback.formatter;

import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.AccessibilityEventUtils;
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFilter;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.google.android.marvin.utils.StringBuilderUtils;

/**
 * Filters and formats click events from checkable items.
 */
public class CheckableClickedFormatter implements AccessibilityEventFilter, AccessibilityEventFormatter {
    @Override
    public boolean accept(AccessibilityEvent event, TalkBackService context) {
        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return false;
        }

        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat source = record.getSource();

        // Checkable roles are supported in API 14+.
        if ((source != null) && (source.isCheckable())) {
            return true;
        } else if (Build.VERSION.SDK_INT >= 14) {
            return false;
        }

        // If an event is checked, then it must be checkable.
        if (event.isChecked()) {
            return true;
        }

        // If the event is not checked and we're on API <14, we must infer
        // checkable from the view's class.
        if (AccessibilityEventUtils.eventMatchesClass(
                context, event, "android.widget.CompoundButton")) {
            return true;
        }

        return false;
    }

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        final StringBuilder text = utterance.getText();

        final CharSequence eventText = AccessibilityEventUtils.getEventText(event);
        if (!TextUtils.isEmpty(eventText)) {
            StringBuilderUtils.appendWithSeparator(text, eventText);
        }

        // Event text doesn't contain the state on API 16+.
        if (Build.VERSION.SDK_INT >= 16) {
            // We're assuming that event.isChecked() and node.isChecked() are
            // equivalent. As of API 16, there's a race condition for
            // node.isChecked() that causes it to return the old state.
            if (event.isChecked()) {
                StringBuilderUtils.appendWithSeparator(
                        text, context.getString(R.string.value_checked));
            } else {
                StringBuilderUtils.appendWithSeparator(
                        text, context.getString(R.string.value_not_checked));
            }
        }

        utterance.getCustomEarcons().add(R.id.sounds_clicked);
        utterance.getCustomVibrations().add(R.id.patterns_clicked);

        return true;
    }
}
