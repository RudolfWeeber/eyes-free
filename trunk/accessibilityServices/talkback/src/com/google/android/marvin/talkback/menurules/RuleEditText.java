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

package com.google.android.marvin.talkback.menurules;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.MenuItem;

import com.google.android.marvin.talkback.ActionHistory;
import com.google.android.marvin.talkback.CursorController;
import com.google.android.marvin.talkback.MappedFeedbackController;
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.widget.RadialMenu;
import com.googlecode.eyesfree.widget.RadialMenuItem;

import java.util.LinkedList;
import java.util.List;

/**
 * Processes editable text fields.
 *
 * @author alanv@google.com (Alan Viverette)
 */
class RuleEditText implements NodeMenuRule {
    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        return AccessibilityNodeInfoUtils.nodeMatchesClassByType(context, node,
                android.widget.EditText.class);
    }

    @Override
    public List<RadialMenuItem> getMenuItemsForNode(
            TalkBackService service, AccessibilityNodeInfoCompat node) {
        final AccessibilityNodeInfoCompat nodeCopy = AccessibilityNodeInfoCompat.obtain(node);
        final CursorController cursorController = service.getCursorController();
        final List<RadialMenuItem> items = new LinkedList<RadialMenuItem>();

        // This action has inconsistencies with EditText nodes that have
        // contentDescription attributes.
        if (TextUtils.isEmpty(nodeCopy.getContentDescription())) {
            if (AccessibilityNodeInfoUtils.supportsAnyAction(nodeCopy,
                    AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)) {
                final RadialMenuItem moveToBeginning = new RadialMenuItem(service, RadialMenu.NONE,
                        R.id.edittext_breakout_move_to_beginning, RadialMenu.NONE,
                        service.getString(R.string.title_edittext_breakout_move_to_beginning));
                items.add(moveToBeginning);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)) {
                final RadialMenuItem moveToEnd = new RadialMenuItem(service, RadialMenu.NONE,
                        R.id.edittext_breakout_move_to_end, RadialMenu.NONE,
                        service.getString(R.string.title_edittext_breakout_move_to_end));
                items.add(moveToEnd);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_CUT)) {
                final RadialMenuItem cut = new RadialMenuItem(service, RadialMenu.NONE,
                        R.id.edittext_breakout_cut, RadialMenu.NONE,
                        service.getString(R.string.title_edittext_breakout_cut));
                items.add(cut);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_COPY)) {
                final RadialMenuItem copy = new RadialMenuItem(service, RadialMenu.NONE,
                        R.id.edittext_breakout_copy, RadialMenu.NONE,
                        service.getString(R.string.title_edittext_breakout_copy));
                items.add(copy);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_PASTE)) {
                final RadialMenuItem paste = new RadialMenuItem(service, RadialMenu.NONE,
                        R.id.edittext_breakout_paste, RadialMenu.NONE,
                        service.getString(R.string.title_edittext_breakout_paste));
                items.add(paste);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    nodeCopy, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)) {
                final RadialMenuItem select = new RadialMenuItem(service, RadialMenu.NONE,
                        R.id.edittext_breakout_select_all, RadialMenu.NONE,
                        service.getString(R.string.title_edittext_breakout_select_all));
                items.add(select);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                // Text selection APIs are available in API 18+
                // TODO(caseyburkhardt) Use a checkable menu item once supported.
                final RadialMenuItem selectionMode;
                if (cursorController.isSelectionModeActive()) {
                    selectionMode = new RadialMenuItem(service, RadialMenu.NONE,
                            R.id.edittext_breakout_end_selection_mode, RadialMenu.NONE,
                            service.getString(R.string.title_edittext_breakout_end_selection_mode));
                } else {
                    selectionMode = new RadialMenuItem(service, RadialMenu.NONE,
                            R.id.edittext_breakout_start_selection_mode, RadialMenu.NONE,
                            service.getString(
                                    R.string.title_edittext_breakout_start_selection_mode));
                }

                items.add(selectionMode);
            }
        }

        for (MenuItem item : items) {
            item.setOnMenuItemClickListener(new EditTextMenuItemClickListener(service, nodeCopy));
        }

        return items;
    }

    @Override
    public CharSequence getUserFriendlyMenuName(Context context) {
        return context.getString(R.string.title_edittext_controls);
    }

    @Override
    public boolean canCollapseMenu() {
        return true;
    }

    private static class EditTextMenuItemClickListener implements MenuItem.OnMenuItemClickListener {
        private final MappedFeedbackController mFeedback;
        private final CursorController mCursorController;
        private final AccessibilityNodeInfoCompat mNode;

        public EditTextMenuItemClickListener(
                TalkBackService service, AccessibilityNodeInfoCompat node) {
            mFeedback = MappedFeedbackController.getInstance();
            mCursorController = service.getCursorController();
            mNode = node;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (item == null) {
                mNode.recycle();
                return true;
            }

            final int itemId = item.getItemId();
            final Bundle args = new Bundle();
            final boolean result;

            if (itemId == R.id.edittext_breakout_move_to_beginning) {
                args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);
                result = mNode.performAction(
                        AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, args);
            } else if (itemId == R.id.edittext_breakout_move_to_end) {
                args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);
                result = mNode.performAction(
                        AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, args);
            } else if (itemId == R.id.edittext_breakout_cut) {
                result = mNode.performAction(AccessibilityNodeInfoCompat.ACTION_CUT);
            } else if (itemId == R.id.edittext_breakout_copy) {
                result = mNode.performAction(AccessibilityNodeInfoCompat.ACTION_COPY);
            } else if (itemId == R.id.edittext_breakout_paste) {
                ActionHistory.getInstance().before(AccessibilityNodeInfoCompat.ACTION_PASTE);
                result = mNode.performAction(AccessibilityNodeInfoCompat.ACTION_PASTE);
                ActionHistory.getInstance().after(AccessibilityNodeInfoCompat.ACTION_PASTE);
            } else if (itemId == R.id.edittext_breakout_select_all) {
                args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, 0);
                args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT,
                        mNode.getText().length());
                result = mNode.performAction(
                        AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args);
            } else if (itemId == R.id.edittext_breakout_start_selection_mode) {
                mCursorController.setSelectionModeActive(mNode, true);
                result = true;
            } else if (itemId == R.id.edittext_breakout_end_selection_mode) {
                mCursorController.setSelectionModeActive(mNode, false);
                result = true;
            } else {
                result = false;
            }

            if (!result) {
                mFeedback.playAuditory(R.id.sounds_complete);
            }

            mNode.recycle();
            return true;
        }
    }
}
