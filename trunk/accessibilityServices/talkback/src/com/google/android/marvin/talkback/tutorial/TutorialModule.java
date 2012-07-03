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

import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.marvin.talkback.R;

/**
 * Abstract class that represents a single module within a tutorial.
 */
abstract class TutorialModule extends FrameLayout implements OnClickListener {
    private static final int TRIGGER_DELAY = 1500;

    private final AccessibilityTutorialActivity mParentTutorial;
    private final TutorialSpeechController mSpeechController;
    private final TextView mInstructions;
    private final Button mSkip;
    private final Button mBack;
    private final Button mNext;
    private final Button mFinish;
    private final int mTitleResId;

    /** Whether this module is currently focused. */
    private boolean mIsVisible;

    public TutorialModule(Context context) {
        super(context);

        // This constructor should never be called.
        throw new UnsupportedOperationException();
    }

    /**
     * Constructs a new tutorial module for the given context and controller
     * with the specified layout.
     *
     * @param layoutResId The layout to use for this module.
     * @param parentTutorial TODO(alanv):
     */
    public TutorialModule(
            AccessibilityTutorialActivity parentTutorial, int layoutResId, int titleResId) {
        super(parentTutorial);

        mParentTutorial = parentTutorial;
        mSpeechController = parentTutorial.getSpeechController();
        mTitleResId = titleResId;

        final LayoutInflater inflater = mParentTutorial.getLayoutInflater();
        final View container = inflater.inflate(
                R.layout.accessibility_tutorial_container, this, true);

        mInstructions = (TextView) container.findViewById(R.id.instructions);
        mSkip = (Button) container.findViewById(R.id.skip_button);
        mSkip.setOnClickListener(this);
        mBack = (Button) container.findViewById(R.id.back_button);
        mBack.setOnClickListener(this);
        mNext = (Button) container.findViewById(R.id.next_button);
        mNext.setOnClickListener(this);
        mFinish = (Button) container.findViewById(R.id.finish_button);
        mFinish.setOnClickListener(this);

        final TextView title = (TextView) container.findViewById(R.id.title);

        if (title != null) {
            title.setText(titleResId);
        }

        final ViewGroup contentHolder = (ViewGroup) container.findViewById(R.id.content);
        inflater.inflate(layoutResId, contentHolder, true);
    }

    /**
     * Called when this tutorial gains focus.
     */
    public final void activate() {
        mIsVisible = true;

        mInstructions.setVisibility(View.GONE);
        mParentTutorial.setTitle(mTitleResId);

        onShown();
    }

    /**
     * Formats an instruction string and adds it to the speaking queue.
     *
     * @param resId The resource id of the instruction string.
     * @param repeat Whether this instruction should repeat if nothing happens.
     * @param formatArgs Optional formatting arguments.
     * @see String#format(String, Object...)
     */
    protected void addInstruction(final int resId, boolean repeat, Object... formatArgs) {
        if (!mIsVisible) {
            return;
        }

        final String text = mParentTutorial.getString(resId, formatArgs);

        mInstructions.setVisibility(View.VISIBLE);
        mInstructions.setText(text);

        // Always request that the instruction repeat if nothing happens.
        mSpeechController.speak(text, resId, repeat);
    }

    protected void installTriggerDelayed(Runnable trigger) {
        mHandler.postDelayed(trigger, TRIGGER_DELAY);
    }

    /**
     * Called when this tutorial loses focus.
     */
    public void deactivate() {
        mIsVisible = false;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.skip_button) {
            mParentTutorial.finish();
        } else if (v.getId() == R.id.back_button) {
            mParentTutorial.previous();
        } else if (v.getId() == R.id.next_button) {
            mParentTutorial.next();
        } else if (v.getId() == R.id.finish_button) {
            mParentTutorial.finish();
        }
    }

    public abstract void onShown();

    protected void setSkipVisible(boolean visible) {
        mSkip.setVisibility(visible ? VISIBLE : GONE);
    }

    protected void setBackVisible(boolean visible) {
        mBack.setVisibility(visible ? VISIBLE : GONE);
    }

    protected void setNextVisible(boolean visible) {
        mNext.setVisibility(visible ? VISIBLE : GONE);
    }

    protected void setFinishVisible(boolean visible) {
        mFinish.setVisibility(visible ? VISIBLE : GONE);
    }

    private final Handler mHandler = new Handler();
}
