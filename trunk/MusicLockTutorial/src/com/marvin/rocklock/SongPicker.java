package com.marvin.rocklock;

import android.os.Environment;

import java.io.File;
import java.io.FileFilter;

public class SongPicker {
    private class DirectoryFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            if (pathname.isDirectory()){
                return true;
            }
            return false;
        }        
    }

    private String currentArtist;

    private String currentAlbum;

    private String currentTrack;
    
    public SongPicker() {
        currentArtist = "";
        currentAlbum = "";
        currentTrack = "";
        nextArtist();
        nextAlbum();
        nextTrack();
    }
    
    public String nextArtist(){
        File musicDir = new File(Environment.getExternalStorageDirectory() + "/music");
        File[] artists = musicDir.listFiles(new DirectoryFilter());
        java.util.Arrays.sort(artists);
        if (currentArtist.length() > 1){
            boolean moved = false;
            for (int i=0; i<artists.length-1; i++){
                if (currentArtist.equals(artists[i].getAbsolutePath())){
                    currentArtist = artists[i+1].getAbsolutePath();
                    moved = true;
                    break;
                }
            }
            if (moved == false){
              currentArtist = artists[0].getAbsolutePath();
            }
        } else {
            currentArtist = artists[0].getAbsolutePath();
        }
        currentAlbum = "";
        currentTrack = "";
        return currentArtist.substring(currentArtist.lastIndexOf("/"));
    }
    
    public String prevArtist(){
        File musicDir = new File(Environment.getExternalStorageDirectory() + "/music");
        File[] artists = musicDir.listFiles(new DirectoryFilter());
        java.util.Arrays.sort(artists);
        if (currentArtist.length() > 1){
            boolean moved = false;
            for (int i=artists.length-1; i>0; i--){
                if (currentArtist.equals(artists[i].getAbsolutePath())){
                    currentArtist = artists[i-1].getAbsolutePath();
                    moved = true;
                    break;
                }
            }
            if (moved == false){
              currentArtist = artists[artists.length-1].getAbsolutePath();
            }
        } else {
            currentArtist = artists[artists.length-1].getAbsolutePath();
        }
        currentAlbum = "";
        currentTrack = "";
        return currentArtist.substring(currentArtist.lastIndexOf("/"));
    }
    
    public String nextAlbum(){
        File artistDir = new File(currentArtist);
        File[] albums = artistDir.listFiles(new DirectoryFilter());
        java.util.Arrays.sort(albums);
        if (currentAlbum.length() > 1){
            boolean moved = false;
            for (int i=0; i<albums.length-1; i++){
                if (currentAlbum.equals(albums[i].getAbsolutePath())){
                    currentAlbum = albums[i+1].getAbsolutePath();
                    moved = true;
                    break;
                }
            }
            if (moved == false){
                currentAlbum = albums[0].getAbsolutePath();
            }
        } else {
            currentAlbum = albums[0].getAbsolutePath();
        }
        currentTrack = "";
        return currentAlbum.substring(currentAlbum.lastIndexOf("/"));
    }
    
    public String prevAlbum(){
        File artistDir = new File(currentArtist);
        File[] albums = artistDir.listFiles(new DirectoryFilter());
        java.util.Arrays.sort(albums);
        if (currentAlbum.length() > 1){
            boolean moved = false;
            for (int i=albums.length-1; i>0; i--){
                if (currentAlbum.equals(albums[i].getAbsolutePath())){
                    currentAlbum = albums[i-1].getAbsolutePath();
                    moved = true;
                    break;
                }
            }
            if (moved == false){
                currentAlbum = albums[albums.length-1].getAbsolutePath();
            }
        } else {
            currentAlbum = albums[albums.length-1].getAbsolutePath();
        }
        currentTrack = "";
        return currentAlbum.substring(currentAlbum.lastIndexOf("/"));        
    }
    
    public String nextTrack(){
        File trackDir = new File(currentAlbum);
        File[] tracks = trackDir.listFiles();
        java.util.Arrays.sort(tracks);
        if (currentTrack.length() > 1){
            boolean moved = false;
            for (int i=0; i<tracks.length-1; i++){
                if (currentTrack.equals(tracks[i].getAbsolutePath())){
                    currentTrack = tracks[i+1].getAbsolutePath();
                    moved = true;
                    break;
                }
            }
            if (moved == false){
                currentTrack = tracks[0].getAbsolutePath();
            }
        } else {
            currentTrack = tracks[0].getAbsolutePath();
        }
        return currentTrack.substring(currentTrack.lastIndexOf("/"), currentTrack.lastIndexOf("."));
    }
    
    public String prevTrack(){
        File trackDir = new File(currentAlbum);
        File[] tracks = trackDir.listFiles();
        java.util.Arrays.sort(tracks);
        if (currentTrack.length() > 1){
            boolean moved = false;
            for (int i=tracks.length-1; i>0; i--){
                if (currentTrack.equals(tracks[i].getAbsolutePath())){
                    currentTrack = tracks[i-1].getAbsolutePath();
                    moved = true;
                    break;
                }
            }
            if (moved == false){
                currentTrack = tracks[tracks.length-1].getAbsolutePath();
            }
        } else {
            currentTrack = tracks[tracks.length-1].getAbsolutePath();
        }
        return currentTrack.substring(currentTrack.lastIndexOf("/"), currentTrack.lastIndexOf("."));
    }
    
    public String getCurrentSongFile(){
        return currentTrack;
    }
}
