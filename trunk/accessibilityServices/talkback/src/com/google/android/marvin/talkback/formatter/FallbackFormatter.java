
package com.google.android.marvin.talkback.formatter;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.googlecode.eyesfree.utils.AccessibilityEventUtils;

/**
 * Provides formatting for {@link AccessibilityEvent#TYPE_VIEW_FOCUSED} and
 * {@link AccessibilityEventCompat#TYPE_VIEW_HOVER_ENTER} events on JellyBean.
 * <p>
 * For events that don't have source nodes, reads the event text aloud;
 * otherwise, just provides the corresponding vibration and earcon feedback.
 * </p>
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class FallbackFormatter implements AccessibilityEventFormatter {
    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        final AccessibilityNodeInfo source = event.getSource();

        // Drop events that have source nodes.
        if (source != null) {
            source.recycle();
            return false;
        }

        // Add earcons and patterns since the event doesn't have a source node
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                utterance.addHaptic(R.id.patterns_focused);
                utterance.addAuditory(R.id.sounds_focused);
                break;
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
                utterance.addHaptic(R.id.patterns_selected);
                utterance.addAuditory(R.id.sounds_selected);
                break;
            case AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER:
                utterance.addHaptic(R.id.patterns_hover);
                utterance.addAuditory(R.id.sounds_hover);
                break;
        }

        final CharSequence text = AccessibilityEventUtils.getEventTextOrDescription(event);
        if (!TextUtils.isEmpty(text)) {
            utterance.addSpoken(text);
        }

        return true;
    }

}
