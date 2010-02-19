
package com.marvin.rocklock;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;

public class DirectoryStructuredSongPicker implements SongPicker {
    private class DirectoryFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory()) {
                return true;
            }
            return false;
        }
    }

    private class MP3Filter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            if (pathname.isFile() && pathname.toString().endsWith(".mp3")) {
                return true;
            }
            return false;
        }
    }

    private String currentArtistFullPath;

    private String currentAlbumFullPath;

    private String currentTrackFullPath;

    public DirectoryStructuredSongPicker() {
        currentArtistFullPath = "";
        currentAlbumFullPath = "";
        currentTrackFullPath = "";
        goNextArtist();
        goNextAlbum();
        goNextTrack();
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
        File musicDir = new File(Environment.getExternalStorageDirectory() + "/music");
        File[] artists = musicDir.listFiles(new DirectoryFilter());
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
        File musicDir = new File(Environment.getExternalStorageDirectory() + "/music");
        File[] artists = musicDir.listFiles(new DirectoryFilter());
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
        File musicDir = new File(Environment.getExternalStorageDirectory() + "/music");
        currentArtistFullPath = musicDir + "/" + peekNextArtist();
        currentAlbumFullPath = "";
        currentTrackFullPath = "";
        return filePathToName(currentArtistFullPath);
    }

    public String goPrevArtist() {
        File musicDir = new File(Environment.getExternalStorageDirectory() + "/music");
        currentArtistFullPath = musicDir + "/" + peekPrevArtist();
        currentAlbumFullPath = "";
        currentTrackFullPath = "";
        return filePathToName(currentArtistFullPath);
    }

    public String peekNextAlbum() {
        File artistDir = new File(currentArtistFullPath);
        File[] albums = artistDir.listFiles(new DirectoryFilter());
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
        return filePathToName(currentAlbumFullPath);
    }

    public String peekPrevAlbum() {
        File artistDir = new File(currentArtistFullPath);
        File[] albums = artistDir.listFiles(new DirectoryFilter());
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
        return filePathToName(currentAlbumFullPath);
    }

    public String peekNextTrack() {
        File trackDir = new File(currentAlbumFullPath);
        File[] tracks = trackDir.listFiles(new MP3Filter());
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
        return currentTrackFullPath;
    }

    public String getCurrentSongInfo() {
        return filePathToName(currentArtistFullPath) + "\n" + filePathToName(currentAlbumFullPath)
                + "\n" + filePathToName(currentTrackFullPath);
    }
}
