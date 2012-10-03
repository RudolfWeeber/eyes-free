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
import android.provider.MediaStore.MediaColumns;

/**
 * SongPicker that organizes by playlist
 *
 * @author sainsley@google.com (Sam Ainsley)
 */

public class PlaylistSongPicker extends CursorGroupedSongPicker {

    private static final String[] MEMBER_PROJECTION = {
            MediaStore.Audio.Playlists.Members._ID,
            MediaStore.Audio.Playlists.Members.ARTIST,
            MediaStore.Audio.Playlists.Members.TITLE,
            MediaColumns.DATA,
            MediaStore.Audio.Playlists.Members.ARTIST_ID,
            MediaStore.Audio.Playlists.Members.ALBUM_ID,
            MediaStore.Audio.Playlists.Members.AUDIO_ID,
            MediaStore.Audio.Media.TITLE_KEY
    };

    public PlaylistSongPicker(Activity parentActivity) {
        super(parentActivity, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME, MEMBER_PROJECTION);
    }

    /**
     * Get the URI for the current genre
     *
     * @return Uri
     */
    @Override
    protected Uri getGroupUri() {
        return MediaStore.Audio.Playlists.Members.getContentUri(
                "external", mGroupCursor.getInt(GROUP_ID));
    }
}
