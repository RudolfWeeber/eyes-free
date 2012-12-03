/*
 * Copyright (C) 2012 Google Inc.
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

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.AccessibilityEventUtils;
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * Formatter for progress bar events.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class ProgressBarFormatter implements AccessibilityEventFormatter {
    private static AccessibilityNodeInfoCompat sRecentlyExplored;

    public static void updateRecentlyExplored(AccessibilityNodeInfoCompat node) {
        if (sRecentlyExplored != null) {
            sRecentlyExplored.recycle();
        }

        if (node != null) {
            sRecentlyExplored = AccessibilityNodeInfoCompat.obtain(node);
        } else {
            sRecentlyExplored = null;
        }
    }

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        if (shouldDropEvent(event)) {
            LogUtils.log(this, Log.VERBOSE, "Dropping unwanted progress bar event");
            return true;
        }

        final CharSequence text = AccessibilityEventUtils.getEventText(event);

        if (!TextUtils.isEmpty(text)) {
            utterance.getText().append(text);
            return true;
        }

        final float percent = getProgressPercent(event);
        final float rate = (float) Math.pow(2.0, (percent / 50.0) - 1);

        utterance.getEarcons().add(R.raw.view_scrolled_tone);
        utterance.getMetadata().putFloat(Utterance.KEY_METADATA_EARCON_RATE, rate);
        utterance.getMetadata().putFloat(Utterance.KEY_METADATA_EARCON_VOLUME, 0.5f);

        return true;
    }

    private boolean shouldDropEvent(AccessibilityEvent event) {
        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat source = record.getSource();

        // Don't drop if we're on pre-ICS or the event was generated (e.g.
        // missing a node).
        if (source == null) {
            return false;
        }

        // Don't drop if the node is currently focused or accessibility focused.
        if (source.isFocused() || source.isAccessibilityFocused()) {
            return false;
        }

        // Don't drop if the node was recently explored.
        if (source.equals(sRecentlyExplored)) {
            return false;
        }

        return true;
    }

    private float getProgressPercent(AccessibilityEvent event) {
        final int maxProgress = event.getItemCount();
        final int progress = event.getCurrentItemIndex();
        final float percent = (progress / (float) maxProgress);

        return (100.0f * Math.max(0.0f, Math.min(1.0f, percent)));
    }

}
