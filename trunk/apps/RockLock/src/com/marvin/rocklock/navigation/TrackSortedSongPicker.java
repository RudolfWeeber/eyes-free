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
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.MediaStore;

import com.marvin.rocklock.R;

/**
 * SongPicker that navigates songs in alphabetical order
 *
 * @author sainsley@google.com (Sam Ainsley)
 */

public class TrackSortedSongPicker extends ColumnGroupedSongPicker {

    private String mCurrentTrack;
    private String mCurrentLetter;

    public TrackSortedSongPicker(Activity parentActivity) {
        super(parentActivity, TRACK, TRACK_ID, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null);
    }

    /**
     * Remove any leading articles
     *
     * @param track
     * @return track
     */
    private String getFormattedTrack(String track) {
        track = track.toLowerCase();
        Resources res = mParentActivity.getResources();
        String[] articles = res.getStringArray(R.array.articles_to_ignore);
        for (int i = 0; i < articles.length; ++i) {
            if (track.startsWith(articles[i] + " ")) {
                track = track.substring(articles[i].length() + 1);
            }
        }
        return track;
    }

    /**
     * Updates the current group
     *
     * @return groupName
     */
    @Override
    public String updateGroup() {
        mCurrentTrack = mMusicCursor.getString(TRACK);
        mCurrentLetter = getFormattedTrack(mCurrentTrack).substring(0, 1);
        return mCurrentLetter;
    }

    /**
     * Returns true is we are pointing to a new group
     *
     * @return isNewGroup
     */
    @Override
    protected boolean isNewGroup() {
        return !getFormattedTrack(mMusicCursor.getString(TRACK)).startsWith(mCurrentLetter);
    }

    /**
     * Returns the name of the currentGroup
     *
     * @return groupName
     */
    @Override
    protected String groupTag(boolean filtering) {
        if (filtering) {
            return mMusicCursor.getString(TRACK);
        }
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

        boolean found = false;
        mMusicCursor.moveToFirst();
        do {
            found = mMusicCursor.getInt(TRACK_ID) == resultId;
        } while (!found && mMusicCursor.moveToNext());
    }

    /**
     * Gets cursor with filter for current group so we know how many elements it
     * has
     */
    @Override
    protected Cursor getGroupCursor() {
        return mMusicCursor;
    }

    /**
     * Returns first index for given group
     *
     * @return base index
     */
    @Override
    protected int getBaseIdx() {
        return 0;
    }
}
