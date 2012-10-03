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
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.content.CursorLoader;

/**
 * SongPicker that groups songs by a particular column value
 *
 * @author sainsley@google.com (Sam Ainsley)
 */
public abstract class ColumnGroupedSongPicker extends SongPicker {

    protected final int mGroupColumn;
    protected final int mIdColumn;
    protected final Uri mSearchUri;

    public ColumnGroupedSongPicker(
            Activity parentActivity, int groupColumn, int idColumn,
            Uri searchUri, String secondarySortOrder) {
        super(parentActivity);

        mGroupColumn = groupColumn;
        mIdColumn = idColumn;
        mSearchUri = searchUri;

        String sortOrder = formatSortOrder(mProjection[mGroupColumn]);
        if (secondarySortOrder != null) {
            sortOrder += "," + secondarySortOrder;
        }

        CursorLoader loader = new CursorLoader(parentActivity,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mProjection, MUSIC_FILTER, null,
                sortOrder);
        mMusicCursor = loader.loadInBackground();

        if (mMusicCursor == null || !resetMusic()) {
            mMusicAvailable = false;
            return;
        }

        if (!restoreFromPrefs()) {
            mMusicCursor.moveToFirst();
            save();
        }

        updateGroup();
    }

    /**
     * Gets cursor with filter for current group so we know how many elements it
     * has
     */
    @Override
    protected Cursor getGroupCursor() {
        String formattedGroup = mCurrentGroup.replace("'", "''");
        String filter = mProjection[mGroupColumn] + " == '" + formattedGroup + "'";
        CursorLoader loader = new CursorLoader(mParentActivity,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mProjection,
                filter, null, null);
        Cursor query = loader.loadInBackground();
        return query;
    }

    /**
     * Returns first index for given group
     *
     * @return base index
     */
    @Override
    protected int getBaseIdx() {
        String currentGroup = mMusicCursor.getString(mGroupColumn);
        while (mMusicCursor.getString(mGroupColumn).equals(currentGroup)
                && !mMusicCursor.isFirst()) {
            navigateTracks(-1);
        }

        if (!mMusicCursor.isFirst()) {
            return mMusicCursor.getPosition() + 1;
        }
        return 0;
    }

    /**
     * Gets the current group id
     *
     * @return id
     */
    @Override
    public int getGroupId() {
        return 0;
    }

    @Override
    public LinkedHashMap<Integer, String> getSearchResults(Context context, String search) {
        ContentResolver resolver = context.getContentResolver();
        search.replace("'", "''");
        String[] proj = {
        mProjection[TRACK_ID], mProjection[mGroupColumn] };
        String filter = mProjection[mGroupColumn] + " LIKE '" + search + "%'";
        Cursor query = resolver.query(mSearchUri, proj, filter, null, null);

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
        mMusicCursor.moveToFirst();
        do {
            found = mMusicCursor.getInt(mIdColumn) == resultId;
        } while (!found && mMusicCursor.moveToNext());
    }
}
