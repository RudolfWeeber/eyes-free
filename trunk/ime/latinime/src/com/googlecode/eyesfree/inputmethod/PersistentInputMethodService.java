/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.inputmethod;

import android.content.res.Configuration;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.google.android.marvin.aime.AccessibleInputMethodService;

/**
 * This class extends {@link AccessibleInputMethodService} by overriding methods
 * related to hiding the input window. It allows the input method to be forced
 * open by long-pressing the MENU key.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class PersistentInputMethodService extends LinearNavigationInputMethodService {
    private static final String TAG = "PersistentInputMethodService";
    private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();

    /** Constant used for LongPressHandler. */
    private static final int LONG_PRESS = 1;

    private LongPressHandler mLongPressHandler;

    /**
     * Avoids handling repeated key presses. This is set after the first down
     * event in a series of down events, then cleared on an up event.
     */
    private KeyEvent mDownEvent;

    private boolean mInLongPress;

    private class LongPressHandler extends Handler {
        LongPressHandler() {
            super();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LONG_PRESS:
                    mInLongPress = true;
                    onKeyLongPress(msg.arg1, (KeyEvent) msg.obj);
                    break;
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mLongPressHandler = new LongPressHandler();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (!isAccessibilityEnabled()) {
            return;
        }

        // Ensure that we force the view open when accessibility is enabled, but
        // only if we've already been attached to a window.
        if (getWindow().getWindow().getAttributes().token != null) {
            try {
                showWindow(true);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onAccessibilityChanged(boolean accessibilityEnabled) {
        if (!accessibilityEnabled) {
            // This is the only correct way to hide the window. Don't use
            // hideWindow() or requestHideSelf() because they'll mess up the
            // internal state.
            onCreateInputMethodInterface().hideSoftInput(0, null);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isAccessibilityEnabled()) {
            return super.onKeyDown(keyCode, event);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                return false;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN: {
                event.startTracking();
                if (mDownEvent == null) {
                    mDownEvent = event;
                    mInLongPress = false;
                    mLongPressHandler.removeMessages(LONG_PRESS);
                    mLongPressHandler.sendMessageDelayed(
                            mLongPressHandler.obtainMessage(LONG_PRESS, keyCode, 0, event),
                            TAP_TIMEOUT + LONGPRESS_TIMEOUT);
                }
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!isAccessibilityEnabled()) {
            return super.onKeyUp(keyCode, event);
        }

        final KeyEvent downEvent = mDownEvent;
        mDownEvent = null;

        // Give the parent class a chance to consume the cached down
        // event, then send it through the input connection.
        if (downEvent != null && !mInLongPress) {
            final InputConnection input = getCurrentInputConnection();
            if (!super.onKeyDown(downEvent.getKeyCode(), downEvent) && input != null) {
                input.sendKeyEvent(downEvent);
            }
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                return false;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN: {
                mLongPressHandler.removeMessages(LONG_PRESS);
                if (mInLongPress) {
                    mInLongPress = false;
                    return true;
                } else {
                    return super.onKeyUp(keyCode, event);
                }
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        if (!isAccessibilityEnabled()) {
            return;
        }

        // Restart input view when changing input.
        onStartInputView(attribute, restarting);
    }

    @Override
    public void hideWindow() {
        if (!isAccessibilityEnabled()) {
            super.hideWindow();
        }
    }

    @Override
    public void showWindow(boolean showInput) {
        if (!isAccessibilityEnabled()) {
            super.showWindow(showInput);
        } else {
            try {
                super.showWindow(true);
            } catch (WindowManager.BadTokenException e) {
                Log.e(TAG, "Failed to show window", e);
            }
        }
    }

    @Override
    public boolean onShowInputRequested(int flags, boolean configChange) {
        if (!isAccessibilityEnabled()) {
            return super.onShowInputRequested(flags, configChange);
        } else {
            return true;
        }
    }

    @Override
    public boolean onEvaluateInputViewShown() {
        if (!isAccessibilityEnabled()) {
            return super.onEvaluateInputViewShown();
        } else {
            return true;
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        if (!isAccessibilityEnabled()) {
            return super.onEvaluateFullscreenMode();
        } else {
            // This input method should never show an extract view.
            return false;
        }
    }
}
