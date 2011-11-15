/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

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
     * Filters {@link AccessibilityNodeInfo}s.
     */
    public static interface NodeFilter {
        /**
         * @param node The node info to filter.
         * @return {@code true} if the node is accepted
         */
        public boolean accept(AccessibilityNodeInfo node);
    }

    private static final Context CONTEXT = TalkBackService.asContext();

    private AccessibilityNodeInfoUtils() {
        // This class is not instantiable.
    }

    /**
     * Returns whether a node is actionable. That is, the node supports one of
     * the following actions:
     * <ul>
     * <li>{@link AccessibilityNodeInfo#isCheckable()}
     * <li>{@link AccessibilityNodeInfo#isClickable()}
     * <li>{@link AccessibilityNodeInfo#isFocusable()}
     * <li>{@link AccessibilityNodeInfo#isLongClickable()}
     * </ul>
     * 
     * @param node The node to check.
     * @return Whether the node is actionable.
     */
    public static boolean isActionable(AccessibilityNodeInfo node) {
        return node.isCheckable() || node.isClickable() || node.isFocusable()
                || node.isLongClickable();
    }

    /**
     * Determines if the generating class of an {@link AccessibilityNodeInfo}
     * matches a given {@link Class} by type.
     * 
     * @param node A sealed {@link AccessibilityNodeInfo} dispatched by the
     *            accessibility framework.
     * @param clazz A {@link Class} to match by type or inherited type.
     * @return {@code true} if the {@link AccessibilityNodeInfo} object matches
     *         the {@link Class} by type or inherited type, {@code false}
     *         otherwise.
     */
    public static boolean nodeMatchesClassByType(AccessibilityNodeInfo node, Class<?> clazz) {
        if (node == null || clazz == null) {
            return false;
        }

        final ClassLoadingManager classLoader = ClassLoadingManager.getInstance();
        final CharSequence className = node.getClassName();
        final CharSequence packageName = node.getPackageName();
        final Class<?> nodeClass =
                classLoader.loadOrGetCachedClass(CONTEXT, className, packageName);

        if (nodeClass == null) {
            return false;
        }

        return clazz.isAssignableFrom(nodeClass);
    }

    /**
     * Gets all the parent {@link AccessibilityNodeInfo} nodes, up to the view
     * root.
     * 
     * @param node The {@link AccessibilityNodeInfo} child from which to begin
     *            collecting parents.
     * @param parentList The list to populate with predecessor nodes.
     */
    public static void getPredecessors(AccessibilityNodeInfo node,
            List<AccessibilityNodeInfo> parentList) {
        if (node == null || parentList == null) {
            return;
        }

        node = node.getParent();

        while (node != null) {
            parentList.add(node);
            node = node.getParent();
        }
    }

    /**
     * Recycles the given nodes.
     * 
     * @param nodes The nodes to recycle.
     */
    public static void recycleNodeList(ArrayList<AccessibilityNodeInfo> nodes) {
        if (nodes == null) {
            return;
        }

        for (AccessibilityNodeInfo node : nodes) {
            node.recycle();
        }

        nodes.clear();
    }

    /**
     * Recycles the given nodes.
     * 
     * @param nodes The nodes to recycle.
     */
    public static void recycleNodes(AccessibilityNodeInfo... nodes) {
        if (nodes == null) {
            return;
        }

        for (AccessibilityNodeInfo node : nodes) {
            if (node != null) {
                node.recycle();
            }
        }
    }

    /**
     * Adds all descendant nodes of the given {@link AccessibilityNodeInfo} in
     * breadth first order.
     * 
     * @param root {@link AccessibilityNodeInfo} for which to add descendants.
     * @param outDescendants The list to which to add descendants.
     * @param filter Optional filter for selecting sub-set of nodes, null for
     *            all.
     * @return The number of nodes that failed to match the filter.
     */
    public static int addDescendantsBfs(AccessibilityNodeInfo root,
            ArrayList<AccessibilityNodeInfo> outDescendants, NodeFilter filter) {
        final int oldOutDescendantsSize = outDescendants.size();

        int failedFilter = addChildren(root, outDescendants, filter);

        final int newOutDescendantsSize = outDescendants.size();

        for (int i = oldOutDescendantsSize; i < newOutDescendantsSize; i++) {
            final AccessibilityNodeInfo child = outDescendants.get(i);

            failedFilter += addDescendantsBfs(child, outDescendants, filter);
        }

        return failedFilter;
    }

    /**
     * Adds only the children of the given {@link AccessibilityNodeInfo}.
     * 
     * @param node {@link AccessibilityNodeInfo} for which to add children.
     * @param outChildren The list to which to add the children.
     * @param filter Optional filter for selecting sub-set of nodes, null for
     *            all.
     * @return The number of nodes that failed to match the filter.
     */
    private static int addChildren(AccessibilityNodeInfo node,
            ArrayList<AccessibilityNodeInfo> outChildren, NodeFilter filter) {
        final int childCount = node.getChildCount();

        int failedFilter = 0;

        for (int i = 0; i < childCount; i++) {
            final AccessibilityNodeInfo child = node.getChild(i);

            if (child == null) {
                continue;
            }

            if (filter == null || filter.accept(child)) {
                outChildren.add(child);
            } else {
                child.recycle();
                failedFilter++;
            }
        }

        return failedFilter;
    }
}
