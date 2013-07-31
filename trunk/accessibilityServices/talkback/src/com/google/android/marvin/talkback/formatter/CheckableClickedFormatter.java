
package com.google.android.marvin.talkback.formatter;

import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFilter;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.googlecode.eyesfree.utils.AccessibilityEventUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;

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
        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat source = record.getSource();

        final CharSequence eventText = AccessibilityEventUtils.getEventTextOrDescription(event);
        if (!TextUtils.isEmpty(eventText)) {
            utterance.addSpoken(eventText);
        }

        // Switch and ToggleButton state is sent along with the event, so only
        // append checked / not checked state for other types of controls.
        if (!AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                context, source, android.widget.ToggleButton.class)
                || AccessibilityNodeInfoUtils.nodeMatchesClassByName(
                        context, source, "android.widget.Switch.class")) {
            // Event text doesn't contain the state on API 16+.
            if (Build.VERSION.SDK_INT >= 16) {
                // We're assuming that event.isChecked() and node.isChecked()
                // are equivalent. As of API 16, there's a race condition for
                // node.isChecked() that causes it to return the old state.
                if (event.isChecked()) {
                    utterance.addSpoken(context.getString(R.string.value_checked));
                } else {
                    utterance.addSpoken(context.getString(R.string.value_not_checked));
                }
            }
        }

        utterance.addAuditory(R.id.sounds_clicked);
        utterance.addHaptic(R.id.patterns_clicked);

        return true;
    }
}
