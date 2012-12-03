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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.ActionProvider;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;

@TargetApi(14)
public class RadialMenuItem implements MenuItem {
    private final Context mContext;
    private final RadialContextMenuInfo mMenuInfo;

    private int mGroupId;
    private int mItemId;
    private int mOrder;
    private CharSequence mTitle;

    private Drawable mIcon;
    private CharSequence mTitleCondensed;
    private Intent mIntent;
    private char mAlphaShortcut;
    private char mNumericShortcut;
    private boolean mVisible;
    private boolean mCheckable;
    private boolean mChecked;
    private boolean mEnabled;
    private boolean mHotCorner;

    private RadialSubMenu mSubMenu;
    private OnMenuItemClickListener mListener;
    private OnMenuItemSelectionListener mSelectionListener;

    /* package */float offset;


    /**
     * Creates a new menu item that represents a sub-menu.
     *
     * @param context The parent context.
     * @param groupId The menu item's group identifier.
     * @param itemId The menu item's identifier (should be unique).
     * @param order The order of this item.
     * @param title The text to be displayed for this menu item.
     * @param subMenu The sub-menu represented by this menu item.
     */
    /* package */RadialMenuItem(Context context, int groupId, int itemId,
            int order, CharSequence title, RadialSubMenu subMenu) {
        this(context, groupId, itemId, order, title);

        mSubMenu = subMenu;
    }

    /**
     * Creates a new menu item.
     *
     * @param context The parent context.
     * @param groupId The menu item's group identifier.
     * @param itemId The menu item's identifier (should be unique).
     * @param order The order of this item.
     * @param title The text to be displayed for this menu item.
     */
    public RadialMenuItem(Context context, int groupId, int itemId, int order, CharSequence title) {
        mContext = context;
        mMenuInfo = new RadialContextMenuInfo();
        mGroupId = groupId;
        mItemId = itemId;
        mTitle = title;

        // Set default properties.
        mVisible = true;
        mCheckable = false;
        mChecked = false;
        mEnabled = true;
    }

    @Override
    public char getAlphabeticShortcut() {
        return mAlphaShortcut;
    }

    @Override
    public int getGroupId() {
        return mGroupId;
    }

    @Override
    public Drawable getIcon() {
        return mIcon;
    }

    @Override
    public Intent getIntent() {
        return mIntent;
    }

    @Override
    public int getItemId() {
        return mItemId;
    }

    @Override
    public ContextMenuInfo getMenuInfo() {
        return mMenuInfo;
    }

    @Override
    public char getNumericShortcut() {
        return mNumericShortcut;
    }

    @Override
    public int getOrder() {
        return mOrder;
    }

    @Override
    public RadialSubMenu getSubMenu() {
        return mSubMenu;
    }

    @Override
    public CharSequence getTitle() {
        return mTitle;
    }

    @Override
    public CharSequence getTitleCondensed() {
        return mTitleCondensed;
    }

    @Override
    public boolean hasSubMenu() {
        return (mSubMenu != null);
    }

    @Override
    public boolean isCheckable() {
        return mCheckable;
    }

    @Override
    public boolean isChecked() {
        return mCheckable && mChecked;
    }

    @Override
    public boolean isEnabled() {
        return mEnabled;
    }

    @Override
    public boolean isVisible() {
        return mVisible;
    }

    /**
     * @return {@code true} if this menu is a hot corner.
     */
    public boolean isHotCorner() {
        return mHotCorner;
    }

    /**
     * Attempts to perform this item's click action.
     *
     * @return {@code true} if this item performs an action
     */
    public boolean onClickPerformed() {
        if (mCheckable) {
            mChecked = !mChecked;
        }

        if (mListener != null) {
            return mListener.onMenuItemClick(this);
        }

        return false;
    }

    /**
     * Attempts to perform this item's selection action.
     *
     * @return {@code true} if this item performs an action
     */
    public boolean onSelectionPerformed() {
        if (mSelectionListener != null) {
            return mSelectionListener.onMenuItemSelection(this);
        }

        return false;
    }

    @Override
    public MenuItem setAlphabeticShortcut(char alphaChar) {
        mAlphaShortcut = alphaChar;

        return this;
    }

    @Override
    public MenuItem setCheckable(boolean checkable) {
        mCheckable = checkable;

        return this;
    }

    @Override
    public MenuItem setChecked(boolean checked) {
        mChecked = checked;

        return this;
    }

    @Override
    public MenuItem setEnabled(boolean enabled) {
        mEnabled = enabled;

        return this;
    }

    @Override
    public MenuItem setIcon(Drawable icon) {
        mIcon = icon;

        return this;
    }

    @Override
    public MenuItem setIcon(int iconRes) {
        if (iconRes == 0) {
            mIcon = null;
        } else {
            mIcon = mContext.getResources().getDrawable(iconRes);
        }

        return this;
    }

    @Override
    public MenuItem setIntent(Intent intent) {
        mIntent = intent;

        return this;
    }

    @Override
    public MenuItem setNumericShortcut(char numericChar) {
        mNumericShortcut = numericChar;

        return this;
    }

    @Override
    public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener menuItemClickListener) {
        mListener = menuItemClickListener;

        return this;
    }

    /**
     * Sets the listener that will receive selection callbacks.
     *
     * @param menuItemSelectionListener The listener to set.
     * @return This item so additional setters can be called.
     */
    public MenuItem setOnMenuItemSelectionListener(
            OnMenuItemSelectionListener menuItemSelectionListener) {
        mSelectionListener = menuItemSelectionListener;

        return this;
    }

    @Override
    public MenuItem setShortcut(char numericChar, char alphaChar) {
        mNumericShortcut = numericChar;
        mAlphaShortcut = alphaChar;

        return this;
    }

    @Override
    public MenuItem setTitle(CharSequence title) {
        mTitle = title;

        return this;
    }

    @Override
    public MenuItem setTitle(int titleRes) {
        mTitle = mContext.getText(titleRes);

        return this;
    }

    @Override
    public MenuItem setTitleCondensed(CharSequence titleCondensed) {
        mTitleCondensed = titleCondensed;
        return this;
    }

    @Override
    public MenuItem setVisible(boolean visible) {
        mVisible = visible;

        return this;
    }

    /**
     * Sets whether this menu item is a hot corner.
     *
     * @param hotCorner Whether this item is a hot corner.
     * @return This item so additional setters can be called.
     */
    /* package */MenuItem setHotCorner(boolean hotCorner) {
        mHotCorner = true;

        return this;
    }

    @Override
    public boolean collapseActionView() {
        return false;
    }

    @Override
    public boolean expandActionView() {
        return false;
    }

    @TargetApi(14)
    @Override
    public ActionProvider getActionProvider() {
        return null;
    }

    @Override
    public View getActionView() {
        return null;
    }

    @Override
    public boolean isActionViewExpanded() {
        return false;
    }

    @TargetApi(14)
    @Override
    public MenuItem setActionProvider(ActionProvider actionProvider) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MenuItem setActionView(View view) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MenuItem setActionView(int resId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MenuItem setOnActionExpandListener(OnActionExpandListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setShowAsAction(int actionEnum) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MenuItem setShowAsActionFlags(int actionEnum) {
        throw new UnsupportedOperationException();
    }

    /**
     * Interface definition for a callback to be invoked when a menu item is
     * selected.
     *
     * @author alanv@google.com (Alan Viverette)
     */
    public interface OnMenuItemSelectionListener {
        /**
         * Called when a menu item has been selected. This is the first code
         * that is executed; if it returns true, no other callbacks will be
         * executed.
         *
         * @param item The menu item that was selected.
         * @return {@code true} to consume this selection event and prevent
         *         other listeners from executing.
         */
        public boolean onMenuItemSelection(RadialMenuItem item);
    }

    /**
     * This class doesn't actually do anything.
     *
     * @author alanv@google.com (Alan Viverette)
     */
    private static class RadialContextMenuInfo implements ContextMenuInfo {
        // Empty class.
    }
}
