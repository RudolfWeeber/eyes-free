/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.marvin.talkback;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/*
 * This content provider enables TalkBack to provide its status (running or not)
 * to other applications.
 *
 * Applications can check if any screen readers are running by doing the
 * following:
 *
 * 1. Detect all installed screen readers by using Intents.
 *    Screen reader services are expected to respond to intent filters for
 *    action = "android.accessibilityservice.AccessibilityService" and
 *    category = "android.accessibilityservice.category.FEEDBACK_SPOKEN".
 *
 * 2. For the list of screen readers installed, check their status provider.
 *    Screen readers are expected to implement:
 *    <packagename>.providers.StatusProvider.
 *    For example, in TalkBack, the status provider is:
 *    com.google.android.marvin.talkback.providers.StatusProvider
 *
 * 3. The status provider returns 0 for inactive, and
 *    1 for active.
 *
 * @author clchen@google.com (Charles L. Chen)
 */

public class StatusProvider extends ContentProvider {
    private class StatusCursor extends MatrixCursor {
        private int status;

        public StatusCursor() {
            super(new String[] {""});
        }

        public void setStatus(int status) {
            this.status = status;
        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public String getString(int column) {
            return String.valueOf(status);
        }

        @Override
        public int getInt(int column) {
            return status;
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        StatusCursor cursor = new StatusCursor();
        if (TalkBackService.isServiceInitialized()) {
            cursor.setStatus(TalkBackService.RESULT_TALKBACK_ENABLED);
        } else {
            cursor.setStatus(TalkBackService.RESULT_TALKBACK_DISABLED);
        }
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
