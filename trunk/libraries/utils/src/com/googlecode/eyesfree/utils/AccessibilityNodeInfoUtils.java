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
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides a series of utilities for interacting with AccessibilityNodeInfo
 * objects. NOTE: This class only recycles unused nodes that were collected
 * internally. Any node passed into or returned from a public method is retained
 * and TalkBack should recycle it when appropriate.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
public class AccessibilityNodeInfoUtils {
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
     * Returns {@code true} if the specified node is a view group or has
     * children.
     *
     * @param node The node to test.
     * @return {@code true} if the specified node is a view group or has
     *         children.
     */
    public static boolean isViewGroup(Context context, AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        if (node.getChildCount() > 0) {
            // This is an implicit group.
            return true;
        }

        if (nodeMatchesClassByType(context, node, android.view.ViewGroup.class)) {
            // This is an explicit group.
            return true;
        }

        return false;
    }

    /**
     * Returns whether a node should receive focus from focus traversal or touch
     * exploration. One of the following must be true:
     * <ul>
     * <li>The node is actionable (see
     * {@link #isActionableForAccessibility(AccessibilityNodeInfoCompat)})</li>
     * <li>The node is a top-level list item (see
     * {@link #isTopLevelListItem(Context, AccessibilityNodeInfoCompat)})</li>
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
        if (!node.isVisibleToUser()) {
            return false;
        }

        // Always focus "actionable" nodes.
        if (isActionableForAccessibility(node)) {
            return true;
        }

        // Always focus top-level list items with text.
        if (hasText(node) && isTopLevelListItem(context, node)) {
            return true;
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

        if (FILTER_ACCESSIBILITY_FOCUSABLE.accept(context, node)) {
            return isSpeakingNode(context, node);
        }

        // If this node has no focusable predecessors, but it still has text,
        // then it should receive focus from navigation and be read aloud.
        if (!hasMatchingPredecessor(context, node, FILTER_ACCESSIBILITY_FOCUSABLE)
                && hasText(node)) {
            return true;
        }

        return false;
    }

    /**
     * Returns the node that should receive focus from hover.
     */
    public static AccessibilityNodeInfoCompat findFocusFromHover(
            Context context, AccessibilityNodeInfoCompat touched) {
        if (touched == null) {
            return null;
        }

        final AccessibilityNodeInfoCompat predecessor = AccessibilityNodeInfoUtils
                .getSelfOrMatchingPredecessor(context, touched, FILTER_ACCESSIBILITY_FOCUSABLE);

        if ((predecessor != null) && isSpeakingNode(context, predecessor)) {
            return predecessor;
        }

        // If the touched node has no actionable predecessors, but it still has
        // text, then it should receive focus from hover and be read aloud.
        if (hasText(touched)) {
            return AccessibilityNodeInfoCompat.obtain(touched);
        }

        return null;
    }

    private static boolean isSpeakingNode(Context context, AccessibilityNodeInfoCompat node) {
        final int childCount = node.getChildCount();

        // TODO: This may still result in focusing non-speaking nodes, but it
        // won't prevent unlabeled buttons from receiving focus.
        if (childCount <= 0) {
            return true;
        }

        if (hasText(node)) {
            return true;
        }

        // Special case for web content.
        if (supportsAnyAction(node,
                AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT,
                AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT)) {
            return true;
        }

        // Special case for containers with non-focusable content.
        if (hasNonActionableSpeakingChildren(context, node)) {
            return true;
        }

        return false;
    }

    private static boolean hasNonActionableSpeakingChildren(Context context, AccessibilityNodeInfoCompat node) {
        final int childCount = node.getChildCount();

        if (childCount == 0) {
            return false;
        }

        AccessibilityNodeInfoCompat child = null;

        // Has non-actionable, speaking children?
        for (int i = 0; i < childCount; i++) {
            try {
                child = node.getChild(i);

                if (child == null) {
                    continue;
                }

                // Ignore invisible nodes.
                if (!child.isVisibleToUser()) {
                    continue;
                }

                // Ignore focusable nodes.
                if (FILTER_ACCESSIBILITY_FOCUSABLE.accept(context, child)) {
                    continue;
                }

                // Recursively check non-focusable child nodes.
                // TODO: Mutual recursion is probably not a good idea.
                if (isSpeakingNode(context, child)) {
                    return true;
                }
            } finally {
                AccessibilityNodeInfoUtils.recycleNodes(child);
            }
        }

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
     * Check whether a given node is scrollable or has a scrollable predecessor.
     *
     * @param node The node to examine.
     * @return {@code true} if the node or one of its predecessors is
     *         scrollable.
     */
    public static boolean isScrollableOrHasScrollablePredecessor(
            Context context, AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        return (FILTER_SCROLLABLE.accept(context, node)
                || hasMatchingPredecessor(context, node, FILTER_SCROLLABLE));
    }

    /**
     * Check whether a given node has a scrollable predecessor.
     *
     * @param node The node to examine.
     * @return {@code true} if one of the node's predecessors is scrollable.
     */
    public static boolean hasMatchingPredecessor(
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
        if (filter.accept(context, node)) {
            return AccessibilityNodeInfoCompat.obtain(node);
        }

        return getMatchingPredecessor(context, node, filter);
    }

    /**
     * Returns the first predecessor of {@code node} that matches the
     * {@code filter}. Returns {@code null} if no nodes match.
     */
    public static AccessibilityNodeInfoCompat getMatchingPredecessor(
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
    public static boolean isScrollable(AccessibilityNodeInfoCompat node) {
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
    public static boolean hasText(AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        return (!TextUtils.isEmpty(node.getText())
                || !TextUtils.isEmpty(node.getContentDescription()));
    }

    /**
     * Determines whether a node is a top-level list item.
     *
     * @param node The node to test.
     * @return {@code true} if {@code node} is a top-level list item.
     */
    public static boolean isTopLevelListItem(Context context, AccessibilityNodeInfoCompat node) {
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

            return nodeMatchesClassByType(context, parent, android.widget.AbsListView.class);
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

        if (isMatchingEdgeListItem(context, node, mScrollBackwardFilter,
                NodeFocusFinder.SEARCH_BACKWARD)) {
            return true;
        }

        if (isMatchingEdgeListItem(context, node, mScrollForwardFilter,
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
     * @param clazz A {@link Class} to match by type or inherited type.
     * @return {@code true} if the {@link AccessibilityNodeInfoCompat} object
     *         matches the {@link Class} by type or inherited type,
     *         {@code false} otherwise.
     */
    public static boolean nodeMatchesClassByType(Context context, AccessibilityNodeInfoCompat node,
            Class<?> clazz) {
        if (node == null || clazz == null) {
            return false;
        }

        final ClassLoadingManager classLoader = ClassLoadingManager.getInstance();
        final CharSequence className = node.getClassName();
        final CharSequence packageName = node.getPackageName();
        final Class<?> nodeClass =
                classLoader.loadOrGetCachedClass(context, className, packageName);

        if (nodeClass == null) {
            return false;
        }

        return clazz.isAssignableFrom(nodeClass);
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
     * @param classes A variable-length list of {@link Class} objects to match
     *            by type or inherited type.
     */
    public static boolean nodeMatchesAnyClassByType(Context context,
            AccessibilityNodeInfoCompat node,
            Class<?>... classes) {
        for (Class<?> clazz : classes) {
            if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                    context, node, clazz)) {
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
     * Gets all the parent {@link AccessibilityNodeInfoCompat} nodes, up to the
     * view root.
     *
     * @param node The {@link AccessibilityNodeInfoCompat} child from which to
     *            begin collecting parents.
     * @return An unordered set of predecessor nodes.
     */
    public static Set<AccessibilityNodeInfoCompat> getPredecessors(
            AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return Collections.emptySet();
        }

        final Set<AccessibilityNodeInfoCompat> predecessors =
                new HashSet<AccessibilityNodeInfoCompat>();

        node = node.getParent();

        while (node != null) {
            if (!predecessors.add(node)) {
                // This should never happen, but if there's a serious cache
                // error or a malicious developer then there may be a loop.
                LogUtils.log(Log.ASSERT, "Found duplicate node while computing predecessors");
                break;
            }

            node = node.getParent();
        }

        return predecessors;
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
     * Adds all descendant nodes of the given
     * {@link AccessibilityNodeInfoCompat} in breadth first order.
     *
     * @param root {@link AccessibilityNodeInfoCompat} for which to add
     *            descendants.
     * @param outDescendants The list to which to add descendants.
     * @param comparator Optional comparator for sorting children.
     * @param filter Optional filter for selecting sub-set of nodes.
     * @return The number of nodes that failed to match the filter.
     */
    public static int addDescendantsBfs(Context context, AccessibilityNodeInfoCompat root,
            ArrayList<AccessibilityNodeInfoCompat> outDescendants,
            Comparator<AccessibilityNodeInfoCompat> comparator, NodeFilter filter) {
        final int oldOutDescendantsSize = outDescendants.size();

        int failedFilter = addChildren(context, root, outDescendants, comparator, filter);

        final int newOutDescendantsSize = outDescendants.size();

        for (int i = oldOutDescendantsSize; i < newOutDescendantsSize; i++) {
            final AccessibilityNodeInfoCompat child = outDescendants.get(i);

            failedFilter += addDescendantsBfs(context, child, outDescendants, comparator, filter);
        }

        return failedFilter;
    }

    /**
     * Adds only the children of the given {@link AccessibilityNodeInfoCompat}.
     *
     * @param node {@link AccessibilityNodeInfoCompat} for which to add
     *            children.
     * @param outChildren The list to which to add the children.
     * @param comparator Optional comparator for sorting children.
     * @param filter Optional filter for selecting sub-set of nodes.
     * @return The number of nodes that failed to match the filter.
     */
    private static int addChildren(Context context, AccessibilityNodeInfoCompat node,
            List<AccessibilityNodeInfoCompat> outChildren,
            Comparator<AccessibilityNodeInfoCompat> comparator,
            NodeFilter filter) {
        final int childCount = node.getChildCount();
        final ArrayList<AccessibilityNodeInfoCompat> children =
                new ArrayList<AccessibilityNodeInfoCompat>(childCount);

        int failedFilter = 0;

        for (int i = 0; i < childCount; i++) {
            final AccessibilityNodeInfoCompat child = node.getChild(i);

            if (child == null) {
                continue;
            }

            if (filter == null || filter.accept(context, child)) {
                children.add(child);
            } else {
                child.recycle();
                failedFilter++;
            }
        }

        if (comparator != null) {
            Collections.sort(children, comparator);
        }

        outChildren.addAll(children);

        return failedFilter;
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
        final int supportedActions = node.getActions();

        for (int action : actions) {
            if ((supportedActions & action) == action) {
                return true;
            }
        }

        return false;
    }

   private static final NodeFilter FILTER_ACCESSIBILITY_FOCUSABLE = new NodeFilter() {
        @Override
        public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
            return isAccessibilityFocusable(context, node);
        }
    };

    /** Filters out actionable and invisible nodes. Used to pick child nodes to read. */
    // TODO: This should be private or somewhere else.
    public static final NodeFilter FILTER_NON_FOCUSABLE = new NodeFilter() {
        @Override
        public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
            if (!node.isVisibleToUser()) {
                return false;
            }

            return !FILTER_ACCESSIBILITY_FOCUSABLE.accept(context, node);
        }
    };

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

    private static final NodeActionFilter mScrollForwardFilter = new NodeActionFilter(
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
    private static final NodeActionFilter mScrollBackwardFilter = new NodeActionFilter(
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

        @Override
        public int compare(AccessibilityNodeInfoCompat first, AccessibilityNodeInfoCompat second) {
            final Rect firstBounds = mFirstBounds;
            first.getBoundsInScreen(firstBounds);

            final Rect secondBounds = mSecondBounds;
            second.getBoundsInScreen(secondBounds);

            // First, compare based on top position difference.
            final int topDifference = firstBounds.top - secondBounds.top;
            if (topDifference != 0) {
                return topDifference;
            }

            // Next, compare based on left position distance.
            final int leftDifference = firstBounds.left - secondBounds.left;
            if (leftDifference != 0) {
                return leftDifference;
            }

            // TODO(alanv): Does it make sense to break ties? If two nodes share
            // the same top and left positions, one should be Z-ordered above
            // the other.

            // Compare based on height difference.
            final int firstHeight = firstBounds.bottom - firstBounds.top;
            final int secondHeight = secondBounds.bottom - secondBounds.top;
            final int heightDiference = firstHeight - secondHeight;
            if (heightDiference != 0) {
                return heightDiference;
            }

            // Compare based on width difference.
            final int firstWidth = firstBounds.right - firstBounds.left;
            final int secondWidth = secondBounds.right - secondBounds.left;
            int widthDiference = firstWidth - secondWidth;
            if (widthDiference != 0) {
                return widthDiference;
            }

            // Do not return 0 to avoid losing data.
            return 1;
        }
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
}
