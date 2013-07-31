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
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.android.marvin.talkback.TalkBackService.AccessibilityEventListener;
import com.googlecode.eyesfree.compat.CompatUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoRef;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.NodeFilter;
import com.googlecode.eyesfree.utils.NodeFocusFinder;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;
import com.googlecode.eyesfree.utils.WebInterfaceUtils;

import java.util.HashSet;

/**
 * Handles screen reader cursor management.
 *
 * @author alanv@google.com (Alan Viverette)
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class CursorController implements AccessibilityEventListener {
    /** The minimum API level supported by the cursor controller. */
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN;

    /** Represents navigation to next element. */
    private static final int NAVIGATION_DIRECTION_NEXT = 1;

    /** Represents navigation to previous element. */
    private static final int NAVIGATION_DIRECTION_PREVIOUS = -1;

    /**
     * Class for Samsung's TouchWiz implementation of AbsListView. May be
     * {@code null} on non-Samsung devices.
     */
    private static final Class<?> CLASS_TOUCHWIZ_TWABSLISTVIEW = CompatUtils.getClass(
            "com.sec.android.touchwiz.widget.TwAbsListView");

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
        mGranularityManager = new CursorGranularityManager(service);
    }

    public void setListener(CursorControllerListener listener) {
        mListener = listener;
    }

    /**
     * Releases all resources held by this controller and save any persistent
     * preferences.
     */
    public void shutdown() {
        mGranularityManager.shutdown();
    }

    /**
     * Clears and replaces focus for the currently focused node. If there is no
     * currently focused node, this method is a no-op.
     *
     * @return Whether the current node was refocused.
     */
    public boolean refocus() {
        final AccessibilityNodeInfoCompat node = getCursor();
        if (node == null) {
            return false;
        }

        clearCursor();
        final boolean result = setCursor(node);
        node.recycle();
        return result;
    }

    /**
     * Attempts to move to the next item using the current navigation mode.
     *
     * @param shouldWrap Whether navigating past the last item on the screen
     *            should wrap around to the first item on the screen.
     * @param shouldScroll Whether navigating past the last visible item in a
     *            scrollable container should automatically scroll to the next
     *            visible item.
     * @return {@code true} if successful.
     */
    public boolean next(boolean shouldWrap, boolean shouldScroll) {
        return navigateWithGranularity(NAVIGATION_DIRECTION_NEXT, shouldWrap, shouldScroll);
    }

    /**
     * Attempts to move to the previous item using the current navigation mode.
     *
     * @param shouldWrap Whether navigating past the last item on the screen
     *            should wrap around to the first item on the screen.
     * @param shouldScroll Whether navigating past the last visible item in a
     *            scrollable container should automatically scroll to the next
     *            visible item.
     * @return {@code true} if successful.
     */
    public boolean previous(boolean shouldWrap, boolean shouldScroll) {
        return navigateWithGranularity(NAVIGATION_DIRECTION_PREVIOUS, shouldWrap, shouldScroll);
    }

    /**
     * Attempts to jump to the first item that appears on the screen.
     *
     * @return {@code true} if successful.
     */
    public boolean jumpToTop() {
        clearCursor();
        mReachedEdge = true;
        return next(true /*shouldWrap*/, false /*shouldScroll*/);
    }

    /**
     * Attempts to jump to the last item that appears on the screen.
     *
     * @return {@code true} if successful.
     */
    public boolean jumpToBottom() {
        clearCursor();
        mReachedEdge = true;
        return previous(true /*shouldWrap*/, false /*shouldScroll*/);
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
     * Adjust the cursor's granularity by moving it directly to the specified
     * granularity. If the granularity is {@link CursorGranularity#DEFAULT},
     * unlocks navigation; otherwise, locks navigation to the current cursor.
     *
     * @param granularity The {@link CursorGranularity} to request.
     * @return {@code true} if the granularity change was successful,
     *         {@code false} otherwise.
     */
    public boolean setGranularity(CursorGranularity granularity, boolean fromUser) {
        AccessibilityNodeInfoCompat current = null;

        try {
            current = getCursor();
            if (current == null) {
                return false;
            }

            if (!mGranularityManager.setGranularityAt(current, granularity)) {
                return false;
            }

            if (mListener != null) {
                mListener.onGranularityChanged(granularity, fromUser);
            }

            // If navigating at cursor granularity, speak the current character
            // by quickly navigating next and previous.
            if (granularity == CursorGranularity.CHARACTER) {
                mGranularityManager.navigate(NAVIGATION_DIRECTION_NEXT);
                mGranularityManager.navigate(NAVIGATION_DIRECTION_PREVIOUS);
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(current);
        }

        return true;
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
     * Sets the current state of selection mode for navigation within text
     * content. When enabled, the manager will attempt to extend selection
     * during navigation. If the target node of selection mode is not locked to
     * a granularity, calling this method will switch to
     * {@link CursorGranularity#CHARACTER}
     *
     * @param node The node on which selection mode should be enabled.
     * @param active {@code true} to activate selection mode, {@code false} to
     *            deactivate.
     */
    public void setSelectionModeActive(AccessibilityNodeInfoCompat node, boolean active) {
        if (active && !mGranularityManager.isLockedTo(node)) {
            setGranularity(CursorGranularity.CHARACTER, false /* fromUser */);
        }

        mGranularityManager.setSelectionModeActive(active);
    }

    /**
     * @return {@code true} if selection mode is active, {@code false}
     *         otherwise.
     */
    public boolean isSelectionModeActive() {
        return mGranularityManager.isSelectionModeActive();
    }

    /**
     * Clears the current cursor position.
     */
    public void clearCursor() {
        final AccessibilityNodeInfo root = mService.getRootInActiveWindow();
        if (root == null) {
            return;
        }

        final AccessibilityNodeInfo focused = root.findFocus(
                AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (focused == null) {
            return;
        }

        focused.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
    }

    /**
     * Returns the node in the active window that has accessibility focus. If no
     * node has focus, or if the focused node is invisible, returns the root
     * node.
     * <p>
     * The client is responsible for recycling the resulting node.
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
        if (!AccessibilityNodeInfoUtils.isVisibleOrLegacy(focusedNode)) {
            focusedNode.recycle();
            return compatRoot;
        }

        return focusedNode;
    }

    /**
     * Return the current granularity at the specified node, or
     * {@link CursorGranularity#DEFAULT} if none is set. Always returns
     * {@link CursorGranularity#DEFAULT} if granular navigation is not locked to
     * the specified node.
     *
     * @param node The node to check.
     * @return A cursor granularity.
     */
    public CursorGranularity getGranularityAt(AccessibilityNodeInfoCompat node) {
        if (mGranularityManager.isLockedTo(node)) {
            return mGranularityManager.getRequestedGranularity();
        }

        return CursorGranularity.DEFAULT;
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

            scrollableNode = getBestScrollableNode(cursor);
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

    private AccessibilityNodeInfoCompat getBestScrollableNode(
            AccessibilityNodeInfoCompat cursor) {
        final AccessibilityNodeInfoCompat predecessor =
                AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(mService, cursor,
                        AccessibilityNodeInfoUtils.FILTER_SCROLLABLE);

        if (predecessor != null) {
            return predecessor;
        }

        // If there isn't a scrollable predecessor, we'll have to search the
        // screen for the "best" scrollable node. Currently, "best" means:
        //   1. Matches FILTER_SCROLLABLE
        //   2. Appears first during BFS from the root node

        final AccessibilityNodeInfoCompat root = AccessibilityNodeInfoUtils.getRoot(cursor);
        final AccessibilityNodeInfoCompat searched = AccessibilityNodeInfoUtils.searchFromBfs(
                mService, root, AccessibilityNodeInfoUtils.FILTER_SCROLLABLE);

        if (searched != null) {
            return searched;
        }

        return null;
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
        AccessibilityNodeInfoCompat currentNode = null;

        try {
            currentNode = getCursor();
            if (currentNode == null) {
                return false;
            }

            final boolean wasAdjusted = mGranularityManager.adjustGranularityAt(
                    currentNode, direction);
            if (wasAdjusted && (mListener != null)) {
                mListener.onGranularityChanged(mGranularityManager.getRequestedGranularity(), true);
            }

            return wasAdjusted;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(currentNode);
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
     * {@link android.view.View#focusSearch(int)}.
     * </p>
     *
     * @param direction The direction to move.
     * @param shouldWrap Whether navigating past the last item on the screen
     *            should wrap around to the first item on the screen.
     * @param shouldScroll Whether navigating past the last visible item in a
     *            scrollable container should automatically scroll to the next
     *            visible item.
     * @return true on success, false on failure.
     */
    private boolean navigateWithGranularity(
            int direction, boolean shouldWrap, boolean shouldScroll) {
        final int navigationAction;
        final int scrollDirection;
        final int focusSearchDirection;
        final int edgeDirection;

        // Map the navigation action to various directions.
        if (direction == NAVIGATION_DIRECTION_NEXT) {
            navigationAction = AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY;
            scrollDirection = AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD;
            focusSearchDirection = NodeFocusFinder.SEARCH_FORWARD;
            edgeDirection = 1;
        } else {
            navigationAction = AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
            scrollDirection = AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD;
            focusSearchDirection = NodeFocusFinder.SEARCH_BACKWARD;
            edgeDirection = -1;
        }

        AccessibilityNodeInfoCompat current = null;
        AccessibilityNodeInfoCompat target = null;

        try {
            current = getCursor();
            if (current == null) {
                return false;
            }

            // If granularity is set to anything other than default, restrict
            // navigation to the current node.
            if (mGranularityManager.isLockedTo(current)) {
                final int result = mGranularityManager.navigate(navigationAction);
                return (result == CursorGranularityManager.SUCCESS);
            }

            // If the current node has web content, attempt HTML navigation.
            if (WebInterfaceUtils.hasWebContent(current)
                    && attemptHtmlNavigation(current, direction)) {
                return true;
            }

            // If the user has disabled automatic scrolling, don't attempt to scroll.
            // TODO(caseyburkhardt): Remove once auto-scroll is settled.
            if (shouldScroll) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                        mService);
                shouldScroll = SharedPreferencesUtils.getBooleanPref(prefs,
                        mService.getResources(), R.string.pref_auto_scroll_key,
                        R.bool.pref_auto_scroll_default);
            }

            // If the current item is at the edge of a scrollable view, try to
            // automatically scroll the view in the direction of navigation.
            if (shouldScroll && AccessibilityNodeInfoUtils.isEdgeListItem(
                    mService, current, edgeDirection, FILTER_AUTO_SCROLL)
                    && attemptScrollAction(scrollDirection)) {
                return true;
            }

            // Otherwise, move focus to next or previous focusable node.
            target = navigateFrom(current, focusSearchDirection);
            if ((target != null) && setCursor(target)) {
                mReachedEdge = false;
                return true;
            }

            if (mReachedEdge && shouldWrap) {
                mReachedEdge = false;
                return navigateWrapAround(focusSearchDirection);
            }

            // TODO(alanv): We need to reset the "reached edge" flag after the user
            // touch explores to a new node.
            mReachedEdge = true;
            return false;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(current, target);
        }
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
                    LogUtils.log(this, Log.ERROR, "Found duplicate during traversal: %s",
                            next.getInfo());
                    // TODO(alanv): Should we return null here or just stop traversing?
                    break;
                }

                LogUtils.log(
                        this, Log.VERBOSE, "Search strategy rejected node: %s", next.getInfo());

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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            final AccessibilityNodeInfo node = event.getSource();
            if (node == null) {
                LogUtils.log(
                        this, Log.WARN, "TYPE_VIEW_ACCESSIBILITY_FOCUSED event without a source.");
                return;
            }

            // When a new view gets focus, clear the state of the granularity
            // manager if this event came from a different node than the locked
            // node but from the same window.
            final AccessibilityNodeInfoCompat nodeCompat = new AccessibilityNodeInfoCompat(node);
            mGranularityManager.onNodeFocused(nodeCompat);
            nodeCompat.recycle();
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

    /**
     * Filter that defines which types of views should be auto-scrolled.
     * Generally speaking, only accepts views that are capable of showing
     * partially-visible data.
     * <p>
     * Accepts the following classes (and sub-classes thereof):
     * <ul>
     * <li>{@link android.widget.AbsListView} (and Samsung's TwAbsListView)
     * <li>{@link android.widget.AbsSpinner}
     * <li>{@link android.widget.ScrollView}
     * <li>{@link android.widget.HorizontalScrollView}
     * </ul>
     * <p>
     * Specifically excludes {@link android.widget.AdapterViewAnimator} and
     * sub-classes, since they represent overlapping views. Also excludes
     * {@link android.support.v4.view.ViewPager} since it exclusively represents
     * off-screen views.
     */
    private static final NodeFilter FILTER_AUTO_SCROLL = new NodeFilter() {
        @Override
        public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
            return AccessibilityNodeInfoUtils.nodeMatchesAnyClassByType(context, node,
                    android.widget.AbsListView.class, android.widget.AbsSpinner.class,
                    android.widget.ScrollView.class, android.widget.HorizontalScrollView.class,
                    CLASS_TOUCHWIZ_TWABSLISTVIEW);
        }
    };

    interface CursorControllerListener {
        public void onGranularityChanged(CursorGranularity granularity, boolean fromUser);

        public void onActionPerformed(int action);
    }
}
