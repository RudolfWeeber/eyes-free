/*
 * Copyright (C) 2013 Google Inc.
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
import android.content.Context;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.MenuItem;

import com.google.android.marvin.talkback.CursorController;
import com.google.android.marvin.talkback.CursorGranularity;
import com.google.android.marvin.talkback.CursorGranularityManager;
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.utils.WebInterfaceUtils;
import com.googlecode.eyesfree.widget.RadialMenuItem;

import java.util.LinkedList;
import java.util.List;

/**
 * Adds supported granularities to the local context menu. If the target node
 * contains web content, adds web-specific granularities.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class RuleGranularity implements NodeMenuRule {
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN;

    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        return (node.getMovementGranularities() != 0) || (node.getChildCount() > 0);
    }

    @Override
    public List<RadialMenuItem> getMenuItemsForNode(
            TalkBackService service, AccessibilityNodeInfoCompat node) {
        final CursorController cursorController = service.getCursorController();
        final CursorGranularity current = cursorController.getGranularityAt(node);
        final List<RadialMenuItem> items = new LinkedList<RadialMenuItem>();
        final List<CursorGranularity> granularities = CursorGranularityManager
                .getSupportedGranularities(service, node);
        final boolean hasWebContent = WebInterfaceUtils.hasNavigableWebContent(service, node);

        // Don't populate the menu if only object is supported.
        if (granularities.size() == 1) {
            return items;
        }

        final GranularityMenuItemClickListener clickListener =
                new GranularityMenuItemClickListener(service, node, hasWebContent);

        for (CursorGranularity granularity : granularities) {
            final RadialMenuItem item = new RadialMenuItem(
                    service, 0, granularity.keyId, 0, service.getString(granularity.resId));
            item.setOnMenuItemClickListener(clickListener);
            item.setCheckable(true);
            item.setChecked(granularity.equals(current));

            // Items are added in "natural" order, e.g. object first.
            items.add(item);
        }

        if (hasWebContent) {
            // Web content support navigation at a pseudo granularity for
            // entering special content like math or tables. This must be
            // special cased as it doesn't fit the semantics of an actual
            // granularity.
            final RadialMenuItem specialContent = new RadialMenuItem(service, 0,
                    R.id.pseudo_web_special_content, 0,
                    service.getString(R.string.granularity_pseudo_web_special_content));
            specialContent.setOnMenuItemClickListener(clickListener);
            items.add(specialContent);

        }

        return items;
    }

    @Override
    public CharSequence getUserFriendlyMenuName(Context context) {
        return context.getString(R.string.title_granularity);
    }

    @Override
    public boolean canCollapseMenu() {
        return true;
    }

    private static class GranularityMenuItemClickListener
            implements MenuItem.OnMenuItemClickListener {

        private final CursorController mCursorController;
        private final AccessibilityNodeInfoCompat mNode;
        private final boolean mHasWebContent;

        public GranularityMenuItemClickListener(
                TalkBackService service, AccessibilityNodeInfoCompat node, boolean hasWebContent) {
            mCursorController = service.getCursorController();
            mNode = AccessibilityNodeInfoCompat.obtain(node);
            mHasWebContent = hasWebContent;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            try {
                if (item == null) {
                    return false;
                }

                final int itemId = item.getItemId();

                if (itemId == R.id.pseudo_web_special_content) {
                    // If the user chooses to enter special web content, notify
                    // ChromeVox that the user entered this navigation mode and
                    // send further navigation movements at the default
                    // granularity.
                    mCursorController
                            .setGranularity(CursorGranularity.DEFAULT, false /* fromUser */);
                    WebInterfaceUtils.setSpecialContentModeEnabled(mNode, true);
                    return true;
                }

                final CursorGranularity granularity = CursorGranularity.fromKey(itemId);
                if (granularity == null) {
                    return false;
                } else if (mHasWebContent && granularity == CursorGranularity.DEFAULT) {
                    // When the user switches to default granularity, always
                    // inform ChromeVox of this change so it can exit special
                    // content navigation mode if applicable. Sending this even
                    // when that mode hasn't been entered is fine and is simply
                    // a no-op on the ChromeVox side.
                    WebInterfaceUtils.setSpecialContentModeEnabled(mNode, false);
                }

                return mCursorController.setGranularity(granularity, true /* fromUser */);
            } finally {
                mNode.recycle();
            }
        }
    }
}
