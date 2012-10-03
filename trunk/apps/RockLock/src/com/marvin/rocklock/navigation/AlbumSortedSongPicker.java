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

/**
 * SongPicker that navigates albums in alphabetical order
 *
 * @author sainsley@google.com (Sam Ainsley)
 */

public class AlbumSortedSongPicker extends ColumnGroupedSongPicker {

    public AlbumSortedSongPicker(Activity parentActivity) {
        super(parentActivity, ALBUM, ALBUM_ID,
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        MediaStore.Audio.Media.TRACK + "," + MediaColumns.TITLE);
    }

    /**
     * Updates the current group
     *
     * @return groupName
     */
    @Override
    public String updateGroup() {
        mCurrentGroup = mMusicCursor.getString(ALBUM);
        return mCurrentGroup;
    }

    /**
     * Returns true is we are pointing to a new group
     *
     * @return isNewGroup
     */
    @Override
    protected boolean isNewGroup() {
        return !mMusicCursor.getString(ALBUM).equals(mCurrentGroup);
    }

    /**
     * Returns the name of the currentGroup
     *
     * @return groupName
     */
    @Override
    protected String groupTag(boolean filtering) {
        return mMusicCursor.getString(ALBUM);
    }
}
