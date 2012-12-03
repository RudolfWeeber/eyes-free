/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.marvin.talkback.tutorial;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ViewAnimator;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.tutorial.TutorialSpeechController.SpeechListener;

/**
 * This class provides a short tutorial that introduces the user to the features
 * available in Touch Exploration.
 */
@TargetApi(16)
public class AccessibilityTutorialActivity extends Activity {
    /** This processor requires JellyBean (API 16). */
    public static final int MIN_API_LEVEL = 16;

    private static final int DIALOG_EXPLORE_BY_TOUCH = 1;

    /** Instance state saving constant for the active module. */
    private static final String KEY_ACTIVE_MODULE = "active_module";

    /** The index of the module to show when first opening the tutorial. */
    private static final int DEFAULT_MODULE = 0;

    /** Whether or not the tutorial is active. */
    private static boolean sTutorialIsActive = false;

    /** View animator for switching between modules. */
    private ViewAnimator mViewAnimator;

    private AccessibilityManager mAccessibilityManager;
    private TutorialSpeechController mSpeechController;
    private SoundPool mSoundPool;

    private int mSoundReady;

    private final AnimationListener mInAnimationListener = new AnimationListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
            final int index = mViewAnimator.getDisplayedChild();
            final TutorialModule module = (TutorialModule) mViewAnimator.getChildAt(index);

            activateModule(module);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
            // Do nothing.
        }

        @Override
        public void onAnimationStart(Animation animation) {
            // Do nothing.
        }
    };

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Animation inAnimation = AnimationUtils.loadAnimation(this,
                android.R.anim.slide_in_left);
        inAnimation.setAnimationListener(mInAnimationListener);

        final Animation outAnimation = AnimationUtils.loadAnimation(this,
                android.R.anim.slide_in_left);

        mSpeechController = new TutorialSpeechController(this);
        mSpeechController.addListener(mSpeechListener);

        mSoundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
        mSoundReady = mSoundPool.load(this, R.raw.ready, 1);

        mViewAnimator = new ViewAnimator(this);
        mViewAnimator.setInAnimation(inAnimation);
        mViewAnimator.setOutAnimation(outAnimation);
        mViewAnimator.addView(new TouchTutorialModule1(this));
        mViewAnimator.addView(new TouchTutorialModule2(this));
        mViewAnimator.addView(new TouchTutorialModule3(this));

        // Ensure the screen stays on and doesn't change orientation.
        final Window window = getWindow();
        final WindowManager.LayoutParams params = window.getAttributes();
        params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        window.setAttributes(params);

        setContentView(mViewAnimator);

        mAccessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);

        if (!mAccessibilityManager.isTouchExplorationEnabled()) {
            showDialog(DIALOG_EXPLORE_BY_TOUCH);
            return;
        }

        if (savedInstanceState != null) {
            show(savedInstanceState.getInt(KEY_ACTIVE_MODULE, DEFAULT_MODULE));
        } else {
            show(DEFAULT_MODULE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sTutorialIsActive = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        sTutorialIsActive = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mSpeechController.shutdown();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(KEY_ACTIVE_MODULE, mViewAnimator.getDisplayedChild());
    }

    @SuppressWarnings("deprecation")
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_EXPLORE_BY_TOUCH: {
                final DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
                        @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                };

                final DialogInterface.OnCancelListener onCancelListener = new DialogInterface.OnCancelListener() {
                        @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                };

                return new AlertDialog.Builder(this).setTitle(R.string.attention)
                        .setMessage(R.string.requires_talkback).setCancelable(true)
                        .setPositiveButton(android.R.string.ok, onClickListener)
                        .setOnCancelListener(onCancelListener).create();
            }
        }

        return super.onCreateDialog(id);
    }

    public static boolean isTutorialActive() {
        return sTutorialIsActive;
    }

    private void activateModule(TutorialModule module) {
        module.activate();
    }

    private void deactivateModule(TutorialModule module) {
        mAccessibilityManager.interrupt();
        mSpeechController.interrupt();
        mViewAnimator.setOnKeyListener(null);
        module.deactivate();
    }

    void next() {
        show(mViewAnimator.getDisplayedChild() + 1);
    }

    void previous() {
        show(mViewAnimator.getDisplayedChild() - 1);
    }

    private void show(int which) {
        if ((which < 0) || (which >= mViewAnimator.getChildCount())) {
            return;
        }

        mAccessibilityManager.interrupt();
        mSpeechController.interrupt();

        final int displayedIndex = mViewAnimator.getDisplayedChild();
        final TutorialModule displayedView = (TutorialModule) mViewAnimator.getChildAt(
                displayedIndex);
        deactivateModule(displayedView);

        mViewAnimator.setDisplayedChild(which);
    }

    public TutorialSpeechController getSpeechController() {
        return mSpeechController;
    }

    private final SpeechListener mSpeechListener = new SpeechListener() {
        @Override
        public void onStartSpeaking() {
            final View touchGuard = mViewAnimator.getCurrentView().findViewById(R.id.touch_guard);

            if (touchGuard != null) {
                touchGuard.setVisibility(View.VISIBLE);
            }

            final long downTime = SystemClock.uptimeMillis();
            final MotionEvent cancelEvent = MotionEvent.obtain(
                    downTime, downTime, MotionEvent.ACTION_CANCEL, 0, 0, 0);

            dispatchTouchEvent(cancelEvent);
        }

        @Override
        public void onDoneSpeaking() {
            final View touchGuard = mViewAnimator.getCurrentView().findViewById(R.id.touch_guard);

            if (touchGuard != null) {
                touchGuard.setVisibility(View.GONE);
                mSoundPool.play(mSoundReady, 1.0f, 1.0f, 1, 0, 1.0f);
            }
        }

        @Override
        public void onDone(int id) {
            // Do nothing.
        }
    };
}
