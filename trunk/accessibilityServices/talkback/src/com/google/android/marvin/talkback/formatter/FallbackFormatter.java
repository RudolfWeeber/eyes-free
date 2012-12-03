
package com.google.android.marvin.talkback.formatter;

import android.support.v4.view.accessibility.AccessibilityEventCompat;
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

/**
 * Provides formatting for {@link AccessibilityEvent#TYPE_VIEW_FOCUSED} and
 * {@link AccessibilityEventCompat#TYPE_VIEW_HOVER_ENTER} events on JellyBean.
 * <p>
 * For events that don't have source nodes, reads the event text aloud;
 * otherwise, just provides the corresponding vibration and earcon feedback.
 * </p>
 */
public class FallbackFormatter implements AccessibilityEventFormatter, AccessibilityEventFilter {
    private static final int EVENT_MASK = AccessibilityEvent.TYPE_VIEW_FOCUSED
            | AccessibilityEvent.TYPE_VIEW_SELECTED
            | AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER;

    @Override
    public boolean accept(AccessibilityEvent event, TalkBackService context) {
        if ((event.getEventType() & EVENT_MASK) == 0) {
            return false;
        }

        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat source = record.getSource();

        if (source != null) {
            source.recycle();
            return false;
        }

        return true;
    }

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat source = record.getSource();

        if (source != null) {
            source.recycle();

            // TODO(caseyburkhardt): Flip to false once we define the
            // "false from formatter drops event" logic.
            return true;
        }

        // Add earcons and patterns since the event doesn't have a source node
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                utterance.getVibrationPatterns().add(R.array.view_focused_or_selected_pattern);
                utterance.getCustomEarcons().add(R.id.sounds_focused);
                break;
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
                utterance.getVibrationPatterns().add(R.array.view_focused_or_selected_pattern);
                utterance.getCustomEarcons().add(R.id.sounds_selected);
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
