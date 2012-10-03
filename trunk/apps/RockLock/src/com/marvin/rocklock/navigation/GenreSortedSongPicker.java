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
import android.net.Uri;
import android.provider.MediaStore;

/**
 * SongPicker that navigates genres
 *
 * @author sainsley@google.com (Sam Ainsley)
 */
public class GenreSortedSongPicker extends CursorGroupedSongPicker {

    public GenreSortedSongPicker(Activity parentActivity) {
        super(parentActivity, MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME, PROJECTION);
    }

    /**
     * Get the URI for the current genre
     *
     * @return Uri
     */
    @Override
    protected Uri getGroupUri() {
        return Uri.parse(new StringBuilder().append(mGroupUri.toString()).append("/")
                .append(mGroupCursor.getInt(GROUP_ID)).append("/")
                .append(MediaStore.Audio.Genres.Members.CONTENT_DIRECTORY)
                .toString());
    }
}
