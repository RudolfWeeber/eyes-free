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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.preference.PreferenceManager;

/**
 * Music player abstraction that takes care of using the MediaPlayer and the
 * SongPickers.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class MusicPlayer {
    private static final String PREF_PLAYLIST = "LAST_PLAYLIST";

    public static final int ROCKLOCK_PLAYLIST = 0;

    public static final int TAGGED_PLAYLIST = 1;

    private MediaPlayer player;

    private SongPicker picker;

    private DirectoryStructuredSongPicker dPicker;

    private boolean dPickerAvailable;

    private TagStructuredSongPicker tPicker;

    boolean tPickerAvailable;

    private int songPickerType;

    private RockLockActivity parent;

    private Editor editor;

    public MusicPlayer(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        editor = prefs.edit();

        parent = (RockLockActivity) ctx;
        dPicker = new DirectoryStructuredSongPicker(parent);
        tPicker = new TagStructuredSongPicker(parent);

        if (prefs.getInt(PREF_PLAYLIST, ROCKLOCK_PLAYLIST) == ROCKLOCK_PLAYLIST) {
            picker = dPicker;
            songPickerType = ROCKLOCK_PLAYLIST;
        } else {
            picker = tPicker;
            songPickerType = TAGGED_PLAYLIST;
        }

        dPickerAvailable = true;
        tPickerAvailable = true;
        if (dPicker.peekNextAlbum().length() < 1) {
            dPickerAvailable = false;
        }
        if (tPicker.peekNextAlbum().length() < 1) {
            tPickerAvailable = false;
        }
        if (tPickerAvailable && !dPickerAvailable) {
            picker = tPicker;
            songPickerType = TAGGED_PLAYLIST;
            editor.putInt(PREF_PLAYLIST, TAGGED_PLAYLIST);
            editor.commit();
        } else if (!tPickerAvailable && dPickerAvailable) {
            picker = dPicker;
            songPickerType = TAGGED_PLAYLIST;
            editor.putInt(PREF_PLAYLIST, TAGGED_PLAYLIST);
            editor.commit();
        }
    }

    public int cycleSongPicker() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return -1;
        }
        if (songPickerType == ROCKLOCK_PLAYLIST) {
            picker = tPicker;
            songPickerType = TAGGED_PLAYLIST;
        } else {
            picker = dPicker;
            songPickerType = ROCKLOCK_PLAYLIST;
        }
        if (tPickerAvailable && !dPickerAvailable) {
            picker = tPicker;
            songPickerType = TAGGED_PLAYLIST;
        } else if (!tPickerAvailable && dPickerAvailable) {
            picker = dPicker;
            songPickerType = ROCKLOCK_PLAYLIST;
        }
        editor.putInt(PREF_PLAYLIST, songPickerType);
        editor.commit();
        return songPickerType;
    }

    public void togglePlayPause() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return;
        }
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

    public boolean isPlaying() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return false;
        }
        if (player != null) {
            return player.isPlaying();
        }
        return false;
    }

    public synchronized void stop() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return;
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }

    public void seekForward() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return;
        }
        if (player != null) {
            player.seekTo(player.getCurrentPosition() + 3000);
        }
    }

    public void seekBackward() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return;
        }
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
        if (!tPickerAvailable && !dPickerAvailable) {
            return;
        }
        picker.goNextTrack();
        play(picker.getCurrentSongFile());
    }

    public void prevTrack() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return;
        }
        picker.goPrevTrack();
        play(picker.getCurrentSongFile());
    }

    public void nextAlbum() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return;
        }
        picker.goNextAlbum();
        play(picker.getCurrentSongFile());
    }

    public void prevAlbum() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return;
        }
        picker.goPrevAlbum();
        play(picker.getCurrentSongFile());
    }

    public void nextArtist() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return;
        }
        picker.goNextArtist();
        play(picker.getCurrentSongFile());
    }

    public void prevArtist() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return;
        }
        picker.goPrevArtist();
        play(picker.getCurrentSongFile());
    }

    private synchronized void play(String filename) {
        if (!tPickerAvailable && !dPickerAvailable) {
            return;
        }
        try {
            if (player != null) {
                player.release();
            }
            player = MediaPlayer.create(parent, Uri.parse(filename));
            if (player == null) {
                return;
            }
            player.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    nextTrack();
                    parent.updateDisplayText(null, null, false);
                }
            });
            player.start();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public String getNextArtistName() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return "";
        }
        return picker.peekNextArtist();
    }

    public String getPrevArtistName() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return "";
        }
        return picker.peekPrevArtist();
    }

    public String getNextAlbumName() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return "";
        }
        return picker.peekNextAlbum();
    }

    public String getPrevAlbumName() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return "";
        }
        return picker.peekPrevAlbum();
    }

    public String getNextTrackName() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return "";
        }
        return picker.peekNextTrack();
    }

    public String getPrevTrackName() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return "";
        }
        return picker.peekPrevTrack();
    }

    public String getCurrentSongInfo() {
        if (!tPickerAvailable && !dPickerAvailable) {
            return "";
        }
        return picker.getCurrentSongInfo();
    }
}
