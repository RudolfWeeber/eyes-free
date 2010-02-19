
package com.marvin.rocklock;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;

public class TagStructuredSongPicker implements SongPicker {
    private static int ARTIST = 0;

    private static int ALBUM = 1;

    private static int TRACK = 2;

    private static int FILEPATH = 3;

    Cursor musicCursor;

    String currentArtist = "";

    String currentAlbum = "";

    String currentTrack = "";

    public TagStructuredSongPicker(Activity parentActivity) {
        String[] proj = {
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA
        };
        musicCursor = parentActivity.managedQuery(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                proj, null, null, null);

        musicCursor.moveToFirst();
        currentArtist = musicCursor.getString(ARTIST);
        currentAlbum = musicCursor.getString(ALBUM);
        currentTrack = musicCursor.getString(TRACK);
    }

    public String peekNextArtist() {
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
        Log.e("peek start", musicCursor.getPosition() + " " + getCurrentSongInfo());
        int initialPosition = musicCursor.getPosition();
        while (musicCursor.moveToNext()) {
            if (musicCursor.getString(ARTIST).equals(currentArtist)
                    && musicCursor.getString(ALBUM).equals(currentAlbum)
                    && !musicCursor.getString(TRACK).equals(currentTrack)) {
                String track = musicCursor.getString(TRACK);
                musicCursor.moveToPosition(initialPosition);
                Log.e("peek end 0", musicCursor.getPosition() + " " + getCurrentSongInfo());
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
                Log.e("peek end 1", musicCursor.getPosition() + " " + getCurrentSongInfo());
                return track;
            }
            musicCursor.moveToNext();
        }
        Log.e("peek end 2", musicCursor.getPosition() + " " + getCurrentSongInfo());
        return musicCursor.getString(TRACK);
    }

    public String goNextTrack() {
        Log.e("go", musicCursor.getPosition() + " " + getCurrentSongInfo());
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
        Log.e("debug", getCurrentSongInfo());
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
        return musicCursor.getString(FILEPATH);
    }

    public String getCurrentSongInfo() {
        return currentArtist + "\n" + currentAlbum + "\n" + currentTrack;
    }
}
