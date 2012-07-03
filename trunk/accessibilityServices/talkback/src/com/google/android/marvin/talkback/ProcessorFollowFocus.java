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

import android.accessibilityservice.AccessibilityService;
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
import com.google.android.marvin.utils.WeakReferenceHandler;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.NodeFocusFinder;

import java.util.HashSet;

/**
 * Requests accessibility focus from system-focused views.
 */
@TargetApi(16)
class ProcessorFollowFocus implements EventListener {
    /** This processor requires JellyBean (API 16). */
    public static final int MIN_API_LEVEL = 16;

    private final AccessibilityService mService;
    private final AccessibilityManager mAccessibilityManager;

    private int mLastAction = 0;
    private int mLastFromIndex = 0;

    public ProcessorFollowFocus(AccessibilityService service) {
        mService = service;
        mAccessibilityManager = (AccessibilityManager) service.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
    }

    @Override
    public void process(AccessibilityEvent event) {
        if (!mAccessibilityManager.isTouchExplorationEnabled()) {
            return;
        }

        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                setFocusFromInput(record);
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                setFocusFromWindow();
                break;
            case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
                setFocusFromHover(record);
                break;
            case AccessibilityEvent.TYPE_VIEW_SCROLLED: {
                handleScrollEvent(record);
                break;
            }
        }
    }

    private void handleScrollEvent(AccessibilityRecordCompat record) {
        final AccessibilityNodeInfoCompat source = record.getSource();

        if (source == null) {
            return;
        }

        source.recycle();

        mHandler.followScrollDelayed(record, mLastFromIndex, mLastAction);
        mLastFromIndex = record.getFromIndex();
        mLastAction = 0;
    }

    /**
     * Attempts to place focus on the {@code event}'s source node.
     */
    private void setFocusFromInput(AccessibilityRecordCompat event) {
        final AccessibilityNodeInfoCompat source = event.getSource();

        if (source == null) {
            return;
        }

        if (AccessibilityNodeInfoUtils.shouldFocusNode(mService, source)) {
            tryFocusing(source);
        }

        source.recycle();
    }

    /**
     * Attempts to place focus within a new window.
     */
    private boolean setFocusFromWindow() {
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
                return true;
            }

            // Next, see if the system has placed input focus.
            inputFocused = compatRoot.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);

            if ((inputFocused != null)
                    && AccessibilityNodeInfoUtils.shouldFocusNode(mService, inputFocused)
                    && tryFocusing(inputFocused)) {
                return true;
            }

            // Finally, just try to focus the first focusable item.
            firstFocus = findFocusFromNode(compatRoot, NodeFocusFinder.SEARCH_FORWARD);

            if ((firstFocus != null) && tryFocusing(firstFocus)) {
                return true;
            }

            // Last-ditch effort, try focusing the root.
            if (tryFocusing(compatRoot)) {
                LogUtils.log(Log.WARN, "Failed to place focus from new window");
                return true;
            }

            LogUtils.log(Log.ERROR, "Failed to place focus on root from new window");

            return false;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(compatRoot, focused, inputFocused, firstFocus);
        }
    }

    /**
     * Attempts to place focus on an accessibility-focusable node, starting from
     * the {@code event}'s source node.
     */
    private void setFocusFromHover(AccessibilityRecordCompat event) {
        AccessibilityNodeInfoCompat touched = null;
        AccessibilityNodeInfoCompat focusable = null;

        try {
            touched = event.getSource();
            if (touched == null) {
                return;
            }

            focusable = AccessibilityNodeInfoUtils.findFocusFromHover(mService, touched);
            if (focusable == null) {
                return;
            }

            focusable.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(touched, focusable);
        }
    }

    private boolean followScrollEvent(
            AccessibilityRecordCompat event, int lastFromIndex, int lastAction) {
        final AccessibilityNodeInfo root = mService.getRootInActiveWindow();

        if (root == null) {
            return false;
        }

        AccessibilityNodeInfoCompat compatRoot = null;
        AccessibilityNodeInfoCompat focused = null;
        AccessibilityNodeInfoCompat source = null;

        try {
            // First, see if we've already placed accessibility focus.
            compatRoot = new AccessibilityNodeInfoCompat(root);
            focused = compatRoot.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);

            if (focused != null && focused.isVisibleToUser()) {
                return true;
            }

            // Next, try to place focus on a child within the scrollable node. If
            // there are no child nodes, this will fail; however, scrolling should
            // only clear focus if there are child nodes.
            source = event.getSource();

            if (source == null) {
                LogUtils.log(this, Log.ERROR, "Failed to obtain source from scroll event");
                return false;
            }

            // Try to determine which direction we're scrolling.
            final boolean wasMovingForward;

            if ((lastAction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    || (event.getFromIndex() > lastFromIndex)) {
                wasMovingForward = true;
            } else {
                wasMovingForward = false;
            }

            // Try focusing the appropriate child node.
            if (tryFocusingChild(source, wasMovingForward)) {
                return true;
            }

            // Finally, try focusing the scrollable node itself.
            if (tryFocusing(source)) {
                return true;
            }

            return false;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(compatRoot, focused, source);
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
        final AccessibilityNodeInfoCompat child = findChildFromNode(parent, direction);

        if (child == null) {
            return false;
        }

        final boolean result = tryFocusing(child);
        child.recycle();
        return result;
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
        return source.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
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
                mLastAction = action;
                break;
        }
    }

    private final FollowFocusHandler mHandler = new FollowFocusHandler(this);

    private static class FollowFocusHandler extends WeakReferenceHandler<ProcessorFollowFocus> {
        private static final int FOCUS_AFTER_SCROLL = 1;

        /** Delay after a scroll event before checking focus. */
        private static final long FOCUS_AFTER_SCROLL_DELAY = 250;

        private AccessibilityRecordCompat mCachedRecord;

        public FollowFocusHandler(ProcessorFollowFocus parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, ProcessorFollowFocus parent) {
            switch (msg.what) {
                case FOCUS_AFTER_SCROLL:
                    parent.followScrollEvent(mCachedRecord, msg.arg1, msg.arg2);

                    if (mCachedRecord != null) {
                        mCachedRecord.recycle();
                        mCachedRecord = null;
                    }
                    break;
            }
        }

        /**
         * Ensure that focus is placed after scroll actions, but use a delay to
         * avoid consuming too many resources.
         *
         * @param record The scroll event.
         * @param lastFromIndex The fromIndex of the most recent scroll event.
         * @param lastAction The most recent accessibility action.
         */
        public void followScrollDelayed(
                AccessibilityRecordCompat record, int lastFromIndex, int lastAction) {
            removeMessages(FOCUS_AFTER_SCROLL);

            if (mCachedRecord != null) {
                mCachedRecord.recycle();
                mCachedRecord = null;
            }

            mCachedRecord = AccessibilityRecordCompat.obtain(record);

            final Message msg = obtainMessage(FOCUS_AFTER_SCROLL, lastFromIndex, lastAction);
            sendMessageDelayed(msg, FOCUS_AFTER_SCROLL_DELAY);
        }
    }
}
