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
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import com.marvin.rocklock.MusicGestureOverlay.Gesture;
import com.marvin.rocklock.MusicGestureOverlay.GestureListener;
import com.marvin.rocklock.navigation.SongNavigator;
import com.marvin.rocklock.navigation.SongPicker;

import java.util.ArrayList;

/**
 * A Fragment that handles playlist management
 *
 * @author sainsley@google.com (Sam Ainsley)
 */
public class PlaylistFragment extends Fragment {

    private RockLockActivity mParent;
    private SongNavigator mNavigator;
    private boolean mHasPlaylists;

    // Playlist building logic
    private boolean mBuildPlaylistMode;
    private ArrayList<String> mPlaylistBuilder;
    private String mPlaylistName;

    // UI elements
    private MusicGestureOverlay mGestureOverlay;
    private ImageView mIconDisplay;
    private boolean mIsScrolling;

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

        mBuildPlaylistMode = false;

        View view = inflater.inflate(R.layout.main, container, false);

        // Visual feedback for sighted users
        mIconDisplay = (ImageView) view.findViewById(R.id.gestureIcon);

        mGestureOverlay = (MusicGestureOverlay) view.findViewById(R.id.gestureLayer);
        mGestureOverlay.setGestureListener(new PlaylistGestureListener(), true);
        updateText();
        mParent.clearIconDisplay();
        mParent.updateUpcoming(getString(R.string.playlist_nav_instructions));

        return view;
    }

    /**
     * Instantiate pickers on attach
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (RockLockActivity) activity;
        mNavigator = new SongNavigator(mParent);
        refresh();
    }

    /**
     * Save music pickers on pause
     */
    @Override
    public void onPause() {
        super.onPause();
        mNavigator.save();
    }

    /**
     * Restores music pickers on resume and sets current picker is it hasn't
     * already been set.
     */
    @Override
    public void onResume() {
        super.onResume();
        mNavigator.restore();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Tell user that playlist building was cancelled
        if (mBuildPlaylistMode) {
            mParent.speakAndDuck(getString(R.string.playlist_cancelled), true);
        }
        mParent.speakAndDuck(getString(R.string.player_announce), false);
    }

    //
    // Playlist Methods
    //

    /**
     * Switches to playlist build mode and prompts user for playlist name
     */
    private void buildPlaylist() {

        AlertDialog.Builder alert = new AlertDialog.Builder(mParent);
        alert.setTitle(getString(R.string.new_playlist));
        // TODO(sainsley): add default name based on
        // number of playlists in directory
        alert.setMessage(getString(R.string.enter_playlist_name));

        final EditText input = new EditText(mParent);
        alert.setView(input);

        alert.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                mPlaylistName = input.getText().toString();
                mBuildPlaylistMode = true;
                mPlaylistBuilder = new ArrayList<String>();
                mParent.speakAndDuck(getString(R.string.playlist_instructions), true);
                // Start out browsing songs
                mNavigator.setPickerMode(SongNavigator.MODE_TRACK);
                mNavigator.peekMode(0, false);
                mNavigator.speakCurrentTrack(false);
                updateText();

                return;
            }
        });

        alert.show();
    }

    /**
     * Adds current song to playlist
     */
    private void addToPlaylist() {
        String id = mNavigator.getCurrentSongId();
        if (id != null) {
            mPlaylistBuilder.add(id);
            mParent.mPlayer.speakAndDuck(getString(R.string.added), true);
        }
    }

    /**
     * Saves current playlist
     */
    private void savePlaylist() {
        mParent.mPlayer.speakAndDuck(getString(R.string.saving), true);
        PlaylistUtils.writePlaylist(mParent, mPlaylistName, mPlaylistBuilder);
        mBuildPlaylistMode = false;
        mParent.mPlayer.speakAndDuck(
                String.format("%s %s", mPlaylistName, getString(R.string.saved)), true);
        refresh();
    }

    /**
     * Deletes the current playlist
     */
    private void deletePlaylist() {

        AlertDialog.Builder alert = new AlertDialog.Builder(mParent);
        alert.setTitle(getString(R.string.delete_playlist));
        // TODO(sainsley): add default name based on
        // number of playlists in directory
        alert.setMessage(getString(R.string.delete_playlist_confirm));

        alert.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                int id = mNavigator.getGroupId();
                PlaylistUtils.deletePlaylist(mParent, id);
                mParent.speakAndDuck("Deleted", true);
                refresh();
                return;
            }
        });

        alert.show();

    }

    private void refresh() {
        mNavigator.setPickerMode(SongNavigator.MODE_PLAYLIST);
        mHasPlaylists = mNavigator.hasMusic();
        if (mHasPlaylists) {
            mNavigator.speakCurrentGroup(false);
            if (mGestureOverlay != null) {
                updateText();
            }
        } else {
            String noPlaylists = getString(R.string.no_playlists);
            String instruct = getString(R.string.new_playlist_instruct);
            mParent.speakAndDuck(noPlaylists + ", " + instruct, false);
            if (mGestureOverlay != null) {
                updateText();
            }
        }
    }

    // update display text
    private void updateText() {
        mParent.updateDisplayText(
                getString(R.string.playlists), mNavigator.getCurrentSongInfo(false), false);
    }

    /**
     * Gesture listener for browser class
     */
    private class PlaylistGestureListener implements GestureListener {

        @Override
        public void onGestureChange(int gCur, int gPrev) {
            mIsScrolling = false;
            mParent.playVibration();
            // Handle wrap-around from Gesture.LEFT to Gesture.UPLEFT and vice
            // versa, but we still want to treat fresh UPLEFT gestures as linear
            boolean circular = Math.abs(gCur - gPrev) <= 1 && gCur != Gesture.UPLEFT;
            if (gCur == Gesture.LEFT && gPrev == Gesture.UPLEFT) {
                circular = true;
            } else if (gCur == Gesture.UPLEFT && gPrev == Gesture.LEFT) {
                circular = true;
            }
            if (circular) {
                mIsScrolling = true;
                int dir;
                Resources res = getResources();
                if (gCur < gPrev) {
                    dir = SongPicker.DIRECTION_BACKWARD;
                    mIconDisplay.setImageDrawable(res
                            .getDrawable(R.drawable.rewind));
                } else {
                    dir = SongPicker.DIRECTION_FORWARD;
                    mIconDisplay.setImageDrawable(res
                            .getDrawable(R.drawable.fastforward));
                }
                mNavigator.browseAdjacentTrack(dir, true);
                updateText();
            } else {
                mIconDisplay.setImageDrawable(null);
                switch (gCur) {
                    case Gesture.UP:
                        mNavigator.peekPrevGroup();
                        break;
                    case Gesture.UPRIGHT:
                        break;
                    case Gesture.RIGHT:
                        if (!mBuildPlaylistMode) {
                            mParent.speakAndDuck(getString(R.string.new_playlist), true);
                        } else {
                            mNavigator.peekNextTrack();
                        }
                        break;
                    case Gesture.DOWNRIGHT:
                        break;
                    case Gesture.DOWN:
                        mNavigator.peekNextGroup();
                        break;
                    case Gesture.DOWNLEFT:
                        break;
                    case Gesture.LEFT:
                        if (!mBuildPlaylistMode) {
                            mParent.speakAndDuck(getString(R.string.delete_playlist), true);
                        } else {
                            mNavigator.peekPrevTrack();
                        }
                        break;
                    case Gesture.UPLEFT:
                        break;
                    default:
                        break;
                }
            }
        }

        @Override
        public void onGestureFinish(int g) {
            if (mIsScrolling) {
                mIsScrolling = false;
                return;
            }
            mParent.playVibration();
            // Regular browsing
            switch (g) {
                case Gesture.LEFT:
                    if (!mBuildPlaylistMode && mHasPlaylists) {
                        deletePlaylist();
                    } else {
                        mNavigator.browseAdjacentTrack(SongPicker.DIRECTION_BACKWARD, true);
                    }
                    break;
                case Gesture.RIGHT:
                    if (!mBuildPlaylistMode) {
                        buildPlaylist();
                    } else {
                        mNavigator.browseAdjacentTrack(SongPicker.DIRECTION_FORWARD, true);
                    }
                    break;
                case Gesture.DOUBLE_TAP:
                    if (mBuildPlaylistMode) {
                        savePlaylist();
                        return;
                    }
            // intentional fall through
                case Gesture.CENTER:
                    if (mBuildPlaylistMode) {
                        addToPlaylist();
                        return;
                    } else if (mHasPlaylists) {
                        mParent.mPlayer.moveTo(SongNavigator.MODE_PLAYLIST,
                                mNavigator.getGroupIdx(), mNavigator.getSongIdx());
                    }
                    mParent.onBackPressed();
                    return;
                case Gesture.UPLEFT:
                case Gesture.UP:
                    mNavigator.browseAdjacentGroup(SongPicker.DIRECTION_BACKWARD, true);
                    break;
                case Gesture.DOWNRIGHT:
                case Gesture.DOWN:
                    mNavigator.browseAdjacentGroup(SongPicker.DIRECTION_FORWARD, true);
                    break;
                case Gesture.UPRIGHT:
                    if (mBuildPlaylistMode) {
                        mNavigator.jumpMode(SongPicker.DIRECTION_FORWARD, true);
                    }
                    break;
                case Gesture.DOWNLEFT:
                    if (mBuildPlaylistMode) {
                        mNavigator.jumpMode(SongPicker.DIRECTION_BACKWARD, true);
                    }
                    break;
            }
            updateText();
        }

        @Override
        public void onGestureStart(int g) {
            mIsScrolling = false;
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
            mIsScrolling = false;
            mParent.playVibration();
            onGestureFinish(g);
            // TODO(sainsley) : add two-finger gesture to activate search
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
