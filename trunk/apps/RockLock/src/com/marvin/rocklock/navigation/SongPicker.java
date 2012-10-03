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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AudioColumns;
import android.provider.MediaStore.MediaColumns;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Random;

import com.marvin.rocklock.Bookmark;
import com.marvin.rocklock.R;

/**
 * Interface for traversing through the songs on the device. Individual
 * implementations can handle traversal differently - for example, traversal
 * through albums vs. artists vs. genres
 *
 * @author clchen@google.com (Charles L. Chen),
 * @author sainsley@google.com (Sam Ainsley)
 */
public abstract class SongPicker {

    protected static final String PREF_FILE = "TAG_FILE";

    protected static final int ARTIST = 0;
    protected static final int ALBUM = 1;
    protected static final int TRACK = 2;
    protected static final int FILEPATH = 3;
    protected static final int ARTIST_ID = 4;
    protected static final int ALBUM_ID = 5;
    protected static final int TRACK_ID = 6;
    protected static final int TITLE_KEY = 7;

    protected static final String[] PROJECTION = {
            AudioColumns.ARTIST,
            AudioColumns.ALBUM, MediaColumns.TITLE, MediaColumns.DATA,
            MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE_KEY,
            MediaStore.Audio.Media.TRACK };
    protected static final String MUSIC_FILTER = AudioColumns.IS_MUSIC + " > 0";

    public static final int DIRECTION_FORWARD = 1;
    public static final int DIRECTION_BACKWARD = -1;

    protected boolean mMusicAvailable = true;
    protected String[] mProjection;
    protected Cursor mMusicCursor;
    protected String mCurrentGroup;

    protected Activity mParentActivity;
    protected Editor mEditor;
    protected int mRestorePos = 0;
    protected boolean mHoldPosition;

    // Shuffling
    protected Random mGenerator = new Random();
    protected ListIterator<Integer> mShuffleIter;
    protected boolean mShuffling;

    /**
     * Base constructor to be called by subclasses
     *
     * @param parentActivity
     */
    public SongPicker(Activity parentActivity) {
        mParentActivity = parentActivity;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(parentActivity);
        mEditor = prefs.edit();
        mProjection = PROJECTION;
        mHoldPosition = false;
    }

    /**
     * Updates the current group
     *
     * @return groupName
     */
    public abstract String updateGroup();

    /**
     * Gets the unique ID for the current group
     *
     * @return groupId
     */
    public abstract int getGroupId();

    /**
     * Returns true is we are pointing to a new group
     *
     * @return isNewGroup
     */
    protected abstract boolean isNewGroup();

    /**
     * Returns the tag for current group to go in front of track
     *
     * @param filtering whether or not we are using a search filter
     * @return groupName
     */
    protected abstract String groupTag(boolean filtering);

    /**
     * Moves towards a new group. Returns true if this move did not require a
     * loop through the groups
     *
     * @param dir
     * @return noLoop
     */
    protected boolean stepGroups(int dir) {
        return navigateTracks(dir);
    }

    /**
     * Go to adjacent group in the given direction
     *
     * @return group
     */
    public String goAdjacentGroup(int dir) {
        if (!mMusicAvailable) {
            return mParentActivity.getString(R.string.no_results);
        }

        boolean hasLooped = false;
        // If we are searching backwards: Move to the end of the two groups
        // before this so we end up on the first track in the previous group
        if (dir < 0) {
            int moves = 0;
            do {
                boolean success = stepGroups(dir);
                if (!success) {
                    // Corner case where we only have one group and we will
                    // enter an infinite loop
                    if (hasLooped) {
                        break;
                    }
                    hasLooped = true;
                }

                if (isNewGroup()) {
                    // On the first move: find match
                    // On the second move: just find new group
                    if (moves == 1 || moves == 0) {
                        updateGroup();
                        moves++;
                    }
                }

            } while (moves < 2);
        }
        // Move to next group
        hasLooped = false;
        do {
            boolean success = stepGroups(1);
            if (!success) {
                // Corner case where we only have one group and we will enter
                // an infinite loop
                if (hasLooped) {
                    break;
                }
                hasLooped = true;
            }
        } while (!isNewGroup());
        // Update group
        String group = updateGroup();
        // Create new shuffle order
        if (mShuffling) {
            createShuffleOrder();
        }
        return group;
    }

    /**
     * Speak adjacent group in given direction, but do not navigate to it
     *
     * @return group
     */
    public String peekAdjacentGroup(int dir) {
        if (!mMusicAvailable) {
            return "";
        }

        save();
        String group = goAdjacentGroup(dir);
        restore();
        updateGroup();

        return group;
    }

    /**
     * Speak adjacent track in given direction, but do not navigate to it
     *
     * @return track
     */
    public String peekAdjacentTrack(int dir) {
        if (!mMusicAvailable) {
            return "";
        }
        boolean wasHolding = mHoldPosition;
        int initialPosition = mMusicCursor.getPosition();
        String track = goAdjacentTrack(dir);
        mMusicCursor.moveToPosition(initialPosition);
        if (mShuffling) {
            if (dir < 0 && mShuffleIter.hasNext()) {
                mShuffleIter.next();
            } else if (dir > 0 && mShuffleIter.hasPrevious()) {
                mShuffleIter.previous();
            }
        }
        updateGroup();
        mHoldPosition = wasHolding;
        return track;
    }

    /**
     * Goes to the next valid track in the given direction
     *
     * @return track
     */
    public String goAdjacentTrack(int dir) {
        if (!mMusicAvailable) {
            return "";
        }
        // Do nothing if we are holding our position
        if (mHoldPosition) {
            mHoldPosition = false;
            return mMusicCursor.getString(TRACK);
        }

        if (!mShuffling) {
            navigateTracks(dir);
        } else {
            int nextIdx = mMusicCursor.getPosition();
            if (dir > 0) {
                // We have reached the end : regenerate
                if (!mShuffleIter.hasNext()) {
                    createShuffleOrder();
                }
                nextIdx = mShuffleIter.next();
            } else {
                // Iterator always point next item, so we want
                // to grab the index two positions back and move one forward
                if (mShuffleIter.hasPrevious()) {
                    nextIdx = mShuffleIter.previous();
                }
                if (mShuffleIter.hasPrevious()) {
                    nextIdx = mShuffleIter.previous();
                }
                if (mShuffleIter.hasNext()) {
                    mShuffleIter.next();
                }
                // We have reached the beginning : do nothing
            }
            mMusicCursor.moveToPosition(nextIdx);
        }

        String track = mMusicCursor.getString(TRACK);
        // New group: speak this information
        if (isNewGroup() && groupTag(false) != null) {
            track = groupTag(false) + ", " + track;
        }
        updateGroup();
        return track;
    }

    /**
     * Tells the picker to hold it's current position next time it is asked to
     * advance. We need this in the case that we switch modes and change songs,
     * but we don't want to skip the new "current" song.
     */
    public void holdPosition() {
        mHoldPosition = true;
    }

    /**
     * Shuffle the play order for current group
     */
    public void toggleShuffle() {
        if (mShuffling) {
            mShuffleIter = null;
            mShuffling = false;
        } else {
            createShuffleOrder();
            mShuffling = true;
        }
    }

    /**
     * True if shuffling
     */
    public boolean isShuffling() {
        return mShuffling;
    }

    /**
     * Gets cursor for the elements in the current group Subclasses that group
     * within the music table should override this
     *
     * @return cursor
     */
    protected Cursor getGroupCursor() {
        return mMusicCursor;
    }

    /**
     * Returns first index for given group
     *
     * @return base index
     */
    protected int getBaseIdx() {
        return 0;
    }

    /**
     * Returns number of valid songs in group (invalid songs are songs whose
     * file does not exist)
     *
     * @param eCursor
     * @return count
     */
    protected int getValidCount(Cursor eCursor) {
        int n = eCursor.getCount();
        eCursor.moveToFirst();
        do {
            if (!exists(eCursor.getPosition())) {
                n--;
            }
        } while (eCursor.moveToNext());
        return n;
    }

    /**
     * Creates a random play order for this group with no repeats
     */
    private void createShuffleOrder() {
        int pos = mMusicCursor.getPosition();
        // get number of elements in this group
        Cursor elementCursor = getGroupCursor();
        int n = elementCursor.getCount();
        // get number of valid elements
        int nValid = getValidCount(elementCursor);
        // get base index
        int baseIdx = getBaseIdx();
        moveTo(baseIdx);
        // map to let us know which values are already placed
        ArrayList<Integer> takenMap = new ArrayList<Integer>(n);
        for (int i = 0; i < n; i++) {
            takenMap.add(-1);
        }
        // generate shuffle order
        ArrayList<Integer> shuffleOrder = new ArrayList<Integer>(nValid);
        int i = 0;
        while (i < nValid) {
            int random = mGenerator.nextInt(n);
            if (takenMap.get(random) == -1 && exists(random)) {
                shuffleOrder.add(random + baseIdx);
                takenMap.set(random, 0);
                ++i;
            }
        }
        mShuffleIter = shuffleOrder.listIterator();
        mMusicCursor.moveToPosition(pos);
    }

    /**
     * Restore last song stored in preferences
     *
     * @return true if successful
     */
    protected boolean restoreFromPrefs() {
        mMusicCursor.moveToFirst();
        save();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParentActivity);
        String currentFile = prefs.getString(PREF_FILE, "");
        while (mMusicCursor.moveToNext()) {
            // TODO(sainsley): pull in integer columns for cheaper comparison
            if (mMusicCursor.getString(FILEPATH).contains(currentFile)) {
                String filepath = getCurrentSongFile();
                File testFile = new File(filepath);
                if (testFile.exists()) {
                    save();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Format sort order string for a given mode
     *
     * @param mode
     * @return sortOrder
     */
    protected String formatSortOrder(String mode) {
        // Nest replace statements
        mode = "REPLACE(" + mode + ", 'The ', '') ";
        mode = "REPLACE(" + mode + ", 'A ', '') ";
        mode = "REPLACE(" + mode + ", 'An ', '') ";
        String sortOrder = mode + "COLLATE NOCASE ASC";
        return sortOrder;
    }

    /**
     * Gets the current song ID
     *
     * @return ID
     */
    public String getCurrentSongId() {
        if (!mMusicAvailable) {
            return "";
        }

        return mMusicCursor.getString(TRACK_ID);
    }

    /**
     * Gets the current song file path
     *
     * @return file
     */
    public String getCurrentSongFile() {
        if (!mMusicAvailable) {
            return "";
        }
        return mMusicCursor.getString(FILEPATH);
    }
    
    /**
     * Gets the current playable song file path.
     * Note: In all cases except directory, this should be identical to
     * getCurrentSongFile.
     *
     * @return file
     */
    public String getCurrentPlayableSongFile() {
        return getCurrentSongFile();
    }

    /**
     * Gets the current song file path
     */
    public void saveCurrentSongFile() {
        if (!mMusicAvailable) {
            return;
        }
        mEditor.putString(PREF_FILE, mMusicCursor.getString(FILEPATH));
        mEditor.commit();
    }

    /**
     * Gets info (artist, album, track) for current song
     *
     * @return info string
     */
    public String getCurrentSongInfo(boolean artistAlbum) {
        if (!mMusicAvailable) {
            return "";
        }

        String info = "";
        try {
            String currentArtist = mMusicCursor.getString(ARTIST);
            if (artistAlbum) {
                String currentAlbum = mMusicCursor.getString(ALBUM);
                info = currentArtist + "\n" + currentAlbum;
            } else {
                String currentTrack = mMusicCursor.getString(TRACK);
                info = currentTrack + "\n" + currentArtist;
            }
        } catch (final CursorIndexOutOfBoundsException e) {
            e.printStackTrace();
        }

        return info;
    }

    /**
     * Gets name of current track
     *
     * @return track
     */
    public String getCurrentTrack() {
        if (!mMusicAvailable) {
            return "";
        }

        return mMusicCursor.getString(TRACK);
    }

    /**
     * Gets index within current group
     *
     * @return position
     */
    public int getCursorIndex() {
        return mMusicCursor.getPosition();
    }

    /**
     * Gets current group index
     *
     * @return group index
     */
    public int getGroupIndex() {
        return 0;
    }

    /**
     * Moves music cursor to next valid position
     *
     * @return true if we looped in this move
     */
    public boolean navigateTracks(int dir) {
        String currentKey = mMusicCursor.getString(TITLE_KEY);
        boolean valid = true;
        boolean hasLooped = false;
        do {

            boolean success = true;
            // Move according to direction
            if (dir > 0) {
                if (!mMusicCursor.moveToNext()) {
                    mMusicCursor.moveToFirst();
                    success = false;
                }
            } else {
                if (!mMusicCursor.moveToPrevious()) {
                    mMusicCursor.moveToLast();
                    success = false;
                }
            }
            // Make sure we are not looping in search of valid files
            if (!success) {
                if (hasLooped) {
                    return false;
                }
                hasLooped = true;
            }
            File test = new File(mMusicCursor.getString(FILEPATH));
            valid = !currentKey.equals(mMusicCursor.getString(TITLE_KEY)) && test.exists();
        } while (!valid);
        return !hasLooped;
    }

    /**
     * Moves music cursor to given position (assumes position is valid)
     */
    public void moveTo(int position) {
        if (!mMusicAvailable) {
            return;
        }

        if (position < mMusicCursor.getCount() && position > -1) {
            mMusicCursor.moveToPosition(position);
        }
    }

    /**
     * Moves group cursor to given position if we are using group cursors (most
     * likely not)
     */
    public void moveGroupTo(int pos) {
        // Do nothing unless this is overridden
        return;
    }

    /**
     * Checks if file at given position actually exists
     *
     * @param position
     * @return true is file exists
     */
    public boolean exists(int position) {
        if (!mMusicAvailable) {
            return false;
        }

        int originalPos = mMusicCursor.getPosition();
        mMusicCursor.moveToPosition(position);
        File test = new File(mMusicCursor.getString(FILEPATH));
        boolean valid = test.exists();
        mMusicCursor.moveToPosition(originalPos);
        return valid;
    }

    /**
     * Resets to first entry
     */
    public void reset() {
        moveGroupTo(0);
        moveTo(0);
    }

    /**
     * Saves position of music cursor
     */
    public void save() {
        if (!mMusicAvailable) {
            return;
        }

        mRestorePos = mMusicCursor.getPosition();
    }

    /**
     * Restores position of music cursor
     */
    public void restore() {
        if (!mMusicAvailable) {
            return;
        }

        moveTo(mRestorePos);
    }

    /**
     * Resets the music cursor and checks if there is any music for this picker
     *
     * @return true if music exists
     */
    protected boolean resetMusic() {
        if (mMusicCursor == null || !mMusicCursor.moveToFirst()) {
            return false;
        }
        // Is first file valid?
        File testFile = new File(mMusicCursor.getString(FILEPATH));
        if (testFile.exists()) {
            return true;
        }
        // Are there any valid files?
        boolean hasNext = navigateTracks(DIRECTION_FORWARD);
        return hasNext;
    }

    /**
     * Moves to a given bookmark
     *
     * @param bookmark
     */
    public void moveToBookmark(Bookmark bookmark) {
        if (!mMusicAvailable) {
            return;
        }

        boolean found = false;
        mMusicCursor.moveToFirst();
        do {
            found = mMusicCursor.getInt(TRACK_ID) == bookmark.getId();
        } while (!found && mMusicCursor.moveToNext());
    }

    /**
     * Moves to a search result
     *
     * @param resultId
     */
    public abstract void moveToResult(Integer resultId);

    /**
     * @param filter
     * @return results
     */
    public abstract LinkedHashMap<Integer, String> getSearchResults(Context context, String filter);
}
