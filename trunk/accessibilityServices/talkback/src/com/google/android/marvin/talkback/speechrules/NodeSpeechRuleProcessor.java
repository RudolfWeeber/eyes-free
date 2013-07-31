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

package com.google.android.marvin.talkback.speechrules;

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.googlecode.eyesfree.compat.view.accessibility.AccessibilityNodeInfoCompatUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils.TopToBottomLeftToRightComparator;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.StringBuilderUtils;

import java.util.LinkedList;
import java.util.TreeSet;

/**
 * Rule-based processor for {@link AccessibilityNodeInfoCompat}s.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class NodeSpeechRuleProcessor {
    /** Comparator for sorting on-screen content. */
    private static final TopToBottomLeftToRightComparator
            COMPARATOR = new TopToBottomLeftToRightComparator();

    private static final LinkedList<NodeSpeechRule> mRules = new LinkedList<NodeSpeechRule>();
    private static final RuleSwitch mRuleSwitch = new RuleSwitch();

    private static NodeSpeechRuleProcessor sInstance;

    static {
        // Rules are matched in the order they are added, so make sure to place
        // general rules after specific ones (e.g. Button after RadioButton).
        mRules.add(new RuleSimpleHintTemplate(android.widget.Spinner.class,
                R.string.template_spinner, R.string.template_hint_spinner));
        mRules.add(mRuleSwitch);
        mRules.add(new RuleNonTextViews()); // ImageViews and ImageButtons
        mRules.add(new RuleSimpleTemplate(android.widget.RadioButton.class,
                R.string.template_radio_button));
        mRules.add(new RuleSimpleTemplate(android.widget.CompoundButton.class,
                R.string.template_checkbox));
        mRules.add(new RuleSimpleTemplate(android.widget.Button.class,
                R.string.template_button));
        mRules.add(new RuleEditText());
        mRules.add(new RuleSeekBar());
        mRules.add(new RuleContainer());
        mRules.add(new RuleViewGroup());

        // Always add the default rule last.
        mRules.add(new RuleDefault());
    }

    public static void initialize(Context context) {
        sInstance = new NodeSpeechRuleProcessor(context);
    }

    public static NodeSpeechRuleProcessor getInstance() {
        if (sInstance == null) {
            throw new RuntimeException("NodeSpeechRuleProcessor not initialized");
        }

        return sInstance;
    }

    /** The parent context. */
    private final Context mContext;

    private NodeSpeechRuleProcessor(Context context) {
        mContext = context;
    }

    /**
     * Returns the best description for the subtree rooted at
     * {@code announcedNode}.
     *
     * @param announcedNode The root node of the subtree to describe.
     * @param event The source event, may be {@code null} when called with
     *            non-source nodes.
     * @param source The event's source node.
     * @return The best description for a node.
     */
    public CharSequence getDescriptionForTree(AccessibilityNodeInfoCompat announcedNode,
            AccessibilityEvent event, AccessibilityNodeInfoCompat source) {
        if (announcedNode == null) {
            return null;
        }

        final SpannableStringBuilder builder = new SpannableStringBuilder();

        appendDescriptionForTree(announcedNode, builder, event, source);
        formatTextWithLabel(announcedNode, builder);
        appendRootMetadataToBuilder(announcedNode, builder);

        return builder;
    }

    /**
     * Returns hint text for a node.
     *
     * @param node The node to provide hint text for.
     * @return The node's hint text.
     */
    public CharSequence getHintForNode(AccessibilityNodeInfoCompat node) {
        for (NodeSpeechRule rule : mRules) {
            if ((rule instanceof NodeHintRule) && rule.accept(mContext, node)) {
                LogUtils.log(this, Log.VERBOSE, "Processing node hint using %s", rule);
                return ((NodeHintRule) rule).getHintText(mContext, node);
            }
        }

        return null;
    }

    private void appendDescriptionForTree(AccessibilityNodeInfoCompat announcedNode,
            SpannableStringBuilder builder, AccessibilityEvent event,
            AccessibilityNodeInfoCompat source) {
        if (announcedNode == null) {
            return;
        }

        // Append the full description for the root node.
        final AccessibilityEvent nodeEvent = (announcedNode.equals(source)) ? event : null;
        final CharSequence nodeDesc = getDescriptionForNode(announcedNode, nodeEvent);
        if (!TextUtils.isEmpty(nodeDesc)) {
            StringBuilderUtils.appendWithSeparator(builder, nodeDesc);

            appendMetadataToBuilder(announcedNode, builder);

            // Setting a content description overrides subtree descriptions.
            final CharSequence announcedDescription = announcedNode.getContentDescription();
            if (!TextUtils.isEmpty(announcedDescription)) {
                return;
            }
        }

        // Recursively append descriptions for visible and non-focusable child nodes.
        final TreeSet<AccessibilityNodeInfoCompat> children = getSortedChildren(announcedNode);
        for (AccessibilityNodeInfoCompat child : children) {
            if (AccessibilityNodeInfoUtils.isVisibleOrLegacy(child)
                    && !AccessibilityNodeInfoUtils.isAccessibilityFocusable(mContext, child)) {
                appendDescriptionForTree(child, builder, event, source);
            }
        }

        AccessibilityNodeInfoUtils.recycleNodes(children);
    }

    /**
     * Returns a sorted set of {@code node}'s direct children.
     */
    private TreeSet<AccessibilityNodeInfoCompat> getSortedChildren(
            AccessibilityNodeInfoCompat node) {
        final TreeSet<AccessibilityNodeInfoCompat> children =
                new TreeSet<AccessibilityNodeInfoCompat>(COMPARATOR);

        for (int i = 0; i < node.getChildCount(); i++) {
            final AccessibilityNodeInfoCompat child = node.getChild(i);
            if (child == null) {
                continue;
            }

            children.add(child);
        }

        return children;
    }

    /**
     * Processes the specified node using a series of speech rules.
     *
     * @param node The node to process.
     * @param event The source event, may be {@code null} when called with
     *            non-source nodes.
     * @return A string representing the given node, or {@code null} if the node
     *         could not be processed.
     */
    private CharSequence getDescriptionForNode(
            AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        for (NodeSpeechRule rule : mRules) {
            if (rule.accept(mContext, node)) {
                LogUtils.log(this, Log.VERBOSE, "Processing node using %s", rule);
                return rule.format(mContext, node, event);
            }
        }

        return null;
    }

    /**
     * If the supplied node has a label, replaces the builder text with a
     * version formatted with the label.
     */
    private void formatTextWithLabel(
            AccessibilityNodeInfoCompat node, SpannableStringBuilder builder) {
        final AccessibilityNodeInfoCompat labelNode =
                AccessibilityNodeInfoCompatUtils.getLabeledBy(node);
        if (labelNode == null) {
            return;
        }

        final SpannableStringBuilder labelDescription = new SpannableStringBuilder();
        appendDescriptionForTree(labelNode, labelDescription, null, null);
        if (TextUtils.isEmpty(labelDescription)) {
            return;
        }

        final String labeled = mContext.getString(
                R.string.template_labeled_item, builder, labelDescription);

        // Replace the text of the builder.
        builder.clear();
        builder.append(labeled);
    }

    /**
     * Appends meta-data about node's disabled state (if actionable).
     * <p>
     * This should only be applied to the root node of a tree.
     */
    private void appendRootMetadataToBuilder(
            AccessibilityNodeInfoCompat node, SpannableStringBuilder descriptionBuilder) {
        // Append state for actionable but disabled nodes.
        if (AccessibilityNodeInfoUtils.isActionableForAccessibility(node) && !node.isEnabled()) {
            StringBuilderUtils.appendWithSeparator(
                    descriptionBuilder, mContext.getString(R.string.value_disabled));
        }

        // Append the control's selected state.
        // TODO: Selected had no meaning outside of TabWidget and ListView.
        // if (node.isSelected()) {
        // StringBuilderUtils.appendWithSeparator(descriptionBuilder,
        // mContext.getString(R.string.value_selected));
        // }
    }

    /**
     * Appends meta-data about the node's checked (if checkable) states.
     * <p>
     * This should be applied to all nodes in a tree, including the root.
     */
    private void appendMetadataToBuilder(
            AccessibilityNodeInfoCompat node, SpannableStringBuilder descriptionBuilder) {
        // Append the control's checked state, if applicable. Ignore nodes that
        // were accepted by the switch rule, since they already include the
        // checkable state.
        if (node.isCheckable() && !mRuleSwitch.accept(mContext, node)) {
            final int res = node.isChecked() ? R.string.value_checked : R.string.value_not_checked;
            StringBuilderUtils.appendWithSeparator(descriptionBuilder, mContext.getString(res));
        }
    }
}
