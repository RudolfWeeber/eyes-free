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

import android.annotation.TargetApi;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.widget.RadialMenu;
import com.googlecode.eyesfree.widget.RadialMenuItem;
import com.googlecode.eyesfree.widget.RadialSubMenu;

import java.util.LinkedList;
import java.util.List;

/**
 * Rule-based processor for adding items to the local breakout menu.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NodeMenuRuleProcessor {
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN;

    private static final LinkedList<NodeMenuRule> mRules = new LinkedList<NodeMenuRule>();

    static {
        // Rules are matched in the order they are added, but any rule that
        // accepts will be able to modify the menu.
        mRules.add(new RuleSpannables());
        mRules.add(new RuleEditText());
        mRules.add(new RuleViewPager());
        mRules.add(new RuleGranularity());
    }

    private final TalkBackService mService;

    public NodeMenuRuleProcessor(TalkBackService service) {
        mService = service;
    }

    /**
     * Populates a {@link RadialMenu} with items specific to the provided node
     * based on {@link NodeMenuRule}s.
     *
     * @param menu The menu to populate.
     * @param node The node with which to populate the menu.
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public boolean prepareMenuForNode(RadialMenu menu, AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }

        // Always reset the menu since it is based on the current cursor.
        menu.clear();

        // Track which rules accept the node.
        final LinkedList<NodeMenuRule> matchingRules = new LinkedList<NodeMenuRule>();
        for (NodeMenuRule rule : mRules) {
            if (rule.accept(mService, node)) {
                matchingRules.add(rule);
            }
        }

        boolean canCollapseMenu = false;

        for (NodeMenuRule rule : matchingRules) {
            final List<RadialMenuItem> ruleResults = rule.getMenuItemsForNode(mService, node);
            if (ruleResults.isEmpty()) {
                continue;
            }

            final CharSequence subMenuName = rule.getUserFriendlyMenuName(mService);
            final RadialSubMenu ruleSubMenu = menu.addSubMenu(
                    RadialMenu.NONE, 0, RadialMenu.NONE, subMenuName);
            ruleSubMenu.addAll(ruleResults);

            canCollapseMenu |= rule.canCollapseMenu();
        }

        // Collapse if the menu contains only a single collapsible sub-menu.
        if ((menu.size() == 1) && canCollapseMenu) {
            collapseSubMenus(menu);
        }

        if (menu.size() == 0) {
            menu.add(mService.getString(R.string.title_local_breakout_no_items));
        }

        return true;
    }

    /**
     * Removes all top-level sub-menus and reassigns their children to the
     * parent menu.
     *
     * @param menu The parent menu containing the sub-menus to collapse.
     */
    private static void collapseSubMenus(RadialMenu menu) {
        final List<RadialMenuItem> menuItems = menu.getItems(false /* includeCorners */);
        for (RadialMenuItem item : menuItems) {
            if (item.hasSubMenu()) {
                final RadialSubMenu subMenu = item.getSubMenu();
                final List<RadialMenuItem> subItems = subMenu.getItems(true /* includeCorners */);
                menu.removeItem(item.getItemId());
                menu.addAll(subItems);
            }
        }
    }
}
