// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.marvin.shell;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Button;

import com.googlecode.eyesfree.utils.compat.MotionEventCompatUtils;

/**
 * Standard button but with an OnHoverListener for compatibility with pre-ICS
 * devices.
 *
 * @author clchen@google.com (Charles Chen)
 */
public class RunButton extends Button {
    public interface OnHoverListener {
        public void onHoverEnter();

        public void onHoverExit();
    }

    private OnHoverListener cb;

    /**
     * @param context
     * @param attrs
     */
    public RunButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnHoverListener(OnHoverListener callback) {
        cb = callback;
    }

    public boolean onHoverEvent(MotionEvent event) {
        if (event.getAction() == MotionEventCompatUtils.ACTION_HOVER_ENTER) {
            if (cb != null) {
                cb.onHoverEnter();
            }
        }
        if (event.getAction() == MotionEventCompatUtils.ACTION_HOVER_EXIT) {
            if (cb != null) {
                cb.onHoverExit();
            }
        }
        return true;
    }

}
