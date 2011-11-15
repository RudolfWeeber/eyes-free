// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.talkback.formatter;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.AccessibilityEventCompatUtils;
import com.google.android.marvin.talkback.Formatter;
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.Utils;
import com.google.android.marvin.talkback.Utterance;

/**
 * Formatter that returns an utterance to announce scrolling.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class ScrollFormatter implements Formatter {
    @Override
    public boolean format(AccessibilityEvent event, Context context, Utterance utterance, Bundle args) {
        final CharSequence text = Utils.getEventText(context, event);

        if (!TextUtils.isEmpty(text)) {
            utterance.getText().append(text);
            return true;
        }

        final float percent = getScrollPercent(event);
        final float rate = (float) Math.pow(2.0, (percent / 50.0) - 1);

        utterance.getEarcons().add(R.raw.item);
        utterance.getMetadata().putFloat(Utterance.KEY_METADATA_EARCON_RATE, rate);
        
        return true;
    }

    /**
     * Returns the percentage scrolled within a scrollable view. The value will
     * be in the range {0..100} where 100 is the maximum scroll amount.
     * 
     * @param event The event from which to obtain the scroll position.
     * @return The percentage scrolled within a scrollable view.
     */
    private float getScrollPercent(AccessibilityEvent event) {
        final float position = getScrollPosition(event);

        return (100.0f * Math.max(0.0f, Math.min(1.0f, position)));
    }

    /**
     * Returns a floating point value representing the scroll position of an
     * {@link AccessibilityEvent}. This value may be outside the range {0..1}.
     * If there's no valid way to obtain a position, this method returns 0.5.
     * 
     * @param event The event from which to obtain the scroll position.
     * @return A floating point value representing the scroll position.
     */
    private float getScrollPosition(AccessibilityEvent event) {
        final int itemCount = event.getItemCount();
        final int fromIndex = event.getFromIndex();

        // First, attempt to use (fromIndex / itemCount).
        if ((fromIndex >= 0) && (itemCount > 0)) {
            return (fromIndex / (float) itemCount);
        }

        final int scrollY = event.getScrollY();
        final int maxScrollY = AccessibilityEventCompatUtils.getMaxScrollY(event);

        // Next, attempt to use (scrollY / maxScrollY). This will fail if the
        // getMaxScrollX() method is not available.
        if ((scrollY >= 0) && (maxScrollY > 0)) {
            return (scrollY / (float) maxScrollY);
        }

        // Finally, attempt to use (scrollY / itemCount).
        // TODO(alanv): Hack from previous versions -- is it still needed?
        if ((scrollY >= 0) && (itemCount > 0) && (scrollY <= itemCount)) {
            return (scrollY / (float) itemCount);
        }

        return 0.5f;
    }
}
