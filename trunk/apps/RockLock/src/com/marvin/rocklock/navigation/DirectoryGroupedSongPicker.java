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
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

import com.marvin.rocklock.Bookmark;

/**
 */
public class DirectoryGroupedSongPicker extends SongPicker {
    private File[] mGroup;

    private File[] mRestoreGroup;

    private int mCurrentPos;

    private Activity mParent;

    private ArrayList<File> mCurrentPlaylist;

    private int mPlaylistPos;

    private static final String MUSIC_FILEPATH = Environment.getExternalStorageDirectory() + "/Music";

    // TODO(sainsley): Check for available codecs for current device. Note that
    // MediaCodecList is API 16
    private static final String[] AUDIO_FILE_EXT = {
    ".mp3", ".mp4", ".m4a", ".3gp", ".mid", ".xmf", ".mxmf", ".flac", ".ogg", ".wav" };

    /**
     * Constructs a new directory song picker
     * 
     * @param parentActivity
     */
    public DirectoryGroupedSongPicker(Activity parentActivity) {
        super(parentActivity);
        mParent = parentActivity;
        File root = new File(MUSIC_FILEPATH);
        if (!restoreFromPrefs()) {
            mGroup = getSortedFiles(root);
            mCurrentPos = 0;
        }        
        save();
    }

    /**
     * Gets files in a group sorted alphabetically
     * 
     * @return files
     */
    private File[] getSortedFiles(File parent) {
        File[] files = parent.listFiles();
        if (files != null) {
            Arrays.sort(files);
        }
        return files;
    }

    /**
     * Updates the current group
     * 
     * @return groupName
     */
    @Override
    public String updateGroup() {
        return mCurrentGroup = mGroup[mCurrentPos].getParentFile().getName();
    }

    /**
     * Gets the unique ID for the current group
     * 
     * @return groupId
     */
    @Override
    public int getGroupId() {
        return 0;
    }

    /**
     * Returns true is we are pointing to a new group
     * 
     * @return isNewGroup
     */
    @Override
    protected boolean isNewGroup() {
        return true;
    }

    /**
     * Returns the tag for current group to go in front of track
     * 
     * @param filtering
     * @return groupName
     */
    @Override
    protected String groupTag(boolean filtering) {
        return mGroup[mCurrentPos].getParentFile().getName();
    }

    /**
     * Moves towards a new group. Returns true if this move did not require a
     * loop through the groups
     * 
     * @param dir
     * @return noLoop
     */
    @Override
    protected boolean stepGroups(int dir) {
        if (dir < 0) {
            File parent = mGroup[mCurrentPos].getParentFile();
            if (!parent.toString().endsWith(MUSIC_FILEPATH)) {
                mGroup = getSortedFiles(parent.getParentFile());
                mCurrentPos = 0;
                while (!mGroup[mCurrentPos].getAbsolutePath().equals(parent.getAbsolutePath())
                        && mCurrentPos < mGroup.length - 1) {
                    mCurrentPos++;
                }
            }
        } else {
            if (mGroup[mCurrentPos].isDirectory()) {
                mGroup = getSortedFiles(mGroup[mCurrentPos]);
                mCurrentPlaylist = null;
                mCurrentPos = -1;
                // find the first valid file
                goAdjacentTrack(SongPicker.DIRECTION_FORWARD);
            }
        }
        return true;
    }

    /**
     * Speak previous group, but do not navigate to it
     * 
     * @return group
     */
    @Override
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
     * Go to next group in the given direction
     * 
     * @return group
     */
    @Override
    public String goAdjacentGroup(int dir) {
        if (!mMusicAvailable) {
            return "";
        }
        stepGroups(dir);
        return updateGroup();
    }

    /**
     * Speak next track in given direction, but do not navigate to it
     * 
     * @return track
     */
    @Override
    public String peekAdjacentTrack(int dir) {
        if (!mMusicAvailable) {
            return "";
        }
        int initialPos = mCurrentPos;
        int initialPosPlaylist = mPlaylistPos;
        String track = goAdjacentTrack(dir);
        mCurrentPos = initialPos;
        mPlaylistPos = initialPosPlaylist;
        return track;
    }

    /**
     * Goes to the next valid track in the given direction
     * 
     * @return track
     */
    @Override
    public String goAdjacentTrack(int dir) {
        if (!mMusicAvailable) {
            return "";
        }
        // Do nothing if we are holding our position
        if (mHoldPosition) {
            mHoldPosition = false;
        }
        if (mCurrentPlaylist != null) {
            if ((dir < 0 && --mPlaylistPos > -1)
                    || (dir > 0 && ++mPlaylistPos < mCurrentPlaylist.size())) {
                return mCurrentPlaylist.get(mPlaylistPos).getName();
            } else {
                // We are done with this playlist
                mCurrentPlaylist = null;
            }
        }
        navigateTracks(dir);
        // We loaded a new playlist
        if (mCurrentPlaylist != null) {
            return mCurrentPlaylist.get(mPlaylistPos).getName();
        }
        return mGroup[mCurrentPos].getName();
    }

    /**
     * Shuffle the play order for current group
     */
    @Override
    public void toggleShuffle() {
        // We do not support shuffling files
        mShuffling = !mShuffling;
    }

    /**
     * Restore last song stored in preferences
     * 
     * @return true if successful
     */
    @Override
    protected boolean restoreFromPrefs() {
        save();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParentActivity);
        String currentFile = prefs.getString(PREF_FILE, "");
        return loadFile(currentFile);
    }

    /**
     * Gets the current song ID
     * 
     * @return ID
     */
    @Override
    public String getCurrentSongId() {
        File currentFile = mGroup[mCurrentPos];
        if (mCurrentPlaylist != null) {
            currentFile = mCurrentPlaylist.get(mPlaylistPos);
        }
        if (currentFile.isFile()) {
            ContentResolver resolver = mParent.getContentResolver();
            String formattedPath = currentFile.getName().replace("'", "''");
            String filter = MediaColumns.DATA + " like '%" + formattedPath + "'";
            String[] proj = new String[] {
            MediaColumns._ID, MediaColumns.DATA };
            Cursor idQuery = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, filter, null, null);
            if (idQuery != null && idQuery.moveToFirst()) {
                return idQuery.getString(0);
            }
            // Indicate that this bookmark is not in the datastore,
            // and needs to be loaded in directory mode
            return "-1";
        }
        // Either this file is a directory or not in the datastore
        return null;
    }

    /**
     * Gets the current song file path
     * 
     * @return file
     */
    @Override
    public String getCurrentSongFile() {
        if (mCurrentPlaylist != null) {
            return mCurrentPlaylist.get(mPlaylistPos).getAbsolutePath();
        }
        if ((mGroup !=null) && (mGroup[mCurrentPos] != null)) {
            return mGroup[mCurrentPos].getAbsolutePath();
        }
        return "";
    }

    @Override
    public String getCurrentPlayableSongFile() {
        if (mCurrentPlaylist != null) {
            return mCurrentPlaylist.get(mPlaylistPos).getAbsolutePath();
        }
        File currentFile = mGroup[mCurrentPos];
        if (currentFile.isDirectory()) {
            String playlistFile = findPlaylistInDirectory(currentFile);
            if (playlistFile.length() > 0) {
                stepGroups(DIRECTION_FORWARD);
                while (!mGroup[mCurrentPos].getPath().endsWith(".m3u")) {
                    if (navigateTracks(DIRECTION_FORWARD)) {
                        // Should never have looped unless something really bad
                        // happened such as the user deleting the file as we're
                        // going through the directory
                        return currentFile.getAbsolutePath();
                    }
                }
                loadPlaylist();
                if (mCurrentPlaylist != null) {
                    return mCurrentPlaylist.get(0).getAbsolutePath();
                }
            }
            String closestPlayableFile = findFirstPlayableFileInDirectory(currentFile);
            if (closestPlayableFile.length() > 0) {
                stepGroups(DIRECTION_FORWARD);
                return closestPlayableFile;
            }
        }
        return currentFile.getAbsolutePath();
    }

    private String findPlaylistInDirectory(File directory) {
        File[] playlistFiles = directory.listFiles(new FilenameFilter() {
                @Override
            public boolean accept(File dir, String name) {
                return (name.endsWith(".m3u"));
            }
        });
        if (playlistFiles.length > 0) {
            return playlistFiles[0].getAbsolutePath();
        }
        return "";
    }

    private String findFirstPlayableFileInDirectory(File directory) {
        File[] files = directory.listFiles();
        Arrays.sort(files);
        for (int i = 0; i < files.length; i++) {
            if (isAudioFile(files[i])) {
                return files[i].getAbsolutePath();
            }
        }
        return "";
    }

    /**
     * Gets the current song file path
     */
    @Override
    public void saveCurrentSongFile() {
        String file = getCurrentSongFile();
        mEditor.putString(PREF_FILE, file);
        mEditor.commit();
    }

    /**
     * Gets info (artist, album, track) for current song
     * 
     * @return info string
     */
    @Override
    public String getCurrentSongInfo(boolean artistAlbum) {
        if (mCurrentPlaylist != null) {
            return mGroup[mCurrentPos].getName() + "\n"
                    + mCurrentPlaylist.get(mPlaylistPos).getName();
        }
        return mGroup[mCurrentPos].getParentFile().getName() + "\n" + mGroup[mCurrentPos].getName();
    }

    /**
     * Gets name of current track
     * 
     * @return track
     */
    @Override
    public String getCurrentTrack() {
        if (mCurrentPlaylist != null) {
            return mCurrentPlaylist.get(mPlaylistPos).getName();
        }
        return mGroup[mCurrentPos].getName();
    }

    /**
     * Gets index within current group
     * 
     * @return position
     */
    @Override
    public int getCursorIndex() {
        return mCurrentPos;
    }

    /**
     * Moves music cursor to next valid position
     * 
     * @return true if we looped in this move
     */
    @Override
    public boolean navigateTracks(int dir) {
        boolean valid = true;
        boolean hasLooped = false;
        do {

            boolean success = true;
            // Move according to direction
            if (dir > 0) {
                if (++mCurrentPos > mGroup.length - 1) {
                    mCurrentPos = 0;
                    success = false;
                }
            } else {
                if (--mCurrentPos < 0) {
                    mCurrentPos = mGroup.length - 1;
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
            valid = mGroup[mCurrentPos].isDirectory() || isAudioFile(mGroup[mCurrentPos]);
            // Check for an m3u playlist file
            if (mGroup[mCurrentPos].getPath().endsWith(".m3u")) {
                valid = loadPlaylist();
            }
        } while (!valid);
        return !hasLooped;
    }

    private boolean loadPlaylist() {
        File current = mGroup[mCurrentPos];
        try {
            BufferedReader reader = new BufferedReader(new FileReader(mGroup[mCurrentPos]));
            String line;
            mCurrentPlaylist = new ArrayList<File>();
            while ((line = reader.readLine()) != null) {
                if (line.length() > 0 && !line.startsWith("#")) {
                    // assume absolute path
                    File playlistFile = new File(line);
                    // try local path
                    if (!playlistFile.exists()) {
                        playlistFile = new File(mGroup[mCurrentPos].getParent(), line);
                    }
                    if (playlistFile.exists()) {
                        mCurrentPlaylist.add(playlistFile);
                    }
                }
            }
            if (!mCurrentPlaylist.isEmpty()) {
                mPlaylistPos = 0;
                return true;
            }
        } catch (Exception e) {
            Log.e("DirectoryGroupedSongPicker", "Failed to load playlist: " + e);
        }
        mCurrentPlaylist = null;
        return false;
    }

    /**
     * Moves music cursor to given position (assumes position is valid)
     */
    @Override
    public void moveTo(int position) {
        if (!mMusicAvailable) {
            return;
        }

        if (position < mGroup.length && position > -1) {
            mCurrentPos = position;
        }
    }

    /**
     * Moves genre cursor to given position
     */
    @Override
    public void moveGroupTo(int pos) {
        if (!mMusicAvailable) {
            return;
        }

        File[] parentGroup = getSortedFiles(mGroup[mCurrentPos].getParentFile());
        if (pos < parentGroup.length && pos > -1) {
            mGroup = getSortedFiles(parentGroup[pos]);
            mCurrentPos = 0;
        }
        return;
    }

    /**
     * Reset to root
     */
    @Override
    public void reset() {
        File root = new File(MUSIC_FILEPATH);
        mGroup = getSortedFiles(root);
        mCurrentPos = 0;
    }

    /**
     * Saves position of music cursor
     */
    @Override
    public void save() {
        mRestorePos = mCurrentPos;
        mRestoreGroup = mGroup;
    }

    /**
     * Restores position of music cursor
     */
    @Override
    public void restore() {
        mCurrentPos = mRestorePos;
        mGroup = mRestoreGroup;
    }

    /**
     * Checks if there is any music for this picker
     * 
     * @return true if music exists
     */
    @Override
    public boolean resetMusic() {
        // Is first file valid?
        File testFile = new File(MUSIC_FILEPATH);
        if (testFile.list().length > 0) {
            return true;
        }
        return false;
    }

    /**
     * Moves to a given bookmark
     * 
     * @param bookmark
     */
    @Override
    public void moveToBookmark(Bookmark bookmark) {
        if (!mMusicAvailable) {
            return;
        }
        loadFile(bookmark.getFile());
    }

    private boolean loadFile(String path) {
        File newFile = new File(path);
        if (newFile.exists()) {
            mGroup = getSortedFiles(newFile.getParentFile());
            for (int i = 0; i < mGroup.length; ++i) {
                if (mGroup[i].equals(newFile)) {
                    mCurrentPos = i;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public LinkedHashMap<Integer, String> getSearchResults(Context context, String search) {
        // TODO(sainsley): support search for directories (if necessary).
        return null;
    }

    /**
     * Moves to a search result
     * 
     * @param resultId
     */
    @Override
    public void moveToResult(Integer resultId) {
        if (!mMusicAvailable) {
            return;
        }
        // do nothing
    }

    private boolean isAudioFile(File file) {
        for (String extension : AUDIO_FILE_EXT) {
            if (file.getPath().endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
