
package com.google.android.marvin.talkback.formatter;

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.AccessibilityEventUtils;
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;

/**
 * Provides formatting for {@link AccessibilityEvent#TYPE_VIEW_FOCUSED} and
 * {@link AccessibilityEventCompat#TYPE_VIEW_HOVER_ENTER} events on JellyBean.
 * <p>
 * For events that don't have source nodes, reads the event text aloud;
 * otherwise, just provides the corresponding vibration and earcon feedback.
 * </p>
 */
public class FallbackFormatter implements AccessibilityEventFormatter {
    @Override
    public boolean format(AccessibilityEvent event, Context context, Utterance utterance) {
        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat source = record.getSource();

        // Always add earcons for FOCUSED so that they play concurrently with
        // the accessibility focus earcons.
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            utterance.getCustomEarcons().add(R.id.sounds_focused);
        }

        // For compatibility's sake, we should only drop events that are
        // populated with a source node. Otherwise, it's someone's custom
        // event which is effectively TYPE_ANNOUNCEMENT.
        if (source != null) {
            source.recycle();
            return true;
        }

        // Add earcons and patterns since the event doesn't have a source node
        // and there won't be a subsequent accessibility focus event.
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                // Already added the earcon for system focus.
                utterance.getVibrationPatterns().add(R.array.view_focused_or_selected_pattern);
                break;
            case AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER:
                utterance.getCustomEarcons().add(R.id.sounds_hover);
                utterance.getVibrationPatterns().add(R.array.view_hovered_pattern);
                break;
        }

        final CharSequence text = AccessibilityEventUtils.getEventText(event);
        if (!TextUtils.isEmpty(text)) {
            utterance.getText().append(text);
        }

        return true;
    }
}
