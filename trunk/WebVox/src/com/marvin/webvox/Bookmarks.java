/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.marvin.webvox;

import com.marvin.webvox.R;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.Browser;
import android.util.Log;
import android.webkit.WebIconDatabase;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.Date;

/**
 *  This class is purely to have a common place for adding/deleting bookmarks.
 */
/* package */ class Bookmarks {
    private static final String     WHERE_CLAUSE
            = "url = ? OR url = ? OR url = ? OR url = ?";
    private static final String     WHERE_CLAUSE_SECURE = "url = ? OR url = ?";

    private static String[]         SELECTION_ARGS;

    /**
     *  Add a bookmark to the database.
     *  @param context Context of the calling Activity.  This is used to make
     *          Toast confirming that the bookmark has been added.  If the
     *          caller provides null, the Toast will not be shown.
     *  @param cr The ContentResolver being used to add the bookmark to the db.
     *  @param url URL of the website to be bookmarked.
     *  @param name Provided name for the bookmark.
     *  @param thumbnail A thumbnail for the bookmark.
     *  @param retainIcon Whether to retain the page's icon in the icon database.
     *          This will usually be <code>true</code> except when bookmarks are
     *          added by a settings restore agent.
     */
    /* package */ static void addBookmark(Context context,
            ContentResolver cr, String url, String name,
            Bitmap thumbnail, boolean retainIcon) {
        // Want to append to the beginning of the list
        long creationTime = new Date().getTime();
        // First we check to see if the user has already visited this
        // site.  They may have bookmarked it in a different way from
        // how it's stored in the database, so allow different combos
        // to map to the same url.
        boolean secure = false;
        String compareString = url;
        if (compareString.startsWith("http://")) {
            compareString = compareString.substring(7);
        } else if (compareString.startsWith("https://")) {
            compareString = compareString.substring(8);
            secure = true;
        }
        if (compareString.startsWith("www.")) {
            compareString = compareString.substring(4);
        }
        if (secure) {
            SELECTION_ARGS = new String[2];
            SELECTION_ARGS[0] = "https://" + compareString;
            SELECTION_ARGS[1] = "https://www." + compareString;
        } else {
            SELECTION_ARGS = new String[4];
            SELECTION_ARGS[0] = compareString;
            SELECTION_ARGS[1] = "www." + compareString;
            SELECTION_ARGS[2] = "http://" + compareString;
            SELECTION_ARGS[3] = "http://" + SELECTION_ARGS[1];
        }
        Cursor cursor = cr.query(Browser.BOOKMARKS_URI,
                Browser.HISTORY_PROJECTION,
                secure ? WHERE_CLAUSE_SECURE : WHERE_CLAUSE,
                SELECTION_ARGS,
                null);
        ContentValues map = new ContentValues();
        if (cursor.moveToFirst() && cursor.getInt(
                Browser.HISTORY_PROJECTION_BOOKMARK_INDEX) == 0) {
            // This means we have been to this site but not bookmarked
            // it, so convert the history item to a bookmark
            map.put(Browser.BookmarkColumns.CREATED, creationTime);
            map.put(Browser.BookmarkColumns.TITLE, name);
            map.put(Browser.BookmarkColumns.BOOKMARK, 1);            
//            map.put(Browser.BookmarkColumns.THUMBNAIL, bitmapToBytes(thumbnail));
            cr.update(Browser.BOOKMARKS_URI, map,
                    "_id = " + cursor.getInt(0), null);
        } else {
            int count = cursor.getCount();
            boolean matchedTitle = false;
            for (int i = 0; i < count; i++) {
                // One or more bookmarks already exist for this site.
                // Check the names of each
                cursor.moveToPosition(i);
                if (cursor.getString(Browser.HISTORY_PROJECTION_TITLE_INDEX)
                        .equals(name)) {
                    // The old bookmark has the same name.
                    // Update its creation time.
                    map.put(Browser.BookmarkColumns.CREATED,
                            creationTime);
                    cr.update(Browser.BOOKMARKS_URI, map,
                            "_id = " + cursor.getInt(0), null);
                    matchedTitle = true;
                    break;
                }
            }
            if (!matchedTitle) {
                // Adding a bookmark for a site the user has visited,
                // or a new bookmark (with a different name) for a site
                // the user has visited
                map.put(Browser.BookmarkColumns.TITLE, name);
                map.put(Browser.BookmarkColumns.URL, url);
                map.put(Browser.BookmarkColumns.CREATED, creationTime);
                map.put(Browser.BookmarkColumns.BOOKMARK, 1);
                map.put(Browser.BookmarkColumns.DATE, 0);
//                map.put(Browser.BookmarkColumns.THUMBNAIL, bitmapToBytes(thumbnail));
                int visits = 0;
                if (count > 0) {
                    // The user has already bookmarked, and possibly
                    // visited this site.  However, they are creating
                    // a new bookmark with the same url but a different
                    // name.  The new bookmark should have the same
                    // number of visits as the already created bookmark.
                    visits = cursor.getInt(
                            Browser.HISTORY_PROJECTION_VISITS_INDEX);
                }
                // Bookmark starts with 3 extra visits so that it will
                // bubble up in the most visited and goto search box
                map.put(Browser.BookmarkColumns.VISITS, visits + 3);
                cr.insert(Browser.BOOKMARKS_URI, map);
            }
        }
        if (retainIcon) {
            WebIconDatabase.getInstance().retainIconForPageUrl(url);
        }
        cursor.deactivate();
        if (context != null) {
            Toast.makeText(context, R.string.added_to_bookmarks,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     *  Remove a bookmark from the database.  If the url is a visited site, it
     *  will remain in the database, but only as a history item, and not as a
     *  bookmarked site.
     *  @param context Context of the calling Activity.  This is used to make
     *          Toast confirming that the bookmark has been removed.  If the
     *          caller provides null, the Toast will not be shown.
     *  @param cr The ContentResolver being used to remove the bookmark.
     *  @param url URL of the website to be removed.
     */
    /* package */ static void removeFromBookmarks(Context context,
            ContentResolver cr, String url, String title) {
        Cursor cursor = cr.query(
                Browser.BOOKMARKS_URI,
                Browser.HISTORY_PROJECTION,
                "url = ? AND title = ?",
                new String[] { url, title },
                null);
        boolean first = cursor.moveToFirst();
        // Should be in the database no matter what
        if (!first) {
            throw new AssertionError("URL is not in the database! " + url + " " + title);
        }
        // Remove from bookmarks
        WebIconDatabase.getInstance().releaseIconForPageUrl(url);
        Uri uri = ContentUris.withAppendedId(Browser.BOOKMARKS_URI,
                cursor.getInt(Browser.HISTORY_PROJECTION_ID_INDEX));
        int numVisits = cursor.getInt(
                Browser.HISTORY_PROJECTION_VISITS_INDEX);
        if (0 == numVisits) {
            cr.delete(uri, null, null);
        } else {
            // It is no longer a bookmark, but it is still a visited
            // site.
            ContentValues values = new ContentValues();
            values.put(Browser.BookmarkColumns.BOOKMARK, 0);
            try {
                cr.update(uri, values, null, null);
            } catch (IllegalStateException e) {
                Log.e("removeFromBookmarks", "no database!");
            }
        }
        if (context != null) {
            Toast.makeText(context, R.string.removed_from_bookmarks,
                    Toast.LENGTH_LONG).show();
        }
        cursor.deactivate();
    }

    private static byte[] bitmapToBytes(Bitmap bm) {
        if (bm == null) {
            return null;
        }

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, os);
        return os.toByteArray();
    }
}
