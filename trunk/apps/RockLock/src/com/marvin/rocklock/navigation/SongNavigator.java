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

package com.marvin.rocklock.navigation;

import java.util.LinkedHashMap;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

import com.marvin.rocklock.Bookmark;
import com.marvin.rocklock.R;
import com.marvin.rocklock.RockLockActivity;

/**
 * A wrapper class for SongPicker that manages different implementations of
 * SongPicker based on the current navigation mode (i.e. Artists, Albums,
 * Genres, etc.)
 *
 * @author sainsley@google.com (Sam Ainsley)
 */
public class SongNavigator {

    protected static final String PREF_MODE = "TAG_MODE";
    protected static final int MODE_COUNT = 6;

    public static final int MODE_ARTIST = 0;
    public static final int MODE_ALBUM = 1;
    public static final int MODE_GENRE = 2;
    public static final int MODE_TRACK = 3;
    public static final int MODE_PLAYLIST = 4;
    public static final int MODE_DIRECTORY = 5;

    protected RockLockActivity mParent;
    private Editor mEditor;

    protected SongPicker mPicker;
    protected int mNavigationMode;

    /**
     * Creates a new SongNavigator. Loads the navigation mode based on
     * preferences.
     *
     * @param parent the parent activity for loading cursors and managing TTS
     */
    public SongNavigator(Activity parent) {
        mParent = (RockLockActivity) parent;
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(parent);
        mEditor = prefs.edit();
        // grab navigation mode from preferences
        mNavigationMode = prefs.getInt(PREF_MODE, 0);
        updatePicker();
    }

    /**
     * Move to given song and group position
     */
    public void moveTo(int navMode, int groupIdx, int songIdx) {
        setPickerMode(navMode);
        mPicker.moveGroupTo(groupIdx);
        mPicker.moveTo(songIdx);
    }

    /**
     * Gets the current group index
     */
    public int getGroupIdx() {
        return mPicker.getGroupIndex();
    }

    /**
     * Gets the current song index
     */
    public int getSongIdx() {
        return mPicker.getCursorIndex();
    }

    /**
     * Speaks the current track
     *
     * @param interrupt whether or not to interrupt current TTS
     */
    public void speakCurrentTrack(boolean interrupt) {
        mParent.speakAndDuck(mPicker.getCurrentTrack(), interrupt);
    }

    /**
     * Speaks the current group
     *
     * @param interrupt whether or not to interrupt current TTS
     */
    public void speakCurrentGroup(boolean interrupt) {
        mParent.speakAndDuck(
                mPicker.groupTag(false) + " " + mPicker.getCurrentTrack(),
                interrupt);
    }

    /**
     * Gets song info for current picker
     *
     * @return the current song info
     */
    public String getCurrentSongInfo(boolean artistAlbum) {
        return mPicker.getCurrentSongInfo(artistAlbum);
    }

    /**
     * Gets song id for current picker
     *
     * @return the current song id
     */
    public String getCurrentSongId() {
        return mPicker.getCurrentSongId();
    }

    /**
     * Gets group id for current picker
     *
     * @return group id
     */
    public int getGroupId() {
        return mPicker.getGroupId();
    }

    /**
     * Speaks the current playback mode
     *
     * @param interrupt whether or not to interrupt current TTS
     */
    public void speakPlayMode(boolean interrupt) {
        if (mPicker.isShuffling()) {
            mParent.speakAndDuck(mParent.getString(R.string.shuffle_on), interrupt);
        } else {
            mParent.speakAndDuck(mParent.getString(R.string.shuffle_off),
                    interrupt);
        }
    }

    /**
     * Toggles shuffle for current picker
     */
    public void toggleShuffle() {
        mPicker.toggleShuffle();
        speakPlayMode(true);
    }

    /**
     * Go to next track in given direction for current picker and speak peek
     *
     * @param dir the direction in which to navigate
     * @param announce whether or not to announce with TTS
     * @return the track name
     */
    public String browseAdjacentTrack(int dir, boolean announce) {
        String newTrack = mPicker.goAdjacentTrack(dir);
        if (announce) {
            mParent.speakAndDuck(newTrack, true);
        }
        return newTrack;
    }

    /**
     * Go to next group in given direction for current picker and speak peek
     *
     * @param dir the direction in which to navigate
     * @param announce whether or not to announce with TTS
     * @return the group name
     */
    public String browseAdjacentGroup(int dir, boolean announce) {
        String newGroup = mPicker.goAdjacentGroup(dir);
        if (announce) {
            mParent.speakAndDuck(newGroup + "," + mPicker.getCurrentTrack(), true);
        }
        return newGroup;
    }

    /**
     * Gets the song name for the current picker
     *
     * @return currentSong
     */
    public String getCurrentSong() {
        return mPicker.getCurrentTrack();
    }

    /**
     * Gets the song file for the current picker
     *
     * @return currentFile
     */
    public String getCurrentSongFile() {
        return mPicker.getCurrentSongFile();
    }

    /**
     * Gets the playable song file for the current picker
     *
     * @return currentFile
     */
    public String getCurrentPlayableSongFile() {
        return mPicker.getCurrentPlayableSongFile();
    }

    /**
     * Gets the current navigation mode
     *
     * @return navigationMode
     */
    public int getNavMode() {
        return mNavigationMode;
    }

    /**
     * Gets the name of the current navigation mode
     *
     * @return mode string
     */
    public String getModeString() {
        switch (mNavigationMode) {
            case MODE_ARTIST:
                return mParent.getString(R.string.artists);
            case MODE_ALBUM:
                return mParent.getString(R.string.albums);
            case MODE_PLAYLIST:
                return mParent.getString(R.string.playlists);
            case MODE_TRACK:
                return mParent.getString(R.string.songs);
            case MODE_GENRE:
                return mParent.getString(R.string.genres);
            default:
                return mParent.getString(R.string.directories);

        }
    }

    /**
     * Sets the picker mode to a specific mode
     *
     * @param mode
     */
    public void setPickerMode(int mode) {
        if (mode > -1 && mode < MODE_COUNT) {
            mNavigationMode = mode;
            updatePicker();
        }
    }

    /**
     * Increments the current mode by a given number of steps
     *
     * @param steps the number of steps to take
     * @param announce whether or not to announce with TTS
     */
    public void jumpMode(int steps, boolean announce) {
        int nextStep = mNavigationMode + steps;
        nextStep = nextStep % MODE_COUNT;
        if (nextStep < 0) {
            mNavigationMode = nextStep + MODE_COUNT;
        } else {
            mNavigationMode = nextStep;
        }
        String currentFile = mPicker.getCurrentSongFile();
        String newPicker = updatePicker();
        if (announce) {
            mParent.speakAndDuck(newPicker, announce);
        }
        String newCurrentFile = mPicker.getCurrentSongFile();
        // In pivoting, we moved to a new file, so we move back one
        // You could probably do a check for this every time we attempt
        // advance by browsing or playing, but this seems cleaner
        if (!newCurrentFile.equals(currentFile)) {
            mPicker.holdPosition();
        }
    }

    /**
     * Goes to the next picker mode that has valid search results
     *
     * @param dir the direction in which to shift
     * @param filter the search filter
     * @return true if result was found
     */
    public LinkedHashMap<Integer, String> searchModes(int dir, String filter) {
        int currentMode = mNavigationMode;
        LinkedHashMap<Integer, String> results = null;
        do {
            results = mPicker.getSearchResults(mParent, filter);
            if (results == null) {
                jumpMode(dir, false);
                if (currentMode == mNavigationMode) {
                    mParent.speakAndDuck(mParent.getString(R.string.no_results), true);
                    return null;
                }
            } else {
                break;
            }
        } while (currentMode != mNavigationMode);
        String firstResult = (String) results.values().toArray()[0];
        mParent.speakAndDuck(getModeString() + ", " + firstResult, true);
        return results;
    }

    //
    // PREVIEW LOGIC
    //

    /**
     * Speaks next track for current picker
     *
     * @return next track
     */
    public String peekNextTrack() {
        return mPicker.peekAdjacentTrack(SongPicker.DIRECTION_FORWARD);
    }

    /**
     * Speaks previous track for current picker
     *
     * @return prev track
     */
    public String peekPrevTrack() {
        return mPicker.peekAdjacentTrack(SongPicker.DIRECTION_BACKWARD);
    }

    /**
     * Speaks next group for current picker
     */
    public void peekNextGroup() {
        mParent.speakAndDuck(mPicker.peekAdjacentGroup(SongPicker.DIRECTION_FORWARD),
                true);
    }

    /**
     * Speaks previous group for current picker
     */
    public void peekPrevGroup() {
        mParent.speakAndDuck(mPicker.peekAdjacentGroup(SongPicker.DIRECTION_BACKWARD),
                true);
    }

    public void moveToBookmark(Bookmark bookmark) {
        if (bookmark.getId() < 0) {
            mNavigationMode = MODE_DIRECTORY;
            updatePicker();
        }
        mPicker.moveToBookmark(bookmark);
    }

    public void moveToResult(Integer resultId) {
        mPicker.moveToResult(resultId);
    }

    /**
     * Check which mode we would switch to given a number of steps
     *
     * @param steps The number of steps to move from the current mode
     * @param interrupt whether or not to interrupt current TTS
     */
    public void peekMode(int steps, boolean interrupt) {
        int nextStep = mNavigationMode + steps;
        nextStep %= MODE_COUNT;
        if (nextStep < 0) {
            nextStep = MODE_COUNT + nextStep;
        }

        StringBuilder modeString = new StringBuilder();
        switch (nextStep) {
            case MODE_ARTIST:
                modeString.append(mParent.getString(R.string.artists));
                break;
            case MODE_ALBUM:
                modeString.append(mParent.getString(R.string.albums));
                break;
            case MODE_GENRE:
                modeString.append(mParent.getString(R.string.genres));
                break;
            case MODE_PLAYLIST:
                modeString.append(mParent.getString(R.string.playlists));
                break;
            case MODE_TRACK:
                modeString.append(mParent.getString(R.string.songs));
                break;
            case MODE_DIRECTORY:
                modeString.append(mParent.getString(R.string.directories));
                break;
        }
        modeString.append(' ');
        modeString.append(mParent.getString(R.string.mode));
        mParent.speakAndDuck(modeString.toString(), interrupt);
    }

    public boolean hasMusic() {
        return mPicker.resetMusic();
    }

    //
    // Persistence
    //

    /**
     * Updates the music picker based on the current navigation mode
     *
     * @return the new picker name
     */
    public String updatePicker() {

        boolean shuffling = mPicker != null && mPicker.isShuffling();
        String modeString = "";

        switch (mNavigationMode) {
            case MODE_ARTIST:
                mPicker = new ArtistSortedSongPicker(mParent);
                modeString = mParent.getString(R.string.artists);
                break;
            case MODE_ALBUM:
                mPicker = new AlbumSortedSongPicker(mParent);
                modeString = mParent.getString(R.string.albums);
                break;
            case MODE_PLAYLIST:
                mPicker = new PlaylistSongPicker(mParent);
                modeString = mParent.getString(R.string.playlists);
                break;
            case MODE_TRACK:
                mPicker = new TrackSortedSongPicker(mParent);
                modeString = mParent.getString(R.string.songs);
                break;
            case MODE_GENRE:
                mPicker = new GenreSortedSongPicker(mParent);
                modeString = mParent.getString(R.string.genres);
                break;
            case MODE_DIRECTORY:
                mPicker = new DirectoryGroupedSongPicker(mParent);
                modeString = mParent.getString(R.string.directories);
                break;
        }

        if (shuffling) {
            mPicker.toggleShuffle();
        }

        return modeString;
    }

    /*
     * Save the current song to preferences
     */
    public void saveToPreferences() {
        mPicker.saveCurrentSongFile();
        saveMode();
    }

    /**
     * Save current picker state
     */
    public void save() {
        mPicker.save();
    }

    /**
     * Restore current picker state
     */
    public void restore() {
        mPicker.restore();
    }

    /**
     * Save current browsing mode
     */
    public void saveMode() {
        mEditor.putInt(PREF_MODE, mNavigationMode);
        mEditor.apply();
    }
}
