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

import com.marvin.rocklock.Bookmark;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.CursorLoader;
import android.util.Log;

/**
 * SongPicker that has a dedicated cursor for group names and ids in addition to
 * a music cursor for all the songs in that group
 *
 * @author sainsley@google.com (Sam Ainsley)
 */
public abstract class CursorGroupedSongPicker extends SongPicker {

    protected static final int GROUP_ID = 0;
    protected static final int GROUP_NAME = 1;

    protected final Uri mGroupUri;
    protected final String[] mGroupProjection;
    protected Cursor mGroupCursor;
    protected int mGroupRestorePos;

    public CursorGroupedSongPicker(Activity parentActivity, Uri uri, String idColumn,
            String nameColumn, String[] musicProjection) {
        super(parentActivity);
        mGroupUri = uri;
        mGroupProjection = new String[] {
                idColumn, nameColumn };
        mProjection = musicProjection;

        String sortOrder = formatSortOrder(nameColumn);
        // First, grab all of our groups
        CursorLoader loader = new CursorLoader(parentActivity, mGroupUri, mGroupProjection,
                null, null, sortOrder);
        mGroupCursor = loader.loadInBackground();

        if (mGroupCursor == null || !mGroupCursor.moveToFirst()) {
            mMusicAvailable = false;
            return;
        }

        mMusicAvailable = false;

        int firstNonEmptyPos = -1;
        // find first group that is has music in it
        while (!mMusicAvailable && !mGroupCursor.isAfterLast()) {
            updateMusicCursor();
            firstNonEmptyPos++;
            if (mMusicCursor != null && resetMusic()) {
                mMusicAvailable = true;
            } else {
                mGroupCursor.moveToNext();
            }
        }
        if (!mMusicAvailable) {
            Log.i("CursorGrouped", "NO MUSIC 2 " + getClass().toString());
            return;
        }

        // Try to restore the last song within this group.
        // Otherwise, search for the right group that contains this song
        boolean restored = restoreFromPrefs();
        while (!restored && mGroupCursor.moveToNext()) {
            updateMusicCursor();
            restored = restoreFromPrefs();
        }

        if (!restored) {
            mGroupCursor.moveToPosition(firstNonEmptyPos);
            updateMusicCursor();
            mMusicCursor.moveToFirst();
            save();
        }

        updateGroup();
    }

    /**
     * Resets to first entry
     */
    @Override
    public void reset() {
        mMusicAvailable = false;
        // find first group that is has music in it
        while (!mMusicAvailable && !mGroupCursor.isAfterLast()) {
            updateMusicCursor();
            if (mMusicCursor != null && resetMusic()) {
                mMusicAvailable = true;
            } else {
                mGroupCursor.moveToNext();
            }
        }
    }

    /**
     * Gets current group index
     *
     * @return group index
     */
    @Override
    public int getGroupIndex() {
        return mGroupCursor.getPosition();
    }

    /**
     * Updates the current group
     *
     * @return groupName
     */
    @Override
    public String updateGroup() {
        mCurrentGroup = mGroupCursor.getString(GROUP_NAME);
        return mCurrentGroup;
    }

    /**
     * Gets the current group id
     *
     * @return id
     */
    @Override
    public int getGroupId() {
        return mGroupCursor.getInt(GROUP_ID);
    }

    /**
     * Returns true is we are pointing to a new group
     *
     * @return isNewGroup
     */
    @Override
    protected boolean isNewGroup() {
        return !mGroupCursor.getString(GROUP_NAME).equals(mCurrentGroup) && resetMusic();
    }

    /**
     * Returns the name of the currentGroup
     *
     * @return groupName
     */
    @Override
    protected String groupTag(boolean filtering) {
        return mGroupCursor.getString(GROUP_NAME);
    }

    /**
     * Save position of group cursor
     */
    @Override
    public void save() {
        super.save();
        mGroupRestorePos = mGroupCursor.getPosition();
    }

    /**
     * Restore group cursor to last recorded position
     */
    @Override
    public void restore() {
        moveGroupTo(mGroupRestorePos);
        super.restore();
    }

    /**
     * Attempts to move the group cursor forward
     *
     * @return true is no looping was necessary
     */
    @Override
    protected boolean stepGroups(int dir) {
        boolean success = ((dir < 0 && mGroupCursor.moveToPrevious()) ||
                (dir > 0 && mGroupCursor.moveToNext()));
        if (!success) {
            if (dir < 0) {
                mGroupCursor.moveToLast();
            }
            if (dir > 0) {
                mGroupCursor.moveToFirst();
            }
        }
        updateMusicCursor();
        return success;
    }

    /**
     * Moves group cursor to given position
     */
    @Override
    public void moveGroupTo(int pos) {
        if (pos < mGroupCursor.getCount() && pos > -1) {
            mGroupCursor.moveToPosition(pos);
            updateMusicCursor();
        }
    }

    /**
     * Get the URI for the current group
     *
     * @return Uri
     */
    protected abstract Uri getGroupUri();

    /**
     * Updates the music cursor given a new group
     */
    protected void updateMusicCursor() {
        if (mGroupCursor == null) {
            return;
        }
        CursorLoader loader = new CursorLoader(
                mParentActivity, getGroupUri(), mProjection, MUSIC_FILTER, null, null);
        mMusicCursor = loader.loadInBackground();
        if (mMusicCursor != null) {
            mMusicAvailable = mMusicCursor.moveToFirst();
        }
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

        boolean found = false;
        mGroupCursor.moveToFirst();
        do {
            updateMusicCursor();
            if (!mMusicCursor.moveToFirst()) {
                continue;
            }
            do {
                found = mMusicCursor.getInt(TRACK_ID) == bookmark.getId();
            } while (!found && mMusicCursor.moveToNext());
        } while (!found && mGroupCursor.moveToNext());
    }

    @Override
    public LinkedHashMap<Integer, String> getSearchResults(Context context, String search) {
        ContentResolver resolver = context.getContentResolver();
        search.replace("'", "''");
        String filter = mGroupProjection[GROUP_NAME] + " LIKE '" + search + "%'";
        Cursor query = resolver.query(mGroupUri, mGroupProjection,
                filter, null, null);
        LinkedHashMap<Integer, String> results = null;
        if (query != null && query.moveToFirst()) {
            results = new LinkedHashMap<Integer, String>();
            do {
                results.put(query.getInt(0), query.getString(1));
            } while (query.moveToNext());
        }
        if (query != null) {
            query.close();
        }
        return results;
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

        boolean found = false;
        mGroupCursor.moveToFirst();
        do {
            found = mGroupCursor.getInt(GROUP_ID) == resultId;
        } while (!found && mGroupCursor.moveToNext());
        updateGroup();
        updateMusicCursor();
    }
}
