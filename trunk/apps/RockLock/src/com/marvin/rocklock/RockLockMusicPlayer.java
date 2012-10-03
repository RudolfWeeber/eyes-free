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

import java.io.File;
import java.util.HashMap;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.marvin.rocklock.navigation.SongNavigator;
import com.marvin.rocklock.navigation.SongPicker;

/**
 * Music player abstraction that takes care of using the MediaPlayer and the
 * SongPickers.
 *
 * @author sainsley@google.com (Sam Ainsley)
 * @author clchen@google.com (Charles L. Chen)
 */
public class RockLockMusicPlayer {

    private static final float VOLUME_MAX = 1.0f;
    private static final float VOLUME_DUCK = 0.1f;
    private static final int SEEK_STEP = 300;

    private final RockLockActivity mParent;
    private final SongNavigator mNavigator;
    private MediaPlayer mPlayer;
    private String mCurrentSong;
    private String mCurrentSongInfo;
    private String mCurrentSongFile;
    private String mCurrentSongId;
    private int mRestartTime;
    private boolean mFirstPlay;
    private boolean mVerbose;
    private boolean mIsBrowsing = false;

    private final OnCompletionListener mPlayerCompletionListener;

    /**
     * Grabs current picker mode from preferences and updates accordingly
     *
     * @param parent the RockLockActivity that owns this player
     */
    public RockLockMusicPlayer(RockLockActivity parent) {
        mParent = parent;
        mNavigator = new SongNavigator(parent);
        updateInfo();

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(parent);
        mVerbose = prefs.getBoolean(mParent.getString(R.string.verbose_mode), true);
        mRestartTime = prefs.getInt(RockLockActivity.PREF_RESUME_TIME, 0);
        mFirstPlay = true;

        mPlayerCompletionListener = new OnCompletionListener() {
                @Override
            public void onCompletion(MediaPlayer mp) {
                boolean isValid = false;
                do {
                    mNavigator.browseAdjacentTrack(1, false);
                    String file = mNavigator.getCurrentSongFile();
                    File testFile = new File(file);
                    isValid = testFile.isFile();
                } while (!isValid);
                play();
                mNavigator.save();
                mParent.handleUpdateRequest();
            }
        };
    }

    /**
     * Gets the current position of the music player in terms of milliseconds
     *
     * @return time
     */
    public int getCurrentSongTime() {
        if (mPlayer == null) {
            return 0;
        }
        return mPlayer.getCurrentPosition();
    }

    /**
     * Gets the current name that is playing
     *
     * @return mCurrentSong
     */
    public String getPlaying() {
        return mCurrentSong;
    }

    /**
     * Gets the current file that is playing (this might differ from the current
     * navigator file id).
     *
     * @return mCurrentSongFile
     */
    public String getPlayingFile() {
        return mCurrentSongFile;
    }

    /**
     * Gets the current info for the song that is playing (this might differ
     * from the current navigator file id).
     *
     * @return mCurrentSongInfo
     */
    public String getPlayingInfo() {
        return mCurrentSongInfo;
    }

    /**
     * Gets the current file that is playing (this might differ from the current
     * navigator song id).
     *
     * @return mCurrentSongFile
     */
    public String getPlayingId() {
        return mCurrentSongId;
    }

    /**
     * Toggle verbose mode
     */
    protected void toggleVerbose() {
        mVerbose = !mVerbose;
    }

    /**
     * Move to given song and group position
     */
    protected void moveTo(int navMode, int groupIdx, int songIdx) {
        mNavigator.moveTo(navMode, groupIdx, songIdx);
        mFirstPlay = false;
        play();
        mParent.handleUpdateRequest();
    }

    /**
     * Play a given bookmark
     *
     * @param bookmark the bookmark to play
     */
    protected void playBookmark(Bookmark bookmark) {
        mNavigator.moveToBookmark(bookmark);
        play();
        mPlayer.seekTo(bookmark.getTime());
    }

    /**
     * Gets the SongNavigator for this player
     *
     * @return navigator
     */
    protected SongNavigator getNavigator() {
        return mNavigator;
    }

    /**
     * Toggles looping for current picker
     */
    public void toggleLoop() {
        if (mPlayer == null) {
            return;
        }
        boolean isLooping = mPlayer.isLooping();
        mPlayer.setLooping(!isLooping);
    }

    /**
     * Toggle pause / play
     */
    public void togglePlayPause() {
        boolean isNewSong = !mNavigator.getCurrentPlayableSongFile().equals(mCurrentSongFile);
        if (mPlayer != null && !isNewSong) {
            if (mPlayer.isPlaying()) {
                mPlayer.pause();
                mParent.mTts.playEarcon(RockLockActivity.TICK_EARCON, 0, null);
            } else {
                mPlayer.start();
            }
        } else {
            play();
            mIsBrowsing = false;
            // TODO(sainsley) : consider turning off browsing for player on back
            // button
        }
    }

    /**
     * Return true if playing
     *
     * @return isPlaying
     */
    public boolean isPlaying() {
        boolean isPlaying = false;
        if (mPlayer != null) {
            try {
                isPlaying = mPlayer.isPlaying();
            } catch (IllegalStateException e) {
                Log.e("RockLockMusicPlayer", e.toString());
            }
        }
        return isPlaying;
    }

    /**
     * Return true if the player is not null
     *
     * @return is ready
     */
    public boolean isReady() {
        return mPlayer != null;
    }

    /**
     * Stops the media player
     */
    public synchronized void stop() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

    /**
     * Seek
     */
    public void seek(int direction) {
        if (mPlayer != null) {
            try {
                mPlayer.seekTo(mPlayer.getCurrentPosition() + direction * SEEK_STEP);
            } catch (IllegalStateException e) {
                Log.e("MusicPlayer", "CANNOT SEEK " + e.toString());
            }
        }
    }

    /**
     * Peek next track for current picker
     */
    public void peekNextTrack() {
        speakAndDuck(mNavigator.peekNextTrack(), true);
    }

    /**
     * Peek prev track for current picker
     */
    public void peekPrevTrack() {
        speakAndDuck(mNavigator.peekPrevTrack(), true);
    }

    /**
     * Go to next track for current picker
     */
    public void nextTrack(boolean browsing) {
        mIsBrowsing = browsing;
        mFirstPlay = false;
        mNavigator.browseAdjacentTrack(SongPicker.DIRECTION_FORWARD, false);
        if (!mIsBrowsing && isPlaying()) {
            play();
        }
    }

    /**
     * Go to previous track for current picker
     */
    public void prevTrack(boolean browsing) {
        mIsBrowsing = browsing;
        mFirstPlay = false;
        mNavigator.browseAdjacentTrack(SongPicker.DIRECTION_BACKWARD, false);
        if (!mIsBrowsing && isPlaying()) {
            play();
        }
    }

    /**
     * Go to next group for current picker
     */
    public void nextGroup(boolean browsing) {
        mIsBrowsing = browsing;
        mNavigator.browseAdjacentGroup(SongPicker.DIRECTION_FORWARD, false);
        if (!mIsBrowsing && isPlaying()) {
            play();
        }
    }

    /**
     * Go to previous group for current picker
     */
    public void prevGroup(boolean browsing) {
        mIsBrowsing = browsing;
        mNavigator.browseAdjacentGroup(SongPicker.DIRECTION_BACKWARD, false);
        if (!mIsBrowsing && isPlaying()) {
            play();
        }
    }

    /**
     * Update info for current track
     */
    private void updateInfo() {
        mCurrentSong = mNavigator.getCurrentSong();
        mCurrentSongInfo = mNavigator.getCurrentSongInfo(true);
        mCurrentSongId = mNavigator.getCurrentSongId();
    }

    /**
     * Play a given file
     */
    private synchronized void play() {
        String filename = mNavigator.getCurrentPlayableSongFile();
        File testFile = new File(filename);
        if (testFile.isFile()) {
            try {
                if (mPlayer != null) {
                    mPlayer.release();
                }
                mPlayer = MediaPlayer.create(mParent, Uri.fromFile(testFile));
                if (mPlayer == null) {
                    return;
                }
                mPlayer.setOnCompletionListener(mPlayerCompletionListener);
                if (mFirstPlay) {
                    mPlayer.seekTo(mRestartTime);
                    mFirstPlay = false;
                }
                mParent.mTts.stop();
                mPlayer.start();
                mCurrentSongFile = filename;
                updateInfo();
                mNavigator.saveToPreferences();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        } else {
            speakAndDuck(testFile.getName(), true);
        }
    }

    /**
     * Speaks an audio preview without too much interruption to the current
     * music
     *
     * @param utterance The {@link String} to speak
     */
    public void speakAndDuck(String utterance, boolean interrupt) {
        if (!mVerbose) {
            return;
        }
        String id = Long.toString(System.currentTimeMillis());
        HashMap<String, String> hashMap = new HashMap<String, String>();
        hashMap.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id);
        int mode = TextToSpeech.QUEUE_ADD;
        if (interrupt) {
            mode = TextToSpeech.QUEUE_FLUSH;
        }
        mParent.mTts.speak(utterance, mode, hashMap);
        mParent.mCurrentUtteranceId = id;
        // Duck playback volume (ducking with AudioManager will only affect
        // other apps)
        if (mPlayer != null) {
            mPlayer.setVolume(VOLUME_DUCK, VOLUME_DUCK);
        }
    }

    /**
     * Restores volume to full
     */
    public void restoreMusic() {
        mPlayer.setVolume(VOLUME_MAX, VOLUME_MAX);
    }
}
