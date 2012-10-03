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

import android.app.AlertDialog;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.content.CursorLoader;
import android.util.Log;
import android.widget.EditText;

import java.util.ArrayList;

/**
 * A class with static methods for building and editing playlists
 *
 * @author sainsley@google.com (Sam Ainsley)
 */
public class PlaylistUtils {

    /**
     * Gets the playlist ID given it's name
     *
     * @param context The activity calling this method
     * @param name The playlist name
     * @return The playlist ID
     */
    public static int getPlaylistId(RockLockActivity context, String name) {
        String[] proj = {
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME };

        String formattedName = name.replace("'", "''");
        String filter = MediaStore.Audio.Playlists.NAME + "= '" + formattedName + "'";

        CursorLoader loader = new CursorLoader(
                context, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, proj, filter, null, null);
        Cursor testCursor = loader.loadInBackground();

        int idx = -1;
        boolean hasFirst = testCursor.moveToFirst();
        if (hasFirst) {
            idx = testCursor.getInt(0);
        }
        testCursor.close();
        return idx;
    }

    /**
     * Writes to a playlist given a list of new song IDs and the playlist name
     *
     * @param context the activity calling this method
     * @param playlistName the playlist name
     * @param ids the song IDs to add
     */
    public static void writePlaylist(RockLockActivity context, String playlistName,
            ArrayList<String> ids) {

        ContentResolver resolver = context.getContentResolver();
        int playlistId = getPlaylistId(context, playlistName);

        Uri uri;
        int playOrder = 1;
        if (playlistId == -1) {
            // Case: new playlist
            ContentValues values = new ContentValues(1);
            values.put(MediaStore.Audio.Playlists.NAME, playlistName);
            // this might be missing a members extension...
            uri = resolver.insert(
                    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values);
        } else {
            // Case: exists playlist
            uri = MediaStore.Audio.Playlists.Members.getContentUri("external",
                    playlistId);
            // Get most recent play order ID from playlist, so we can append
            Cursor orderCursor = resolver.query(uri,
                    new String[] {
                            MediaStore.Audio.Playlists.Members.PLAY_ORDER }, null, null,
                            MediaStore.Audio.Playlists.Members.PLAY_ORDER + " DESC ");

            if (orderCursor != null) {
                if (orderCursor.moveToFirst()) {
                    playOrder = orderCursor.getInt(0) + 1;
                }
                orderCursor.close();
            }
        }

        Log.d("PLAYLIST ACTIVITY", String.format("Writing playlist %s", uri));

        // Add all the new tracks to the playlist.
        int size = ids.size();
        ContentValues values[] = new ContentValues[size];

        final ContentProviderClient provider = resolver.acquireContentProviderClient(uri);
        for (int i = 0; i < size; ++i) {
            values[i] = new ContentValues();
            values[i].put(MediaStore.Audio.Playlists.Members.AUDIO_ID, ids.get(i));
            values[i].put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, playOrder++);
            resolver.insert(uri, values[i]);
        }
        provider.release();
    }

    /**
     * Deletes the playlist with the given ID
     *
     * @param context the managing activity
     * @param playlistId the playlist id to delete
     */
    public static void deletePlaylist(RockLockActivity context, int playlistId) {
        ContentResolver resolver = context.getContentResolver();
        // Delete playlist contents
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external",
                playlistId);
        resolver.delete(uri, null, null);
        // Delete row in playlist database
        String filter = MediaStore.Audio.Playlists._ID + "=" + playlistId;
        resolver.delete(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, filter, null);
    }

    /**
     * Adds a given song to an existing playlist
     *
     * @param context the managing activity
     * @param id the song id to add
     */
    public static void showPlaylistDialog(final RockLockActivity context, final String id) {

        // Get list of playlists
        String[] proj = {
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME };
        CursorLoader loader = new CursorLoader(
                context, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, proj, null, null, null);
        final Cursor playlistCursor = loader.loadInBackground();
        // Show playlists
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if (playlistCursor.moveToFirst()) {
            DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {

                    @Override
                public void onClick(DialogInterface dialog, int which) {
                    addToPlaylist(context, playlistCursor.getInt(0), id);
                }

            };
            builder.setCursor(playlistCursor, clickListener,
                    MediaStore.Audio.Playlists.NAME);
        } else {
            // No playlists: show create dialog
            builder.setTitle(context.getString(R.string.new_playlist));
            // TODO(sainsley): add default name based on
            // number of playlists in directory
            builder.setMessage(context.getString(R.string.enter_playlist_name));

            final EditText input = new EditText(context);
            builder.setView(input);

            builder.setPositiveButton(
                    context.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            String name = input.getText().toString();
                            ArrayList<String> ids = new ArrayList<String>();
                            ids.add(id);
                            writePlaylist(context, name, ids);
                            return;
                        }
                    });
        }
        builder.show();
    }

    /**
     * Adds a given song to an existing playlist
     *
     * @param context the managing activity
     * @param playlistId the playlist id to append
     * @param id the song id to add
     */
    private static void addToPlaylist(RockLockActivity context, int playlistId, String id) {
        ContentResolver resolver = context.getContentResolver();
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external",
                playlistId);
        // Get most recent play order ID from playlist, so we can append
        Cursor orderCursor = resolver.query(uri,
                new String[] {
                        MediaStore.Audio.Playlists.Members.PLAY_ORDER }, null, null,
                        MediaStore.Audio.Playlists.Members.PLAY_ORDER + " DESC ");

        int playOrder = 0;
        if (orderCursor != null) {
            if (orderCursor.moveToFirst()) {
                playOrder = orderCursor.getInt(0) + 1;
            }
            orderCursor.close();
        }

        ContentValues value = new ContentValues();
        value.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, id);
        value.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, playOrder++);
        resolver.insert(uri, value);
    }
}
