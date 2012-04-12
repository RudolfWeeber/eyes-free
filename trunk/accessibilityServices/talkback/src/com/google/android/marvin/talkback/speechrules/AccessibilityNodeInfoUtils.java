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

package com.google.android.marvin.talkback.speechrules;

import android.content.Context;
import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.AdapterView;

import com.google.android.marvin.utils.ClassLoadingManager;
import com.googlecode.eyesfree.utils.LogUtils;

import java.util.ArrayList;
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
        public boolean accept(AccessibilityNodeInfoCompat node);
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

        final CharSequence contentDescription = node.getContentDescription();

        if (!TextUtils.isEmpty(contentDescription)
                && (TextUtils.getTrimmedLength(contentDescription) > 0)) {
            return contentDescription;
        }

        final CharSequence text = node.getText();

        if (!TextUtils.isEmpty(text) && (TextUtils.getTrimmedLength(text) > 0)) {
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

        return ((node.getChildCount() > 0) || AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                context, node, android.view.ViewGroup.class));
    }

    /**
     * Computes the node that is to be announced.
     *
     * @param node The node from which to start searching.
     * @return The node to announce or {@code null} if no node is found.
     */
    public static AccessibilityNodeInfoCompat computeAnnouncedNode(Context context,
            AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return null;
        }

        AccessibilityNodeInfoCompat temp;
        AccessibilityNodeInfoCompat current = AccessibilityNodeInfoCompat.obtain(node);

        // Find the highest-depth node to announce.
        while (current != null) {
            if (shouldAnnounceNode(context, current)) {
                break;
            }

            temp = current.getParent();
            current.recycle();
            current = temp;
        }

        return current;
    }

    /**
     * Determines whether a node should to be announced. This is used when
     * traversing up from a touched node to ensure that enough information is
     * spoken to provide context for what will happen if the user performs an
     * action on the item.
     *
     * @param node The node to examine.
     * @return {@code true} if the node is to be announced.
     */
    private static boolean shouldAnnounceNode(Context context, AccessibilityNodeInfoCompat node) {
        // Do not announce AdapterView or anything that extends it.
        if (nodeMatchesClassByType(context, node, AdapterView.class)) {
            return false;
        }

        // Always announce "actionable" nodes.
        if (AccessibilityNodeInfoUtils.isActionable(node)) {
            return true;
        }

        final AccessibilityNodeInfoCompat parent = node.getParent();
        if (parent == null) {
            return false;
        }

        // If the parent is an AdapterView, then the node is a list item, so
        // announce it.
        final boolean isListItem = nodeMatchesClassByType(context, parent, AdapterView.class);
        parent.recycle();

        return isListItem;
    }

    /**
     * Returns whether a node is actionable. That is, the node supports one of
     * the following actions:
     * <ul>
     * <li>{@link AccessibilityNodeInfoCompat#isCheckable()}
     * <li>{@link AccessibilityNodeInfoCompat#isClickable()}
     * <li>{@link AccessibilityNodeInfoCompat#isFocusable()}
     * <li>{@link AccessibilityNodeInfoCompat#isLongClickable()}
     * </ul>
     *
     * @param node The node to examine.
     * @return {@code true} if node is actionable.
     */
    public static boolean isActionable(AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        return node.isCheckable() || node.isClickable() || node.isFocusable()
                || node.isLongClickable();
    }

    /**
     * Check whether a given node is scrollable or has a scrollable predecessor.
     *
     * @param node The node to examine.
     * @return {@code true} if the node or one of its predecessors is
     *         scrollable.
     */
    public static boolean isScrollableOrHasScrollablePredecessor(AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        // Check whether the node is scrollable.
        if (node.isScrollable()) {
            return true;
        }

        final Set<AccessibilityNodeInfoCompat> predecessors = getPredecessors(node);

        // Check whether the any of the node predecessors is scrollable.
        for (AccessibilityNodeInfoCompat predecessor : predecessors) {
            if (predecessor.isScrollable()) {
                return true;
            }
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
    public static void recycleNodeList(ArrayList<AccessibilityNodeInfoCompat> nodes) {
        if (nodes == null) {
            return;
        }

        for (AccessibilityNodeInfoCompat node : nodes) {
            node.recycle();
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
    public static int addDescendantsBfs(AccessibilityNodeInfoCompat root,
            ArrayList<AccessibilityNodeInfoCompat> outDescendants,
            Comparator<AccessibilityNodeInfoCompat> comparator, NodeFilter filter) {
        final int oldOutDescendantsSize = outDescendants.size();

        int failedFilter = addChildren(root, outDescendants, comparator, filter);

        final int newOutDescendantsSize = outDescendants.size();

        for (int i = oldOutDescendantsSize; i < newOutDescendantsSize; i++) {
            final AccessibilityNodeInfoCompat child = outDescendants.get(i);

            failedFilter += addDescendantsBfs(child, outDescendants, comparator, filter);
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
    private static int addChildren(AccessibilityNodeInfoCompat node,
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

            if (filter == null || filter.accept(child)) {
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
}
