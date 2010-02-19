
package com.marvin.rocklock;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;

public class MusicPlayer {
    public static final int ROCKLOCK_PLAYLIST = 0;
    public static final int TAGGED_PLAYLIST = 1;

    private MediaPlayer player;

    private SongPicker picker;
    private DirectoryStructuredSongPicker dPicker;
    private TagStructuredSongPicker tPicker;
    private int songPickerType;

    private RockLockActivity parent;

    public MusicPlayer(Context ctx) {
        parent = (RockLockActivity) ctx;
        dPicker = new DirectoryStructuredSongPicker();
        tPicker = new TagStructuredSongPicker(parent);
        picker = dPicker;
        songPickerType = 0;
    }
    
    public int cycleSongPicker(){
        if (songPickerType == ROCKLOCK_PLAYLIST){
            picker = tPicker;
            songPickerType = TAGGED_PLAYLIST;
        } else {
            picker = dPicker;  
            songPickerType = ROCKLOCK_PLAYLIST;          
        }
        return songPickerType;
    }

    public void togglePlayPause() {
        if (player != null) {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.start();
            }
        } else {
            play(picker.getCurrentSongFile());
        }
    }

    public boolean isPlaying(){
        if (player != null) {
            return player.isPlaying();
        }
        return false;
    }
    
    public void stop() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
    
    public void seekForward(){
        if (player != null) {
            player.seekTo(player.getCurrentPosition() + 3000);
        }
    }
    
    public void seekBackward(){
        if (player != null) {
            player.seekTo(player.getCurrentPosition() - 3000);
        }
    }

    public void lowerMediaVolume() {
        // TODO: implement this!
    }

    public void restoreMediaVolume() {
        // TODO: implement this!
    }

    public void nextTrack() {
        picker.goNextTrack();
        play(picker.getCurrentSongFile());
    }

    public void prevTrack() {
        picker.goPrevTrack();
        play(picker.getCurrentSongFile());
    }

    public void nextAlbum() {
        picker.goNextAlbum();
        picker.goNextTrack();
        play(picker.getCurrentSongFile());
    }

    public void prevAlbum() {
        picker.goPrevAlbum();
        picker.goNextTrack();
        play(picker.getCurrentSongFile());
    }

    public void nextArtist() {
        picker.goNextArtist();
        picker.goNextAlbum();
        picker.goNextTrack();
        play(picker.getCurrentSongFile());
    }

    public void prevArtist() {
        picker.goPrevArtist();
        picker.goNextAlbum();
        picker.goNextTrack();
        play(picker.getCurrentSongFile());
    }

    private void play(String filename) {
        try {
            if (player != null) {
                player.release();
            }
            player = MediaPlayer.create(parent, Uri.parse(filename));
            player.setOnCompletionListener(new OnCompletionListener(){
                @Override
                public void onCompletion(MediaPlayer mp) {
                    nextTrack();
                    parent.updateUi();
                }
            });
            player.start();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public String getNextArtistName(){
        return picker.peekNextArtist();
    }
    
    public String getPrevArtistName(){
        return picker.peekPrevArtist();
    }
    
    public String getNextAlbumName(){
        return picker.peekNextAlbum();
    }
    
    public String getPrevAlbumName(){
        return picker.peekPrevAlbum();
    }
    
    public String getNextTrackName(){
        return picker.peekNextTrack();
    }
    
    public String getPrevTrackName(){
        return picker.peekPrevTrack();
    }

    public String getCurrentSongInfo(){
        return picker.getCurrentSongInfo();
    }
}
