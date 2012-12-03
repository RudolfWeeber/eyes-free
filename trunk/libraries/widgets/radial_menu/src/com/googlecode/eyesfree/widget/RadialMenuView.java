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

package com.googlecode.eyesfree.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeProviderCompat;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import com.googlecode.eyesfree.utils.TouchExplorationHelper;
import com.googlecode.eyesfree.widget.RadialMenu.MenuLayoutListener;

import java.util.List;

public class RadialMenuView extends SurfaceView {
    private final RadialMenu mRootMenu;
    private final Paint mPaint;
    private final LongPressHandler mHandler;

    // Cached menu size.
    private int mCachedMenuSize = 0;

    // Cached bounds.
    private final RectF mCachedInnerBound = new RectF();
    private final RectF mCachedOuterBound = new RectF();
    private final RectF mCachedCornerBound = new RectF();
    private final RectF mCachedExtremeBound = new RectF();

    // Cached paths representing a single item.
    private final Path mCachedInnerPath = new Path();
    private final Path mCachedOuterPath = new Path();
    private final Path mCachedOuterPathReverse = new Path();
    private final Path mCachedCornerPath = new Path();
    private final Path mCachedCornerPathReverse = new Path();

    private SurfaceHolder mHolder;
    private RadialMenuItem mFocusedItem;

    private RadialSubMenu mSubMenu;
    private float mSubMenuOffset;

    // Dimensions loaded from context.
    private final int mInnerRadius;
    private final int mOuterRadius;
    private final int mCornerRadius;
    private final int mExtremeRadius;
    private final int mSpacing;
    private final int mTextSize;

    // Generated from dimensions.
    private final int mInnerRadiusSq;
    private final int mCornerRadiusSq;
    private final int mExtremeRadiusSq;

    /**
     * Whether to use a node provider. If set to {@code false}, converts hover
     * events to touch events and does not send any accessibility events.
     */
    private final boolean mUseNodeProvider;

    private int mRootMenuOffset = 0;

    private float mCenterX;
    private float mCenterY;

    // Colors loaded from context.
    private final int mInnerFillColor;
    private final int mOuterFillColor;
    private final int mOuterStrokeColor;
    private final int mTextFillColor;
    private final int mCornerFillColor;
    private final int mCornerStrokeColor;
    private final int mCornerTextFillColor;

    // Color filters for modal content.
    private final ColorFilter mSelectionFilter;
    private final ColorFilter mSubMenuFilter;

    // Whether to display the "carrot" dot.
    private boolean mDisplayDot;

    /** Lazily-constructed node provider helper. */
    private RadialMenuHelper mTouchExplorer;

    public RadialMenuView(Context context, RadialMenu menu) {
        this(context, menu, true);
    }

    public RadialMenuView(Context context, RadialMenu menu, boolean useNodeProvider) {
        super(context);

        mUseNodeProvider = useNodeProvider;

        mRootMenu = menu;
        mRootMenu.setLayoutListener(mLayoutListener);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);

        mHandler = new LongPressHandler(context);
        mHandler.setListener(mLongPressListener);

        final SurfaceHolder holder = getHolder();
        holder.setFormat(PixelFormat.TRANSLUCENT);
        holder.addCallback(mSurfaceCallback);

        final Resources res = context.getResources();

        // Dimensions.
        mInnerRadius = res.getDimensionPixelSize(R.dimen.inner_radius);
        mOuterRadius = res.getDimensionPixelSize(R.dimen.outer_radius);
        mCornerRadius = res.getDimensionPixelSize(R.dimen.corner_radius);
        mExtremeRadius = res.getDimensionPixelSize(R.dimen.extreme_radius);
        mSpacing = res.getDimensionPixelOffset(R.dimen.spacing);
        mTextSize = res.getDimensionPixelSize(R.dimen.text_size);

        // Colors.
        mInnerFillColor = res.getColor(R.color.inner_fill);
        mOuterFillColor = res.getColor(R.color.outer_fill);
        mOuterStrokeColor = res.getColor(R.color.outer_stroke);
        mTextFillColor = res.getColor(R.color.text_fill);
        mCornerFillColor = res.getColor(R.color.corner_fill);
        mCornerStrokeColor = res.getColor(R.color.corner_stroke);
        mCornerTextFillColor = res.getColor(R.color.corner_text_fill);

        final int selectionOverlayColor = res.getColor(R.color.selection_overlay);
        final int subMenuOverlayColor = res.getColor(R.color.submenu_overlay);

        // Lighting filters generated from colors.
        mSelectionFilter = new PorterDuffColorFilter(selectionOverlayColor, PorterDuff.Mode.SCREEN);
        mSubMenuFilter = new PorterDuffColorFilter(subMenuOverlayColor, PorterDuff.Mode.SCREEN);

        mInnerRadiusSq = mInnerRadius * mInnerRadius;
        mCornerRadiusSq = mCornerRadius * mCornerRadius;
        mExtremeRadiusSq = mExtremeRadius * mExtremeRadius;

        if (mUseNodeProvider) {
            // Set the accessibility delegate.
            ViewCompat.setAccessibilityDelegate(this, mAccessDelegate);
        }
    }

    public void displayDot() {
        mDisplayDot = true;

        refresh();
    }

    private static void createBounds(RectF target, int diameter, int radius) {
        final float center = diameter / 2.0f;
        final float left = center - radius;
        final float right = center + radius;

        target.set(left, left, right, right);
    }

    /**
     * Re-draw cached wedge bitmaps.
     */
    private void refreshCache() {
        final RadialMenu menu = mSubMenu != null ? mSubMenu : mRootMenu;
        final int menuSize = menu.size();

        if (menuSize <= 0) {
            return;
        }

        final float wedgeArc = 360 / menuSize;
        final float offsetArc = (wedgeArc / 2.0f) + 90;
        final float cornerWedgeArc = 90;
        final float cornerOffsetArc = (cornerWedgeArc / 2.0f) + 90;
        final int diameter = mExtremeRadius * 2;

        createBounds(mCachedInnerBound, diameter, mInnerRadius);
        createBounds(mCachedOuterBound, diameter, mOuterRadius);
        createBounds(mCachedCornerBound, diameter, mCornerRadius);
        createBounds(mCachedExtremeBound, diameter, mExtremeRadius);

        final float left = wedgeArc - mSpacing - offsetArc;
        final float center = ((wedgeArc / 2.0f) + mSpacing) - offsetArc;
        final float right = mSpacing - offsetArc;

        mCachedInnerPath.rewind();
        mCachedInnerPath.arcTo(mCachedInnerBound, left, (right - left));
        mCachedInnerPath.arcTo(mCachedOuterBound, right, (left - right));
        mCachedInnerPath.close();

        mCachedOuterPath.rewind();
        mCachedOuterPath.arcTo(mCachedOuterBound, center, (left - center));
        mCachedOuterPath.arcTo(mCachedExtremeBound, left, (right - left));
        mCachedOuterPath.arcTo(mCachedOuterBound, right, (center - right));
        mCachedOuterPath.close();

        mCachedOuterPathReverse.rewind();
        mCachedOuterPathReverse.arcTo(mCachedOuterBound, center, (right - center));
        mCachedOuterPathReverse.arcTo(mCachedExtremeBound, right, (left - right));
        mCachedOuterPathReverse.arcTo(mCachedOuterBound, left, (center - left));
        mCachedOuterPathReverse.close();

        final float cornerLeft = cornerWedgeArc - cornerOffsetArc;
        final float cornerCenter = (cornerWedgeArc / 2.0f) - cornerOffsetArc;
        final float cornerRight = -cornerOffsetArc;

        mCachedCornerPath.rewind();
        mCachedCornerPath.arcTo(mCachedCornerBound, cornerCenter, (cornerLeft - cornerCenter));
        mCachedCornerPath.arcTo(mCachedExtremeBound, cornerLeft, (cornerRight - cornerLeft));
        mCachedCornerPath.arcTo(mCachedCornerBound, cornerRight, (cornerCenter - cornerRight));
        mCachedCornerPath.close();

        mCachedCornerPathReverse.rewind();
        mCachedCornerPathReverse.arcTo(mCachedCornerBound, cornerCenter, (cornerRight - cornerCenter));
        mCachedCornerPathReverse.arcTo(mCachedExtremeBound, cornerRight, (cornerLeft - cornerRight));
        mCachedCornerPathReverse.arcTo(mCachedCornerBound, cornerLeft, (cornerCenter - cornerLeft));
        mCachedCornerPathReverse.close();

        mCachedMenuSize = menuSize;
    }

    @Override
    public void invalidate() {
        super.invalidate();

        refresh();
    }

    /**
     * Re-draw display.
     */
    public void refresh() {
        refreshInner();
    }

    public synchronized void refreshInner() {
        // TODO: Why is this synchronized?
        final SurfaceHolder holder = mHolder;

        if (holder == null) {
            return;
        }

        final Canvas canvas = holder.lockCanvas();

        if (canvas == null) {
            return;
        }

        // Clear the canvas.
        canvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);

        if (getVisibility() != View.VISIBLE) {
            holder.unlockCanvasAndPost(canvas);
            return;
        }

        final int width = getWidth();
        final int height = getHeight();

        // Only draw the "carrot" dot.
        if (mDisplayDot) {
            final Context context = getContext();
            final float centerX = (width / 2.0f);
            final float centerY = (height / 2.0f);
            final float radius = mInnerRadius;
            final RectF dot = new RectF(
                    (centerX - radius), (centerY - radius), (centerX + radius), (centerY + radius));

            mPaint.setStyle(Style.FILL);
            mPaint.setColor(mCornerFillColor);
            canvas.drawOval(dot, mPaint);

            mPaint.setStyle(Style.FILL);
            mPaint.setColor(mCornerTextFillColor);
            mPaint.setTextAlign(Align.CENTER);
            mPaint.setTextSize(mTextSize);
            canvas.drawText(context.getString(R.string.tap_and), centerX, centerY, mPaint);
            canvas.drawText(context.getString(R.string.hold), centerX, centerY + mTextSize, mPaint);

            holder.unlockCanvasAndPost(canvas);
            return;
        }

        // Draw the menu wedges.
        final RadialMenu menu = mSubMenu != null ? mSubMenu : mRootMenu;
        final float offset = mSubMenu != null ? mSubMenuOffset : mRootMenuOffset;
        final int wedges = menu.size();
        final float center = mExtremeRadius;
        final float degrees = 360 / (float) wedges;
        final Matrix matrix = new Matrix();

        if (mCachedMenuSize != menu.size()) {
            refreshCache();
        }

        for (int i = 0; i < wedges; i++) {
            final RadialMenuItem wedge = menu.getItem(i);
            final String title = wedge.getTitle().toString();
            final float rotation = (degrees * i) + offset;

            // Apply the appropriate color filters.
            if (wedge.equals(mFocusedItem)) {
                mPaint.setColorFilter(mSelectionFilter);
            } else if (wedge.hasSubMenu()) {
                mPaint.setColorFilter(mSubMenuFilter);
            }

            wedge.offset = rotation;

            matrix.setRotate(rotation, center, center);
            matrix.postTranslate(mCenterX - center, mCenterY - center);
            canvas.setMatrix(matrix);

            mPaint.setStyle(Style.FILL);
            mPaint.setColor(mInnerFillColor);
            canvas.drawPath(mCachedInnerPath, mPaint);

            mPaint.setStyle(Style.FILL);
            mPaint.setColor(mOuterFillColor);
            canvas.drawPath(mCachedOuterPath, mPaint);

            mPaint.setStrokeWidth(2.0f);
            mPaint.setStyle(Style.STROKE);
            mPaint.setColor(mOuterStrokeColor);
            canvas.drawPath(mCachedOuterPath, mPaint);

            mPaint.setStyle(Style.FILL);
            mPaint.setColor(mTextFillColor);
            mPaint.setTextAlign(Align.CENTER);
            mPaint.setTextSize(mTextSize);

            if (rotation < 90 || rotation > 270) {
                canvas.drawTextOnPath(title, mCachedOuterPathReverse, 0, 2 * mTextSize, mPaint);
            } else {
                canvas.drawTextOnPath(title, mCachedOuterPath, 0, -mTextSize, mPaint);
            }

            mPaint.setColorFilter(null);
        }

        // Draw menu hot corners.
        // TODO: Should we allow sub-menus to have hot corners?
        for (int i = 0; i < 4; i++) {
            final RadialMenuItem wedge = mRootMenu.getHotCorner(i);

            if (wedge == null) {
                continue;
            }

            final float rotation = RadialMenu.getHotCornerRotation(i);
            final PointF cornerLocation = RadialMenu.getHotCornerLocation(i);
            final float cornerX = cornerLocation.x * width;
            final float cornerY = cornerLocation.y * height;
            final String title = wedge.getTitle().toString();

            // Apply the appropriate color filters.
            if (wedge.equals(mFocusedItem)) {
                mPaint.setColorFilter(mSelectionFilter);
            } else if (wedge.hasSubMenu()) {
                mPaint.setColorFilter(mSubMenuFilter);
            } else {
                mPaint.setColorFilter(null);
            }

            wedge.offset = rotation;

            matrix.setRotate(rotation, center, center);
            matrix.postTranslate(cornerX - center, cornerY - center);
            canvas.setMatrix(matrix);

            mPaint.setStyle(Style.FILL);
            mPaint.setColor(mCornerFillColor);
            canvas.drawPath(mCachedCornerPath, mPaint);

            mPaint.setStrokeWidth(2.0f);
            mPaint.setStyle(Style.STROKE);
            mPaint.setColor(mCornerStrokeColor);
            canvas.drawPath(mCachedCornerPath, mPaint);

            mPaint.setStyle(Style.FILL);
            mPaint.setColor(mCornerTextFillColor);
            mPaint.setTextAlign(Align.CENTER);
            mPaint.setTextSize(mTextSize);

            if ((rotation < 90 && rotation > -90) || rotation > 270) {
                canvas.drawTextOnPath(title, mCachedCornerPathReverse, 0, 2 * mTextSize, mPaint);
            } else {
                canvas.drawTextOnPath(title, mCachedCornerPath, 0, -mTextSize, mPaint);
            }
        }

        holder.unlockCanvasAndPost(canvas);
    }

    /**
     * Sets the starting offset in degrees. Menu items will be positioned in
     * clockwise order starting at this offset. By default, this is 0 degrees.
     *
     * @param degrees The offset in degrees.
     */
    public void setOffset(int degrees) {
        mRootMenuOffset = degrees;
    }

    /**
     * Displays the menu centered at the specified coordinates.
     *
     * @param centerX The center X coordinate.
     * @param centerY The center Y coordinate.
     */
    public void displayAt(float centerX, float centerY) {
        mCenterX = centerX;
        mCenterY = centerY;

        mDisplayDot = false;
        mSubMenu = null;
        mFocusedItem = null;

        refresh();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        final int measuredWidth = widthMode == MeasureSpec.UNSPECIFIED ? 320 : widthSize;
        final int measuredHeight = heightMode == MeasureSpec.UNSPECIFIED ? 480 : heightSize;

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    private final AccessibilityDelegateCompat mAccessDelegate = new AccessibilityDelegateCompat() {
        @Override
        public AccessibilityNodeProviderCompat getAccessibilityNodeProvider(View host) {
            if (mTouchExplorer == null) {
                mTouchExplorer = new RadialMenuHelper(getContext());
            }

            return mTouchExplorer;
        }
    };

    @TargetApi(14)
    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (mUseNodeProvider) {
            return super.onHoverEvent(event);
        } else {
            return onTouchEvent(event);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_HOVER_ENTER:
                // Fall-through to movement events.
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                onMove(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_HOVER_EXIT:
                onUp(event.getX(), event.getY());
                break;
            default:
                // Don't handle other types of events.
                return false;
        }

        mHandler.onTouch(this, event);

        return true;
    }

    /**
     * Computes and returns which menu item or hot corner the user is touching.
     *
     * @param x The touch X coordinate.
     * @param y The touch Y coordinate.
     * @return The menu item that the user is touching.
     */
    private RadialMenuItem computeTouchedMenuItem(float x, float y) {
        final RadialMenu menu = mSubMenu != null ? mSubMenu : mRootMenu;
        final float offset = mSubMenu != null ? mSubMenuOffset : mRootMenuOffset;
        final float dX = x - mCenterX;
        final float dY = y - mCenterY;
        final float touchDistSq = (dX * dX) + (dY * dY);

        // Never pick up touches within the inner radius.
        if (touchDistSq < mInnerRadiusSq) {
            return null;
        }

        // Is the user touching a hot corner?
        final Pair<RadialMenuItem, Float> closestCorner = computeTouchedHotCorner(x, y);

        if ((closestCorner != null)
                && ((closestCorner.second == null) || (closestCorner.second < touchDistSq))) {
            return closestCorner.first;
        }

        // Which wedge is the user touching?
        final double angle = Math.atan2(dX, dY);
        final double wedgeArc = 360 / (double) menu.size();
        final double offsetArc = (wedgeArc / 2.0) - offset;

        double touchArc = ((180 - Math.toDegrees(angle)) + offsetArc) % 360;

        if (touchArc < 0) {
            touchArc += 360;
        }

        final int wedgeNum = (int) (touchArc / wedgeArc);

        if ((wedgeNum < 0) || (wedgeNum > menu.size())) {
            Log.e(VIEW_LOG_TAG, "Invalid wedge index: " + wedgeNum);
            return null;
        }

        return menu.getItem(wedgeNum);
    }

    /**
     * Computes which hot corner the user is touching, if any. Returns a
     * {@link Pair} containing the touched menu item and distance from the
     * center of the item. If the touch is within the item's center area, the
     * distance will be returned as {@code null}.
     *
     * @param x The touch X coordinate.
     * @param y The touch Y coordinate.
     * @return A pair containing the menu item and touch distance (or
     *         {@code null} if the touch is within the item's center area.
     */
    private Pair<RadialMenuItem, Float> computeTouchedHotCorner(float x, float y) {
        final int width = getWidth();
        final int height = getHeight();

        // How close is the user to a hot corner?
        for (int groupId = 0; groupId < 4; groupId++) {
            final RadialMenuItem hotCorner = mRootMenu.getHotCorner(groupId);

            // Not all hot corners are populated.
            if (hotCorner == null) {
                continue;
            }

            final PointF cornerLocation = RadialMenu.getHotCornerLocation(groupId);
            final float cornerDX = x - (cornerLocation.x * width);
            final float cornerDY = y - (cornerLocation.y * height);
            final float cornerTouchDistSq = (cornerDX * cornerDX) + (cornerDY * cornerDY);

            // If the user is touching within a corner's inner radius, then
            // they're definitely touching a hot corner. Otherwise, if
            // they're within the outer radius then check whether they're
            // closer to a corner or a wedge.
            if (cornerTouchDistSq < mExtremeRadiusSq) {
                if (cornerTouchDistSq < mCornerRadiusSq) {
                    return new Pair<RadialMenuItem, Float>(hotCorner, null);
                } else {
                    return new Pair<RadialMenuItem, Float>(hotCorner, cornerTouchDistSq);
                }
            }
        }

        return null;
    }

    /**
     * Called when the user lifts their finger. Selects a menu item.
     *
     * @param x The touch X coordinate.
     * @param y The touch Y coordinate.
     */
    private void onUp(float x, float y) {
        onItemSelected(computeTouchedMenuItem(x, y));
    }

    /**
     * Called when the user moves their finger. Focuses a menu item.
     *
     * @param x The touch X coordinate.
     * @param y The touch Y coordinate.
     */
    private void onMove(float x, float y) {
        if (mDisplayDot) {
            mDisplayDot = false;
            displayAt(x, y);
            return;
        }

        onItemFocused(computeTouchedMenuItem(x, y));
    }

    /**
     * Sets a sub-menu as the currently displayed menu.
     *
     * @param subMenu The sub-menu to display.
     * @param offset The offset of the sub-menu's menu item.
     */
    private void setSubMenu(RadialSubMenu subMenu, float offset) {
        mSubMenu = subMenu;
        mSubMenuOffset = offset;

        refreshCache();
        refresh();

        if ((subMenu != null) && (subMenu.size() > 0)) {
            onItemFocused(subMenu.getItem(0));
        }
    }

    /**
     * Called when an item is focused. If the newly focused item is the same as
     * the previously focused item, this is a no-op. Otherwise, the menu item's
     * select action is triggered and an accessibility select event is fired.
     *
     * @param item The item that the user focused.
     */
    private void onItemFocused(RadialMenuItem item) {
        if (mFocusedItem == item) {
            return;
        }

        final RadialMenu menu = mSubMenu != null ? mSubMenu : mRootMenu;

        mFocusedItem = item;

        refresh();

        if (item == null) {
            menu.clearSelection(0);
        } else if (item.isHotCorner()) {
            mRootMenu.selectIdentifierAction(item.getItemId(), 0);
        } else {
            menu.selectIdentifierAction(item.getItemId(), 0);
        }

        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    /**
     * Called when the user stops over an item. If the user stops over the
     * "no selection" area and the current menu is a sub-menu, the sub-menu
     * closes. If the user stops over a sub-menu item, that sub-menu opens.
     *
     * @param item The menu item that the user stopped over.
     */
    private void onItemLongPressed(RadialMenuItem item) {
        if (item != null) {
            if (item.hasSubMenu()) {
                setSubMenu(item.getSubMenu(), item.offset);
            }
        } else {
            if (mSubMenu != null) {
                setSubMenu(null, 0);
            }
        }
    }

    /**
     * Called when a menu item is selected. The menu item's perform action is
     * triggered and a click accessibility event is fired.
     *
     * @param item The item that the used selected.
     */
    private void onItemSelected(RadialMenuItem item) {
        final RadialMenu menu = mSubMenu != null ? mSubMenu : mRootMenu;

        mFocusedItem = item;

        refresh();

        if (item == null) {
            menu.performIdentifierAction(RadialMenu.ID_CANCEL, 0);
        } else if (item.isHotCorner()) {
            mRootMenu.performIdentifierAction(item.getItemId(), 0);
        } else {
            menu.performIdentifierAction(item.getItemId(), 0);
        }

        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    private final SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mHolder = holder;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mHolder = null;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            refresh();
        }
    };

    private final LongPressHandler.LongPressListener mLongPressListener = new LongPressHandler.LongPressListener() {
        @Override
        public void onLongPress(MotionEvent e) {
            onItemLongPressed(mFocusedItem);
        }
    };

    private final MenuLayoutListener mLayoutListener = new MenuLayoutListener() {
        @Override
        public void onLayoutChanged() {
            postInvalidate();
        }
    };

    private class RadialMenuHelper extends TouchExplorationHelper<RadialMenuItem> {
        /**
         * @param context
         */
        public RadialMenuHelper(Context context) {
            super(context);
        }

        private final Rect mGlobalBounds = new Rect();
        private final Rect mLocalBounds = new Rect();

        @Override
        protected void populateNodeForItem(RadialMenuItem item, AccessibilityNodeInfoCompat node) {
            getGlobalVisibleRect(mGlobalBounds);
            getLocalVisibleRect(mLocalBounds);

            node.setContentDescription(item.getTitle());
            node.setVisibleToUser(item.isVisible());
            node.setCheckable(item.isCheckable());
            node.setChecked(item.isChecked());
            node.setEnabled(item.isEnabled());
            node.setBoundsInParent(mLocalBounds);
            node.setBoundsInScreen(mGlobalBounds);
            node.setClickable(true);
        }

        @Override
        protected void populateEventForItem(RadialMenuItem item, AccessibilityEvent event) {
            event.setContentDescription(item.getTitle());
            event.setChecked(item.isChecked());
            event.setEnabled(item.isEnabled());
        }

        @Override
        protected boolean performActionForItem(RadialMenuItem item, int action, Bundle arguments) {
            switch (action) {
                case AccessibilityNodeInfoCompat.ACTION_CLICK:
                    item.onClickPerformed();
                    return true;
            }

            return false;
        }

        @Override
        protected RadialMenuItem getItemForId(int id) {
            return mRootMenu.getItem(id);
        }

        @Override
        protected int getIdForItem(RadialMenuItem item) {
            return mRootMenu.indexOf(item);
        }

        @Override
        protected RadialMenuItem getItemAt(float x, float y) {
            return computeTouchedMenuItem(x, y);
        }

        @Override
        protected void getVisibleItems(List<RadialMenuItem> items) {
            for (int i = 0; i < mRootMenu.size(); i++) {
                final RadialMenuItem item = mRootMenu.getItem(i);
                items.add(item);
            }
        }
    }
}
