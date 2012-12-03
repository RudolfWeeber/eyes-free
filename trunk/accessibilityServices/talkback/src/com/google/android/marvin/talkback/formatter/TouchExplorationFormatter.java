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

import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.AccessibilityEventUtils;
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.SpeechController;
import com.google.android.marvin.talkback.TalkBackService;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.ContextBasedRule;
import com.google.android.marvin.talkback.speechrules.NodeSpeechRuleProcessor;
import com.google.android.marvin.utils.StringBuilderUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * This class is a formatter for handling touch exploration events. Current
 * implementation is simple and handles only hover enter events.
 */
public final class TouchExplorationFormatter
        implements AccessibilityEventFormatter, ContextBasedRule, TalkBackService.EventListener {

    /** Whether this build supports accessibility focus. */
    private static final boolean SUPPORTS_A11Y_FOCUS = (Build.VERSION.SDK_INT >= 16);

    /** Whether this build supports caching node infos. */
    private static final boolean SUPPORTS_NODE_CACHE = (Build.VERSION.SDK_INT >= 16);

    /** The default queuing mode for touch exploration feedback. */
    private static final int DEFAULT_QUEUING_MODE = SpeechController.QUEUE_MODE_FLUSH_ALL;

    /** Whether the last region the user explored was scrollable. */
    private boolean mLastNodeWasScrollable;

    /**
     * The parent context. Should be set only while this class is formatting an
     * event, {@code null} otherwise.
     */
    private TalkBackService mContext;

    /**
     * The node processor used to generate spoken descriptions. Should be set
     * only while this class is formatting an event, {@code null} otherwise.
     */
    private NodeSpeechRuleProcessor mNodeProcessor;

    /**
     * The most recently focused node. Used to keep track of simulated
     * accessibility focus on pre-Jelly Bean devices.
     */
    private AccessibilityNodeInfoCompat mLastFocusedNode;

    @Override
    public void initialize(TalkBackService context) {
        mContext = context;
        mContext.addEventListener(this);

        mNodeProcessor = context.getNodeProcessor();
    }

    /**
     * Implementation of {@link TalkBackService.EventListener#process}.
     * <p>
     * Resets cached scrollable state when touch exploration after window state
     * changes.
     */
    @Override
    public void process(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                // Reset cached scrollable state.
                mLastNodeWasScrollable = false;
                break;
        }
    }

    /**
     * Formatter that returns an utterance to announce touch exploration.
     */
    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat sourceNode = record.getSource();
        final AccessibilityNodeInfoCompat focusedNode = getFocusedNode(
                event.getEventType(), sourceNode);

        // Drop the event if the source node was non-null, but the focus
        // algorithm decided to drop the event by returning null.
        if ((sourceNode != null) && (focusedNode == null)) {
            AccessibilityNodeInfoUtils.recycleNodes(sourceNode);
            return false;
        }

        LogUtils.log(this, Log.VERBOSE, "Announcing node: %s", focusedNode);

        // Populate the utterance.
        addDescription(utterance, focusedNode, event, sourceNode);
        addEarcons(utterance, focusedNode);

        // By default, touch exploration flushes all other events.
        utterance.getMetadata().putInt(Utterance.KEY_METADATA_QUEUING, DEFAULT_QUEUING_MODE);

        AccessibilityNodeInfoUtils.recycleNodes(sourceNode, focusedNode);

        return true;
    }

    /**
     * Computes a focused node based on the device's supported APIs and the
     * event type.
     *
     * @param eventType The event type.
     * @param sourceNode The source node.
     * @return The focused node, or {@code null} to drop the event.
     */
    private AccessibilityNodeInfoCompat getFocusedNode(
            int eventType, AccessibilityNodeInfoCompat sourceNode) {
        if (sourceNode == null) {
            return null;
        }

        // On Jelly Bean, use the source node of accessibility focus events and
        // discard all other event types.
        if (SUPPORTS_A11Y_FOCUS) {
            if (eventType != AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                return null;
            }

            return AccessibilityNodeInfoCompat.obtain(sourceNode);
        }

        // On previous versions, simulate accessibility focus by computing a
        // focused node for hover enter events and discard all other events.
        if (eventType != AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER) {
            return null;
        }

        final AccessibilityNodeInfoCompat focusedNode = AccessibilityNodeInfoUtils
                .findFocusFromHover(mContext, sourceNode);

        // If the focused node already has accessibility focus, abort.
        if ((focusedNode != null) && focusedNode.equals(mLastFocusedNode)) {
            AccessibilityNodeInfoUtils.recycleNodes(focusedNode);
            return null;
        }

        // Cache the current focus target for later comparison.
        if (mLastFocusedNode != null) {
            mLastFocusedNode.recycle();
        }

        if (focusedNode != null) {
            mLastFocusedNode = AccessibilityNodeInfoCompat.obtain(focusedNode);
        } else {
            // TODO(alanv): Calling AccessibilityNodeInfoCompat.obtain(null)
            // should return null. Is there a bug in the support library?
            mLastFocusedNode = null;
        }

        // Since API <16 doesn't support accessibility focus, we need to fake it
        // by keeping track of the most recently explored view.
        // TODO(alanv): Add global support for keeping track of fake A11y focus.
        ProgressBarFormatter.updateRecentlyExplored(focusedNode);

        return focusedNode;
    }

    /**
     * Populates an utterance with text, either from the node or event.
     *
     * @param utterance The target utterance.
     * @param announcedNode The computed announced node.
     * @param event The source event, only used to providing a description when
     *            the source node is a progress bar.
     * @param source The source node, used to determine whether the source event
     *            should be passed to the node formatter.
     * @return {@code true} if the utterance was populated with text.
     */
    private boolean addDescription(Utterance utterance, AccessibilityNodeInfoCompat announcedNode,
            AccessibilityEvent event, AccessibilityNodeInfoCompat source) {
        final CharSequence treeDescription = mNodeProcessor.getDescriptionForTree(
                announcedNode, event, source);
        if (!TextUtils.isEmpty(treeDescription)) {
            StringBuilderUtils.appendWithSeparator(utterance.getText(), treeDescription);
            return true;
        }

        // If all else fails, fall back on the event description.
        return addDescriptionForEvent(utterance, event);
    }

    /**
     * Populates an utterance with text from an event.
     *
     * @param utterance The target utterance.
     * @param event The source event.
     * @return {@code true} if the utterance was populated with text.
     */
    private boolean addDescriptionForEvent(Utterance utterance, AccessibilityEvent event) {
        final CharSequence eventText = AccessibilityEventUtils.getEventText(event);
        if (TextUtils.isEmpty(eventText)) {
            return false;
        }

        final StringBuilder builder = utterance.getText();
        StringBuilderUtils.appendWithSeparator(builder, eventText);

        return true;
    }

    /**
     * Adds earcons for a focused node.
     *
     * @param utterance The utterance to which to add the earcons.
     * @param announcedNode The node that is announced.
     */
    private void addEarcons(Utterance utterance, AccessibilityNodeInfoCompat announcedNode) {
        if (announcedNode == null) {
            return;
        }

        final boolean userCanScroll = AccessibilityNodeInfoUtils
                .isScrollableOrHasScrollablePredecessor(mContext, announcedNode);

        // Announce changes in whether the user can scroll the item they are
        // touching. This includes items with scrollable parents.
        if (mLastNodeWasScrollable != userCanScroll) {
            mLastNodeWasScrollable = userCanScroll;

            if (userCanScroll) {
                utterance.getEarcons().add(R.raw.chime_up);
            } else {
                utterance.getEarcons().add(R.raw.chime_down);
            }
        }

        // If the user can scroll, also check whether this item is at the edge
        // of a list and provide feedback if the user can scroll for more items.
        // Don't run this for API < 16 because it's slow without node caching.
        if (SUPPORTS_NODE_CACHE && userCanScroll
                && AccessibilityNodeInfoUtils.isEdgeListItem(mContext, announcedNode)) {
            utterance.getCustomEarcons().add(R.id.sounds_scroll_for_more);
        }

        // Actionable items provide different feedback than non-actionable ones.
        if (AccessibilityNodeInfoUtils.isActionableForAccessibility(announcedNode)) {
            utterance.getCustomEarcons().add(R.id.sounds_actionable);
            utterance.getCustomVibrations().add(R.id.patterns_actionable);
        } else {
            utterance.getCustomEarcons().add(R.id.sounds_hover);
            utterance.getCustomVibrations().add(R.id.patterns_hover);
        }
    }
}
