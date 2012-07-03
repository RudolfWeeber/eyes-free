/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.android.marvin.screenmagnifier;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;

import com.googlecode.eyesfree.utils.ColorUtils;

/**
 * An image view that supports zoom, brightness, and color enhancements.
 */
public class FilteredImageView extends ImageView {
    private static final long ANIM_DURATION = 100;
    private static final float MIN_SCALE_FACTOR = 1.0f;
    private static final float MAX_SCALE_FACTOR = 4.0f;

    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleGestureDetector;
    private final Interpolator mInterpolator;
    private final Paint mPaint;

    private MagnifierListener mListener;
    private float mPointerX;
    private float mPointerY;
    private boolean mZoomedIn;

    private boolean mInvertBrightness;
    private boolean mEnhanceContrast;

    private boolean mAnimateZoom = false;

    private float mZoomFactor = 2.0f;

    public FilteredImageView(Context context, AttributeSet attr) {
        super(context, attr);

        mGestureDetector = new GestureDetector(context, mOnGestureListener);
        mScaleGestureDetector = new ScaleGestureDetector(context, mOnScaleGestureListener);
        mInterpolator = new AccelerateDecelerateInterpolator();

        mPaint = new Paint();
        mPaint.setDither(true);
        mPaint.setFilterBitmap(true);

        setScaleType(ScaleType.MATRIX);
    }

    /**
     * Sets whether contrast enhancement is enabled. This increases saturation
     * and multiplies value without affecting hue.
     *
     * @param enabled Whether to enable contrast enhancement.
     */
    public void setEnhanceContrast(boolean enabled) {
        if (mEnhanceContrast == enabled) {
            return;
        }

        mEnhanceContrast = enabled;
        updateColorFilter();
    }

    /**
     * Sets whether brightness inversion is enabled. This inverts value (e.g.
     * white becomes black) without affecting hue or saturation.
     *
     * @param enabled Whether to enable brightness inversion.
     */
    public void setInvertBrightness(boolean enabled) {
        if (mInvertBrightness == enabled) {
            return;
        }

        mInvertBrightness = enabled;
        updateColorFilter();
    }

    /**
     * Sets the {@link MagnifierListener} which responds to various zoom-related
     * events.
     *
     * @param listener The listener to set.
     */
    public void setListener(MagnifierListener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            mScaleGestureDetector.onTouchEvent(event);
            if (!mScaleGestureDetector.isInProgress()) {
                mGestureDetector.onTouchEvent(event);
            }
        } catch (NullPointerException e) {
            // Do nothing.
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                zoomOut();
                break;
        }

        return true;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mListener != null) {
            mListener.onInvalidated(this);
        }
    }

    /**
     * Updates the color filter to apply enhancements.
     */
    private void updateColorFilter() {
        if (!mEnhanceContrast && !mInvertBrightness) {
            clearColorFilter();
            return;
        }

        final ColorMatrix colorMatrix = new ColorMatrix();

        if (mInvertBrightness) {
            colorMatrix.postConcat(new ColorMatrix(ColorUtils.MATRIX_INVERT));
            colorMatrix.postConcat(new ColorMatrix(ColorUtils.MATRIX_INVERT_COLOR));
        }

        if (mEnhanceContrast) {
            colorMatrix.postConcat(new ColorMatrix(ColorUtils.MATRIX_HIGH_CONTRAST));
        }

        setColorFilter(new ColorMatrixColorFilter(colorMatrix));
    }

    /**
     * Animates zoom-in around the specified coordinates.
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     */
    private void zoomIn(float x, float y) {
        if (mZoomedIn) {
            return;
        }

        mZoomedIn = true;
        mPointerX = x;
        mPointerY = y;

        if (!mAnimateZoom) {
            setPivotX(x);
            setPivotY(y);

            setScaleX(mZoomFactor);
            setScaleY(mZoomFactor);

            if (mListener != null) {
                mListener.onMagnificationStarted(this);
            }
            return;
        }

        final ScaleAnimation scaleAnim = new ScaleAnimation(
                1.0f, mZoomFactor, 1.0f, mZoomFactor, mPointerX, mPointerY);
        scaleAnim.setDuration(ANIM_DURATION);
        scaleAnim.setFillBefore(true);
        scaleAnim.setFillAfter(true);
        scaleAnim.setInterpolator(mInterpolator);
        scaleAnim.setAnimationListener(mAnimationListener);

        startAnimation(scaleAnim);

        if (mListener != null) {
            mListener.onMagnificationStarted(this);
        }
    }

    /**
     * Shifts the zoom focus to the specified coordinates.
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     */
    private void zoomTo(float x, float y) {
        if (!mZoomedIn) {
            zoomIn(x, y);
            return;
        }

        mPointerX = x;
        mPointerY = y;

        if (!mAnimateZoom) {
            setPivotX(x);
            setPivotY(y);
            return;
        }

        if (getAnimation() != null && !getAnimation().hasEnded()) {
            // Don't interrupt animations!
            return;
        }

        final ScaleAnimation scaleAnim = new ScaleAnimation(
                mZoomFactor, mZoomFactor, mZoomFactor, mZoomFactor, mPointerX, mPointerY);
        scaleAnim.setFillBefore(true);
        scaleAnim.setFillAfter(true);

        startAnimation(scaleAnim);
    }

    /**
     * Animates zoom to the specified magnification level.
     */
    private void setScaleFactor(float x, float y, float scaleFactor) {
        mZoomFactor = Math.min(MAX_SCALE_FACTOR, Math.max(MIN_SCALE_FACTOR, mZoomFactor * scaleFactor));

        mPointerX = x;
        mPointerY = y;

        if (!mAnimateZoom) {
            setPivotX(x);
            setPivotY(y);
            setScaleX(mZoomFactor);
            setScaleY(mZoomFactor);
            return;
        }

        if (getAnimation() != null && !getAnimation().hasEnded()) {
            // Don't interrupt animations!
            return;
        }

        final ScaleAnimation scaleAnim = new ScaleAnimation(
                mZoomFactor, mZoomFactor, mZoomFactor, mZoomFactor, mPointerX, mPointerY);
        scaleAnim.setFillBefore(true);
        scaleAnim.setFillAfter(true);

        startAnimation(scaleAnim);
    }

    /**
     * Animates zoom to the default (1:1) magnification level.
     */
    private void zoomOut() {
        mZoomedIn = false;

        if (!mAnimateZoom) {
            setScaleX(1.0f);
            setScaleY(1.0f);

            if (mListener != null) {
                mListener.onMagnificationFinished(FilteredImageView.this);
            }
            return;
        }

        final ScaleAnimation scaleAnim = new ScaleAnimation(
                mZoomFactor, 1.0f, mZoomFactor, 1.0f, mPointerX, mPointerY);
        scaleAnim.setDuration(ANIM_DURATION);
        scaleAnim.setFillBefore(true);
        scaleAnim.setFillAfter(true);
        scaleAnim.setInterpolator(mInterpolator);
        scaleAnim.setAnimationListener(mAnimationListener);

        startAnimation(scaleAnim);
    }

    private final Animation.AnimationListener mAnimationListener =
            new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    // Do nothing.

                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                    // Do nothing.
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    // This MUST be posted on the UI thread to avoid prematurely
                    // removing the window and messing up the GL surface.
                    post(mFinishedRunnable);
                }
            };

    private final Runnable mFinishedRunnable = new Runnable() {
        @Override
        public void run() {
            if (mListener != null) {
                mListener.onMagnificationFinished(FilteredImageView.this);
            }
        }
    };

    private final ScaleGestureDetector.OnScaleGestureListener mOnScaleGestureListener =
            new ScaleGestureDetector.OnScaleGestureListener() {
                private float mPreviousScaleFactor = 0;

                @Override
                public boolean onScaleBegin(ScaleGestureDetector detector) {
                    // Always zoom when scaling.
                    return true;
                }

                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    final float x = detector.getFocusX() - detector.getCurrentSpanX() / 2.0f;
                    final float y = detector.getFocusY() - detector.getCurrentSpanY() / 2.0f;
                    final float scaleFactor = detector.getScaleFactor();
                    final float smoothed = (scaleFactor * 0.9f) + (mPreviousScaleFactor * 0.1f);

                    mPreviousScaleFactor = scaleFactor;

                    setScaleFactor(mPointerX, mPointerY, scaleFactor);

                    return true;
                }

                @Override
                public void onScaleEnd(ScaleGestureDetector detector) {
                    // Do nothing.
                }
    };

    private final GestureDetector.SimpleOnGestureListener mOnGestureListener =
            new GestureDetector.SimpleOnGestureListener() {
                private MotionEvent mCachedDownEvent;

                @Override
                public boolean onDown(MotionEvent e) {
                    mCachedDownEvent = MotionEvent.obtain(e);

                    return false;
                }

                @Override
                public void onShowPress(MotionEvent e) {
                    zoomIn(e.getX(0), e.getY(0));
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float velX, float velY) {
                    zoomTo(e2.getX(0), e2.getY(0));

                    return true;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    if (mListener != null && mCachedDownEvent != null) {
                        mListener.onSingleTap(FilteredImageView.this, mCachedDownEvent, e);
                        mCachedDownEvent.recycle();
                        mCachedDownEvent = null;
                    }

                    return true;
                }
            };

    public interface MagnifierListener {
        public void onMagnificationStarted(FilteredImageView view);

        public void onMagnificationFinished(FilteredImageView view);

        public void onSingleTap(FilteredImageView view, MotionEvent downEvent, MotionEvent upEvent);

        public void onInvalidated(FilteredImageView view);
    }
}
