package com.google.android.marvin.talkback.menurules;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.MenuItem;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.NodeFilter;
import com.googlecode.eyesfree.widget.RadialMenu;
import com.googlecode.eyesfree.widget.RadialMenuItem;

import java.util.LinkedList;
import java.util.List;

/**
 * Rule for generating menu items related to ViewPager layouts.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class RuleViewPager implements NodeMenuRule {
    private static final NodeFilter FILTER_PAGED = new NodeFilter() {
        @Override
        public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
            return AccessibilityNodeInfoUtils.nodeMatchesAnyClassByType(
                    context, node, android.support.v4.view.ViewPager.class);
        }
    };

    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        AccessibilityNodeInfoCompat rootNode = null;
        AccessibilityNodeInfoCompat pagerNode = null;

        try {
            rootNode = AccessibilityNodeInfoUtils.getRoot(node);
            if (rootNode == null) {
                return false;
            }

            pagerNode = AccessibilityNodeInfoUtils.searchFromBfs(context, rootNode, FILTER_PAGED);
            if (pagerNode == null) {
                return false;
            }

            return true;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(rootNode, pagerNode);
        }
    }

    @Override
    public List<RadialMenuItem> getMenuItemsForNode(
            TalkBackService service, AccessibilityNodeInfoCompat node) {
        final LinkedList<RadialMenuItem> items = new LinkedList<RadialMenuItem>();

        AccessibilityNodeInfoCompat rootNode = null;
        AccessibilityNodeInfoCompat pagerNode = null;

        try {
            rootNode = AccessibilityNodeInfoUtils.getRoot(node);
            if (rootNode == null) {
                return items;
            }

            pagerNode = AccessibilityNodeInfoUtils.searchFromBfs(service, rootNode, FILTER_PAGED);
            if (pagerNode == null) {
                return items;
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    pagerNode, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)) {
                final RadialMenuItem prevPage = new RadialMenuItem(service, RadialMenu.NONE,
                        R.id.viewpager_breakout_prev_page, RadialMenu.NONE,
                        service.getString(R.string.title_viewpager_breakout_prev_page));
                items.add(prevPage);
            }

            if (AccessibilityNodeInfoUtils.supportsAnyAction(
                    pagerNode, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)) {
                final RadialMenuItem nextPage = new RadialMenuItem(service, RadialMenu.NONE,
                        R.id.viewpager_breakout_next_page, RadialMenu.NONE,
                        service.getString(R.string.title_viewpager_breakout_next_page));
                items.add(nextPage);
            }

            if (items.isEmpty()) {
                return items;
            }

            final AccessibilityNodeInfoCompat pagerNodeClone =
                    AccessibilityNodeInfoCompat.obtain(pagerNode);
            final ViewPagerItemClickListener itemClickListener =
                    new ViewPagerItemClickListener(pagerNodeClone);
            for (MenuItem item : items) {
                item.setOnMenuItemClickListener(itemClickListener);
            }

            return items;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(rootNode, pagerNode);
        }
    }

    @Override
    public CharSequence getUserFriendlyMenuName(Context context) {
        return context.getString(R.string.title_viewpager_controls);
    }

    @Override
    public boolean canCollapseMenu() {
        return true;
    }

    private static class ViewPagerItemClickListener implements MenuItem.OnMenuItemClickListener {
        private final AccessibilityNodeInfoCompat mNode;

        public ViewPagerItemClickListener(AccessibilityNodeInfoCompat node) {
            mNode = node;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (item == null) {
                mNode.recycle();
                return true;
            }

            final int itemId = item.getItemId();
            if (itemId == R.id.viewpager_breakout_prev_page) {
                mNode.performAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
            } else if (itemId == R.id.viewpager_breakout_next_page) {
                mNode.performAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
            } else {
                return false;
            }

            mNode.recycle();
            return true;
        }
    }
}
