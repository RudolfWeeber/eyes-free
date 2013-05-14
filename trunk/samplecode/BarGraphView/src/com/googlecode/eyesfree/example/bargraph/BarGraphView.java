/*
 * Copyright (C) 2013 Google Inc.
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

package com.googlecode.eyesfree.example.bargraph;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import com.googlecode.eyesfree.utils.ExploreByTouchHelper;

import java.util.Arrays;
import java.util.List;

public class BarGraphView extends View {
    private final Rect mTempRect = new Rect();

    /** Scale used to draw bars and map touch coordinates. */
    private final Scale mScaleX = new Scale.Linear();

    /** Scale used to draw bars and map touch coordinates. */
    private final Scale mScaleY = new Scale.Linear();

    /** Paint used to draw bars and text to the canvas. */
    private final Paint mPaint = new Paint();

    /** Explore by touch helper, used to expose contents for accessibility. */
    private final BarGraphAccessHelper mBarGraphAccessHelper;

    /** Color to use for bars. */
    private final int mBarColor = 0xff33b5e5;

    /** Color to use for selected bars. */
    private final int mBarColorSelected = 0xffddff00;

    /** Color to use for Y-axis. */
    private final int mAxisColor = 0xff0099cc;

    /** Color to use for title. */
    private final int mTitleColor = 0xffffffff;

    /** Bar graph title size, in pixels. */
    private float mTitleSize = 16;

    /** Margin between bars, in pixels. */
    private float mBarMargin = 0;

    /** Minimum bar width, in pixels. */
    private float mBarMinWidth = 1;

    /** Minimum bar height, in pixels. */
    private float mBarMinHeight = 16;

    /** Bar graph title, may be {@code null}. */
    private CharSequence mTitle;

    /** Array of data to display. May be larger than actual data set. */
    private int[] mData = new int[10];

    /** Size of displayed data set. */
    private int mSize = 0;

    /** Index of selected bar, or {@code -1} if no bar is selected. */
    private int mHighlightedIndex = -1;

    public BarGraphView(Context context) {
        this(context, null);
    }

    public BarGraphView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BarGraphView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // Load styled attributes.
        final int defStyleRes = (isInEditMode() ? 0 : R.style.BarGraph);
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.BarGraph, defStyle, defStyleRes);

        final int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            final int attr = a.getIndex(i);

            switch (attr) {
                case R.styleable.BarGraph_barMargin:
                    mBarMargin = a.getDimension(attr, mBarMargin);
                    break;
                case R.styleable.BarGraph_barMinWidth:
                    mBarMinHeight = a.getDimension(attr, mBarMinHeight);
                    break;
                case R.styleable.BarGraph_barMinHeight:
                    mBarMinWidth = a.getDimension(attr, mBarMinWidth);
                    break;
                case R.styleable.BarGraph_title:
                    setTitle(a.getText(attr));
                    break;
                case R.styleable.BarGraph_titleSize:
                    mTitleSize = a.getDimension(attr, mTitleSize);
                    break;
                case R.styleable.BarGraph_values:
                    if (!isInEditMode()) {
                        setData(context.getResources().getIntArray(a.getResourceId(attr, 0)));
                    }
                    break;
            }
        }

        a.recycle();

        if (isInEditMode()) {
            // Special considerations for edit mode.
            mBarGraphAccessHelper = null;
            setData(new int[] {1, 1, 2, 3, 5, 8});
            setSelection(3);
        } else {
            // Set up accessibility helper class.
            mBarGraphAccessHelper = new BarGraphAccessHelper(this);
            ViewCompat.setAccessibilityDelegate(this, mBarGraphAccessHelper);
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        // Always attempt to dispatch hover events to accessibility first.
        if ((mBarGraphAccessHelper != null)
                && mBarGraphAccessHelper.dispatchHoverEvent(event)) {
            return true;
        }

        return super.dispatchHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                final int barIndex = getBarIndexAt(event.getX(), event.getY());
                if (barIndex >= 0) {
                    onBarClicked(barIndex);
                }
                return true;
            case MotionEvent.ACTION_UP:
                return true;
        }

        return super.onTouchEvent(event);
    }

    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);

        if (mTitle != null) {
            drawTitle(c);
        }

        for (int i = 0; i < mSize; i++) {
            drawBarAtIndex(c, i);
        }

        drawAxisY(c);
    }

    /**
     * Draws the title of the bar graph.
     *
     * @param c The canvas on which to draw.
     */
    private void drawTitle(Canvas c) {
        mPaint.reset();
        mPaint.setAntiAlias(true);
        mPaint.setColor(mTitleColor);
        mPaint.setTextSize(mTitleSize);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.getTextBounds(mTitle.toString(), 0, mTitle.length(), mTempRect);

        final float x = (getWidth() / 2.0f);
        final float y = mTempRect.height();
        c.drawText(mTitle, 0, mTitle.length(), x, y, mPaint);
    }

    /**
     * Draws the bar at the specified index.
     *
     * @param c The canvas on which to draw.
     * @param index The index of the bar to draw.
     */
    private void drawBarAtIndex(Canvas c, int index) {
        final int color = ((index == mHighlightedIndex) ? mBarColorSelected : mBarColor);

        mPaint.reset();
        mPaint.setColor(color);

        final Rect bounds = getBoundsForIndex(index, mTempRect);
        c.drawRect(bounds, mPaint);
    }

    /**
     * Draws the Y-axis.
     *
     * @param c The canvas on which to draw.
     */
    private void drawAxisY(Canvas c) {
        mPaint.reset();
        mPaint.setColor(mAxisColor);

        final float[] x = mScaleX.transform(0, (mSize + 1));
        final float y = mScaleY.transform(0);

        c.drawLine(x[0], y, x[1], y, mPaint);
    }

    /**
     * Sets the selected bar index.
     *
     * @param index The index of the bar to select.
     */
    public void setSelection(int index) {
        if (mHighlightedIndex == index) {
            // Already selected, nothing to do here.
            return;
        }

        mHighlightedIndex = index;

        // Since mHighlightedIndex affects the properties of the bar at this index,
        // invalidate the virtual view for this index.
        if (mBarGraphAccessHelper != null) {
            mBarGraphAccessHelper.invalidateVirtualViewId(index);
        }

        postInvalidate();
    }


    /**
     * Sets the data to display as bars.
     *
     * @param data An integer array of data to display.
     */
    public void setData(int[] data) {
        if (data.length > mData.length) {
            mData = new int[data.length];
        }

        System.arraycopy(data, 0, mData, 0, data.length);
        mSize = data.length;

        updateDomain();
        updateContentDescription();
        postInvalidate();
    }

    /**
     * Sets the title of the bar graph.
     *
     * @param title The title to display.
     */
    public void setTitle(CharSequence title) {
        mTitle = title;

        updateContentDescription();
        postInvalidate();
    }

    /**
     * @return The number of bars displayed.
     */
    private int getBarCount() {
        return mSize;
    }

    /**
     * @return The value of the bar at the specified <code>index</code>.
     */
    private int getBarValue(int index) {
        return mData[index];
    }

    /**
     * Returns the index of the bar under the specified coordinates, or
     * {@code -1} if there is no bar.
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @return The index of the bar under the specified coordinates, or
     *         {@code -1} if there is no bar.
     */
    private int getBarIndexAt(float x, float y) {
        final int index = (int) mScaleX.invert(x);
        final int value = (int) mScaleY.invert(y);
        if (!isValueInRange(value, 0, getBarValue(index))) {
            return -1;
        }

        return index;
    }

    /**
     * Handles a click action on the bar at the specified index.
     *
     * @param index The index of the clicked bar.
     */
    private void onBarClicked(int index) {
        setSelection(index);

        // Send an accessibility event for this action.
        if (mBarGraphAccessHelper != null) {
            mBarGraphAccessHelper.sendEventForVirtualViewId(
                    index, AccessibilityEvent.TYPE_VIEW_CLICKED);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        int measuredHeight = heightSize;
        int measuredWidth = widthSize;

        final Rect contentBounds = mTempRect;
        getContentBounds(contentBounds);

        if (heightMode != MeasureSpec.EXACTLY) {
            measuredHeight = contentBounds.height();

            if (heightMode == MeasureSpec.AT_MOST) {
                if (measuredHeight > heightSize) {
                    measuredHeight = heightSize;
                }
            }
        }

        if (widthMode == MeasureSpec.UNSPECIFIED) {
            measuredWidth = contentBounds.width();
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        updateRange();
    }

    /**
     * Obtains the recommended content bounds.
     *
     * @param bounds The rect in which to place the recommended bounds.
     */
    private void getContentBounds(Rect bounds) {
        mPaint.reset();
        mPaint.setTextSize(mTitleSize);
        mPaint.getTextBounds(mTitle.toString(), 0, mTitle.length(), bounds);

        final int minBarWidth = (int) ((mBarMargin + mBarMinWidth) * mSize + 0.5f);
        final int minBarHeight = (int) (mBarMinHeight + 0.5f);

        bounds.bottom += minBarHeight;
        bounds.right = Math.max(bounds.right, minBarWidth);
    }

    /**
     * Obtains the bounding rectangle of the bar at the specified index.
     *
     * @param index The index of the bar.
     * @param out (Optional) The rectangle in which to place the bounds.
     * @result A rectangle containing the bounds. If <code>out</code> was <code>null
     *         </code>, returns a new rect. Otherwise, returns <code>out</code>.
     */
    private Rect getBoundsForIndex(int index, Rect out) {
        final int value = mData[index];
        final float[] x = mScaleX.transform(index, (index + 1));
        final float[] y = mScaleY.transform(0, value);
        final float halfMargin = (mBarMargin / 2);

        // Ensure the rect bounds are sane.
        Arrays.sort(x);
        Arrays.sort(y);

        // Display zero-height bars as a thick line.
        if (y[0] == y[1]) {
            y[0] -= halfMargin;
            y[1] += halfMargin;
        }

        // Adjust width for margins, but only down to one pixel.
        final float halfWidth = ((x[1] - x[0]) / 2);
        if (halfMargin < halfWidth) {
            x[0] += halfMargin;
            x[1] -= halfMargin;
        } else {
            x[0] += (halfWidth - 0.5f);
            x[1] -= (halfWidth - 0.5f);
        }

        // Round everything using floor/ceil.
        final int x0 = (int) Math.floor(x[0]);
        final int x1 = (int) Math.ceil(x[1]);
        final int y0 = (int) Math.floor(y[0]);
        final int y1 = (int) Math.ceil(y[1]);

        if (out != null) {
            out.set(x0, y0, x1, y1);
            return out;
        }

        return new Rect(x0, y0, x1, y1);
    }

    /**
     * Updates the domain of the scaling functions used to draw bars on screen
     * and map touch coordinates to bar indices.
     */
    private void updateDomain() {
        int min = 0;
        int max = 0;

        for (int i = 0; i < mSize; i++) {
            final int value = mData[i];
            if (value < min) {
                min = value;
            } else if (value > max) {
                max = value;
            }
        }

        mScaleY.domain((min - 1), (max + 1));
        mScaleX.domain(0, Math.max(1, mSize));
    }

    /**
     * Updates the range of the scaling functions used to draw bars on screen
     * and map touch coordinates to bar indices.
     */
    private void updateRange() {
        final float titleHeight = mTitleSize;

        mScaleY.range(getHeight(), titleHeight);
        mScaleX.range(0, getWidth());
    }

    /**
     * Updates the content description to reflect the title and number of items
     * displayed.
     */
    private void updateContentDescription() {
        final CharSequence description = getContext().getString(
                R.string.bar_graph_description, mTitle, mSize);

        setContentDescription(description);
    }

    /**
     * Given a value <code>x</code>, returns whether the value is within the
     * range <code>[a, b]</code>.
     */
    private static boolean isValueInRange(float x, float a, float b) {
        return (((x >= a) && (x <= b)) || ((x <= a) && (x >= b)));
    }

    /**
     * Implementation of {@link ExploreByTouchHelper} that exposes the contents
     * of a {@link BarGraphView} to accessibility services by mapping each bar
     * to a virtual view.
     */
    private class BarGraphAccessHelper extends ExploreByTouchHelper {
        private final Rect mTempParentBounds = new Rect();

        public BarGraphAccessHelper(View parentView) {
            super(parentView);
        }

        @Override
        protected int getVirtualViewIdAt(float x, float y) {
            // We already map (x,y) to bar index for onTouchEvent().
            final int index = getBarIndexAt(x, y);
            if (index >= 0) {
                return index;
            }

            return ExploreByTouchHelper.INVALID_ID;
        }

        @Override
        protected void getVisibleVirtualViewIds(List<Integer> virtualViewIds) {
            final int count = getBarCount();
            for (int index = 0; index < count; index++) {
                virtualViewIds.add(index);
            }
        }

        private CharSequence getDescriptionForIndex(int index) {
            final int value = getBarValue(index);
            final int templateRes = ((mHighlightedIndex == index) ?
                    R.string.bar_desc_highlight : R.string.bar_desc);
            return getContext().getString(templateRes, index, value);
        }

        @Override
        protected void populateEventForVirtualViewId(int virtualViewId, AccessibilityEvent event) {
            final CharSequence desc = getDescriptionForIndex(virtualViewId);
            event.setContentDescription(desc);
        }

        @Override
        protected void populateNodeForVirtualViewId(
                int virtualViewId, AccessibilityNodeInfoCompat node) {
            // Node and event descriptions are usually identical.
            final CharSequence desc = getDescriptionForIndex(virtualViewId);
            node.setContentDescription(desc);

            // Since the user can tap a bar, add the CLICK action.
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);

            // Reported bounds should be consistent with onDraw().
            final Rect bounds = getBoundsForIndex(virtualViewId, mTempParentBounds);
            node.setBoundsInParent(bounds);
        }

        @Override
        protected boolean performActionForVirtualViewId(
                int virtualViewId, int action, Bundle arguments) {
            switch (action) {
                case AccessibilityNodeInfoCompat.ACTION_CLICK:
                    // Click handling should be consistent with onTouchEvent().
                    onBarClicked(virtualViewId);
                    return true;
            }

            // Only need to handle actions added in populateNode.
            return false;
        }
    }
}
