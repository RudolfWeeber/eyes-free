/*
 * Copyright (C) 2012 Google Inc.
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

package com.marvin.rocklock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.marvin.rocklock.MusicGestureOverlay.Gesture;
import com.marvin.rocklock.MusicGestureOverlay.GestureListener;
import com.marvin.rocklock.navigation.SongPicker;

import java.util.ArrayList;

/**
 * A Fragment that handles bookmark management
 *
 * @author sainsley@google.com (Sam Ainsley)
 */
public class BookmarkFragment extends Fragment {

    private RockLockActivity mParent;
    private boolean mHasBookmarks;
    private int mCurrentPos;

    // UI elements
    private MusicGestureOverlay mGestureOverlay;

    /**
     * Inflate view and instantiate gesture listener
     *
     * @param inflater the LayoutInflater from the parent class
     * @param container the ViewGroup that will contain this view
     * @param savedInstanceState the parent's saved instance bundle
     * @return the created View
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.main, container, false);

        mGestureOverlay = (MusicGestureOverlay) view
                .findViewById(R.id.gestureLayer);
        mGestureOverlay
                .setGestureListener(new BookmarkGestureListener(), false);
        if (mHasBookmarks) {
            updateText();
        }

        return view;
    }

    /**
     * Instantiate pickers on attach
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (RockLockActivity) activity;
        refresh();
    }

    /**
     * Announce upcoming bookmark in given direction
     *
     * @param dir the direction
     * @return the bookmark name
     */
    private String peekBookmark(int dir) {
        int lastPos = mCurrentPos;
        String bookmark = goBookmark(dir);
        mCurrentPos = lastPos;
        return bookmark;
    }

    /**
     * Go to the next bookmark in a given direction
     *
     * @param dir the direction
     * @return the bookmark name
     */
    private String goBookmark(int dir) {
        ArrayList<Bookmark> bookmarks = mParent.getBookmarks();
        if (dir < 0 && --mCurrentPos < 0) {
            mCurrentPos = bookmarks.size() - 1;
        }
        if (dir > 0 && ++mCurrentPos > bookmarks.size() - 1) {
            mCurrentPos = 0;
        }
        Bookmark bookmark = bookmarks.get(mCurrentPos);
        return bookmark.getName(mParent);
    }

    /**
     * Deletes the current playlist
     */
    private void deleteBookmark() {

        // Confirm deletion
        AlertDialog.Builder alert = new AlertDialog.Builder(mParent);
        alert.setTitle(getString(R.string.delete_bookmark));
        alert.setMessage(getString(R.string.delete_bookmark_confirm));
        alert.setPositiveButton(getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mParent.getBookmarks().remove(mCurrentPos);
                        refresh();
                    }
                });

        alert.show();
    }

    private void refresh() {
        mHasBookmarks = !mParent.getBookmarks().isEmpty();
        mCurrentPos = 0;
        String info = updateText();
        mParent.speakAndDuck(info, true);
    }

    private String updateText() {
        mParent.clearIconDisplay();
        mParent.updateUpcoming("");
        if (mHasBookmarks) {
            // speak current bookmark
            String name = mParent.getBookmarks().get(mCurrentPos).getName(mParent);
            mParent.updateDisplayText(getString(R.string.bookmarks), name, false);
            return name;
        } else {
            String noBookmarks = getString(R.string.no_bookmarks);
            String instruct = getString(R.string.new_bookmark_instruct);
            mParent.updateDisplayText(noBookmarks, instruct, false);
            return noBookmarks + ", " + instruct;
        }
    }

    /**
     * Gesture listener for browser class
     */
    private class BookmarkGestureListener implements GestureListener {

        @Override
        public void onGestureChange(int gCur, int gPrev) {
            if (!mHasBookmarks) {
                return;
            }
            mParent.playVibration();
            switch (gCur) {
                case Gesture.UPRIGHT:
                case Gesture.UPLEFT:
                case Gesture.UP:
                    mParent.speakAndDuck(peekBookmark(SongPicker.DIRECTION_BACKWARD), true);
                    break;
                case Gesture.DOWNRIGHT:
                case Gesture.DOWNLEFT:
                case Gesture.DOWN:
                    mParent.speakAndDuck(peekBookmark(SongPicker.DIRECTION_FORWARD), true);
                    break;
                case Gesture.RIGHT:
                case Gesture.LEFT:
                    mParent.speakAndDuck(getString(R.string.delete_bookmark), true);
                    break;
            }
        }

        @Override
        public void onGestureFinish(int g) {
            if (!mHasBookmarks) {
                return;
            }
            mParent.playVibration();
            switch (g) {
                case Gesture.UPRIGHT:
                case Gesture.UPLEFT:
                case Gesture.UP:
                    goBookmark(SongPicker.DIRECTION_BACKWARD);
                    break;
                case Gesture.DOWNRIGHT:
                case Gesture.DOWNLEFT:
                case Gesture.DOWN:
                    goBookmark(SongPicker.DIRECTION_FORWARD);
                    break;
                case Gesture.RIGHT:
                case Gesture.LEFT:
                    deleteBookmark();
                    break;
                case Gesture.CENTER:
                    Bookmark current = mParent.getBookmarks().get(mCurrentPos);
                    mParent.mPlayer.playBookmark(current);
                    mParent.onBackPressed();
                    mParent.speakAndDuck(getString(R.string.player_announce), true);
                    return;
            }
            updateText();
        }

        @Override
        public void onGestureStart(int g) {
            mParent.playVibration();
            mParent.poke();
        }

        @Override
        public void onGesture2Change(int gCur, int gPrev) {
            mParent.playVibration();
            onGestureChange(gCur, gPrev);
        }

        @Override
        public void onGesture2Finish(int g) {
            mParent.playVibration();
            onGestureFinish(g);
        }

        @Override
        public void onGestureHold(int g) {
            // No need for preview
        }

        @Override
        public void onGestureHold2(int g) {
            // No need for preview
        }
    }
}
