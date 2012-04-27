// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.marvin.talkingdialer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.googlecode.eyesfree.compat.view.MotionEventCompatUtils;

/**
 * Scrolling gesture detector for scrolling through the list of apps. Works with
 * touch exploration.
 * 
 * @author clchen@google.com (Charles Chen)
 */
public class ScrollSidebarView extends View {

    interface OnScrollDetectedListener {
        public void onScrollDetected(int direction);
    }

    private OnScrollDetectedListener cb;

    private float p2DownY;

    public ScrollSidebarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnScrollDetectedListener(OnScrollDetectedListener listener) {
        cb = listener;
    }

    public boolean onHoverEvent(MotionEvent event) {
        return onTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        event.getX();
        final float y = event.getY();

        switch (action) {
            case MotionEventCompatUtils.ACTION_HOVER_EXIT:
            case MotionEvent.ACTION_UP:
                final float p2DeltaY = y - p2DownY;
                if (p2DeltaY < -100) {
                    cb.onScrollDetected(-1);
                } else if (p2DeltaY > 100) {
                    cb.onScrollDetected(1);
                }
                break;

            case MotionEventCompatUtils.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_DOWN:
                p2DownY = y;
        }

        return true;
    }

}
