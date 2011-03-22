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

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ViewAnimator;

import com.googlecode.eyesfree.inputmethod.latin.R;

import java.util.TreeSet;

/**
 * Abstract class that represents a single module within a tutorial.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public abstract class TutorialModule extends FrameLayout implements TutorialReader.ReaderListener {
    private final TutorialController mController;
    private final TreeSet<Integer> mFlags;
    private final ViewAnimator mInstructions;

    /** Whether this module is currently focused. */
    private boolean mIsVisible;

    /**
     * Constructs a new tutorial module for the given context and controller
     * with the specified layout.
     *
     * @param context The parent context.
     * @param controller The parent tutorial controller.
     * @param layoutResId The layout to use for this module.
     */
    public TutorialModule(Context context, TutorialController controller, int layoutResId) {
        super(context);

        LayoutInflater.from(context).inflate(layoutResId, this, true);

        mController = controller;
        mFlags = new TreeSet<Integer>();

        mInstructions = (ViewAnimator) findViewById(R.id.tutorial_instructions);
    }

    /**
     * Returns the controller for this tutorial.
     *
     * @return Tthe controller for this tutorial.
     */
    protected TutorialController getController() {
        return mController;
    }

    /**
     * Called when this tutorial gains focus.
     */
    public void onShown() {
        mIsVisible = true;

        // Reset tutorial.
        mFlags.clear();
        mInstructions.removeAllViews();
    }

    /**
     * Called when this tutorial loses focus.
     */
    public void onHidden() {
        mIsVisible = false;
    }

    /**
     * Returns {@code true} if this tutorial is currently focused.
     *
     * @return {@code true} if this tutorial is currently focused.
     */
    public boolean isVisible() {
        return mIsVisible;
    }

    /**
     * Formats an instruction string and adds it to the speaking queue.
     *
     * @param resId The resource id of the instruction string.
     * @param formatArgs Optional formatting arguments.
     * @see String#format(String, Object...)
     */
    protected void addInstruction(final int resId, Object... formatArgs) {
        if (!mIsVisible || mInstructions == null) {
            return;
        }

        // Construct a new TextView with the instruction
        final String text = getContext().getString(resId, formatArgs);
        final int index = mInstructions.getChildCount();
        final TextView view = new TextView(getContext());

        view.setText(text);
        view.setTextAppearance(getContext(), android.R.style.TextAppearance_Small_Inverse);

        mInstructions.addView(view, index);
        mInstructions.setDisplayedChild(index);
        mController.speak(text, resId);
    }

    @Override
    public void onUtteranceCompleted(final int resId) {
        mController.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onInstructionRead(resId);
            }
        });
    }

    /**
     * Optional override. Receives spoken instruction completion events.
     *
     * @param resId The resource if of the spoken instruction.
     * @see #addInstruction(int, Object...)
     */
    public void onInstructionRead(int resId) {
        // Placeholder, do nothing.
    }

    /**
     * Returns {@code true} if the flag with the specified id has been set.
     *
     * @param flagId The id of the flag to check for.
     * @return {@code true} if the flag with the specified id has been set.
     */
    protected boolean hasFlag(int flagId) {
        return mFlags.contains(flagId);
    }

    /**
     * Sets or removes the flag with the specified id.
     *
     * @param flagId The id of the flag to modify.
     * @param value {@code true} to set the flag, {@code false} to remove it.
     */
    protected void setFlag(int flagId, boolean value) {
        if (value) {
            mFlags.add(flagId);
        } else {
            mFlags.remove(flagId);
        }
    }
}
