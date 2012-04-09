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
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.ImageView;

import com.google.android.marvin.talkback.AccessibilityEventUtils;
import com.google.android.marvin.talkback.DeveloperOverlay;
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.google.android.marvin.talkback.speechrules.AccessibilityNodeInfoUtils;
import com.google.android.marvin.talkback.speechrules.AccessibilityNodeInfoUtils.NodeFilter;
import com.google.android.marvin.talkback.speechrules.AccessibilityNodeInfoUtils.TopToBottomLeftToRightComparator;
import com.google.android.marvin.talkback.speechrules.NodeSpeechRuleProcessor;
import com.google.android.marvin.utils.StringBuilderUtils;
import com.googlecode.eyesfree.utils.LogUtils;

import java.util.ArrayList;

/**
 * This class is a formatter for handling touch exploration events. Current
 * implementation is simple and handles only hover enter events.
 */
public final class TouchExplorationFormatter implements AccessibilityEventFormatter {
    private static final TopToBottomLeftToRightComparator COMPARATOR =
            new TopToBottomLeftToRightComparator();

    /** Describes the result of running a speech rule. */
    private enum DescriptionResult {
        /** Success means the node generated speech output. */
        HANDLED,
        /** Failure means the node should be continue processing. */
        CONTINUE,
        /** Abort means the node should be rejected. */
        ABORTED
    }

    /** Whether the last region the user explored was scrollable. */
    private boolean mUserCanScroll;

    /** The most recently announced node. Used for duplicate detection. */
    private AccessibilityNodeInfoCompat mLastAnnouncedNode;

    /** The most recent source node. Used for duplicate detection. */
    private AccessibilityNodeInfoCompat mLastSourceNode;

    /** The node processor. Used to get spoken descriptions. */
    private NodeSpeechRuleProcessor mNodeProcessor;

    /** Filters out actionable nodes. Used to pick child nodes to read. */
    private final NodeFilter mNonActionableInfoFilter = new NodeFilter() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
            return !AccessibilityNodeInfoUtils.isActionable(node);
        }
    };

    /**
     * Formatter that returns an utterance to announce touch exploration.
     */
    @Override
    public boolean format(AccessibilityEvent event, Context context, Utterance utterance) {
        final int eventType = event.getEventType();

        switch (eventType) {
            case AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_START:
                onTouchExplorationStarted(utterance);
                return true;
            case AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_END:
                onTouchExplorationEnded(utterance);
                return true;
            case AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT:
                // Don't actually do any processing for exit events.
                return true;
        }

        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat source = record.getSource();
        final AccessibilityNodeInfoCompat announcedNode;

        // HACK: If the event was FOCUSED, don't bother computing the announced
        // node because the source is (theoretically) focusable.
        if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED && source != null) {
            announcedNode = AccessibilityNodeInfoCompat.obtain(source);
        } else {
            announcedNode = AccessibilityNodeInfoUtils.computeAnnouncedNode(context, source);
        }

        if (announcedNode != null) {
            LogUtils.log(TouchExplorationFormatter.class, Log.DEBUG, "Announcing node: %s",
                    announcedNode);
        }

        ProgressBarFormatter.updateRecentlyExplored(announcedNode);
        DeveloperOverlay.updateNodes(source, announcedNode);

        if (eventType == AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER) {
            // HACK: The announced node will always be null for an AdapterView,
            // but in the case of a FOCUSED or SELECTED event, we still want to
            // read the event text. Otherwise, we shouldn't be reading layouts.
            if (announcedNode == null
                    && (AccessibilityNodeInfoUtils.isViewGroup(context, source) || AccessibilityEventUtils
                            .isViewGroup(context, event))) {
                LogUtils.log(TouchExplorationFormatter.class, Log.INFO,
                        "No node to announce, ignoring view with children");

                AccessibilityNodeInfoUtils.recycleNodes(source);
                AccessibilityNodeInfoUtils.recycleNodes(announcedNode);
                return false;
            }

            // Do not announce the same node twice in a row UNLESS we're certain
            // it's not the result of computing the announced node.
            if (announcedNode != null && announcedNode.equals(mLastAnnouncedNode)
                    && (source == null || !source.equals(mLastSourceNode))) {
                LogUtils.log(TouchExplorationFormatter.class, Log.INFO,
                        "Same as last announced node, not speaking");

                AccessibilityNodeInfoUtils.recycleNodes(source);
                AccessibilityNodeInfoUtils.recycleNodes(announcedNode);
                return false;
            }
        }

        final boolean populated = addDescription(context, event, utterance, source, announcedNode);

        if (!populated) {
            LogUtils.log(TouchExplorationFormatter.class, Log.INFO,
                    "Failed to populate utterance, not speaking");

            AccessibilityNodeInfoUtils.recycleNodes(source);
            AccessibilityNodeInfoUtils.recycleNodes(announcedNode);
            return false;
        }

        addEarcons(announcedNode, event, utterance);

        setLastAnnouncedNode(announcedNode);
        setLastSourceNode(source);

        AccessibilityNodeInfoUtils.recycleNodes(source);
        AccessibilityNodeInfoUtils.recycleNodes(announcedNode);

        return true;
    }

    /**
     * Sets the most recent source node.
     *
     * @param node The node to set.
     */
    private void setLastSourceNode(AccessibilityNodeInfoCompat node) {
        if (mLastSourceNode != null) {
            mLastSourceNode.recycle();
        }

        if (node != null) {
            mLastSourceNode = AccessibilityNodeInfoCompat.obtain(node);
        } else {
            mLastSourceNode = null;
        }
    }

    /**
     * Sets the most recently announced node.
     *
     * @param node The node to set.
     */
    private void setLastAnnouncedNode(AccessibilityNodeInfoCompat node) {
        if (mLastAnnouncedNode != null) {
            mLastAnnouncedNode.recycle();
        }

        if (node != null) {
            mLastAnnouncedNode = AccessibilityNodeInfoCompat.obtain(node);
        } else {
            mLastAnnouncedNode = null;
        }
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
            AccessibilityNodeInfoCompat source, AccessibilityNodeInfoCompat announcedNode) {
        // Attempt to populate with node description.
        final DescriptionResult result =
                addNodeDescription(context, event, utterance, source, announcedNode);

        if (result == DescriptionResult.HANDLED) {
            return true;
        } else if (result == DescriptionResult.ABORTED) {
            return false;
        }

        // If the node description failed, try the event.
        if (addEventDescription(context, event, utterance)) {
            return true;
        }

        // If the event description failed, speak the node type.
        if (addTextForUnlabeledControl(context, event, utterance, announcedNode)) {
            return true;
        }

        return true;
    }

    // TODO: Refactor this into something sane.
    private static boolean addTextForUnlabeledControl(
            Context context, AccessibilityEvent event, Utterance utterance,
            AccessibilityNodeInfoCompat announcedNode) {
        CharSequence text = null;

        if (announcedNode != null) {
            text = getTextForUnlabeledNode(context, announcedNode);
        }

        if (text == null) {
            text = getTextForUnlabeledEvent(context, event);
        }

        if (TextUtils.isEmpty(text)) {
            return false;
        }

        utterance.getText().append(text);
        return true;
    }

    private static String getTextForUnlabeledEvent(Context context, AccessibilityEvent event) {
        if (AccessibilityEventUtils.eventMatchesClassByType(context, event, Button.class)) {
            return context.getString(R.string.template_button, "");
        }

        if (AccessibilityEventUtils.eventMatchesClassByType(
                context, event, ImageView.class)) {
            return context.getString(R.string.template_image_view, "");
        }

        return null;
    }

    private static String getTextForUnlabeledNode(
            Context context, AccessibilityNodeInfoCompat node) {
        if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                context, node, Button.class)) {
            return context.getString(R.string.template_button, "");
        }

        if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                context, node, ImageView.class)) {
            return context.getString(R.string.template_image_view, "");
        }

        return null;
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
        final CharSequence eventText = AccessibilityEventUtils.getEventText(event);

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
            Utterance utterance, AccessibilityNodeInfoCompat source, AccessibilityNodeInfoCompat announcedNode) {
        final StringBuilder builder = utterance.getText();

        if (announcedNode == null) {
            return DescriptionResult.CONTINUE;
        }

        final CharSequence desc = announcedNode.getContentDescription();

        // HACK: If the node to announce is a view group and already has a
        // content description, don't bother fetching its children.
        if (!TextUtils.isEmpty(desc)
                && AccessibilityNodeInfoUtils.isViewGroup(context, announcedNode)) {
            builder.append(desc);
            return DescriptionResult.HANDLED;
        }

        final AccessibilityNodeInfoCompat announcedNodeClone =
                AccessibilityNodeInfoCompat.obtain(announcedNode);
        final ArrayList<AccessibilityNodeInfoCompat> announcedSubtreeNodes =
                new ArrayList<AccessibilityNodeInfoCompat>();

        // Fetch the subtree of the node to announce.
        announcedSubtreeNodes.add(announcedNodeClone);

        final int failedFilter =
                AccessibilityNodeInfoUtils.addDescendantsBfs(announcedNode, announcedSubtreeNodes,
                        COMPARATOR, mNonActionableInfoFilter);

        // Make sure we have a node processor.
        // TODO(alanv): We should do this on initialization.
        if (mNodeProcessor == null) {
            mNodeProcessor = new NodeSpeechRuleProcessor(context);
        }

        // Add text for the nodes. If we have text (or there are children that
        // failed the filter) then we've handled the node.
        final boolean handled = addNodesText(event, source, announcedSubtreeNodes, utterance);

        AccessibilityNodeInfoUtils.recycleNodeList(announcedSubtreeNodes);

        if (handled) {
            return DescriptionResult.HANDLED;
        }

        // Don't read non-actionable layouts that contain actionable content.
        if (failedFilter > 0) {
            return DescriptionResult.ABORTED;
        }

        return DescriptionResult.CONTINUE;
    }

    /**
     * Handles the beginning of touch exploration. Maintains exploration state.
     *
     * @param utterance
     */
    private void onTouchExplorationStarted(Utterance utterance) {
        mUserCanScroll = false;
    }

    /**
     * Handles the end of touch exploration. Maintains exploration state.
     *
     * @param utterance
     */
    private void onTouchExplorationEnded(Utterance utterance) {
        if (mLastAnnouncedNode != null) {
            mLastAnnouncedNode.recycle();
            mLastAnnouncedNode = null;
        }
    }

    /**
     * Adds earcons when moving between scrollable and non-scrollable views.
     *
     * @param announcedNode The node that is announces.
     * @param event The received accessibility event.
     * @param utterance The utterance to which to add the earcons.
     */
    private void addEarcons(AccessibilityNodeInfoCompat announcedNode, AccessibilityEvent event,
            Utterance utterance) {
        final boolean userCanScroll =
                AccessibilityNodeInfoUtils.isScrollableOrHasScrollablePredecessor(announcedNode);

        // Only add the scroll state earcon if the scrollable state has changed.
        if (mUserCanScroll != userCanScroll) {
            mUserCanScroll = userCanScroll;
            if (userCanScroll) {
                utterance.getEarcons().add(R.raw.chime_up);
            } else {
                utterance.getEarcons().add(R.raw.chime_down);
            }
        }

        final boolean actionable = AccessibilityNodeInfoUtils.isActionable(announcedNode);

        if (actionable) {
            utterance.getCustomEarcons().add(R.string.pref_sounds_actionable_key);
            utterance.getCustomVibrations().add(R.string.pref_patterns_actionable_key);
        } else {
            utterance.getCustomEarcons().add(R.string.pref_sounds_hover_key);
            utterance.getCustomVibrations().add(R.string.pref_patterns_hover_key);
        }
    }

    /**
     * Adds the text of the given nodes to the specified utterance.
     *
     * @param nodes The nodes whose text to add.
     * @param utterance The utterance to which to add text.
     */
    private boolean addNodesText(AccessibilityEvent event, AccessibilityNodeInfoCompat source,
            ArrayList<AccessibilityNodeInfoCompat> nodes, Utterance utterance) {
        final StringBuilder builder = utterance.getText();

        for (AccessibilityNodeInfoCompat node : nodes) {
            final CharSequence nodeDesc;

            // Since the event was generated by the source node, only pass it if
            // we're processing the source node.
            if (node.equals(source)) {
                nodeDesc = mNodeProcessor.process(node, event);
            } else {
                nodeDesc = mNodeProcessor.process(node, null);
            }

            if (TextUtils.isEmpty(nodeDesc)) {
                continue;
            }

            StringBuilderUtils.appendWithSeparator(builder, nodeDesc);
        }

        return !TextUtils.isEmpty(builder);
    }
}
