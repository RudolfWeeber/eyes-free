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

package com.googlecode.eyesfree.widget;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Implements a radial menu with up to four hot corners.
 *
 * @see Menu
 */
public class RadialMenu implements Menu {
    public static final int ORDER_NW = 0;
    public static final int ORDER_NE = 1;
    public static final int ORDER_SW = 2;
    public static final int ORDER_SE = 3;

    public static final int ID_CANCEL = -1;

    private final Context mContext;
    private final DialogInterface mParent;
    private final List<RadialMenuItem> mItems;
    private final SparseArray<RadialMenuItem> mHotCorners;

    private MenuItem.OnMenuItemClickListener mListener;
    private RadialMenuItem.OnMenuItemSelectionListener mSelectionListener;
    private MenuLayoutListener mLayoutListener;

    @SuppressWarnings("unused")
    private boolean mQwertyMode;

    /**
     * Creates a new radial menu with the specified parent dialog interface.
     *
     * @param context
     * @param parent
     */
    public RadialMenu(Context context, DialogInterface parent) {
        mContext = context;
        mParent = parent;
        mItems = new ArrayList<RadialMenuItem>();
        mHotCorners = new SparseArray<RadialMenuItem>();

        // Set default properties.
        mQwertyMode = false;
    }

    public void setLayoutListener(MenuLayoutListener layoutListener) {
        mLayoutListener = layoutListener;
    }

    /**
     * Sets the default click listener for menu items. This change is propagated
     * to sub-menus.
     *
     * @param listener The default click listener for menu items.
     */
    public void setDefaultListener(MenuItem.OnMenuItemClickListener listener) {
        mListener = listener;

        // Propagate this change to all sub-menus.
        for (RadialMenuItem item : mItems) {
            if (item.hasSubMenu()) {
                final RadialSubMenu subMenu = item.getSubMenu();
                subMenu.setDefaultListener(listener);
            }
        }
    }

    /**
     * Sets the default selection listener for menu items. This change is
     * propagated to sub-menus.
     *
     * @param selectionListener The default selection listener for menu items.
     */
    public void setDefaultSelectionListener(
            RadialMenuItem.OnMenuItemSelectionListener selectionListener) {
        mSelectionListener = selectionListener;

        // Propagate this change to all sub-menus.
        for (RadialMenuItem item : mItems) {
            if (item.hasSubMenu()) {
                final RadialSubMenu subMenu = item.getSubMenu();
                subMenu.setDefaultSelectionListener(selectionListener);
            }
        }
    }

    @Override
    public RadialMenuItem add(int titleRes) {
        return add(NONE, NONE, NONE, titleRes);
    }

    @Override
    public RadialMenuItem add(CharSequence title) {
        return add(NONE, NONE, NONE, title);
    }

    @Override
    public RadialMenuItem add(int groupId, int itemId, int order, int titleRes) {
        CharSequence title = mContext.getText(titleRes);

        return add(groupId, itemId, order, title);
    }

    @Override
    public RadialMenuItem add(int groupId, int itemId, int order, CharSequence title) {
        final RadialMenuItem item = new RadialMenuItem(mContext, groupId, itemId, order, title);

        if (groupId == R.id.group_corners) {
            item.setHotCorner(true);
            mHotCorners.put(order, item);
        } else {
            mItems.add(item);
        }

        onLayoutChanged();

        return item;
    }

    @Override
    public int addIntentOptions(int groupId, int itemId, int order, ComponentName caller,
            Intent[] specifics, Intent intent, int flags, MenuItem[] outSpecificItems) {
        final PackageManager manager = mContext.getPackageManager();
        final List<ResolveInfo> infoList = manager.queryIntentActivityOptions(caller, specifics,
                intent, 0);

        if ((flags & FLAG_APPEND_TO_GROUP) == 0) {
            removeGroup(groupId);
        }

        int i = 0;

        for (ResolveInfo info : infoList) {
            final Drawable icon = info.loadIcon(manager);
            final CharSequence title = info.loadLabel(manager);
            final MenuItem item = add(groupId, itemId, order, title);

            item.setIcon(icon);

            if (i < outSpecificItems.length) {
                outSpecificItems[i++] = item;
            } else {
                throw new ArrayIndexOutOfBoundsException();
            }
        }

        return infoList.size();
    }

    @Override
    public RadialSubMenu addSubMenu(int titleRes) {
        return addSubMenu(NONE, NONE, NONE, titleRes);
    }

    @Override
    public RadialSubMenu addSubMenu(CharSequence title) {
        return addSubMenu(NONE, NONE, NONE, title);
    }

    @Override
    public RadialSubMenu addSubMenu(int groupId, int itemId, int order, int titleRes) {
        final CharSequence title = mContext.getText(titleRes);

        return addSubMenu(groupId, itemId, order, title);
    }

    @Override
    public RadialSubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title) {
        final RadialSubMenu submenu = new RadialSubMenu(mContext, mParent, groupId, itemId, order,
                title);
        final RadialMenuItem item = submenu.getItem();

        mItems.add(item);

        // Propagate the current default listeners.
        submenu.setDefaultListener(mListener);
        submenu.setDefaultSelectionListener(mSelectionListener);

        onLayoutChanged();

        return submenu;
    }

    @Override
    public void clear() {
        mItems.clear();
    }

    @Override
    public void close() {
        mParent.dismiss();
    }

    @Override
    public RadialMenuItem findItem(int id) {
        if (id == ID_CANCEL) {
            return null;
        }

        for (RadialMenuItem item : mItems) {
            if (item.getItemId() == id) {
                return item;
            }
        }

        for (int i = 0; i < mHotCorners.size(); i++) {
            final RadialMenuItem item = mHotCorners.valueAt(i);

            if (item.getItemId() == id) {
                return item;
            }
        }

        return null;
    }

    @Override
    public RadialMenuItem getItem(int index) {
        return mItems.get(index);
    }

    public int indexOf(RadialMenuItem item) {
        return mItems.indexOf(item);
    }

    /**
     * Gets the hot corner menu item with the given group ID.
     *
     * @param groupId The corner group ID of the item to be returned. One of:
     *            <ul>
     *            <li>{@link RadialMenu#ORDER_NW}
     *            <li>{@link RadialMenu#ORDER_NE}
     *            <li>{@link RadialMenu#ORDER_SE}
     *            <li>{@link RadialMenu#ORDER_SW}
     *            </ul>
     * @return The hot corner menu item.
     */
    public RadialMenuItem getHotCorner(int groupId) {
        return mHotCorners.get(groupId);
    }

    /**
     * Returns the rotation of a hot corner in degrees.
     *
     * @param groupId The corner group ID of the item to be returned. One of:
     *            <ul>
     *            <li>{@link RadialMenu#ORDER_NW}
     *            <li>{@link RadialMenu#ORDER_NE}
     *            <li>{@link RadialMenu#ORDER_SE}
     *            <li>{@link RadialMenu#ORDER_SW}
     *            </ul>
     * @return The rotation of a hot corner in degrees.
     */
    /* package */static float getHotCornerRotation(int groupId) {
        final float rotation;

        switch (groupId) {
            case RadialMenu.ORDER_NW:
                rotation = 135;
                break;
            case RadialMenu.ORDER_NE:
                rotation = -135;
                break;
            case RadialMenu.ORDER_SE:
                rotation = -45;
                break;
            case RadialMenu.ORDER_SW:
                rotation = 45;
                break;
            default:
                rotation = 0;
        }

        return rotation;
    }

    /**
     * Returns the on-screen location of a hot corner as percentages of the
     * screen size. The resulting point coordinates should be multiplied by
     * screen width and height.
     *
     * @param groupId The corner group ID of the item to be returned. One of:
     *            <ul>
     *            <li>{@link RadialMenu#ORDER_NW}
     *            <li>{@link RadialMenu#ORDER_NE}
     *            <li>{@link RadialMenu#ORDER_SE}
     *            <li>{@link RadialMenu#ORDER_SW}
     *            </ul>
     * @return The on-screen location of a hot corner as percentages of the
     *         screen size.
     */
    /* package */static PointF getHotCornerLocation(int groupId) {
        final float x;
        final float y;

        switch (groupId) {
            case RadialMenu.ORDER_NW:
                x = 0;
                y = 0;
                break;
            case RadialMenu.ORDER_NE:
                x = 1;
                y = 0;
                break;
            case RadialMenu.ORDER_SE:
                x = 1;
                y = 1;
                break;
            case RadialMenu.ORDER_SW:
                x = 0;
                y = 1;
                break;
            default:
                return null;
        }

        return new PointF(x, y);
    }

    @Override
    public void removeItem(int id) {
        final MenuItem item = findItem(id);

        if (item == null) {
            return;
        }

        mItems.remove(item);

        onLayoutChanged();
    }

    /**
     * Removes the hot corner item with the given group ID.
     *
     * @param groupId The corner group ID of the item to be removed. One of:
     *            <ul>
     *            <li>{@link RadialMenu#ORDER_NW}
     *            <li>{@link RadialMenu#ORDER_NE}
     *            <li>{@link RadialMenu#ORDER_SE}
     *            <li>{@link RadialMenu#ORDER_SW}
     *            </ul>
     */
    public void removeHotCorner(int groupId) {
        final RadialMenuItem hotCorner = mHotCorners.get(groupId);

        if (hotCorner == null) {
            return;
        }

        hotCorner.setHotCorner(false);
        mHotCorners.put(groupId, null);

        onLayoutChanged();
    }

    @Override
    public boolean hasVisibleItems() {
        for (MenuItem item : mItems) {
            if (item.isVisible()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the {@link MenuItem} that responds to a given shortcut key.
     *
     * @param keyCode
     * @param event
     * @return the {@link MenuItem} that responds to a given shortcut key
     */
    private RadialMenuItem getItemForShortcut(int keyCode, KeyEvent event) {
        for (RadialMenuItem item : mItems) {
            if (item.getAlphabeticShortcut() == keyCode || item.getNumericShortcut() == keyCode) {
                return item;
            }
        }

        return null;
    }

    @Override
    public boolean isShortcutKey(int keyCode, KeyEvent event) {
        final MenuItem item = getItemForShortcut(keyCode, event);

        return (item != null);
    }

    /**
     * Performs the selection action associated with a particular
     * {@link RadialMenuItem}.
     *
     * @param item
     * @param flags
     * @return {@code true} if the menu item performs an action
     */
    private boolean selectMenuItem(RadialMenuItem item, int flags) {
        if (item == null || !item.onSelectionPerformed()) {
            return mSelectionListener != null && mSelectionListener.onMenuItemSelection(item);
        }

        return true;
    }

    public boolean selectIdentifierAction(int id, int flags) {
        final RadialMenuItem item = findItem(id);

        return selectMenuItem(item, flags);
    }

    public boolean selectShortcut(int keyCode, KeyEvent event, int flags) {
        final RadialMenuItem item = getItemForShortcut(keyCode, event);

        return selectMenuItem(item, flags);
    }

    public boolean clearSelection(int flags) {
        return selectMenuItem(null, flags);
    }

    /**
     * Performs the action associated with a particular {@link RadialMenuItem},
     * or null if the menu was cancelled.
     *
     * @param item
     * @param flags
     * @return {@code true} if the menu item performs an action
     */
    private boolean performMenuItem(RadialMenuItem item, int flags) {
        if (item == null || !item.onClickPerformed()) {
            if (mListener == null || !mListener.onMenuItemClick(item)) {
                return false;
            }
        }

        if (item == null || (flags & FLAG_PERFORM_NO_CLOSE) == 0 || (flags & FLAG_ALWAYS_PERFORM_CLOSE) != 0) {
            close();
        }

        return true;
    }

    @Override
    public boolean performIdentifierAction(int id, int flags) {
        final RadialMenuItem item = findItem(id);

        return performMenuItem(item, flags);
    }

    @Override
    public boolean performShortcut(int keyCode, KeyEvent event, int flags) {
        final RadialMenuItem item = getItemForShortcut(keyCode, event);

        return performMenuItem(item, flags);
    }

    @Override
    public void removeGroup(int group) {
        final List<MenuItem> removeItems = new LinkedList<MenuItem>();

        for (MenuItem item : mItems) {
            if (item.getGroupId() == group) {
                removeItems.add(item);
            }
        }

        mItems.removeAll(removeItems);

        if (removeItems.size() > 0) {
            onLayoutChanged();
        }
    }

    @Override
    public void setGroupCheckable(int group, boolean checkable, boolean exclusive) {
        boolean layoutChanged = false;

        for (MenuItem item : mItems) {
            if (item.getGroupId() == group) {
                item.setCheckable(checkable);
                layoutChanged = true;
            }
        }

        if (layoutChanged) {
            onLayoutChanged();
        }
    }

    @Override
    public void setGroupEnabled(int group, boolean enabled) {
        boolean layoutChanged = false;

        for (MenuItem item : mItems) {
            if (item.getGroupId() == group) {
                item.setEnabled(enabled);
                layoutChanged = true;
            }
        }

        if (layoutChanged) {
            onLayoutChanged();
        }
    }

    @Override
    public void setGroupVisible(int group, boolean visible) {
        boolean layoutChanged = false;

        for (MenuItem item : mItems) {
            if (item.getGroupId() == group) {
                item.setVisible(visible);
                layoutChanged = true;
            }
        }

        if (layoutChanged) {
            onLayoutChanged();
        }
    }

    @Override
    public void setQwertyMode(boolean isQwerty) {
        mQwertyMode = isQwerty;
    }

    @Override
    public int size() {
        return mItems.size();
    }

    protected void onLayoutChanged() {
        if (mLayoutListener != null) {
            mLayoutListener.onLayoutChanged();
        }
    }

    public interface MenuLayoutListener {
        public void onLayoutChanged();
    }
}
