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

import android.annotation.TargetApi;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Log;

import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;

import java.util.ArrayList;

@TargetApi(16)
public class CursorGranularityManager {

    /** The minimum API level supported by the granularity manager. */
    public static final int MIN_API_LEVEL = 16;

    /* package */enum CursorGranularity {
        DEFAULT(Integer.MIN_VALUE, R.string.granularity_default),
        CHARACTER(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER,
                R.string.granularity_character),
        WORD(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD,
                R.string.granularity_word),
        // LINE(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE,
        // R.string.axis_line),
        PARAGRAPH(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH,
                R.string.granularity_paragraph);
        // PAGE(AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE,
        // R.string.axis_page),

        /** The system identifier for this granularity. */
        public final int id;

        /** The resource identifier for the name of this granularity. */
        public final int resId;

        /**
         * Constructs a new granularity with the specified system identifier.
         *
         * @param id The system identifier. See the GRANULARITY_ constants in
         *            {@link AccessibilityNodeInfoCompat} for a complete list.
         */
        private CursorGranularity(int id, int resId) {
            this.id = id;
            this.resId = resId;
        }

        /**
         * Returns the next best granularity supported by a given node. The
         * returned granularity will always be equal to or larger than the
         * requested granularity.
         *
         * @param requested The requested granularity.
         * @param node The node to test.
         * @return The next best granularity supported by the node.
         */
        public static CursorGranularity getNextBestGranularity(
                CursorGranularity requested, AccessibilityNodeInfoCompat node) {
            final int bitmask = node.getMovementGranularities();

            for (CursorGranularity granularity : CursorGranularity.values()) {
                if (granularity.id < requested.id) {
                    // Don't return a smaller granularity.
                    continue;
                }

                if ((bitmask & granularity.id) == granularity.id) {
                    // This is a supported granularity.
                    return granularity;
                }
            }

            // If we cannot find a supported granularity, use the default.
            return DEFAULT;
        }

        public static void extractFromMask(int bitmask, ArrayList<CursorGranularity> result) {
            final CursorGranularity[] values = values();

            result.clear();
            result.add(DEFAULT);

            for (CursorGranularity value : values) {
                if ((bitmask & value.id) == value.id) {
                    result.add(value);
                }
            }
        }
    }

    private static final CursorGranularity[] GRANULARITIES = CursorGranularity.values();

    /** Unsupported movement within a granularity */
    public static final int NOT_SUPPORTED = -1;

    /** Movement within a granularity reached the edge of possible movement */
    public static final int HIT_EDGE = 0;

    /** Movement within a granularity was successful */
    public static final int SUCCESS = 1;

    /** Represents an increase in granularity */
    public static final int CHANGE_GRANULARITY_HIGHER = 1;

    /** Represents a decrease in granularity */
    public static final int CHANGE_GRANULARITY_LOWER = -1;

    private final ArrayList<AccessibilityNodeInfoCompat>
            mNodes = new ArrayList<AccessibilityNodeInfoCompat>();
    private final Bundle mArguments = new Bundle();

    private AccessibilityNodeInfoCompat mLockedNode;

    private int mCurrentNodeIndex;
    private int mRequestedGranularityIndex;

    /**
     * Releases resources associated with this object.
     */
    public void shutdown() {
        clearCurrentNode();
    }

    public CursorGranularity getRequestedGranularity() {
        return GRANULARITIES[mRequestedGranularityIndex];
    }

    /**
     * Attempt to navigate within the specified cursor at the current
     * granularity.
     *
     * @param node The node to navigate within.
     * @return The result of navigation.
     */
    public int navigateWithin(AccessibilityNodeInfoCompat node, int action) {
        handleRequestedNode(node);

        final int count = mNodes.size();
        final CursorGranularity requestedGranularity = GRANULARITIES[mRequestedGranularityIndex];

        if (requestedGranularity == CursorGranularity.DEFAULT) {
            return NOT_SUPPORTED;
        }

        switch (action) {
            case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
                if (mCurrentNodeIndex < 0) {
                    mCurrentNodeIndex++;
                }
                break;
            case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
                if (mCurrentNodeIndex >= count) {
                    mCurrentNodeIndex--;
                }
                break;
        }

        while ((mCurrentNodeIndex >= 0) && (mCurrentNodeIndex < count)) {
            final AccessibilityNodeInfoCompat currentNode = mNodes.get(mCurrentNodeIndex);
            final CursorGranularity supportedAtNode = CursorGranularity.getNextBestGranularity(
                    requestedGranularity, currentNode);

            mArguments.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                    supportedAtNode.id);

            if ((supportedAtNode != CursorGranularity.DEFAULT)
                    && currentNode.performAction(action, mArguments)) {
                return SUCCESS;
            }

            // If granularity movement failed, advance the cursor to the
            // next node and try again.
            switch (action) {
                case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
                    mCurrentNodeIndex++;
                    break;
                case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
                    mCurrentNodeIndex--;
                    break;
            }

            LogUtils.log(this, Log.VERBOSE, "Failed to move with granularity %s, trying next node",
                    supportedAtNode.name());
        }

        return HIT_EDGE;
    }

    /**
     * Adjust the granularity within the specified cursor.
     *
     * @param node The node to navigate within.
     * @param direction The direction to adjust granularity. One of
     *            {@link CursorGranularityManager#CHANGE_GRANULARITY_HIGHER} or
     *            {@link CursorGranularityManager#CHANGE_GRANULARITY_LOWER}
     * @return {@code true} if the granularity changed.
     */
    public boolean adjustWithin(AccessibilityNodeInfoCompat node, int direction) {
        handleRequestedNode(node);

        final int current = mRequestedGranularityIndex;
        final int count = GRANULARITIES.length;

        mRequestedGranularityIndex += direction;

        if (mRequestedGranularityIndex < 0) {
            mRequestedGranularityIndex = (count - 1);
        } else if (mRequestedGranularityIndex >= count) {
            mRequestedGranularityIndex = 0;
        }

        return (mRequestedGranularityIndex != current);
    }

    public boolean isLockedToNode(AccessibilityNodeInfoCompat node) {
        if (getRequestedGranularity() == CursorGranularity.DEFAULT) {
            return false;
        }

        return ((mLockedNode != null) && mLockedNode.equals(node));
    }

    private void handleRequestedNode(AccessibilityNodeInfoCompat node) {
        if ((mLockedNode != null) && !mLockedNode.equals(node)) {
            // Recycle the old cursor.
            // TODO: We need to be clearing the current node whenever
            // accessibility focus moves away from the locked node.
            clearCurrentNode();
        }

        if (mLockedNode == null) {
            // Load the cursor.
            setCurrentNode(node);
        }
    }

    private void setCurrentNode(AccessibilityNodeInfoCompat node) {
        extractNodes(node, mNodes);

        mLockedNode = AccessibilityNodeInfoCompat.obtain(node);
    }

    private void clearCurrentNode() {
        mArguments.clear();

        mCurrentNodeIndex = 0;
        mRequestedGranularityIndex = 0;

        AccessibilityNodeInfoUtils.recycleNodes(mNodes);
        AccessibilityNodeInfoUtils.recycleNodes(mLockedNode);

        mLockedNode = null;
    }

    /**
     * Extract the child nodes from the given root.
     *
     * @param root The root node.
     * @param nodes The list of child nodes.
     * @return The mask of supported granularities.
     */
    private static int extractNodes(
            AccessibilityNodeInfoCompat root, ArrayList<AccessibilityNodeInfoCompat> nodes) {
        if (root == null) {
            return 0;
        }

        nodes.add(AccessibilityNodeInfoCompat.obtain(root));

        int supportedGranularities = root.getMovementGranularities();

        // Don pull children from nodes with content descriptions.
        if (!TextUtils.isEmpty(root.getContentDescription())) {
            return supportedGranularities;
        }

        final int childCount = root.getChildCount();

        for (int i = 0; i < childCount; i++) {
            final AccessibilityNodeInfoCompat child = root.getChild(i);

            // Only extract nodes that aren't reachable by traversal.
            // TODO: This is incomplete, we should probably use shouldFocusNode().
            if (!AccessibilityNodeInfoUtils.isActionableForAccessibility(child)) {
                supportedGranularities |= extractNodes(child, nodes);
            }

            child.recycle();
        }

        return supportedGranularities;
    }
}
