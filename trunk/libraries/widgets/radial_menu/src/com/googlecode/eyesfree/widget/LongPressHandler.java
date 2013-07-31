// Copyright 2011 Google Inc. All Rights Reserved.

package com.googlecode.eyesfree.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Detects long presses.
 *
 * @author alanv@google.com (Alan Viverette)
 */
class LongPressHandler extends Handler implements View.OnTouchListener {
    private static final long LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int MSG_LONG_PRESS = 1;

    private final int TOUCH_SLOP_SQUARED;

    private LongPressListener mListener;
    private MotionEvent mPreviousEvent;

    private float mMoved;

    /**
     * Creates a new long press handler.
     *
     * @param context The parent context.
     */
    public LongPressHandler(Context context) {
        super(context.getMainLooper());

        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        TOUCH_SLOP_SQUARED = touchSlop * touchSlop;
    }

    /**
     * Sets the listener that receives long press callbacks.
     *
     * @param listener The listener to set.
     */
    public void setListener(LongPressListener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_HOVER_ENTER:
                mMoved = 0;
                //$FALL-THROUGH$
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                if (mPreviousEvent != null) {
                    final float dX = event.getX() - mPreviousEvent.getX();
                    final float dY = event.getY() - mPreviousEvent.getY();
                    final float moved = (dX * dX) + (dY * dY);

                    mMoved += moved;
                }

                if (mMoved > TOUCH_SLOP_SQUARED) {
                    mMoved = 0;

                    removeMessages(MSG_LONG_PRESS);

                    final Message msg = obtainMessage(MSG_LONG_PRESS, mPreviousEvent);
                    sendMessageDelayed(msg, LONG_PRESS_TIMEOUT);
                }

                mPreviousEvent = MotionEvent.obtain(event);

                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_HOVER_EXIT:
                removeMessages(MSG_LONG_PRESS);

                if (mPreviousEvent != null) {
                    mPreviousEvent.recycle();
                    mPreviousEvent = null;
                }

                break;
        }

        return false;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_LONG_PRESS:
                if (mListener != null) {
                    mListener.onLongPress((MotionEvent) msg.obj);
                }
                break;
        }
    }

    /**
     * Handles long press callbacks.
     *
     * @author alanv@google.com (Alan Viverette)
     *
     */
    public interface LongPressListener {
        /**
         * Called when a long press is detected.
         *
         * @param downEvent The down event that started the long press.
         */
        public void onLongPress(MotionEvent downEvent);
    }
}
