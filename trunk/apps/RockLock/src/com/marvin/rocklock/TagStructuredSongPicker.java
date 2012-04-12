/*
 * Copyright (C) 2010 Google Inc.
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

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AudioColumns;
import android.provider.MediaStore.MediaColumns;

/**
 * SongPicker implementation that uses the tags in the music files to organize
 * the music.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class TagStructuredSongPicker implements SongPicker {
    private static final String PREF_ARTIST = "TAG_ARTIST";

    private static final String PREF_ALBUM = "TAG_ALBUM";

    private static final String PREF_TRACK = "TAG_TRACK";

    private static final int ARTIST = 0;

    private static final int ALBUM = 1;

    private static final int TRACK = 2;

    private static final int FILEPATH = 3;

    private Cursor musicCursor;

    private String currentArtist = "";

    private String currentAlbum = "";

    private String currentTrack = "";

    private Editor editor;

    private boolean taggedMusicAvailable = true;

    public TagStructuredSongPicker(Activity parentActivity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(parentActivity);
        editor = prefs.edit();

        String[] proj = {
                AudioColumns.ARTIST, AudioColumns.ALBUM, MediaColumns.TITLE, MediaColumns.DATA
        };
        taggedMusicAvailable = true;
        musicCursor = parentActivity.managedQuery(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                proj, null, null, null);
        if (musicCursor == null) {
            taggedMusicAvailable = false;
            return;
        }
        taggedMusicAvailable = musicCursor.moveToFirst();
        if (!taggedMusicAvailable) {
            return;
        }
        if (!restoreFromPrefs(prefs)) {
            musicCursor.moveToFirst();
            currentArtist = musicCursor.getString(ARTIST);
            currentAlbum = musicCursor.getString(ALBUM);
            currentTrack = musicCursor.getString(TRACK);
        }
    }

    private boolean restoreFromPrefs(SharedPreferences prefs) {
        musicCursor.moveToFirst();
        currentArtist = prefs.getString(PREF_ARTIST, "");
        currentAlbum = prefs.getString(PREF_ALBUM, "");
        currentTrack = prefs.getString(PREF_TRACK, "");
        while (musicCursor.moveToNext()) {
            if ((musicCursor.getString(ARTIST) != null)
                    && musicCursor.getString(ARTIST).equals(currentArtist)
                    && musicCursor.getString(ALBUM).equals(currentAlbum)
                    && musicCursor.getString(TRACK).equals(currentTrack)) {
                return true;
            }
        }
        return false;
    }

    public String peekNextArtist() {
        if (!taggedMusicAvailable) {
            return "";
        }
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToNext()) {
            if (!musicCursor.getString(ARTIST).equals(currentArtist)) {
                String artist = musicCursor.getString(ARTIST);
                musicCursor.moveToPosition(initialPosition);
                return artist;
            }
        }
        musicCursor.moveToFirst();
        while (musicCursor.getPosition() < initialPosition) {
            if (!musicCursor.getString(ARTIST).equals(currentArtist)) {
                String artist = musicCursor.getString(ARTIST);
                musicCursor.moveToPosition(initialPosition);
                return artist;
            }
            musicCursor.moveToNext();
        }
        return musicCursor.getString(ARTIST);
    }

    public String goNextArtist() {
        if (!taggedMusicAvailable) {
            return "";
        }
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToNext()) {
            if (!musicCursor.getString(ARTIST).equals(currentArtist)) {
                currentArtist = musicCursor.getString(ARTIST);
                return currentArtist;
            }
        }
        musicCursor.moveToFirst();
        while (musicCursor.getPosition() < initialPosition) {
            if (!musicCursor.getString(ARTIST).equals(currentArtist)) {
                currentArtist = musicCursor.getString(ARTIST);
                return currentArtist;
            }
            musicCursor.moveToNext();
        }
        currentArtist = musicCursor.getString(ARTIST);
        return currentArtist;
    }

    public String peekPrevArtist() {
        if (!taggedMusicAvailable) {
            return "";
        }
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToPrevious()) {
            if (!musicCursor.getString(ARTIST).equals(currentArtist)) {
                String artist = musicCursor.getString(ARTIST);
                musicCursor.moveToPosition(initialPosition);
                return artist;
            }
        }
        musicCursor.moveToLast();
        while (musicCursor.getPosition() > initialPosition) {
            if (!musicCursor.getString(ARTIST).equals(currentArtist)) {
                String artist = musicCursor.getString(ARTIST);
                musicCursor.moveToPosition(initialPosition);
                return artist;
            }
            musicCursor.moveToPrevious();
        }
        return musicCursor.getString(ARTIST);
    }

    public String goPrevArtist() {
        if (!taggedMusicAvailable) {
            return "";
        }
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToPrevious()) {
            if (!musicCursor.getString(ARTIST).equals(currentArtist)) {
                currentArtist = musicCursor.getString(ARTIST);
                return currentArtist;
            }
        }
        musicCursor.moveToLast();
        while (musicCursor.getPosition() > initialPosition) {
            if (!musicCursor.getString(ARTIST).equals(currentArtist)) {
                currentArtist = musicCursor.getString(ARTIST);
                return currentArtist;
            }
            musicCursor.moveToPrevious();
        }
        currentArtist = musicCursor.getString(ARTIST);
        return currentArtist;
    }

    public String peekNextAlbum() {
        if (!taggedMusicAvailable) {
            return "";
        }
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToNext()) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && !musicCursor.getString(ALBUM).equals(currentAlbum)) {
                String album = musicCursor.getString(ALBUM);
                musicCursor.moveToPosition(initialPosition);
                return album;
            }
        }
        musicCursor.moveToFirst();
        while (musicCursor.getPosition() < initialPosition) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && !musicCursor.getString(ALBUM).equals(currentAlbum)) {
                String album = musicCursor.getString(ALBUM);
                musicCursor.moveToPosition(initialPosition);
                return album;
            }
            musicCursor.moveToNext();
        }
        return musicCursor.getString(ALBUM);
    }

    public String goNextAlbum() {
        if (!taggedMusicAvailable) {
            return "";
        }
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToNext()) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && !musicCursor.getString(ALBUM).equals(currentAlbum)) {
                currentAlbum = musicCursor.getString(ALBUM);
                return currentAlbum;
            }
        }
        musicCursor.moveToFirst();
        while (musicCursor.getPosition() < initialPosition) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && !musicCursor.getString(ALBUM).equals(currentAlbum)) {
                currentAlbum = musicCursor.getString(ALBUM);
                return currentAlbum;
            }
            musicCursor.moveToNext();
        }
        currentAlbum = musicCursor.getString(ALBUM);
        return currentAlbum;
    }

    public String peekPrevAlbum() {
        if (!taggedMusicAvailable) {
            return "";
        }
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToPrevious()) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && !musicCursor.getString(ALBUM).equals(currentAlbum)) {
                String album = musicCursor.getString(ALBUM);
                musicCursor.moveToPosition(initialPosition);
                return album;
            }
        }
        musicCursor.moveToLast();
        while (musicCursor.getPosition() > initialPosition) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && !musicCursor.getString(ALBUM).equals(currentAlbum)) {
                String album = musicCursor.getString(ALBUM);
                musicCursor.moveToPosition(initialPosition);
                return album;
            }
            musicCursor.moveToPrevious();
        }
        return musicCursor.getString(ALBUM);
    }

    public String goPrevAlbum() {
        if (!taggedMusicAvailable) {
            return "";
        }
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToPrevious()) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && !musicCursor.getString(ALBUM).equals(currentAlbum)) {
                currentAlbum = musicCursor.getString(ALBUM);
                return currentAlbum;
            }
        }
        musicCursor.moveToLast();
        while (musicCursor.getPosition() > initialPosition) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && !musicCursor.getString(ALBUM).equals(currentAlbum)) {
                currentAlbum = musicCursor.getString(ALBUM);
                return currentAlbum;
            }
            musicCursor.moveToPrevious();
        }
        currentAlbum = musicCursor.getString(ALBUM);
        return currentAlbum;
    }

    public String peekNextTrack() {
        if (!taggedMusicAvailable) {
            return "";
        }
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToNext()) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && musicCursor.getString(ALBUM).equals(currentAlbum)
                    && !musicCursor.getString(TRACK).equals(currentTrack)) {
                String track = musicCursor.getString(TRACK);
                musicCursor.moveToPosition(initialPosition);
                return track;
            }
        }
        musicCursor.moveToFirst();
        while (musicCursor.getPosition() < initialPosition) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && musicCursor.getString(ALBUM).equals(currentAlbum)
                    && !musicCursor.getString(TRACK).equals(currentTrack)) {
                String track = musicCursor.getString(TRACK);
                musicCursor.moveToPosition(initialPosition);
                return track;
            }
            musicCursor.moveToNext();
        }
        return musicCursor.getString(TRACK);
    }

    public String goNextTrack() {
        if (!taggedMusicAvailable) {
            return "";
        }
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToNext()) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && musicCursor.getString(ALBUM).equals(currentAlbum)
                    && !musicCursor.getString(TRACK).equals(currentTrack)) {
                currentTrack = musicCursor.getString(TRACK);
                return currentTrack;
            }
        }
        musicCursor.moveToFirst();
        while (musicCursor.getPosition() < initialPosition) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && musicCursor.getString(ALBUM).equals(currentAlbum)
                    && !musicCursor.getString(TRACK).equals(currentTrack)) {
                currentTrack = musicCursor.getString(TRACK);
                return currentTrack;
            }
            musicCursor.moveToNext();
        }
        currentTrack = musicCursor.getString(TRACK);
        return currentTrack;
    }

    public String peekPrevTrack() {
        if (!taggedMusicAvailable) {
            return "";
        }
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToPrevious()) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && musicCursor.getString(ALBUM).equals(currentAlbum)
                    && !musicCursor.getString(TRACK).equals(currentTrack)) {
                String track = musicCursor.getString(TRACK);
                musicCursor.moveToPosition(initialPosition);
                return track;
            }
        }
        musicCursor.moveToLast();
        while (musicCursor.getPosition() > initialPosition) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && musicCursor.getString(ALBUM).equals(currentAlbum)
                    && !musicCursor.getString(TRACK).equals(currentTrack)) {
                String track = musicCursor.getString(TRACK);
                musicCursor.moveToPosition(initialPosition);
                return track;
            }
            musicCursor.moveToPrevious();
        }
        return musicCursor.getString(TRACK);
    }

    public String goPrevTrack() {
        if (!taggedMusicAvailable) {
            return "";
        }
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToPrevious()) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && musicCursor.getString(ALBUM).equals(currentAlbum)
                    && !musicCursor.getString(TRACK).equals(currentTrack)) {
                currentTrack = musicCursor.getString(TRACK);
                return currentTrack;
            }
        }
        musicCursor.moveToLast();
        while (musicCursor.getPosition() > initialPosition) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && musicCursor.getString(ALBUM).equals(currentAlbum)
                    && !musicCursor.getString(TRACK).equals(currentTrack)) {
                currentTrack = musicCursor.getString(TRACK);
                return currentTrack;
            }
            musicCursor.moveToPrevious();
        }
        currentTrack = musicCursor.getString(TRACK);
        return currentTrack;
    }

    public String getCurrentSongFile() {
        if (!taggedMusicAvailable) {
            return "";
        }
        currentArtist = musicCursor.getString(ARTIST);
        currentAlbum = musicCursor.getString(ALBUM);
        currentTrack = musicCursor.getString(TRACK);
        editor.putString(PREF_ARTIST, currentArtist);
        editor.putString(PREF_ALBUM, currentAlbum);
        editor.putString(PREF_TRACK, currentTrack);
        editor.commit();
        return musicCursor.getString(FILEPATH);
    }

    public String getCurrentSongInfo() {
        if (!taggedMusicAvailable) {
            return "";
        }
        currentArtist = musicCursor.getString(ARTIST);
        currentAlbum = musicCursor.getString(ALBUM);
        currentTrack = musicCursor.getString(TRACK);
        return currentArtist + "\n" + currentAlbum + "\n" + currentTrack;
    }
}
