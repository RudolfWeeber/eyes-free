/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.eyesfree.utils;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Log;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Provides a series of utilities for interacting with AccessibilityNodeInfo
 * objects. NOTE: This class only recycles unused nodes that were collected
 * internally. Any node passed into or returned from a public method is retained
 * and TalkBack should recycle it when appropriate.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
public class AccessibilityNodeInfoUtils {
    /** Whether isVisibleToUser() is supported by the current SDK. */
    private static final boolean SUPPORTS_VISIBILITY = (Build.VERSION.SDK_INT >= 16);

    private AccessibilityNodeInfoUtils() {
        // This class is not instantiable.
    }

    /**
     * Gets the text of a <code>node</code> by returning the content description
     * (if available) or by returning the text.
     *
     * @param node The node.
     * @return The node text.
     */
    public static CharSequence getNodeText(AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return null;
        }

        // Prefer content description over text.
        // TODO: Why are we checking the trimmed length?
        final CharSequence contentDescription = node.getContentDescription();
        if (!TextUtils.isEmpty(contentDescription)
                && (TextUtils.getTrimmedLength(contentDescription) > 0)) {
            return contentDescription;
        }

        final CharSequence text = node.getText();
        if (!TextUtils.isEmpty(text)
                && (TextUtils.getTrimmedLength(text) > 0)) {
            return text;
        }

        return null;
    }

    /**
     * Returns the root node of the tree containing {@code node}.
     */
    public static AccessibilityNodeInfoCompat getRoot(AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return null;
        }

        AccessibilityNodeInfoCompat current = null;
        AccessibilityNodeInfoCompat parent = AccessibilityNodeInfoCompat.obtain(node);

        do {
            current = parent;
            parent = current.getParent();
        } while (parent != null);

        return current;
    }

    /**
     * Returns whether a node should receive focus from focus traversal or touch
     * exploration. One of the following must be true:
     * <ul>
     * <li>The node is actionable (see
     * {@link #isActionableForAccessibility(AccessibilityNodeInfoCompat)})</li>
     * <li>The node is a top-level list item (see
     * {@link #isTopLevelScrollItem(Context, AccessibilityNodeInfoCompat)})</li>
     * </ul>
     *
     * @param node
     * @return {@code true} of the node is accessibility focusable.
     */
    public static boolean isAccessibilityFocusable(
            Context context, AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        // Never focus invisible nodes.
        if (!isVisibleOrLegacy(node)) {
            return false;
        }

        // Always focus "actionable" nodes.
        if (isActionableForAccessibility(node)) {
            return true;
        }

        if ((Build.VERSION.SDK_INT < 16)) {
            // In pre-JellyBean, always focus ALL top-level list items and items
            // that should have independently focusable children.
            if (isTopLevelScrollItem(context, node)) {
                return true;
            }
        } else {
            // In post-JellyBean, only focus top-level list items with text.
            if (hasText(node) && isTopLevelScrollItem(context, node)) {
                return true;
            }
        }


        return false;
    }

    /**
     * Returns whether a node should receive accessibility focus from
     * navigation. This method should never be called recursively, since it
     * traverses up the parent hierarchy on every call.
     *
     * @see #findFocusFromHover(Context, AccessibilityNodeInfoCompat)
     */
    public static boolean shouldFocusNode(Context context, AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        if (!isVisibleOrLegacy(node)) {
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Don't focus, node is not visible");
            return false;
        }

        if (FILTER_ACCESSIBILITY_FOCUSABLE.accept(context, node)) {
            // TODO: This may still result in focusing non-speaking nodes, but it
            // won't prevent unlabeled buttons from receiving focus.
            if (node.getChildCount() <= 0) {
                LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                        "Focus, node is focusable and has no children");
                return true;
            } else if (isSpeakingNode(context, node)) {
                LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                        "Focus, node is focusable and has something to speak");
                return true;
            } else {
                LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                        "Don't focus, node is focusable but has nothing to speak");
                return false;
            }
        }

        // If this node has no focusable predecessors, but it still has text,
        // then it should receive focus from navigation and be read aloud.
        if (!hasMatchingPredecessor(context, node, FILTER_ACCESSIBILITY_FOCUSABLE)
                && hasText(node)) {
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Focus, node has text and no focusable predecessors");
            return true;
        }

        LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                "Don't focus, failed all focusability tests");
        return false;
    }

    /**
     * Returns the node that should receive focus from hover by starting from
     * the touched node and calling {@link #shouldFocusNode} at each level of
     * the view hierarchy.
     */
    public static AccessibilityNodeInfoCompat findFocusFromHover(
            Context context, AccessibilityNodeInfoCompat touched) {
        return AccessibilityNodeInfoUtils.getSelfOrMatchingPredecessor(
                context, touched, FILTER_SHOULD_FOCUS);
    }

    private static boolean isSpeakingNode(Context context, AccessibilityNodeInfoCompat node) {
        if (hasText(node)) {
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Speaking, has text");
            return true;
        }

        // Special case for check boxes.
        if (node.isCheckable()) {
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Speaking, is checkable");
            return true;
        }

        // Special case for web content.
        if (WebInterfaceUtils.hasWebContent(node)) {
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Speaking, has web content");
            return true;
        }

        // Special case for containers with non-focusable content.
        if (hasNonActionableSpeakingChildren(context, node)) {
            LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                    "Speaking, has non-actionable speaking children");
            return true;
        }

        return false;
    }

    private static boolean hasNonActionableSpeakingChildren(Context context, AccessibilityNodeInfoCompat node) {
        final int childCount = node.getChildCount();

        AccessibilityNodeInfoCompat child = null;

        // Has non-actionable, speaking children?
        for (int i = 0; i < childCount; i++) {
            try {
                child = node.getChild(i);

                if (child == null) {
                    LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                            "Child %d is null, skipping it", i);
                    continue;
                }

                // Ignore invisible nodes.
                if (!isVisibleOrLegacy(child)) {
                    LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                            "Child %d is invisible, skipping it", i);
                    continue;
                }

                // Ignore focusable nodes.
                if (FILTER_ACCESSIBILITY_FOCUSABLE.accept(context, child)) {
                    LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                            "Child %d is focusable, skipping it", i);
                    continue;
                }

                // Recursively check non-focusable child nodes.
                // TODO: Mutual recursion is probably not a good idea.
                if (isSpeakingNode(context, child)) {
                    LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                            "Does have actionable speaking children (child %d)", i);
                    return true;
                }
            } finally {
                AccessibilityNodeInfoUtils.recycleNodes(child);
            }
        }

        LogUtils.log(AccessibilityNodeInfoUtils.class, Log.VERBOSE,
                "Does not have non-actionable speaking children");
        return false;
    }

    /**
     * Returns whether a node is actionable. That is, the node supports one of
     * the following actions:
     * <ul>
     * <li>{@link AccessibilityNodeInfoCompat#isClickable()}
     * <li>{@link AccessibilityNodeInfoCompat#isFocusable()}
     * <li>{@link AccessibilityNodeInfoCompat#isLongClickable()}
     * </ul>
     * This parities the system method View#isActionableForAccessibility(), which
     * was added in JellyBean.
     *
     * @param node The node to examine.
     * @return {@code true} if node is actionable.
     */
    public static boolean isActionableForAccessibility(AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        if (node.isClickable() || node.isLongClickable() || node.isFocusable()) {
            return true;
        }

        if (supportsAnyAction(node, AccessibilityNodeInfoCompat.ACTION_CLICK,
                AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
                AccessibilityNodeInfoCompat.ACTION_FOCUS,
                AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT,
                AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT)) {
            return true;
        }

        return false;
    }

    /**
     * Check whether a given node has a scrollable predecessor.
     *
     * @param node The node to examine.
     * @return {@code true} if one of the node's predecessors is scrollable.
     */
    private static boolean hasMatchingPredecessor(
            Context context, AccessibilityNodeInfoCompat node, NodeFilter filter) {
        if (node == null) {
            return false;
        }

        final AccessibilityNodeInfoCompat result = getMatchingPredecessor(context, node, filter);

        if (result != null) {
            result.recycle();
            return true;
        }

        return false;
    }

    /**
     * Returns the {@code node} if it matches the {@code filter}, or the first
     * matching predecessor. Returns {@code null} if no nodes match.
     */
    public static AccessibilityNodeInfoCompat getSelfOrMatchingPredecessor(
            Context context, AccessibilityNodeInfoCompat node, NodeFilter filter) {
        if (node == null) {
            return null;
        }

        if (filter.accept(context, node)) {
            return AccessibilityNodeInfoCompat.obtain(node);
        }

        return getMatchingPredecessor(context, node, filter);
    }

    /**
     * Returns the first predecessor of {@code node} that matches the
     * {@code filter}. Returns {@code null} if no nodes match.
     */
    private static AccessibilityNodeInfoCompat getMatchingPredecessor(
            Context context, AccessibilityNodeInfoCompat node, NodeFilter filter) {
        if (node == null) {
            return null;
        }

        final HashSet<AccessibilityNodeInfoCompat> predecessors =
                new HashSet<AccessibilityNodeInfoCompat>();

        try {
            predecessors.add(AccessibilityNodeInfoCompat.obtain(node));
            node = node.getParent();

            while (node != null) {
                if (!predecessors.add(node)) {
                    // Already seen this node, so abort!
                    node.recycle();
                    return null;
                }

                if (filter.accept(context, node)) {
                    // Send a copy since node gets recycled.
                    return AccessibilityNodeInfoCompat.obtain(node);
                }

                node = node.getParent();
            }
        } finally {
            recycleNodes(predecessors);
        }

        return null;
    }

    /**
     * Check whether a given node is scrollable.
     *
     * @param node The node to examine.
     * @return {@code true} if the node is scrollable.
     */
    private static boolean isScrollable(AccessibilityNodeInfoCompat node) {
        if (node.isScrollable()) {
            return true;
        }

        return supportsAnyAction(node,
                AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
                AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
    }

    /**
     * Returns whether the specified node has text.
     *
     * @param node The node to check.
     * @return {@code true} if the node has text.
     */
    private static boolean hasText(AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        return (!TextUtils.isEmpty(node.getText())
                || !TextUtils.isEmpty(node.getContentDescription()));
    }

    /**
     * Determines whether a node is a top-level item in a scrollable container.
     *
     * @param node The node to test.
     * @return {@code true} if {@code node} is a top-level item in a scrollable
     *         container.
     */
    public static boolean isTopLevelScrollItem(Context context, AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        AccessibilityNodeInfoCompat parent = null;

        try {
            parent = node.getParent();
            if (parent == null) {
                // Not a child node of anything.
                return false;
            }

            if (isScrollable(node)) {
                return true;
            }

            // AdapterView, ScrollView, and HorizontalScrollView are focusable
            // containers, but Spinner is a special case.
            // TODO: Rename or break up this method, since it actually returns
            // whether the parent is scrollable OR is a focusable container that
            // should not block its children from receiving focus.
            if (nodeMatchesAnyClassByType(context, parent, android.widget.AdapterView.class,
                    android.widget.ScrollView.class, android.widget.HorizontalScrollView.class)
                    && !nodeMatchesAnyClassByType(context, parent, android.widget.Spinner.class)) {
                return true;
            }

            return false;
        } finally {
            recycleNodes(parent);
        }
    }

    /**
     * Determines if the current item is at the edge of a list by checking the
     * scrollable predecessors of the items on either side.
     *
     * @return true if the current item is at the edge of a list.
     */
    public static boolean isEdgeListItem(Context context, AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        if (isMatchingEdgeListItem(context, node, FILTER_SCROLL_BACKWARD,
                NodeFocusFinder.SEARCH_BACKWARD)) {
            return true;
        }

        if (isMatchingEdgeListItem(context, node, FILTER_SCROLL_FORWARD,
                NodeFocusFinder.SEARCH_FORWARD)) {
            return true;
        }

        return false;
    }

    /**
     * Utility method for determining if a searching past a particular node will
     * fall off the edge of a scrollable container.
     *
     * @param cursor
     * @param filter
     * @param direction
     * @return {@code true} if focusing search in the specified direction will
     *         fall off the edge of the container.
     */
    private static boolean isMatchingEdgeListItem(
            Context context, AccessibilityNodeInfoCompat cursor, NodeFilter filter, int direction) {
        AccessibilityNodeInfoCompat predecessor = null;
        AccessibilityNodeInfoCompat searched = null;
        AccessibilityNodeInfoCompat searchedPredecessor = null;

        try {
            predecessor = getMatchingPredecessor(null, cursor, filter);
            if (predecessor == null) {
                // Not contained in a scrollable list.
                return false;
            }

            // TODO: This happens elsewhere -- make into a single utility method.
            searched = NodeFocusFinder.focusSearch(cursor, direction);
            while ((searched != null)
                    && !AccessibilityNodeInfoUtils.shouldFocusNode(context, searched)) {
                final AccessibilityNodeInfoCompat temp = searched;
                searched = NodeFocusFinder.focusSearch(temp, direction);
                temp.recycle();
            }

            if ((searched == null) || searched.equals(predecessor)) {
                // Can't move from this position.
                return true;
            }

            searchedPredecessor = getMatchingPredecessor(null, searched, filter);
            if (!predecessor.equals(searchedPredecessor)) {
                // Moves outside of the scrollable list.
                return true;
            }
        } finally {
            recycleNodes(predecessor, searched, searchedPredecessor);
        }

        return false;
    }

    /**
     * Determines if the generating class of an
     * {@link AccessibilityNodeInfoCompat} matches a given {@link Class} by
     * type.
     *
     * @param node A sealed {@link AccessibilityNodeInfoCompat} dispatched by
     *            the accessibility framework.
     * @param referenceClass A {@link Class} to match by type or inherited type.
     * @return {@code true} if the {@link AccessibilityNodeInfoCompat} object
     *         matches the {@link Class} by type or inherited type,
     *         {@code false} otherwise.
     */
    public static boolean nodeMatchesClassByType(
            Context context, AccessibilityNodeInfoCompat node, Class<?> referenceClass) {
        if ((node == null) || (referenceClass == null)) {
            return false;
        }

        // Attempt to take a shortcut.
        final CharSequence nodeClassName = node.getClassName();
        if (TextUtils.equals(nodeClassName, referenceClass.getName())) {
            return true;
        }

        final ClassLoadingManager loader = ClassLoadingManager.getInstance();
        final CharSequence appPackage = node.getPackageName();
        return loader.checkInstanceOf(context, nodeClassName, appPackage, referenceClass);
    }

    /**
     * Determines if the generating class of an
     * {@link AccessibilityNodeInfoCompat} matches any of the given
     * {@link Class}es by type.
     *
     * @param node A sealed {@link AccessibilityNodeInfoCompat} dispatched by
     *            the accessibility framework.
     * @return {@code true} if the {@link AccessibilityNodeInfoCompat} object
     *         matches the {@link Class} by type or inherited type,
     *         {@code false} otherwise.
     * @param referenceClasses A variable-length list of {@link Class} objects
     *            to match by type or inherited type.
     */
    public static boolean nodeMatchesAnyClassByType(
            Context context, AccessibilityNodeInfoCompat node, Class<?>... referenceClasses) {
        for (Class<?> referenceClass : referenceClasses) {
            if (nodeMatchesClassByType(context, node, referenceClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if the class of an {@link AccessibilityNodeInfoCompat} matches
     * a given {@link Class} by package and name.
     *
     * @param node A sealed {@link AccessibilityNodeInfoCompat} dispatched by
     *            the accessibility framework.
     * @param className A class name to match.
     * @return {@code true} if the {@link AccessibilityNodeInfoCompat} matches
     *         the class name.
     */
    public static boolean nodeMatchesClassByName(
            AccessibilityNodeInfoCompat node, CharSequence className) {
        return TextUtils.equals(node.getClassName(), className);
    }

    /**
     * Recycles the given nodes.
     *
     * @param nodes The nodes to recycle.
     */
    public static void recycleNodes(Collection<AccessibilityNodeInfoCompat> nodes) {
        if (nodes == null) {
            return;
        }

        for (AccessibilityNodeInfoCompat node : nodes) {
            if (node != null) {
                node.recycle();
            }
        }

        nodes.clear();
    }

    /**
     * Recycles the given nodes.
     *
     * @param nodes The nodes to recycle.
     */
    public static void recycleNodes(AccessibilityNodeInfoCompat... nodes) {
        if (nodes == null) {
            return;
        }

        for (AccessibilityNodeInfoCompat node : nodes) {
            if (node != null) {
                node.recycle();
            }
        }
    }

    /**
     * Returns {@code true} if the node supports at least one of the specified
     * actions. To check whether a node supports multiple actions, combine them
     * using the {@code |} (logical OR) operator.
     *
     * @param node The node to check.
     * @param actions The actions to check.
     * @return {@code true} if at least one action is supported.
     */
    public static boolean supportsAnyAction(AccessibilityNodeInfoCompat node,
            int... actions) {
        if (node != null) {
            final int supportedActions = node.getActions();

            for (int action : actions) {
                if ((supportedActions & action) == action) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns the result of applying a filter using breadth-first traversal.
     *
     * @param context The activity context.
     * @param node The root node to traverse from.
     * @param filter The filter to satisfy.
     * @return The first node reached via BFS traversal that satisfies the
     *         filter.
     */
    public static AccessibilityNodeInfoCompat searchFromBfs(
            Context context, AccessibilityNodeInfoCompat node, NodeFilter filter) {
        if (node == null) {
            return null;
        }

        final LinkedList<AccessibilityNodeInfoCompat> queue =
                new LinkedList<AccessibilityNodeInfoCompat>();

        queue.add(AccessibilityNodeInfoCompat.obtain(node));

        while (!queue.isEmpty()) {
            final AccessibilityNodeInfoCompat item = queue.removeFirst();

            if (filter.accept(context, item)) {
                return AccessibilityNodeInfoCompat.obtain(item);
            }

            final int childCount = item.getChildCount();

            for (int i = 0; i < childCount; i++) {
                final AccessibilityNodeInfoCompat child = item.getChild(i);

                if (child == null) {
                    continue;
                }

                queue.addLast(child);
            }
        }

        return null;
    }

    /**
     * Returns a fresh copy of {@code node} with properties that are
     * less likely to be stale.  Returns {@code null} if the node can't be
     * found anymore.
     */
    public static AccessibilityNodeInfoCompat refreshNode(
        AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return null;
        }
        AccessibilityNodeInfoCompat result = refreshFromChild(node);
        if (result == null) {
            result = refreshFromParent(node);
        }
        return result;
    }

    private static AccessibilityNodeInfoCompat refreshFromChild(
            AccessibilityNodeInfoCompat node) {
        if (node.getChildCount() > 0) {
            AccessibilityNodeInfoCompat firstChild = node.getChild(0);
            if (firstChild != null) {
                AccessibilityNodeInfoCompat parent = firstChild.getParent();
                firstChild.recycle();
                if (node.equals(parent)) {
                    return parent;
                } else {
                    recycleNodes(parent);
                }
            }
        }
        return null;
    }

    private static AccessibilityNodeInfoCompat refreshFromParent(
            AccessibilityNodeInfoCompat node) {
        AccessibilityNodeInfoCompat parent = node.getParent();
        if (parent != null) {
            try {
                int childCount = parent.getChildCount();
                for (int i = 0; i < childCount; ++i) {
                    AccessibilityNodeInfoCompat child = parent.getChild(i);
                    if (node.equals(child)) {
                        return child;
                    }
                    recycleNodes(child);
                }
            } finally {
                parent.recycle();
            }
        }
        return null;
    }

    /**
     * Helper method that returns {@code true} if the specified node is visible
     * to the user or if the current SDK doesn't support checking visibility.
     */
    public static  boolean isVisibleOrLegacy(AccessibilityNodeInfoCompat node) {
        return (!AccessibilityNodeInfoUtils.SUPPORTS_VISIBILITY || node.isVisibleToUser());
    }

    /**
     * Filter for scrollable items. One of the following must be true:
     * <ul>
     * <li>{@link AccessibilityNodeInfoCompat#isScrollable()} returns
     * {@code true}</li>
     * <li>{@link AccessibilityNodeInfoCompat#getActions()} supports
     * {@link AccessibilityNodeInfoCompat#ACTION_SCROLL_FORWARD}</li>
     * <li>{@link AccessibilityNodeInfoCompat#getActions()} supports
     * {@link AccessibilityNodeInfoCompat#ACTION_SCROLL_BACKWARD}</li>
     * </ul>
     */
    public static final NodeFilter FILTER_SCROLLABLE = new NodeFilter() {
        @Override
        public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
            return isScrollable(node);
        }
    };

    private static final NodeFilter FILTER_ACCESSIBILITY_FOCUSABLE = new NodeFilter() {
        @Override
        public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
            return isAccessibilityFocusable(context, node);
        }
    };

    private static final NodeFilter FILTER_SHOULD_FOCUS = new NodeFilter() {
        @Override
        public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
            return shouldFocusNode(context, node);
        }
    };

    private static final NodeActionFilter FILTER_SCROLL_FORWARD = new NodeActionFilter(
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);

    private static final NodeActionFilter FILTER_SCROLL_BACKWARD = new NodeActionFilter(
            AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);

    /**
     * Convenience class for a {@link NodeFilter} that checks whether nodes
     * support a specific action or set of actions.
     */
    private static class NodeActionFilter implements NodeFilter {
        private final int mAction;

        public NodeActionFilter(int action) {
            mAction = action;
        }

        @Override
        public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
            return ((node.getActions() & mAction) == mAction);
        }
    }

    /**
     * Compares two AccessibilityNodeInfos in left-to-right and top-to-bottom
     * fashion.
     */
    public static class TopToBottomLeftToRightComparator implements
            Comparator<AccessibilityNodeInfoCompat> {
        private final Rect mFirstBounds = new Rect();
        private final Rect mSecondBounds = new Rect();

        private static final int BEFORE = -1;
        private static final int AFTER = 1;

        @Override
        public int compare(AccessibilityNodeInfoCompat first, AccessibilityNodeInfoCompat second) {
            final Rect firstBounds = mFirstBounds;
            first.getBoundsInScreen(firstBounds);

            final Rect secondBounds = mSecondBounds;
            second.getBoundsInScreen(secondBounds);

            // First is entirely above second.
            if (firstBounds.bottom <= secondBounds.top) {
                return BEFORE;
            }

            // First is entirely below second.
            if (firstBounds.top >= secondBounds.bottom) {
                return AFTER;
            }

            // Smaller left-bound.
            final int leftDifference = (firstBounds.left - secondBounds.left);
            if (leftDifference != 0) {
                return leftDifference;
            }

            // Smaller top-bound.
            final int topDifference = (firstBounds.top - secondBounds.top);
            if (topDifference != 0) {
                return topDifference;
            }

            // Smaller bottom-bound.
            final int bottomDifference = (firstBounds.bottom - secondBounds.bottom);
            if (bottomDifference != 0) {
                return bottomDifference;
            }

            // Smaller right-bound.
            final int rightDifference = (firstBounds.right - secondBounds.right);
            if (rightDifference != 0) {
                return rightDifference;
            }

            // Just break the tie somehow. The hash codes are unique
            // and stable, hence this is deterministic tie breaking.
            return first.hashCode() - second.hashCode();
        }
    }

    /**
     * Filters {@link AccessibilityNodeInfoCompat}s.
     */
    public static interface NodeFilter {
        /**
         * @param node The node info to filter.
         * @return {@code true} if the node is accepted
         */
        public boolean accept(Context context, AccessibilityNodeInfoCompat node);
    }
}
