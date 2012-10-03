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
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

/**
 * SongPicker that navigates artists in alphabetical order and albums within
 * artists in alphabetical order as well
 *
 * @author sainsley@google.com (Sam Ainsley)
 */

public class ArtistSortedSongPicker extends ColumnGroupedSongPicker {

    private String mCurrentAlbum;

    public ArtistSortedSongPicker(Activity parentActivity) {
        super(parentActivity, ARTIST, ARTIST_ID,
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                        MediaStore.Audio.Media.ALBUM + ","
                        + MediaStore.Audio.Media.TRACK + "," + MediaColumns.TITLE);
    }

    /**
     * Updates the current group
     *
     * @return groupName
     */
    @Override
    public String updateGroup() {
        mCurrentAlbum = mMusicCursor.getString(ALBUM);
        mCurrentGroup = mMusicCursor.getString(ARTIST);
        return mCurrentGroup;
    }

    /**
     * Returns true is we are pointing to a new group
     *
     * @return isNewGroup
     */
    @Override
    protected boolean isNewGroup() {
        // Move to the next album within this artist before moving to the next
        // artist (this is essentially a group within a group, which breaks
        // convention, but the use case is very strong
        return !mMusicCursor.getString(ALBUM).equals(mCurrentAlbum)
                || !mMusicCursor.getString(ARTIST).equals(mCurrentGroup);
    }

    /**
     * Returns the name of the currentGroup
     *
     * @return groupName
     */
    @Override
    protected String groupTag(boolean filtering) {
        if (filtering) {
            return mCurrentGroup;
        }
        String group = "";
        if (!mMusicCursor.getString(ARTIST).equals(mCurrentGroup)) {
            group = mMusicCursor.getString(ARTIST);
        } else if (!mMusicCursor.getString(ALBUM).equals(mCurrentAlbum)) {
            group = mMusicCursor.getString(ALBUM);
        }
        Log.i("ArtistSorted", mMusicCursor.getString(ARTIST) + " " + mCurrentGroup + " "
                + mMusicCursor.getString(ALBUM) + " " + mCurrentAlbum);
        return group;
    }
}
