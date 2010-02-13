
package com.marvin.rocklock;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

public class MusicPlayer {

    private MediaPlayer player;

    private SongPicker picker;

    private Context parent;

    public MusicPlayer(Context ctx) {
        parent = ctx;
        picker = new SongPicker();
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

    public void stop() {
        if (player != null) {
            player.release();
        }
    }

    public void lowerMediaVolume() {
        // TODO: implement this!
    }

    public void restoreMediaVolume() {
        // TODO: implement this!
    }

    public void nextTrack() {
        picker.nextTrack();
        play(picker.getCurrentSongFile());
    }

    public void prevTrack() {
        picker.prevTrack();
        play(picker.getCurrentSongFile());
    }

    public void nextAlbum() {
        picker.nextAlbum();
        picker.nextTrack();
        play(picker.getCurrentSongFile());
    }

    public void prevAlbum() {
        picker.prevAlbum();
        picker.nextTrack();
        play(picker.getCurrentSongFile());
    }

    public void nextArtist() {
        picker.nextArtist();
        picker.nextAlbum();
        picker.nextTrack();
        play(picker.getCurrentSongFile());
    }

    public void prevArtist() {
        picker.prevArtist();
        picker.nextAlbum();
        picker.nextTrack();
        play(picker.getCurrentSongFile());
    }

    private void play(String filename) {
        try {
            if (player != null) {
                player.release();
            }
            player = MediaPlayer.create(parent, Uri.parse(filename));
            player.start();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
