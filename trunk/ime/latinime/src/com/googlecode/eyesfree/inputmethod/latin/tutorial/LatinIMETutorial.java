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

package com.googlecode.eyesfree.inputmethod.latin.tutorial;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ViewAnimator;

import com.googlecode.eyesfree.inputmethod.latin.LatinIME;
import com.googlecode.eyesfree.inputmethod.latin.R;

/**
 * This class provides a short tutorial that introduces the user to the features
 * available in the accessible Latin IME.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class LatinIMETutorial extends Activity
        implements TutorialController, View.OnClickListener, TutorialReader.ReaderListener {
    /** Intent action for launching this activity. */
    public static final String ACTION =
            "com.googlecode.eyesfree.inputmethod.latin.tutorial.LAUNCH_TUTORIAL";

    /** Instance state saving constant for the active module. */
    private static final String KEY_ACTIVE_MODULE = "active_module";

    /** The index of the module to show when first opening the tutorial. */
    private static final int DEFAULT_MODULE = 0;

    /** Broadcast receiver for high-level keyboard events. */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int mode = intent.getIntExtra(LatinIME.EXTRA_MODE, -1);

            LatinTutorialLogger.log(Log.INFO, "Received broadcast with action %s", action);

            if (LatinIME.BROADCAST_KEYBOARD_MODE_CHANGE.equals(action)) {
                onKeyboardModeChanged(mode);
            } else if (LatinIME.BROADCAST_KEYBOARD_MODE_UPDATE.equals(action)) {
                onKeyboardModeUpdated(mode);
            } else {
                onKeyboardBroadcast(action, intent.getExtras());
            }
        }
    };

    /** View animator for switching between modules. */
    private ViewAnimator mViewAnimator;

    /** The currently focused tutorial module. */
    private TutorialModule mActiveModule;

    /** The TTS queue for this tutorial. */
    private TutorialReader mReader;

    /** The current keyboard mode, or -1 if no keyboard active. */
    private int mKeyboardMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle("");
        setContentView(R.layout.tutorial_0_switcher);

        mReader = new TutorialReader(this);
        mReader.setListener(this);

        mViewAnimator = (ViewAnimator) findViewById(R.id.tutorial_switcher);
        mViewAnimator.addView(new LatinTutorialModule1(this, this));
        mViewAnimator.addView(new LatinTutorialModule2(this, this));
        mViewAnimator.addView(new LatinTutorialModule3(this, this));
        mViewAnimator.addView(new LatinTutorialModule4(this, this));

        mKeyboardMode = -1;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LatinIME.BROADCAST_KEYBOARD_MODE_CHANGE);
        intentFilter.addAction(LatinIME.BROADCAST_KEYBOARD_MODE_UPDATE);
        intentFilter.addAction(LatinIME.BROADCAST_LEFT_KEYBOARD_AREA);
        intentFilter.addAction(LatinIME.BROADCAST_ENTERED_KEYBOARD_AREA);
        intentFilter.addAction(LatinIME.BROADCAST_UP_OUTSIDE_KEYBOARD);
        intentFilter.addAction(LatinIME.BROADCAST_GRANULARITY_CHANGE);

        registerReceiver(mReceiver, intentFilter);

        requestKeyboardModeUpdate();

        if (savedInstanceState != null) {
            show(savedInstanceState.getInt(KEY_ACTIVE_MODULE, DEFAULT_MODULE));
        } else {
            show(DEFAULT_MODULE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(KEY_ACTIVE_MODULE, mViewAnimator.getDisplayedChild());
    }

    @Override
    protected void onPause() {
        super.onPause();

        mReader.clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mReceiver);

        mReader.release();
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        mReader.clear();

        super.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (mActiveModule != null && mActiveModule instanceof ActivityResultListener) {
            ((ActivityResultListener) mActiveModule).onActivityResult(
                    requestCode, resultCode, data);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean handled = false;

        if (mActiveModule != null && mActiveModule instanceof View.OnKeyListener) {
            handled = ((View.OnKeyListener) mActiveModule).onKey(null, event.getKeyCode(), event);
        }

        if (!handled) {
            handled = super.dispatchKeyEvent(event);
        }

        return handled;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tutorial_instructions) {
            mReader.next();
            return;
        }

        if (mActiveModule != null && mActiveModule instanceof View.OnClickListener) {
            ((View.OnClickListener) mActiveModule).onClick(v);
        }
    }

    @Override
    public void onUtteranceCompleted(int utteranceId) {
        if (mActiveModule != null) {
            mActiveModule.onUtteranceCompleted(utteranceId);
        }
    }

    /**
     * Updates the internal representation of the keyboard mode and notifies
     * children.
     *
     * @param mode The current keyboard mode.
     * @see
     *      com.googlecode.eyesfree.inputmethod.latin.KeyboardSwitcher#getKeyboardMode()
     */
    protected void onKeyboardModeChanged(int mode) {
        LatinTutorialLogger.log(Log.INFO, "Keyboard mode changed to %d", mode);

        mKeyboardMode = mode;

        if (mActiveModule != null && mActiveModule instanceof KeyboardModeListener) {
            ((KeyboardModeListener) mActiveModule).onKeyboardModeChanged(mode);
        }
    }

    /**
     * Forwards keyboard event broadcasts to the active module, if the module
     * implements {@link KeyboardBroadcastListener}.
     *
     * @param action The broadcast's action.
     * @param extras The broadcast's extras bundle.
     */
    protected void onKeyboardBroadcast(String action, Bundle extras) {
        if (mActiveModule != null && mActiveModule instanceof KeyboardBroadcastListener) {
            ((KeyboardBroadcastListener) mActiveModule).onKeyboardBroadcast(action, extras);
        }
    }

    /**
     * Updates the internal representation of the keyboard mode, but does not
     * notify children because the mode has not changed.
     *
     * @param mode The current keyboard mode.
     * @see
     *      com.googlecode.eyesfree.inputmethod.latin.KeyboardSwitcher#getKeyboardMode()
     */
    protected void onKeyboardModeUpdated(int mode) {
        mKeyboardMode = mode;

        if (mActiveModule != null && mActiveModule instanceof KeyboardModeListener) {
            ((KeyboardModeListener) mActiveModule).onKeyboardModeUpdated(mode);
        }
    }

    @Override
    public int getKeyboardMode() {
        return mKeyboardMode;
    }

    @Override
    public void requestKeyboardModeUpdate() {
        sendBroadcast(new Intent(LatinIME.REQUEST_KEYBOARD_MODE_UPDATE));
    }

    @Override
    public void setKeyboardMode(int mode) {
        Intent modeRequest = new Intent(LatinIME.REQUEST_KEYBOARD_MODE_CHANGE);
        modeRequest.putExtra(LatinIME.EXTRA_MODE, mode);
        sendBroadcast(modeRequest);
    }

    @Override
    public void speak(String text, int utteranceId) {
        mReader.queue(text, utteranceId);
    }

    @Override
    public void show(int which) {
        if (which < 0 || which >= mViewAnimator.getChildCount()) {
            LatinTutorialLogger.log(Log.ERROR, "Attempted to show invalid child index %d", which);
            return;
        }

        mReader.clear();

        int displayedIndex = mViewAnimator.getDisplayedChild();
        View displayedView = mViewAnimator.getChildAt(displayedIndex);

        if (displayedView instanceof TutorialModule) {
            deactivateModule((TutorialModule) displayedView);
        }

        mViewAnimator.setDisplayedChild(which);
        View viewToDisplay = mViewAnimator.getChildAt(which);

        if (viewToDisplay instanceof TutorialModule) {
            activateModule((TutorialModule) viewToDisplay);
        }
    }

    private void deactivateModule(TutorialModule module) {
        mReader.clear();
        mActiveModule = null;
        mViewAnimator.setOnKeyListener(null);
        module.onHidden();
    }

    private void activateModule(TutorialModule module) {
        mActiveModule = module;
        module.onShown();
    }

    @Override
    public void next() {
        show(mViewAnimator.getDisplayedChild() + 1);
    }

    @Override
    public void skip(int index) {
        show(mViewAnimator.getDisplayedChild() + 1 + index);
    }

    @Override
    public void previous() {
        show(mViewAnimator.getDisplayedChild() - 1);
    }

    static interface ActivityResultListener {
        public void onActivityResult(int requestCode, int resultCode, Intent data);
    }

    static interface KeyboardModeListener {
        public void onKeyboardModeChanged(int mode);

        public void onKeyboardModeUpdated(int mode);
    }

    static interface KeyboardBroadcastListener {
        public void onKeyboardBroadcast(String action, Bundle extras);
    }
}
