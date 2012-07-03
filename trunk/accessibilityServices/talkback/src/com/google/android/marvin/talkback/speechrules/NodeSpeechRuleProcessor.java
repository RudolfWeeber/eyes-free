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
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.Utterance;
import com.googlecode.eyesfree.utils.LogUtils;

import java.util.LinkedList;

/**
 * @author alanv@google.com (Alan Viverette)
 */
public class NodeSpeechRuleProcessor {
    private static final String SEPARATOR = "\n";

    private final LinkedList<NodeSpeechRule> mRules = new LinkedList<NodeSpeechRule>();
    private final Context mContext;

    public NodeSpeechRuleProcessor(Context context) {
        mContext = context;

        loadRules();
    }

    /**
     * Returns the best description for a node.
     *
     * @param node The node to describe.
     * @param event The source event, may be {@code null} when called with
     *            non-source nodes.
     * @return The best description for a node.
     */
    public CharSequence process(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        // Generate node description using rules.
        final CharSequence text = processWithRules(node, event);

        if (TextUtils.isEmpty(text)) {
            return null;
        }

        final StringBuilder descriptionBuilder = new StringBuilder(text);

        // Append the control's disabled state.
        if (!node.isEnabled()) {
            appendTextToBuilder(mContext.getString(R.string.value_disabled), descriptionBuilder);
        }

        // Append the control's checked state, if applicable.
        if (node.isCheckable()) {
            if (node.isChecked()) {
                appendTextToBuilder(mContext.getString(R.string.value_checked), descriptionBuilder);
            } else {
                appendTextToBuilder(mContext.getString(R.string.value_not_checked), descriptionBuilder);
            }
        }

        // Append the control's selected state.
        // TODO: Selected had no meaning outside of TabWidget and ListView.
        //if (node.isSelected()) {
        //    appendTextToBuilder(mContext.getString(R.string.value_selected), descriptionBuilder);
        //}

        return descriptionBuilder;
    }

    /**
     * Loads the default rule set.
     */
    private void loadRules() {
        // Rules are matched in the order they are added, so make sure to place
        // general rules after specific ones (e.g. Button after RadioButton).
        mRules.add(new RuleSimpleTemplate(android.widget.Spinner.class, R.string.template_spinner));
        mRules.add(new RuleSimpleTemplate(android.widget.RadioButton.class, R.string.template_radio_button));
        mRules.add(new RuleSimpleTemplate(android.widget.CompoundButton.class, R.string.template_checkbox));
        mRules.add(new RuleSimpleTemplate(android.widget.ImageButton.class, R.string.template_button));
        mRules.add(new RuleSimpleTemplate(android.widget.Button.class, R.string.template_button));
        mRules.add(new RuleWebContent());
        mRules.add(new RuleImageView());
        mRules.add(new RuleEditText());
        mRules.add(new RuleSeekBar());
        mRules.add(new RuleContainer());
        mRules.add(new RuleViewGroup());

        // Always add the default rule last.
        mRules.add(new RuleDefault());
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
    private CharSequence processWithRules(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        for (NodeSpeechRule rule : mRules) {
            if (rule.accept(mContext, node)) {
                LogUtils.log(this, Log.VERBOSE, "Processing node using %s", rule);
                return rule.format(mContext, node, event);
            }
        }

        return null;
    }

    /**
     * Returns the verbose description for a node.
     *
     * @param node The node to describe.
     * @return The verbose description for a node.
     */
    public static CharSequence processVerbose(Context context, AccessibilityNodeInfoCompat node) {
        final StringBuilder populator = new StringBuilder();
        final CharSequence action = context.getString(
                (Build.VERSION.SDK_INT >= 16) ? R.string.value_double_tap
                        : R.string.value_single_tap);

        // Append hints for clickable, long-clickable, etc.
        // TODO: Allow hints based on node type.
        if (node.isEnabled()) {
            // Don't read both the checkable AND clickable hints!
            if (node.isCheckable()) {
                appendTextToBuilder(
                        context.getString(R.string.template_hint_checkable, action), populator);
            } else if (node.isClickable()) {
                appendTextToBuilder(
                        context.getString(R.string.template_hint_clickable, action), populator);
            }

            if (node.isLongClickable()) {
                appendTextToBuilder(
                        context.getString(R.string.template_hint_long_clickable, action),
                        populator);
            }
        }

        return populator;
    }

    /**
     * Helper for appending the given {@link String} to the existing text in an
     * {@link Utterance}. Also adds a punctuation separator.
     *
     * @param text {@link String} of text to append.
     * @param builder {@link StringBuilder} to which text is appended.
     */
    private static void appendTextToBuilder(CharSequence text, StringBuilder builder) {
        builder.append(SEPARATOR);
        builder.append(text);
    }
}
