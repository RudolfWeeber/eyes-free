/*
 * Copyright (C) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.marvin.talkback;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.TalkBackService.AccessibilityEventListener;
import com.google.android.marvin.talkback.TalkBackService.KeyEventListener;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.WeakReferenceHandler;

/**
 * Locks the volume control stream during a touch interaction event.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ProcessorVolumeStream implements AccessibilityEventListener, KeyEventListener {
    /** Minimum API version required for this class to function. */
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR2;

    /** Default flags for volume adjustment. */
    private static final int DEFAULT_FLAGS = (AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_VIBRATE
            | AudioManager.FLAG_PLAY_SOUND);

    /** Stream to control when the user is touching the screen. */
    private static final int STREAM_TOUCHING_SCREEN = SpeechController.DEFAULT_STREAM;

    /** Stream to control when the user is not touching the screen. */
    private static final int STREAM_DEFAULT = AudioManager.STREAM_RING;

    /** Tag used for identification of the wake lock held by this class */
    private static final String WL_TAG = ProcessorVolumeStream.class.getSimpleName();

    /** The service context */
    private final Context mContext;

    /** The audio manager, used to adjust speech volume. */
    private final AudioManager mAudioManager;

    /** WakeLock used to keep the screen active during key events */
    private final WakeLock mWakeLock;

    /**
     * The cursor controller, used for determining the focused node and
     * navigating.
     */
    private final CursorController mCursorController;

    /** Handler used for processing long-press key events */
    private final LongPressHandler mLongPressHandler;

    /** Used for tracking which keys were long pressed and are still down */
    private final SparseBooleanArray mKeysLongPressedAndDown = new SparseBooleanArray();

    /** Whether the user is touching the screen. */
    private boolean mTouchingScreen = false;

    @SuppressWarnings("deprecation")
    public ProcessorVolumeStream(TalkBackService service) {
        mContext = service;
        mAudioManager = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
        mCursorController = service.getCursorController();
        mLongPressHandler = new LongPressHandler(this);

        final PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, WL_TAG);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
                mTouchingScreen = true;
                break;
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
                mTouchingScreen = false;
                break;
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        boolean handled = false;
        switch (event.getAction()) {
            case KeyEvent.ACTION_DOWN:
                handled = handleKeyDown(event.getKeyCode());
                break;
            case KeyEvent.ACTION_UP:
                handled = handleKeyUp(event.getKeyCode());
                break;
        }

        if (handled) {
            // Quickly acquire and release the wake lock so that
            // PowerManager.ON_AFTER_RELEASE takes effect.
            mWakeLock.acquire();
            mWakeLock.release();
        }

        return handled;
    }

    private boolean handleKeyDown(int keyCode) {
        if (isFromVolumeKey(keyCode)) {
            handleVolumeKeyDown(keyCode);
            return true;
        }

        return false;
    }

    private boolean handleKeyUp(int keyCode) {
        if (isFromVolumeKey(keyCode)) {
            handleVolumeKeyUp(keyCode);
            return true;
        }

        return false;
    }

    private void handleVolumeKeyDown(int keyCode) {
        // Don't perform actions on key down, just track long-presses
        mLongPressHandler.sendMessageDelayed(
                Message.obtain(mLongPressHandler, LongPressHandler.MSG_LONG_PRESSED, keyCode),
                ViewConfiguration.getLongPressTimeout());
    }

    private void handleVolumeKeyUp(int keyCode) {
        if (mKeysLongPressedAndDown.get(keyCode, false)) {
            // In the event of a volume key being long pressed, we assume the
            // user may have performed some other action, like taking a
            // screenshot. In this case, we won't take any action when we
            // receive an ACTION_UP from that key.
            mKeysLongPressedAndDown.delete(keyCode);
            return;
        }

        // Cancel any long-press messages for this key
        mLongPressHandler.removeMessages(LongPressHandler.MSG_LONG_PRESSED, keyCode);

        if (attemptEditTextNavigation(keyCode)) {
            return;
        }

        adjustVolumeFromKeyEvent(keyCode);
    }

    private void handleVolumeKeyLongPressed(int keyCode) {
        mKeysLongPressedAndDown.put(keyCode, true);

        if (mKeysLongPressedAndDown.size() == 2) {
            handleBothVolumeKeysLongPressed();
        }
    }

    private void handleBothVolumeKeysLongPressed() {
        // TODO(caseyburkhardt): This works. We should do something awesome with it.
    }

    private boolean attemptEditTextNavigation(int keyCode) {
        AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
        try {
            if ((currentNode == null) || !AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                    mContext, currentNode, android.widget.EditText.class)) {
                return false;
            }

            final CursorGranularity currentGranularity =
                    mCursorController.getGranularityAt(currentNode);
            if (currentGranularity == CursorGranularity.DEFAULT) {
                mCursorController.setGranularity(CursorGranularity.CHARACTER, false /* fromUser */);
            }

            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return mCursorController.next(false /* shouldWrap */, false /* shouldScroll */);
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return mCursorController.previous(false /* shouldWarp */, false /* shouldScroll */);
            }

            return false;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(currentNode);
        }
    }

    private void adjustVolumeFromKeyEvent(int keyCode) {
        final int direction = ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) ? AudioManager.ADJUST_RAISE
                : AudioManager.ADJUST_LOWER);

        if (mTouchingScreen) {
            mAudioManager.adjustStreamVolume(STREAM_TOUCHING_SCREEN, direction, DEFAULT_FLAGS);
        } else {
            // Attempt to adjust the suggested stream, but let the system
            // override in special situations like during voice calls, when an
            // application has locked the volume control stream, or when music
            // is playing.
            mAudioManager.adjustSuggestedStreamVolume(direction, STREAM_DEFAULT, DEFAULT_FLAGS);
        }
    }

    private boolean isFromVolumeKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                return true;
        }

        return false;
    }

    private static class LongPressHandler extends WeakReferenceHandler<ProcessorVolumeStream> {
        public static final int MSG_LONG_PRESSED = 1;

        public LongPressHandler(ProcessorVolumeStream parent) {
            super(parent);
        }

        @Override
        protected void handleMessage(Message msg, ProcessorVolumeStream parent) {
            if (MSG_LONG_PRESSED == msg.what) {
                parent.handleVolumeKeyLongPressed((Integer) msg.obj);
            }
        }
    }
}
