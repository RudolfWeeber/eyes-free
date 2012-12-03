/*
 * Copyright (C) 2012 Google Inc.
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

package com.googlecode.eyesfree.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityManagerCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeProviderCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnHoverListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.util.LinkedList;
import java.util.List;

@TargetApi(14)
public abstract class TouchExplorationHelper<T> extends AccessibilityNodeProviderCompat {
    /** Virtual node identifier value for invalid nodes. */
    public static final int INVALID_ID = Integer.MIN_VALUE;

    private final Rect mTempScreenRect = new Rect();
    private final Rect mTempParentRect = new Rect();
    private final Rect mTempGlobalRect = new Rect();

    private final AccessibilityManager mManager;

    private View mParentView;
    private int mFocusedItemId = INVALID_ID;
    private T mCurrentItem = null;

    /** Map of virtual node identifiers to cached virtual nodes. */
    private final SparseArray<AccessibilityNodeInfoCompat>
            mNodeCache = new SparseArray<AccessibilityNodeInfoCompat>();

    /**
     * Constructs a new touch exploration helper.
     *
     * @param context The parent context.
     */
    public TouchExplorationHelper(Context context) {
        mManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    /**
     * Installs this helper on the specified {@link View}.
     * <p>
     * Sets this helper as the target {@link View}'s accessibility delegate and
     * hover event listener.
     * </p>
     *
     * @param parentView The view to handle touch exploration for.
     */
    public void install(View parentView) {
        if (ViewCompat.getAccessibilityNodeProvider(parentView) instanceof TouchExplorationHelper) {
            throw new RuntimeException("Cannot install TouchExplorationHelper on a View that "
                    + "already has a helper installed.");
        }

        mParentView = parentView;
        mParentView.setOnHoverListener(mOnHoverListener);

        ViewCompat.setAccessibilityDelegate(mParentView, mDelegate);
        ViewCompat.setImportantForAccessibility(
                mParentView, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);

        invalidateParent();
    }

    /**
     * Removes this helper from the specified {@link View}.
     * <p>
     * Sets the previously specified {@link View}'s accessibility delegate and
     * hover event listener to {@code null}.
     * </p>
     */
    public void uninstall() {
        if (mParentView == null) {
            throw new RuntimeException("Cannot uninstall TouchExplorationHelper on a View that "
                    + "does not have a helper installed.");
        }

        // Setting an empty delegate will fall through to the parent's methods.
        ViewCompat.setAccessibilityDelegate(mParentView, new AccessibilityDelegateCompat());
        ViewCompat.setImportantForAccessibility(
                mParentView, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO);

        clearCache();

        mParentView.setOnHoverListener(null);
        mParentView = null;
    }

    /**
     * Invalidates cached information about the parent view.
     * <p>
     * You <b>must</b> call this method after adding or removing items from the
     * parent view.
     * </p>
     */
    public void invalidateParent() {
        clearCache();

        ViewCompat.setAccessibilityDelegate(mParentView, mDelegate);

        mParentView.sendAccessibilityEvent(AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED);
    }

    /**
     * Invalidates cached information for a particular item.
     * <p>
     * You <b>must</b> call this method when any of the properties set in
     * {@link #populateNodeForItem(Object, AccessibilityNodeInfoCompat)} have
     * changed.
     * </p>
     *
     * @param item
     */
    public void invalidateItem(T item) {
        final int virtualViewId = getIdForItem(item);

        // Remove the item from the cache.
        mNodeCache.get(virtualViewId).recycle();
        mNodeCache.remove(virtualViewId);

        sendEventForItem(item, AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED);
    }

    @Override
    public AccessibilityNodeInfoCompat createAccessibilityNodeInfo(int virtualViewId) {
        if (virtualViewId == View.NO_ID) {
            return getNodeForParent();
        }

        final AccessibilityNodeInfoCompat cachedNode = mNodeCache.get(virtualViewId);
        if (cachedNode != null) {
            // Allow the system to use a copy of the cached node.
            return AccessibilityNodeInfoCompat.obtain(cachedNode);
        }

        final T item = getItemForId(virtualViewId);
        if (item == null) {
            return null;
        }

        final AccessibilityNodeInfoCompat node = getNodeForItem(item);

        // Cache a copy of this node for later.
        mNodeCache.put(virtualViewId, AccessibilityNodeInfoCompat.obtain(node));

        return node;
    }

    @Override
    public boolean performAction(int virtualViewId, int action, Bundle arguments) {
        if (virtualViewId == View.NO_ID) {
            return mDelegate.performAccessibilityAction(mParentView, action, arguments);
        }

        final T item = getItemForId(virtualViewId);
        if (item == null) {
            return false;
        }

        boolean handled = false;

        switch (action) {
            case AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS:
                if (mFocusedItemId != virtualViewId) {
                    mFocusedItemId = virtualViewId;
                    sendEventForItem(
                            item, AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                    handled = true;
                }
                break;
            case AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
                if (mFocusedItemId == virtualViewId) {
                    mFocusedItemId = INVALID_ID;
                    sendEventForItem(
                            item, AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                    handled = true;
                }
                break;
        }

        handled |= performActionForItem(item, action, arguments);

        return handled;
    }

    private void clearCache() {
        for (int i = 0; i < mNodeCache.size(); i++) {
            mNodeCache.valueAt(i).recycle();
        }

        mNodeCache.clear();
    }

    private void setCurrentItem(T item) {
        if (mCurrentItem == item) {
            return;
        }

        if (mCurrentItem != null) {
            sendEventForItem(mCurrentItem, AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT);
        }

        mCurrentItem = item;

        if (mCurrentItem != null) {
            sendEventForItem(mCurrentItem, AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER);
        }
    }

    private void sendEventForItem(T item, int eventType) {
        final AccessibilityEvent event = getEventForItem(item, eventType);
        final ViewGroup group = (ViewGroup) mParentView.getParent();

        group.requestSendAccessibilityEvent(mParentView, event);
    }

    private AccessibilityEvent getEventForItem(T item, int eventType) {
        final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final int virtualDescendantId = getIdForItem(item);

        // Ensure the client has good defaults.
        event.setEnabled(true);

        // Allow the client to populate the event.
        populateEventForItem(item, event);

        if (event.getText().isEmpty() && TextUtils.isEmpty(event.getContentDescription())) {
            throw new RuntimeException(
                    "You must add text or a content description in populateEventForItem()");
        }

        // Don't allow the client to override these properties.
        event.setClassName(item.getClass().getName());
        event.setPackageName(mParentView.getContext().getPackageName());
        record.setSource(mParentView, virtualDescendantId);

        return event;
    }

    private AccessibilityNodeInfoCompat getNodeForParent() {
        final AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain(mParentView);
        ViewCompat.onInitializeAccessibilityNodeInfo(mParentView, info);

        final LinkedList<T> items = new LinkedList<T>();
        getVisibleItems(items);

        for (T item : items) {
            final int virtualDescendantId = getIdForItem(item);
            info.addChild(mParentView, virtualDescendantId);
        }

        return info;
    }

    private AccessibilityNodeInfoCompat getNodeForItem(T item) {
        final AccessibilityNodeInfoCompat node = AccessibilityNodeInfoCompat.obtain();
        final int virtualDescendantId = getIdForItem(item);

        // Ensure the client has good defaults.
        node.setEnabled(true);
        node.setVisibleToUser(true);

        // Allow the client to populate the node.
        populateNodeForItem(item, node);

        if (TextUtils.isEmpty(node.getText()) && TextUtils.isEmpty(node.getContentDescription())) {
            throw new RuntimeException(
                    "You must add text or a content description in populateNodeForItem()");
        }

        // Don't allow the client to override these properties.
        node.setPackageName(mParentView.getContext().getPackageName());
        node.setClassName(item.getClass().getName());
        node.setParent(mParentView);
        node.setSource(mParentView, virtualDescendantId);

        if (mFocusedItemId == virtualDescendantId) {
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        } else {
            node.addAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
        }

        // Check whether the bounds are populated.
        node.getBoundsInParent(mTempParentRect);
        node.getBoundsInScreen(mTempScreenRect);

        if (mTempParentRect.isEmpty() && mTempScreenRect.isEmpty()) {
            throw new RuntimeException(
                    "You must set parent or screen bounds in populateNodeForItem()");
        }

        // If either bound is empty, infer from the populated bound.
        if (mTempScreenRect.isEmpty() || mTempParentRect.isEmpty()) {
            mParentView.getGlobalVisibleRect(mTempGlobalRect);

            final int offsetX = mTempGlobalRect.left;
            final int offsetY = mTempGlobalRect.top;

            if (mTempScreenRect.isEmpty()) {
                mTempScreenRect.set(mTempParentRect);
                mTempScreenRect.offset(offsetX, offsetY);
                node.setBoundsInScreen(mTempScreenRect);
            } else {
                mTempParentRect.set(mTempScreenRect);
                mTempParentRect.offset(-offsetX, -offsetY);
                node.setBoundsInParent(mTempParentRect);
            }
        }

        return node;
    }

    private final AccessibilityDelegateCompat mDelegate = new AccessibilityDelegateCompat() {
        @Override
        public AccessibilityNodeProviderCompat getAccessibilityNodeProvider(View host) {
            return TouchExplorationHelper.this;
        }
    };

    private final OnHoverListener mOnHoverListener = new OnHoverListener() {
        @Override
        public boolean onHover(View view, MotionEvent event) {
            if (!AccessibilityManagerCompat.isTouchExplorationEnabled(mManager)) {
                return false;
            }

            switch (event.getAction()) {
                case MotionEventCompat.ACTION_HOVER_ENTER:
                case MotionEventCompat.ACTION_HOVER_MOVE:
                    final T item = getItemAt(event.getX(), event.getY());
                    setCurrentItem(item);
                    return true;
                case MotionEventCompat.ACTION_HOVER_EXIT:
                    setCurrentItem(null);
                    return true;
            }

            return false;
        }
    };

    /**
     * Performs an accessibility action on the specified item. See
     * {@link AccessibilityNodeInfoCompat#performAction(int, Bundle)}.
     * <p>
     * The helper class automatically handles focus management resulting from
     * {@link AccessibilityNodeInfoCompat#ACTION_ACCESSIBILITY_FOCUS} and
     * {@link AccessibilityNodeInfoCompat#ACTION_CLEAR_ACCESSIBILITY_FOCUS}, so
     * typically a developer only needs to handle actions added manually in the
     * {{@link #populateNodeForItem(Object, AccessibilityNodeInfoCompat)}
     * method.
     * </p>
     *
     * @param item The item on which to perform the action.
     * @param action The accessibility action to perform.
     * @param arguments Arguments for the action, or optionally {@code null}.
     * @return {@code true} if the action was performed successfully.
     */
    protected abstract boolean performActionForItem(T item, int action, Bundle arguments);

    /**
     * Populates an event with information about the specified item.
     * <p>
     * At a minimum, a developer must populate the event text by doing one of
     * the following:
     * <ul>
     * <li>appending text to {@link AccessibilityEvent#getText()}</li>
     * <li>populating a description with
     * {@link AccessibilityEvent#setContentDescription(CharSequence)}</li>
     * </ul>
     * </p>
     *
     * @param item The item for which to populate the event.
     * @param event The event to populate.
     */
    protected abstract void populateEventForItem(T item, AccessibilityEvent event);

    /**
     * Populates a node with information about the specified item.
     * <p>
     * At a minimum, a developer must:
     * <ul>
     * <li>populate the event text using
     * {@link AccessibilityNodeInfoCompat#setText(CharSequence)} or
     * {@link AccessibilityNodeInfoCompat#setContentDescription(CharSequence)}
     * </li>
     * <li>set the item's parent-relative bounds using
     * {@link AccessibilityNodeInfoCompat#setBoundsInParent(Rect)}
     * </ul>
     *
     * @param item The item for which to populate the node.
     * @param node The node to populate.
     */
    protected abstract void populateNodeForItem(T item, AccessibilityNodeInfoCompat node);

    /**
     * Populates a list with the parent view's visible items.
     * <p>
     * The result of this method is cached until the developer calls
     * {@link #invalidateParent()}.
     * </p>
     *
     * @param items The list to populate with visible items.
     */
    protected abstract void getVisibleItems(List<T> items);

    /**
     * Returns the item under the specified parent-relative coordinates.
     *
     * @param x The parent-relative x coordinate.
     * @param y The parent-relative y coordinate.
     * @return The item under coordinates (x,y).
     */
    protected abstract T getItemAt(float x, float y);

    /**
     * Returns the unique identifier for an item. If the specified item does not
     * exist, returns {@link #INVALID_ID}.
     * <p>
     * This result of this method must be consistent with
     * {@link #getItemForId(int)}.
     * </p>
     *
     * @param item The item whose identifier to return.
     * @return A unique identifier, or {@link #INVALID_ID}.
     */
    protected abstract int getIdForItem(T item);

    /**
     * Returns the item for a unique identifier. If the specified item does not
     * exist, returns {@code null}.
     *
     * @param id The identifier for the item to return.
     * @return An item, or {@code null}.
     */
    protected abstract T getItemForId(int id);
}
