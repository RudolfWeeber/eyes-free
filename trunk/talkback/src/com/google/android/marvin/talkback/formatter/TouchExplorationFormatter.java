/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.google.android.marvin.talkback.formatter;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.AdapterView;

import com.google.android.marvin.talkback.AccessibilityNodeInfoUtils;
import com.google.android.marvin.talkback.AccessibilityNodeInfoUtils.NodeFilter;
import com.google.android.marvin.talkback.Formatter;
import com.google.android.marvin.talkback.speechrules.NodeProcessor;
import com.google.android.marvin.talkback.LogUtils;
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.Utils;
import com.google.android.marvin.talkback.Utterance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class is a formatter for handling touch exploration events. Current
 * implementation is simple and handles only hover enter events.
 */
public final class TouchExplorationFormatter implements Formatter {
    private static final Comparator<AccessibilityNodeInfo> COMPARATOR =
            new TopToBottomLeftToRightComparator();
    private static final String SEPARATOR = " ";

    /** Whether the last region the user explored was scrollable. */
    private boolean mUserCanScroll;

    /**
     * Whether the user is currently touch exploring. We can still receive
     * {@link AccessibilityEvent#TYPE_VIEW_HOVER_ENTER} events when this is
     * {@code false}.
     */
    private boolean mIsTouchExploring;

    /** The most recently announced node. Used for duplicate detection. */
    private AccessibilityNodeInfo mLastAnnouncedNode;

    /** The node processor. Used to get spoken descriptions. */
    private NodeProcessor mNodeProcessor;

    /** Filters out actionable nodes. Used to pick child nodes to read. */
    private final NodeFilter mNonActionableInfoFilter = new NodeFilter() {
        @Override
        public boolean accept(AccessibilityNodeInfo node) {
            return !AccessibilityNodeInfoUtils.isActionable(node);
        }
    };

    /**
     * Formatter that returns an utterance to announce touch exploration.
     */
    @Override
    public boolean format(AccessibilityEvent event, Context context, Utterance utterance,
            Bundle args) {
        final int eventType = event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START) {
            onTouchExplorationStarted(utterance);
            return true;
        }

        if (eventType == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END
                || (eventType == AccessibilityEvent.TYPE_VIEW_HOVER_EXIT && !mIsTouchExploring)) {
            onTouchExplorationEnded(utterance);
            return true;
        }

        if (eventType == AccessibilityEvent.TYPE_VIEW_HOVER_EXIT) {
            // Don't actually do any processing for exit events.
            return true;
        }

        final AccessibilityNodeInfo source = event.getSource();
        if (source == null) {
            LogUtils.log(TouchExplorationFormatter.class, Log.INFO,
                    "Failed to obtain source for event");
            return false;
        }

        // Starting from the current node go up and find the best node to
        // announce.
        final ArrayList<AccessibilityNodeInfo> sourceAndPredecessors =
                new ArrayList<AccessibilityNodeInfo>();
        sourceAndPredecessors.add(source);
        AccessibilityNodeInfoUtils.getPredecessors(source, sourceAndPredecessors);

        final AccessibilityNodeInfo announcedNode = computeAnnouncedNode(sourceAndPredecessors);

        if (announcedNode != null) {
            LogUtils.log(TouchExplorationFormatter.class, Log.DEBUG, "Announcing node: %s",
                    announcedNode);
        }

        // HACK: The announced node will always be null for an AdapterView,
        // but in the case of a FOCUSED or SELECTED event, we still want to
        // read the event text. Otherwise, we shouldn't be reading layouts.
        if (eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER && announcedNode == null
                && Utils.isViewGroup(event, source)) {
            LogUtils.log(TouchExplorationFormatter.class, Log.INFO,
                    "No node to announce, ignoring view with children");

            AccessibilityNodeInfoUtils.recycleNodeList(sourceAndPredecessors);
            AccessibilityNodeInfoUtils.recycleNodes(announcedNode);
            return false;
        }

        // Do not announce the same node twice in a row.
        if (eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER && announcedNode != null
                && announcedNode.equals(mLastAnnouncedNode)) {
            LogUtils.log(TouchExplorationFormatter.class, Log.INFO,
                    "Same as last announced node, not speaking");

            AccessibilityNodeInfoUtils.recycleNodeList(sourceAndPredecessors);
            AccessibilityNodeInfoUtils.recycleNodes(announcedNode);
            return false;
        }

        // If neither succeeded, abort.
        if (!addDescription(context, event, utterance, source, announcedNode)) {
            LogUtils.log(TouchExplorationFormatter.class, Log.INFO,
                    "Failed to populate utterance, not speaking");

            AccessibilityNodeInfoUtils.recycleNodeList(sourceAndPredecessors);
            AccessibilityNodeInfoUtils.recycleNodes(announcedNode);
            return false;
        }

        addEarcons(announcedNode, sourceAndPredecessors, event, utterance);

        setLastAnnouncedNode(announcedNode);

        AccessibilityNodeInfoUtils.recycleNodeList(sourceAndPredecessors);
        AccessibilityNodeInfoUtils.recycleNodes(announcedNode);
        return true;
    }

    /**
     * Sets the most recently announced node.
     * 
     * @param node The node to set.
     */
    private void setLastAnnouncedNode(AccessibilityNodeInfo node) {
        if (mLastAnnouncedNode != null) {
            mLastAnnouncedNode.recycle();
        }

        if (node != null) {
            mLastAnnouncedNode = AccessibilityNodeInfo.obtain(node);
        } else {
            mLastAnnouncedNode = null;
        }
    }

    private enum DescriptionResult {
        SUCCESS, FAILURE, ABORT
    }

    /**
     * Populates an utterance with text, either from the node or event.
     * 
     * @param context The parent context.
     * @param event The source event.
     * @param utterance The target utterance.
     * @param source The source node.
     * @param announcedNode The computed announced node.
     * @return {@code true} if the utterance was populated with text.
     */
    private boolean addDescription(Context context, AccessibilityEvent event, Utterance utterance,
            AccessibilityNodeInfo source, AccessibilityNodeInfo announcedNode) {
        // Attempt to populate with node description.
        final DescriptionResult result =
                addNodeDescription(context, event, utterance, source, announcedNode);

        // If the node description failed (but didn't abort) try the event.
        if (result == DescriptionResult.FAILURE) {
            return addEventDescription(context, event, utterance);
        }

        return (result == DescriptionResult.SUCCESS);
    }

    /**
     * Populates an utterance with text from an event.
     * 
     * @param context The parent context.
     * @param event The source event.
     * @param utterance The target utterance.
     */
    private boolean addEventDescription(Context context, AccessibilityEvent event,
            Utterance utterance) {
        final CharSequence eventText = Utils.getEventText(context, event);

        if (TextUtils.isEmpty(eventText)) {
            return false;
        }

        utterance.getText().append(eventText);

        return true;
    }

    /**
     * Adds a description to an utterance for the specified node.
     * 
     * @param context The parent context.
     * @param event The source event.
     * @param utterance The output utterance.
     * @param source The source node.
     * @param announcedNode The node to announce.
     */
    private DescriptionResult addNodeDescription(Context context, AccessibilityEvent event,
            Utterance utterance, AccessibilityNodeInfo source,
            AccessibilityNodeInfo announcedNode) {
        final StringBuilder builder = utterance.getText();

        if (announcedNode == null) {
            return DescriptionResult.FAILURE;
        }

        final CharSequence desc = announcedNode.getContentDescription();

        // HACK: If the node to announce is a view group and already has a
        // content description, don't bother fetching its children.
        if (!TextUtils.isEmpty(desc) && Utils.isViewGroup(null, announcedNode)) {
            builder.append(desc);
            return DescriptionResult.SUCCESS;
        }

        final AccessibilityNodeInfo announcedNodeClone =
                AccessibilityNodeInfo.obtain(announcedNode);
        final ArrayList<AccessibilityNodeInfo> announcedSubtreeNodes =
                new ArrayList<AccessibilityNodeInfo>();

        // Fetch the subtree of the node to announce.
        announcedSubtreeNodes.add(announcedNodeClone);

        final int failedFilter =
                AccessibilityNodeInfoUtils.addDescendantsBfs(announcedNode, announcedSubtreeNodes,
                        mNonActionableInfoFilter);

        Collections.sort(announcedSubtreeNodes, COMPARATOR);

        // Make sure we have a node processor.
        // TODO(alanv): We should do this on initialization.
        if (mNodeProcessor == null) {
            mNodeProcessor = new NodeProcessor(context);
        }

        // Add text for the nodes. If we have text (or there are children that
        // failed the filter) then we've handled the node.
        final boolean handled = addNodesText(event, source, announcedSubtreeNodes, utterance);

        AccessibilityNodeInfoUtils.recycleNodeList(announcedSubtreeNodes);

        if (handled) {
            return DescriptionResult.SUCCESS;
        } else if (failedFilter > 0) {
            return DescriptionResult.ABORT;
        } else {
            return DescriptionResult.FAILURE;
        }
    }

    private void onTouchExplorationStarted(Utterance utterance) {
        if (!mIsTouchExploring) {
            utterance.getEarcons().add(R.raw.explore_begin);
        }

        mUserCanScroll = false;
        mIsTouchExploring = true;
    }

    private void onTouchExplorationEnded(Utterance utterance) {
        if (mIsTouchExploring) {
            utterance.getEarcons().add(R.raw.explore_end);
        }

        if (mLastAnnouncedNode != null) {
            mLastAnnouncedNode.recycle();
            mLastAnnouncedNode = null;
        }

        mIsTouchExploring = false;
    }

    /**
     * Adds earcons when moving between scrollable and non-scrollable views.
     * 
     * @param announcedNode The node that is announces.
     * @param eventSourcePredecessors The event source predecessors.
     * @param event The received accessibility event.
     * @param utterance The utterance to which to add the earcons.
     */
    private void addEarcons(AccessibilityNodeInfo announcedNode,
            ArrayList<AccessibilityNodeInfo> eventSourcePredecessors, AccessibilityEvent event,
            Utterance utterance) {
        // If the announced node is in a scrollable container - add an earcon to
        // convey that.
        final boolean userCanScroll =
                isScrollableOrHasScrollablePredecessor(announcedNode, eventSourcePredecessors);
        if (mUserCanScroll != userCanScroll) {
            mUserCanScroll = userCanScroll;
            if (userCanScroll) {
                utterance.getEarcons().add(R.raw.chime_up);
            } else {
                utterance.getEarcons().add(R.raw.chime_down);
            }
        }

        final boolean actionable =
                isActionableOrHasActionablePredecessor(announcedNode, eventSourcePredecessors);
        if (actionable) {
            utterance.getCustomEarcons().add(R.string.pref_sounds_actionable_key);
            utterance.getCustomVibrations().add(R.string.pref_patterns_actionable_key);
        } else {
            utterance.getCustomEarcons().add(R.string.pref_sounds_hover_key);
            utterance.getCustomVibrations().add(R.string.pref_patterns_hover_key);
        }
    }

    /**
     * Computes the node that is to be announced.
     * 
     * @param nodes The candidate nodes.
     * @return The node to announce or null if no such is found.
     */
    private AccessibilityNodeInfo computeAnnouncedNode(ArrayList<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo node : nodes) {
            if (shouldAnnounceNode(node)) {
                return AccessibilityNodeInfo.obtain(node);
            }
        }

        return null;
    }

    /**
     * Determines whether a node should to be announced. This is used when
     * traversing up from a touched node to ensure that enough information is
     * spoken to provide context for what will happen if the user performs an
     * action on the item.
     * 
     * @param node The examined node.
     * @return True if the node is to be announced.
     */
    private boolean shouldAnnounceNode(AccessibilityNodeInfo node) {
        // Do not announce AdapterView or anything that extends it.
        if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, AdapterView.class)) {
            return false;
        }

        // Always announce "actionable" nodes.
        if (AccessibilityNodeInfoUtils.isActionable(node)) {
            return true;
        }

        // If the parent is an AdapterView, then the node is a list item, so
        // announce it.
        final AccessibilityNodeInfo parent = node.getParent();

        if (parent == null) {
            return false;
        }

        final boolean isListItem =
                AccessibilityNodeInfoUtils.nodeMatchesClassByType(parent, AdapterView.class);
        parent.recycle();

        return isListItem;
    }

    /**
     * Adds the text of the given nodes to the specified utterance.
     * 
     * @param nodes The nodes whose text to add.
     * @param utterance The utterance to which to add text.
     */
    private boolean addNodesText(AccessibilityEvent event, AccessibilityNodeInfo source,
            ArrayList<AccessibilityNodeInfo> nodes, Utterance utterance) {
        final StringBuilder builder = utterance.getText();

        for (AccessibilityNodeInfo node : nodes) {
            final CharSequence nodeDesc;

            if (node.equals(source)) {
                nodeDesc = mNodeProcessor.process(node, event);
            } else {
                nodeDesc = mNodeProcessor.process(node, null);
            }

            if (TextUtils.isEmpty(nodeDesc)) {
                continue;
            }

            builder.append(nodeDesc);
            builder.append(SEPARATOR);
        }

        return !TextUtils.isEmpty(builder);
    }

    /**
     * Check whether a given node is actionable or has an actionable
     * predecessor.
     * 
     * @param source The announced node.
     * @param predecessors The predecessors of the event source node.
     * @return True if the node or some of its predecessors is actionable.
     */
    private boolean isActionableOrHasActionablePredecessor(AccessibilityNodeInfo source,
            List<AccessibilityNodeInfo> predecessors) {
        // Nothing is always non-actionable.
        if (source == null || predecessors == null) {
            return false;
        }

        // Check whether the node is actionable.
        if (AccessibilityNodeInfoUtils.isActionable(source)) {
            return true;
        }

        // Check whether the any of the node predecessors is actionable.
        for (AccessibilityNodeInfo predecessor : predecessors) {
            if (AccessibilityNodeInfoUtils.isActionable(predecessor)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check whether a given node is scrollable or has a scrollable predecessor.
     * 
     * @param source The announced node.
     * @param predecessors The predecessors of the event source node.
     * @return True if the node or some of its predecessors is scrollable.
     */
    private boolean isScrollableOrHasScrollablePredecessor(AccessibilityNodeInfo source,
            List<AccessibilityNodeInfo> predecessors) {
        // Nothing is always non-actionable.
        if (source == null || predecessors == null) {
            return false;
        }

        // Check whether the node is scrollable.
        if (source.isScrollable()) {
            return true;
        }

        // Check whether the any of the node predecessors is scrollable.
        for (AccessibilityNodeInfo predecessor : predecessors) {
            if (predecessor.isScrollable()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Compares two AccessibilityNodeInfos in left-to-right and top-to-bottom
     * fashion.
     */
    private static class TopToBottomLeftToRightComparator implements
            Comparator<AccessibilityNodeInfo> {
        private final Rect mFirstBounds = new Rect();
        private final Rect mSecondBounds = new Rect();

        @Override
        public int compare(AccessibilityNodeInfo first, AccessibilityNodeInfo second) {
            Rect firstBounds = mFirstBounds;
            first.getBoundsInScreen(firstBounds);

            Rect secondBounds = mSecondBounds;
            second.getBoundsInScreen(secondBounds);

            // top - bottom
            final int topDifference = firstBounds.top - secondBounds.top;
            if (topDifference != 0) {
                return topDifference;
            }
            // left - right
            final int leftDifference = firstBounds.left - secondBounds.left;
            if (leftDifference != 0) {
                return leftDifference;
            }
            // break tie by height
            final int firstHeight = firstBounds.bottom - firstBounds.top;
            final int secondHeight = secondBounds.bottom - secondBounds.top;
            final int heightDiference = firstHeight - secondHeight;
            if (heightDiference != 0) {
                return heightDiference;
            }
            // break tie by width
            final int firstWidth = firstBounds.right - firstBounds.left;
            final int secondWidth = secondBounds.right - secondBounds.left;
            int widthDiference = firstWidth - secondWidth;
            if (widthDiference != 0) {
                return widthDiference;
            }
            // do not return 0 to avoid losing data
            return 1;
        }
    }
}
