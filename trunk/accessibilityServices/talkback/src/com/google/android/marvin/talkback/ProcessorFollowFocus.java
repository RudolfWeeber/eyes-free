/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.marvin.talkback;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.android.marvin.talkback.TalkBackService.EventListener;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.NodeFocusFinder;
import com.googlecode.eyesfree.utils.WeakReferenceHandler;

import java.util.HashSet;

/**
 * Requests accessibility focus from system-focused views.
 */
@TargetApi(16)
class ProcessorFollowFocus implements EventListener {
    /** This processor requires JellyBean (API 16). */
    public static final int MIN_API_LEVEL = 16;

    /** The minimum delay between window state change and scroll events. */
    private static final long DELAY_FOLLOW_AFTER_STATE = 100;

    private final TalkBackService mService;
    private final CursorController mCursorController;
    private final AccessibilityManager mAccessibilityManager;

    /** Event time for the most recent window state changed event. */
    private long mLastWindowStateChanged = 0;

    private AccessibilityNodeInfoCompat mLastScrollSource;
    private int mLastScrollAction = 0;
    private int mLastScrollFromIndex = -1;
    private int mLastScrollToIndex = -1;

    public ProcessorFollowFocus(TalkBackService service) {
        mService = service;
        mCursorController = service.getCursorController();
        mAccessibilityManager = (AccessibilityManager) service.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
    }

    @Override
    public void process(AccessibilityEvent event) {
        if (!mAccessibilityManager.isTouchExplorationEnabled()) {
            // Don't manage focus when touch exploration is disabled.
            return;
        }

        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                setFocusFromInput(record);
                break;
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
                setFocusFromSelection(event, record);
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                // Invalidate scrolling information.
                if (mLastScrollSource != null) {
                    mLastScrollSource.recycle();
                    mLastScrollSource = null;
                }
                mLastScrollAction = 0;
                mLastScrollFromIndex = -1;
                mLastScrollToIndex = -1;

                mLastWindowStateChanged = event.getEventTime();
                mCursorController.clearCursor();
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                handleContentChangedEvent(record);
                break;
            case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
                setFocusFromHover(record);
                break;
            case AccessibilityEvent.TYPE_VIEW_SCROLLED: {
                handleScrollEvent(event, record);
                break;
            }
        }
    }

    private void handleContentChangedEvent(AccessibilityRecordCompat record) {
        final AccessibilityNodeInfoCompat source = record.getSource();

        if (source == null) {
            return;
        }

        source.recycle();

        mHandler.followContentChangedDelayed(record);
    }

    private void handleScrollEvent(AccessibilityEvent event, AccessibilityRecordCompat record) {
        final AccessibilityNodeInfoCompat source = record.getSource();
        if (source == null) {
            LogUtils.log(this, Log.ERROR, "Drop scroll with no source node");
            return;
        }

        // Only move focus if we've already seen the source.
        if (source.equals(mLastScrollSource)) {
            final boolean isMovingForward =
                    (mLastScrollAction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    || (event.getFromIndex() > mLastScrollFromIndex)
                    || (event.getToIndex() > mLastScrollToIndex);

            mHandler.followScrollDelayed(source, isMovingForward);
        }

        if (mLastScrollSource != null) {
            mLastScrollSource.recycle();
        }

        mLastScrollSource = source;
        mLastScrollFromIndex = record.getFromIndex();
        mLastScrollToIndex = record.getToIndex();
        mLastScrollAction = 0;
    }

    /**
     * Attempts to place focus on the {@code event}'s selected item.
     */
    private boolean setFocusFromSelection(AccessibilityEvent event, AccessibilityRecordCompat record) {
        AccessibilityNodeInfoCompat source = null;
        AccessibilityNodeInfoCompat child = null;

        if ((event.getEventTime() - mLastWindowStateChanged) < DELAY_FOLLOW_AFTER_STATE) {
            LogUtils.log(this, Log.INFO, "Drop selected events following state change.");
            return false;
        }

        try {
            source = record.getSource();
            if (source == null) {
                return false;
            }

            final int index = (record.getCurrentItemIndex() - record.getFromIndex());
            if ((index < 0) || (index >= source.getChildCount())) {
                return false;
            }

            child = source.getChild(index);
            if (child == null) {
                return false;
            }

            if (!AccessibilityNodeInfoUtils.isTopLevelScrollItem(mService, child)) {
                return false;
            }

            return tryFocusing(child);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(source, child);
        }
    }

    /**
     * Attempts to place focus on the {@code event}'s source node.
     */
    private boolean setFocusFromInput(AccessibilityRecordCompat event) {
        AccessibilityNodeInfoCompat source = null;

        try {
            source= event.getSource();
            if (source == null) {
                return false;
            }

            return tryFocusing(source);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(source);
        }
    }

    /**
     * Attempts to place focus within a new window.
     */
    private boolean ensureFocusConsistency() {
        final AccessibilityNodeInfo root = mService.getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        AccessibilityNodeInfoCompat compatRoot = null;
        AccessibilityNodeInfoCompat focused = null;
        AccessibilityNodeInfoCompat inputFocused = null;
        AccessibilityNodeInfoCompat firstFocus = null;

        try {
            compatRoot = new AccessibilityNodeInfoCompat(root);

            // First, see if we've already placed accessibility focus.
            focused = compatRoot.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (focused != null) {
                if (AccessibilityNodeInfoUtils.shouldFocusNode(mService, focused)) {
                    return true;
                }

                LogUtils.log(Log.ERROR, "Clearing old focus");
                focused.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            }

            // Next, see if the system has placed input focus.
            inputFocused = compatRoot.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (tryFocusing(inputFocused)) {
                return true;
            }

            // Finally, just try to focus the first focusable item.
            firstFocus = findFocusFromNode(compatRoot, NodeFocusFinder.SEARCH_FORWARD);
            if (tryFocusing(firstFocus)) {
                return true;
            }

            LogUtils.log(Log.ERROR, "Failed to place focus from new window");

            return false;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(compatRoot, focused, inputFocused, firstFocus);
        }
    }

    /**
     * Attempts to place focus on an accessibility-focusable node, starting from
     * the {@code event}'s source node.
     */
    private boolean setFocusFromHover(AccessibilityRecordCompat event) {
        AccessibilityNodeInfoCompat touched = null;
        AccessibilityNodeInfoCompat focusable = null;

        try {
            touched = event.getSource();
            if (touched == null) {
                return false;
            }

            focusable = AccessibilityNodeInfoUtils.findFocusFromHover(mService, touched);
            if (focusable == null) {
                return false;
            }

            return tryFocusing(focusable);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(touched, focusable);
        }
    }

    private boolean followContentChangedEvent(AccessibilityRecordCompat event) {
        return ensureFocusConsistency();
    }

    private boolean followScrollEvent(AccessibilityNodeInfoCompat source, boolean isMovingForward) {
        final AccessibilityNodeInfo root = mService.getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        AccessibilityNodeInfoCompat compatRoot = null;
        AccessibilityNodeInfoCompat focused = null;

        try {
            // First, see if we've already placed accessibility focus.
            compatRoot = new AccessibilityNodeInfoCompat(root);

            focused = compatRoot.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (focused != null) {
                if (AccessibilityNodeInfoUtils.shouldFocusNode(mService, focused)) {
                    return true;
                }

                focused.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            }

            // Try focusing the appropriate child node.
            if (tryFocusingChild(source, isMovingForward)) {
                return true;
            }

            // Finally, try focusing the scrollable node itself.
            if (tryFocusing(source)) {
                return true;
            }

            return false;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(compatRoot, focused);
        }
    }

    /**
     * If {@code wasMovingForward} is true, moves to the last child in the
     * {@code list} that matches {@code filter}. Otherwise, moves to the first
     * child in the {@code list} that matches {@code filter}.
     */
    private boolean tryFocusingChild(
            AccessibilityNodeInfoCompat parent, boolean wasMovingForward) {
        final int direction = wasMovingForward ? NodeFocusFinder.SEARCH_FORWARD
                : NodeFocusFinder.SEARCH_BACKWARD;

        AccessibilityNodeInfoCompat child = null;

        try {
            child = findChildFromNode(parent, direction);
            if (child == null) {
                return false;
            }

            return tryFocusing(child);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(child);
        }
    }

    private AccessibilityNodeInfoCompat findChildFromNode(
            AccessibilityNodeInfoCompat root, int direction) {
        final int childCount = root.getChildCount();
        if (childCount == 0) {
            return null;
        }

        final int increment;
        final int startIndex;

        if (direction == NodeFocusFinder.SEARCH_FORWARD) {
            increment = 1;
            startIndex = 0;
        } else {
            increment = -1;
            startIndex = (childCount - 1);
        }

        for (int childIndex = startIndex; (childIndex >= 0) && (childIndex < childCount);
                childIndex += increment) {
            final AccessibilityNodeInfoCompat child = root.getChild(childIndex);
            if (child == null) {
                continue;
            }

            if (AccessibilityNodeInfoUtils.shouldFocusNode(mService, child)) {
                return child;
            }

            child.recycle();
        }

        return null;
    }

    /**
     * Searches starting from {@code root} in a {@code direction} for a node
     * that matches the {@code filter}.
     */
    private AccessibilityNodeInfoCompat findFocusFromNode(
            AccessibilityNodeInfoCompat root, int direction) {
        AccessibilityNodeInfoCompat currentNode = NodeFocusFinder.focusSearch(root, direction);

        final HashSet<AccessibilityNodeInfoCompat> seenNodes = new HashSet<
                AccessibilityNodeInfoCompat>();

        while ((currentNode != null) && !seenNodes.contains(currentNode)
                && !AccessibilityNodeInfoUtils.shouldFocusNode(mService, currentNode)) {
            seenNodes.add(currentNode);
            currentNode = NodeFocusFinder.focusSearch(currentNode, direction);
        }

        // Recycle all the seen nodes.
        AccessibilityNodeInfoUtils.recycleNodes(seenNodes);

        if (currentNode == null) {
            return null;
        }

        return currentNode;
    }

    private boolean tryFocusing(AccessibilityNodeInfoCompat source) {
        if (source == null) {
            return false;
        }

        if (!AccessibilityNodeInfoUtils.shouldFocusNode(mService, source)) {
            return false;
        }

        if (!source.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS)) {
            return false;
        }

        mHandler.interruptFollowDelayed();
        return true;
    }

    /**
     * Listens to accessibility actions performed by the parent service.
     *
     * @param action The action performed.
     */
    public void onActionPerformed(int action) {
        switch (action) {
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:
                mLastScrollAction = action;
                break;
        }
    }

    private final FollowFocusHandler mHandler = new FollowFocusHandler(this);

    private static class FollowFocusHandler extends WeakReferenceHandler<ProcessorFollowFocus> {
        private static final int FOCUS_AFTER_SCROLL = 1;
        private static final int FOCUS_AFTER_CONTENT_CHANGED = 2;

        /** Delay after a scroll event before checking focus. */
        private static final long FOCUS_AFTER_SCROLL_DELAY = 250;
        private static final long FOCUS_AFTER_CONTENT_CHANGED_DELAY = 500;

        private AccessibilityNodeInfoCompat mCachedScrollNode;
        private AccessibilityRecordCompat mCachedContentRecord;

        public FollowFocusHandler(ProcessorFollowFocus parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, ProcessorFollowFocus parent) {
            switch (msg.what) {
                case FOCUS_AFTER_SCROLL:
                    parent.followScrollEvent(mCachedScrollNode, (Boolean) msg.obj);

                    if (mCachedScrollNode != null) {
                        mCachedScrollNode.recycle();
                        mCachedScrollNode = null;
                    }
                    break;
                case FOCUS_AFTER_CONTENT_CHANGED:
                    parent.followContentChangedEvent(mCachedContentRecord);

                    if (mCachedContentRecord != null) {
                        mCachedContentRecord.recycle();
                        mCachedContentRecord = null;
                    }
                    break;
            }
        }

        /**
         * Ensure that focus is placed after content change actions, but use a delay to
         * avoid consuming too many resources.
         *
         * @param record The scroll event.
         */
        public void followContentChangedDelayed(AccessibilityRecordCompat record) {
            removeMessages(FOCUS_AFTER_CONTENT_CHANGED);

            if (mCachedContentRecord != null) {
                mCachedContentRecord.recycle();
                mCachedContentRecord = null;
            }

            mCachedContentRecord = AccessibilityRecordCompat.obtain(record);

            final Message msg = obtainMessage(FOCUS_AFTER_CONTENT_CHANGED);
            sendMessageDelayed(msg, FOCUS_AFTER_CONTENT_CHANGED_DELAY);
        }

        /**
         * Ensure that focus is placed after scroll actions, but use a delay to
         * avoid consuming too many resources.
         */
        public void followScrollDelayed(
                AccessibilityNodeInfoCompat source, boolean isMovingForward) {
            removeMessages(FOCUS_AFTER_SCROLL);

            if (mCachedScrollNode != null) {
                mCachedScrollNode.recycle();
                mCachedScrollNode = null;
            }

            mCachedScrollNode = AccessibilityNodeInfoCompat.obtain(source);

            final Message msg = obtainMessage(FOCUS_AFTER_SCROLL, isMovingForward);
            sendMessageDelayed(msg, FOCUS_AFTER_SCROLL_DELAY);
        }

        /**
         * Interrupt any pending follow-focus events.
         */
        public void interruptFollowDelayed() {
            removeMessages(FOCUS_AFTER_CONTENT_CHANGED);
            removeMessages(FOCUS_AFTER_SCROLL);
        }
    }
}
