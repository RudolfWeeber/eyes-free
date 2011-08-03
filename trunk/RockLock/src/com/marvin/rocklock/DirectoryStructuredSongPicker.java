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
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileFilter;

/**
 * SongPicker implementation that uses the directory structure to organize the
 * music instead of using the tags in the music files. For music to be picked
 * up, it has to be put in the /sdcard/RockLock/.music or /sdcard/RockLock/music
 * directory.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class DirectoryStructuredSongPicker implements SongPicker {
    private static final String PREF_ARTIST = "DIR_ARTIST";

    private static final String PREF_ALBUM = "DIR_ALBUM";

    private static final String PREF_TRACK = "DIR_TRACK";

    private File musicDir = new File(Environment.getExternalStorageDirectory() + "/RockLock/.music");

    private class DirectoryFilter implements FileFilter {
        public boolean accept(File pathname) {
            if (pathname.isDirectory()) {
                return true;
            }
            return false;
        }
    }

    private class MP3Filter implements FileFilter {
        public boolean accept(File pathname) {
            if (pathname.isFile() && pathname.toString().endsWith(".mp3")) {
                return true;
            }
            return false;
        }
    }

    private Editor editor;

    private String currentArtistFullPath;

    private String currentAlbumFullPath;

    private String currentTrackFullPath;

    public DirectoryStructuredSongPicker(Activity parentActivity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(parentActivity);
        editor = prefs.edit();
        if (!musicDir.exists()) {
            musicDir = new File(Environment.getExternalStorageDirectory() + "/RockLock");
        }
        if (!musicDir.exists()) {
            musicDir.mkdirs();
        }
        if (!restoreFromPrefs(prefs)) {
            currentArtistFullPath = "";
            currentAlbumFullPath = "";
            currentTrackFullPath = "";
            goNextArtist();
        }
    }

    private boolean restoreFromPrefs(SharedPreferences prefs) {
        currentArtistFullPath = prefs.getString(PREF_ARTIST, "");
        currentAlbumFullPath = prefs.getString(PREF_ALBUM, "");
        currentTrackFullPath = prefs.getString(PREF_TRACK, "");
        if (new File(currentArtistFullPath).exists() && new File(currentAlbumFullPath).exists()
                && new File(currentTrackFullPath).exists()) {
            return true;
        }
        return false;
    }

    private String filePathToName(final String filePath) {
        String name = filePath;
        int startIndex = name.lastIndexOf("/") + 1;
        int endIndex = name.lastIndexOf(".");
        if (endIndex > startIndex) {
            return name.substring(startIndex, endIndex);
        } else {
            return name.substring(startIndex);
        }
    }

    public String peekNextArtist() {
        String artist = "";
        File[] artists = musicDir.listFiles(new DirectoryFilter());
        if ((artists == null) || (artists.length == 0)) {
            return "";
        }
        java.util.Arrays.sort(artists);
        if (currentArtistFullPath.length() > 1) {
            boolean moved = false;
            for (int i = 0; i < artists.length - 1; i++) {
                if (currentArtistFullPath.equals(artists[i].getAbsolutePath())) {
                    return filePathToName(artists[i + 1].getAbsolutePath());
                }
            }
        }
        return filePathToName(artists[0].getAbsolutePath());
    }

    public String peekPrevArtist() {
        String artist = "";
        File[] artists = musicDir.listFiles(new DirectoryFilter());
        if ((artists == null) || (artists.length == 0)) {
            return "";
        }
        java.util.Arrays.sort(artists);
        if (currentArtistFullPath.length() > 1) {
            boolean moved = false;
            for (int i = artists.length - 1; i > 0; i--) {
                if (currentArtistFullPath.equals(artists[i].getAbsolutePath())) {
                    return filePathToName(artists[i - 1].getAbsolutePath());
                }
            }
        }
        return filePathToName(artists[artists.length - 1].getAbsolutePath());
    }

    public String goNextArtist() {
        currentArtistFullPath = musicDir + "/" + peekNextArtist();
        currentAlbumFullPath = "";
        goNextAlbum();
        currentTrackFullPath = "";
        goNextTrack();
        return filePathToName(currentArtistFullPath);
    }

    public String goPrevArtist() {
        currentArtistFullPath = musicDir + "/" + peekPrevArtist();
        currentAlbumFullPath = "";
        goNextAlbum();
        currentTrackFullPath = "";
        goNextTrack();
        return filePathToName(currentArtistFullPath);
    }

    public String peekNextAlbum() {
        File artistDir = new File(currentArtistFullPath);
        File[] albums = artistDir.listFiles(new DirectoryFilter());
        if ((albums == null) || (albums.length == 0)) {
            return "";
        }
        java.util.Arrays.sort(albums);
        if (currentAlbumFullPath.length() > 1) {
            boolean moved = false;
            for (int i = 0; i < albums.length - 1; i++) {
                if (currentAlbumFullPath.equals(albums[i].getAbsolutePath())) {
                    return filePathToName(albums[i + 1].getAbsolutePath());
                }
            }
        }
        return filePathToName(albums[0].getAbsolutePath());
    }

    public String goNextAlbum() {
        currentAlbumFullPath = currentArtistFullPath + "/" + peekNextAlbum();
        currentTrackFullPath = "";
        goNextTrack();
        return filePathToName(currentAlbumFullPath);
    }

    public String peekPrevAlbum() {
        File artistDir = new File(currentArtistFullPath);
        File[] albums = artistDir.listFiles(new DirectoryFilter());
        if ((albums == null) || (albums.length == 0)) {
            return "";
        }
        java.util.Arrays.sort(albums);
        if (currentAlbumFullPath.length() > 1) {
            boolean moved = false;
            for (int i = albums.length - 1; i > 0; i--) {
                if (currentAlbumFullPath.equals(albums[i].getAbsolutePath())) {
                    return filePathToName(albums[i - 1].getAbsolutePath());
                }
            }
        }
        return filePathToName(albums[albums.length - 1].getAbsolutePath());
    }

    public String goPrevAlbum() {
        currentAlbumFullPath = currentArtistFullPath + "/" + peekPrevAlbum();
        currentTrackFullPath = "";
        goNextTrack();
        return filePathToName(currentAlbumFullPath);
    }

    public String peekNextTrack() {
        File trackDir = new File(currentAlbumFullPath);
        File[] tracks = trackDir.listFiles(new MP3Filter());
        if ((tracks == null) || (tracks.length == 0)) {
            return "";
        }
        java.util.Arrays.sort(tracks);
        if (currentTrackFullPath.length() > 1) {
            boolean moved = false;
            for (int i = 0; i < tracks.length - 1; i++) {
                if (currentTrackFullPath.equals(tracks[i].getAbsolutePath())) {
                    return filePathToName(tracks[i + 1].getAbsolutePath());
                }
            }
        }
        return filePathToName(tracks[0].getAbsolutePath());
    }

    public String goNextTrack() {
        currentTrackFullPath = currentAlbumFullPath + "/" + peekNextTrack() + ".mp3";
        return filePathToName(currentTrackFullPath);
    }

    public String peekPrevTrack() {
        File trackDir = new File(currentAlbumFullPath);
        File[] tracks = trackDir.listFiles(new MP3Filter());
        if ((tracks == null) || (tracks.length == 0)) {
            return "";
        }
        java.util.Arrays.sort(tracks);
        if (currentTrackFullPath.length() > 1) {
            boolean moved = false;
            for (int i = tracks.length - 1; i > 0; i--) {
                if (currentTrackFullPath.equals(tracks[i].getAbsolutePath())) {
                    return filePathToName(tracks[i - 1].getAbsolutePath());
                }
            }
        }
        return filePathToName(tracks[tracks.length - 1].getAbsolutePath());
    }

    public String goPrevTrack() {
        currentTrackFullPath = currentAlbumFullPath + "/" + peekPrevTrack() + ".mp3";
        return filePathToName(currentTrackFullPath);
    }

    public String getCurrentSongFile() {
        editor.putString(PREF_ARTIST, currentArtistFullPath);
        editor.putString(PREF_ALBUM, currentAlbumFullPath);
        editor.putString(PREF_TRACK, currentTrackFullPath);
        editor.commit();
        return currentTrackFullPath;
    }

    public String getCurrentSongInfo() {
        return filePathToName(currentArtistFullPath) + "\n" + filePathToName(currentAlbumFullPath)
                + "\n" + filePathToName(currentTrackFullPath);
    }
}
