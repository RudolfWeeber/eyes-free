// Copyright 2011 Google Inc. All Rights Reserved.

package com.googlecode.eyesfree.inputmethod;

import android.view.MotionEvent;
import android.view.View;

/**
 * @author alanv@google.com (Alan Viverette)
 */
public class SegmentDetector {
    private static final int NUM_SEGMENTS = 5;
    private static final float CANCEL_SIZE = 0.25f;

    public static final int SEGMENT_INVALID = -2;
    public static final int SEGMENT_CANCEL = -1;

    private SegmentListener mListener;
    private MotionEvent mLeftEvent;
    private int mSegmentSize;
    private int mCancelLeft;
    private int mCancelRight;
    private int mPreviousSegment;

    public void setListener(SegmentListener listener) {
        mListener = listener;
    }

    public void updateDimensions(View v) {
        int width = v.getWidth();
        int cancelSize = (int) (v.getResources().getDisplayMetrics().xdpi * CANCEL_SIZE);

        mSegmentSize = (int) (0.5 * width / NUM_SEGMENTS);
        mCancelLeft = cancelSize;
        mCancelRight = width - cancelSize;
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (event.getY() >= 0 || event.getPointerCount() > 1) {
            if (mLeftEvent != null) {
                if (mListener != null) {
                    mListener.onEnteredKeyboard();
                }
                mLeftEvent = null;
                mPreviousSegment = SEGMENT_INVALID;
            }
            return false;
        }

        if (mLeftEvent == null) {
            if (mListener != null) {
                mListener.onLeftKeyboard();
            }
            mLeftEvent = MotionEvent.obtainNoHistory(event);
            mPreviousSegment = SEGMENT_INVALID;
        }

        int currentSegment;
        float x = event.getX();

        if (x < mCancelLeft || x >= mCancelRight) {
            currentSegment = SEGMENT_CANCEL;
        } else {
            currentSegment = (int) (Math.abs(x - mLeftEvent.getX()) / mSegmentSize);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if (currentSegment != mPreviousSegment) {
                    if (mListener != null) {
                        mListener.onEnteredSegment(currentSegment);
                    }
                    mPreviousSegment = currentSegment;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mListener != null) {
                    mListener.onSelectedSegment(currentSegment);
                }
                mLeftEvent = null;
                mPreviousSegment = SEGMENT_INVALID;
                break;
        }

        return true;
    }

    public interface SegmentListener {
        public void onLeftKeyboard();
        public void onEnteredKeyboard();
        public void onEnteredSegment(int segmentId);
        public void onSelectedSegment(int segmentId);
    }
}
