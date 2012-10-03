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

package com.marvin.rocklock;

import java.io.File;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;

/**
 * A bookmark representation with a filepath, id and track time
 *
 * @author sainsley@google.com (Sam Ainsley)
 */
public class Bookmark {

    private final String mFile;
    private final int mTrackId;
    private final int mTime;
    private String mName;

    /**
     * Create a bookmark for a given bookmark string
     *
     * @param bookmarkString
     */
    public Bookmark(String bookmarkString) {
        String[] parts = bookmarkString.split(":");
        mTrackId = Integer.parseInt(parts[0]);
        mFile = parts[1];
        mTime = Integer.parseInt(parts[2]);
    }

    /**
     * Create a bookmark for a given bookmark string
     *
     * @param id the track ID
     * @param filePath the track filepath
     * @param time the bookmark time
     */
    public Bookmark(String id, String filePath, int time) {
        mTrackId = Integer.parseInt(id);
        mFile = filePath;
        mTime = time;
    }

    /**
     * Gets the file path for this bookmark
     *
     * @return mFile
     */
    public String getFile() {
        return mFile;
    }

    /**
     * Gets the track ID for this bookmark
     *
     * @return mTrackId
     */
    public int getId() {
        return mTrackId;
    }

    /**
     * Gets the time associated with this bookmark
     *
     * @return mTime
     */
    public int getTime() {
        return mTime;
    }

    /**
     * Gets the bookmark name
     *
     * @return name
     */
    public String getName(Context context) {
        if (mName != null) {
            return mName;
        }
        ContentResolver resolver = context.getContentResolver();
        String filter = MediaColumns._ID + "=" + mTrackId;
        String[] proj = new String[] {
        MediaColumns._ID, MediaColumns.TITLE };
        Cursor nameQuery = resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, filter,
                null, null);
        if (nameQuery != null && nameQuery.moveToFirst()) {
            mName = nameQuery.getString(1) + " " + mTime;
        } else {
            mName = new File(mFile).getName() + " " + mTime;
        }
        return mName;
    }

    /**
     * Gets the bookmark information for storage
     */
    @Override
    public String toString() {
        return mTrackId + ":" + mFile + ":" + mTime;
    }

    /**
     * Returns true if this bookmark is still valid
     *
     * @return
     */
    public boolean isValid() {
        File testFile = new File(mFile);
        return testFile.exists();
    }
}
