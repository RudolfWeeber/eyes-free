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

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.ListView;

/**
 * @author alanv@google.com (Alan Viverette)
 */
public class SelectionFinder {
    /** Used for hit rectangle calculations. */
    private static final Rect mTempFrame = new Rect();

    /**
     * Sets the current selection to be the {@link View} at the given
     * coordinate.
     *
     * @param currentSelection The currently selected {@link View}.
     * @param root The top-most {@link View} in the search space. Must be non-null.
     * @param rootX Root-relative X coordinate.
     * @param rootY Root-relative Y coordinate.
     */
    public static View getSelectionAtPoint(View currentSelection, View root, int rootX, int rootY) {
        if (root == null) {
            throw new IllegalArgumentException("Root view must be non-null.");
        }

        View searchView = currentSelection == null ? root : currentSelection;

        searchView.getGlobalVisibleRect(mTempFrame);

        // Adjust root-relative coordinates to be global.
        int globalX = rootX + root.getLeft() - root.getScrollX();
        int globalY = rootY + root.getTop() - root.getScrollY();

        // If the search rect doesn't contain the point, use its parent.
        while (!mTempFrame.contains(globalX, globalY)) {
            ViewParent parent = searchView.getParent();

            // If the parent isn't a View, we've exhaused the search space.
            if (parent instanceof View && parent != root.getParent()) {
                searchView = (View) parent;
                searchView.getGlobalVisibleRect(mTempFrame);
            } else {
                return null;
            }
        }

        // Adjust global coordinates to be selection-parent-relative.
        int localX = globalX - mTempFrame.left + searchView.getLeft();
        int localY = globalY - mTempFrame.top + searchView.getTop();

        // Search for selection from highest containing view.
        View selection = searchSelectionView(searchView, localX, localY);

        return selection;
    }

    /**
     * Finds the {@link View} within a {@link View} that contains the given
     * parent-relative coordinates. May return the supplied view.
     *
     * @param v The {@link View} to search.
     * @param parentX Parent-relative X coordinate.
     * @param parentY Parent-relative Y coordinate.
     * @return The {@link View} at (xf,xy) or <code>null</code> if none.
     */
    private static View searchSelectionView(View v, int parentX, int parentY) {
        // The view must be visible.
        if (v.getVisibility() != View.VISIBLE && v.getAnimation() == null) {
            return null;
        }

        v.getHitRect(mTempFrame);

        // The view must contain the point.
        if (!mTempFrame.contains(parentX, parentY)) {
            return null;
        }

        // Adjust to view-relative coordinates for child views.
        final int x = parentX - v.getLeft() + v.getScrollX();
        final int y = parentY - v.getTop() + v.getScrollY();

        // Delegate to class-specific search methods.
        if (v instanceof AbsListView) {
            return searchListFocusables((AbsListView) v, x, y);
        } else if (v instanceof ViewGroup) {
            return searchSelectionInGroup((ViewGroup) v, x, y);
        //} else if (v instanceof TextView) {
        //    return v;
        } else if (v.isFocusable() || v.isClickable()) {
            return v;
        } else if (v.getParent() instanceof AbsListView) {
            AbsListView parent = (AbsListView) v.getParent();

            // List items aren't focusable, so if we try to search outward from
            // a currently selected list item then we'll end up here.
            if (parent.getPositionForView(v) != AbsListView.INVALID_POSITION) {
                return v;
            }
        }

        return null;
    }

    /**
     * Finds the {@link View} within a {@link ViewGroup} that contains the given
     * coordinates.
     *
     * @param v The {@link ViewGroup} to search.
     * @param x View-relative X coordinate.
     * @param y View-relative Y coordinate.
     * @return The {@link View} at (xf,xy) or <code>null</code> if none.
     */
    private static View searchSelectionInGroup(ViewGroup v, int x, int y) {
        int focusability = v.getDescendantFocusability();

        // TODO(alanv): This is necessary for things like widgets where the
        // TextViews should not be focusable, but messes with things like the
        // Launcher that handle focus events on their own.

        /* if (focusability == ViewGroup.FOCUS_BEFORE_DESCENDANTS) {
            return v.isFocusable() ? v : searchSelectionInGroupHelper(v, x, y);
        } else */
        if (focusability == ViewGroup.FOCUS_BLOCK_DESCENDANTS) {
            return v.isFocusable() ? v : null;
        } else {
            View focused = searchSelectionInGroupHelper(v, x, y);
            return focused != null ? focused : v.isFocusable() ? v : null;
        }
    }

    /**
     * Searches through a {@link ViewGroup}'s children to find the {@link View}
     * at the given coordinates.
     *
     * @param v The {@link ViewGroup} to search.
     * @param x View-relative X coordinate.
     * @param y View-relative Y coordinate.
     * @return The {@link View} at (xf,xy) or <code>null</code> if none.
     */
    private static View searchSelectionInGroupHelper(ViewGroup v, int x, int y) {
        View result = null;

        // Search within this view's children for a selection candidate.
        for (int i = v.getChildCount() - 1; result == null && i >= 0; i--) {
            result = searchSelectionView(v.getChildAt(i), x, y);
        }

        return result;
    }

    /**
     * Finds the {@link View} within an {@link AbsListView} that contains the
     * given coordinates.
     *
     * @param v The {@link AbsListView} to search.
     * @param x View-relative X coordinate.
     * @param y View-relative Y coordinate.
     * @return The {@link View} at (xf,xy) or <code>null</code> if none.
     */
    private static View searchListFocusables(AbsListView v, int x, int y) {
        final int position = v.pointToPosition(x, y);

        // If the view contains a child at (x, y) then return its view.
        if (position != ListView.INVALID_POSITION) {
            return v.getChildAt(position - v.getFirstVisiblePosition());
        }

        // Even if the ListView is focusable, we don't actually want to focus on it.
        return null;
    }
}
