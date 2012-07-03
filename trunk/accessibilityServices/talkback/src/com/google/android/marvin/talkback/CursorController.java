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

package com.google.android.marvin.talkback;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.android.marvin.talkback.CursorGranularityManager.CursorGranularity;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoRef;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.NodeFocusFinder;

import java.util.HashSet;

/**
 * Handles screen reader cursor management.
 *
 * @author alanv@google.com (Alan Viverette)
 */
@TargetApi(16)
class CursorController {
    /** The minimum API level supported by the cursor controller. */
    public static final int MIN_API_LEVEL = 16;

    /** Represents navigation to next element. */
    private static final int NAVIGATION_DIRECTION_NEXT = 1;

    /** Represents navigation to previous element. */
    private static final int NAVIGATION_DIRECTION_PREVIOUS = -1;

    /** Set of "seen" nodes used for eliminating duplicates during navigation. */
    private final HashSet<AccessibilityNodeInfoCompat> mNavigateSeenNodes =
            new HashSet<AccessibilityNodeInfoCompat>();

    /** The host service. Used to access the root node. */
    private final AccessibilityService mService;

    /** Callback for responding to granularity change events. */
    private CursorControllerListener mListener;

    /** Handles traversal using granularity. */
    private CursorGranularityManager mGranularityManager;

    /** Whether the user hit an edge with the last swipe. */
    private boolean mReachedEdge;

    /**
     * Creates a new cursor controller using the specified input controller.
     *
     * @param service The accessibility service. Used to obtain the current root
     *            node.
     */
    public CursorController(AccessibilityService service) {
        mService = service;
        mGranularityManager = new CursorGranularityManager();
    }

    public void setListener(CursorControllerListener listener) {
        mListener = listener;
    }

    /**
     * Releases all resources held by this controller and save any persistent
     * preferences.
     */
    public void shutdown() {
        mListener = null;
        mGranularityManager.shutdown();
    }

    /**
     * Attempts to move to the next item using the current navigation mode.
     *
     * @return {@code true} if successful.
     */
    public boolean next() {
        return navigateWithGranularity(NAVIGATION_DIRECTION_NEXT);
    }

    /**
     * Attempts to move to the previous item using the current navigation mode.
     *
     * @return {@code true} if successful.
     */
    public boolean previous() {
        return navigateWithGranularity(NAVIGATION_DIRECTION_PREVIOUS);
    }

    /**
     * Attempts to scroll forward within the current cursor.
     *
     * @return {@code true} if successful.
     */
    public boolean more() {
        return attemptScrollAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
    }

    /**
     * Attempts to scroll backward within the current cursor.
     *
     * @return {@code true} if successful.
     */
    public boolean less() {
        return attemptScrollAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
    }

    /**
     * Attempts to click on the center of the current cursor.
     *
     * @return {@code true} if successful.
     */
    public boolean clickCurrent() {
        return performAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
    }

    /**
     * Sends a long press event to the center of the current cursor.
     *
     * @return {@code true} if successful.
     */
    public boolean longPressCurrent() {
        return performAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
    }

    /**
     * Attempts to move to the next reading level.
     *
     * @return {@code true} if successful.
     */
    public boolean nextGranularity() {
        return adjustGranularity(1);
    }

    /**
     * Attempts to move to the previous reading level.
     *
     * @return {@code true} if successful.
     */
    public boolean previousGranularity() {
        return adjustGranularity(-1);
    }

    /**
     * Sets the current cursor position.
     *
     * @param node The node to set as the cursor.
     * @return {@code true} if successful.
     */
    public boolean setCursor(AccessibilityNodeInfoCompat node) {
        return node.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
    }

    /**
     * Returns the node in the active window that has accessibility focus. If no
     * node has focus, or if the focused node is invisible, returns the root
     * node.
     *
     * @return The node in the active window that has accessibility focus.
     */
    public AccessibilityNodeInfoCompat getCursor() {
        final AccessibilityNodeInfo activeRoot = mService.getRootInActiveWindow();
        if (activeRoot == null) {
            return null;
        }

        final AccessibilityNodeInfoCompat compatRoot = new AccessibilityNodeInfoCompat(activeRoot);
        final AccessibilityNodeInfoCompat focusedNode = compatRoot.findFocus(
                AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY);

        // TODO: If there's no focused node, we should either mimic following
        // focus from new window or try to be smart for things like list views.
        if (focusedNode == null) {
            return compatRoot;
        }

        // TODO: We should treat non-focusable nodes (e.g. invisible or fail the
        // heuristic we're using elsewhere) then we should try to find a
        // focusable node.
        if (!focusedNode.isVisibleToUser()) {
            focusedNode.recycle();
            return compatRoot;
        }

        return focusedNode;
    }

    /**
     * Attempts to scroll using the specified action.
     *
     * @param action The scroll action to perform.
     * @return Whether the action was performed.
     */
    private boolean attemptScrollAction(int action) {
        AccessibilityNodeInfoCompat cursor = null;
        AccessibilityNodeInfoCompat scrollableNode = null;

        try {
            cursor = getCursor();
            if (cursor == null) {
                return false;
            }

            scrollableNode = AccessibilityNodeInfoUtils.getSelfOrMatchingPredecessor(
                    mService, cursor, AccessibilityNodeInfoUtils.FILTER_SCROLLABLE);
            if (scrollableNode == null) {
                return false;
            }

            final boolean performedAction = scrollableNode.performAction(action);
            if (performedAction && (mListener != null)) {
                mListener.onActionPerformed(action);
            }

            return performedAction;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(cursor, scrollableNode);
        }
    }

    /**
     * Attempts to adjust granularity in the direction indicated.
     *
     * @param direction The direction to adjust granularity. One of
     *            {@link CursorGranularityManager#CHANGE_GRANULARITY_HIGHER} or
     *            {@link CursorGranularityManager#CHANGE_GRANULARITY_LOWER}
     * @return true on success, false if no nodes in the current hierarchy
     *         support a granularity other than the default.
     */
    private boolean adjustGranularity(int direction) {
        AccessibilityNodeInfoCompat current = null;

        try {
            current = getCursor();
            if (current == null) {
                return false;
            }

            final boolean wasAdjusted = mGranularityManager.adjustWithin(current, direction);
            if (wasAdjusted && (mListener != null)) {
                mListener.onGranularityChanged(mGranularityManager.getRequestedGranularity(), true);
            }

            return wasAdjusted;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(current);
        }
    }

    /**
     * Attempts to move in the direction indicated.
     * <p>
     * If a navigation granularity other than DEFAULT has been applied, attempts
     * to move within the current object at the specified granularity.
     * </p>
     * <p>
     * If no granularity has been applied, or if the DEFAULT granularity has
     * been applied, attempts to move in the specified direction using
     * {@link View#focusSearch(int)}.
     * </p>
     *
     * @param direction The direction to move.
     * @return true on success, false on failure.
     */
    private boolean navigateWithGranularity(int direction) {
        AccessibilityNodeInfoCompat current = null;

        try {
            current = getCursor();
            if (current == null) {
                return false;
            }

            // Are we currently reading at a granularity?
            if (mGranularityManager.isLockedToNode(current)) {
                final int navigationAction = (direction == NAVIGATION_DIRECTION_NEXT) ?
                        AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
                        : AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;

                final int result = mGranularityManager.navigateWithin(current, navigationAction);

                switch (result) {
                    case CursorGranularityManager.SUCCESS:
                        return true;
                    case CursorGranularityManager.HIT_EDGE:
                        // Returning false constrains the user to the
                        // contents of the current reading group.
                        return false;
                }
            }

            // Are we currently navigating web content?
            if (AccessibilityNodeInfoUtils.supportsAnyAction(current,
                    AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT,
                    AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT)
                    && attemptHtmlNavigation(current, direction)) {
                return true;
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(current);
        }

        // Navigation not supported in the current granularity. Move focus to
        // next or previous view instead.
        final int focusSearchDirection = (direction == NAVIGATION_DIRECTION_NEXT) ?
                NodeFocusFinder.SEARCH_FORWARD : NodeFocusFinder.SEARCH_BACKWARD;
        return navigateWithEdges(focusSearchDirection);
    }

    private boolean navigateWithEdges(int direction) {
        final boolean success = navigate(direction);

        // TODO: We need to reset the "reached edge" flag after the user touch
        // explores to a new node.
        if (success) {
            mReachedEdge = false;
        } else if (mReachedEdge) {
            mReachedEdge = false;
            return navigateWrapAround(direction);
        } else {
            mReachedEdge = true;
        }

        return success;
    }

    private boolean navigateWrapAround(int direction) {
        final AccessibilityNodeInfo root = mService.getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        AccessibilityNodeInfoCompat compatRoot = null;
        AccessibilityNodeInfoCompat tempNode = null;
        AccessibilityNodeInfoCompat wrapNode = null;

        try {
            compatRoot = new AccessibilityNodeInfoCompat(root);

            switch (direction) {
                case NodeFocusFinder.SEARCH_FORWARD:
                    wrapNode = navigateSelfOrFrom(compatRoot, direction);
                    break;
                case NodeFocusFinder.SEARCH_BACKWARD:
                    tempNode = getLastNodeFrom(compatRoot);
                    wrapNode = navigateSelfOrFrom(tempNode, direction);
                    break;
            }

            if (wrapNode == null) {
                LogUtils.log(this, Log.ERROR, "Failed to wrap navigation");
                return false;
            }

            return setCursor(wrapNode);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(compatRoot, tempNode, wrapNode);
        }
    }

    private static AccessibilityNodeInfoCompat getLastNodeFrom(AccessibilityNodeInfoCompat root) {
        final AccessibilityNodeInfoRef ref = AccessibilityNodeInfoRef.obtain(root);
        ref.lastDescendant();
        return ref.release();
    }

    /**
     * Attempts to move A11y focus in the specified direction.
     *
     * @param direction One of:
     *            <ul>
     *            <li>{@link NodeFocusFinder#SEARCH_FORWARD}</li>
     *            <li>{@link NodeFocusFinder#SEARCH_BACKWARD}</li>
     *            </ul>
     * @return {@code true} if successful.
     */
    private synchronized boolean navigate(int direction) {
        AccessibilityNodeInfoCompat current = null;
        AccessibilityNodeInfoCompat next = null;

        try {
            current = getCursor();
            if (current == null) {
                return false;
            }

            next = navigateFrom(current, direction);
            if (next == null) {
                return false;
            }

            return setCursor(next);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(current, next);
        }
    }

    private AccessibilityNodeInfoCompat navigateSelfOrFrom(
            AccessibilityNodeInfoCompat node, int direction) {
        if (node == null) {
            return null;
        }

        if (AccessibilityNodeInfoUtils.shouldFocusNode(mService, node)) {
            return AccessibilityNodeInfoCompat.obtain(node);
        }

        return navigateFrom(node, direction);
    }

    private AccessibilityNodeInfoCompat navigateFrom(
            AccessibilityNodeInfoCompat node, int direction) {
        if (node == null) {
            return null;
        }

        AccessibilityNodeInfoCompat next = null;

        try {
            // Be cautious and always clear the list of seen nodes.
            AccessibilityNodeInfoUtils.recycleNodes(mNavigateSeenNodes);

            next = NodeFocusFinder.focusSearch(node, direction);

            while ((next != null) && !AccessibilityNodeInfoUtils.shouldFocusNode(mService, next)) {
                if (mNavigateSeenNodes.contains(next)) {
                    LogUtils.log(this, Log.ERROR, "Found duplicate during traversal: %s", next);
                    // TODO: Should we return null here or just stop traversing?
                    break;
                }

                LogUtils.log(this, Log.VERBOSE, "Search strategy rejected node: %s", next);

                mNavigateSeenNodes.add(next);

                next = NodeFocusFinder.focusSearch(next, direction);
            }

            return next;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(mNavigateSeenNodes);
        }
    }

    /**
     * Performs the specified action on the current cursor.
     *
     * @param action The action to perform on the current cursor.
     * @return {@code true} if successful.
     */
    private boolean performAction(int action) {
        AccessibilityNodeInfoCompat current = null;

        try {
            current = getCursor();
            if (current == null) {
                return false;
            }

            return current.performAction(action);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(current);
        }
    }

    /**
     * Attempts to navigate the node using HTML navigation.
     *
     * @param node
     * @param direction The direction to navigate, one of:
     *            <ul>
     *            <li>{@link #NAVIGATION_DIRECTION_NEXT}</li>
     *            <li>{@link #NAVIGATION_DIRECTION_PREVIOUS}</li>
     *            </ul>
     * @return {@code true} if navigation succeeded.
     */
    private static boolean attemptHtmlNavigation(AccessibilityNodeInfoCompat node, int direction) {
        final int action = (direction == NAVIGATION_DIRECTION_NEXT) ?
                AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT
                : AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT;
        return node.performAction(action);
    }

    interface CursorControllerListener {
        public void onGranularityChanged(CursorGranularity granularity, boolean fromUser);

        public void onActionPerformed(int action);
    }
}
