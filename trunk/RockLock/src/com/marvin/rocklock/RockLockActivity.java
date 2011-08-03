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

import com.google.marvin.widget.GestureOverlay;
import com.google.marvin.widget.GestureOverlay.Gesture;
import com.google.marvin.widget.GestureOverlay.GestureListener;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * The main Rock Lock application that runs as an alternate lock screen which
 * enables the user to use stroke gestures to play music. If there is no lock
 * pattern, Rock Lock will replace the lock screen entirely; dismissing Rock
 * Lock will unlock the phone. If there is a lock pattern, Rock Lock will put up
 * the default pattern locked screen when the user dismisses Rock Lock.
 *
 * @author clchen@google.com (Charles L. Chen)
 */
public class RockLockActivity extends Activity {
    public static final String EXTRA_STARTED_BY_SERVICE = "STARTED_BY_SERVICE";

    public static final String TICK_EARCON = "[TICK]";

    private static final long[] VIBE_PATTERN = { 0, 10, 70, 80 };

    private RockLockActivity self;

    private boolean wasStartedByService = false;

    private boolean poked = false;

    private FrameLayout contentFrame;

    private MusicPlayer mp;

    private boolean isSeeking = false;

    private boolean seekingStopped = true;

    private GestureOverlay gestureOverlay;

    private AnimationLayer uiAnimation;

    private Vibrator vibe;

    private TextToSpeech tts;

    private TextView dateText;

    private TextView statusText;

    private TextView infoText;

    // Catch media button events so that controls from plugged in headsets and
    // BlueTooth headsets will work.
    //
    // Note that this only works if there are NO other apps that are trying to
    // consume the media button events and aborting the broadcasts; otherwise,
    // whether it works or not is a function of the order in which the
    // broadcasts are sent.
    private BroadcastReceiver mediaButtonReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent data) {
            this.abortBroadcast();
            KeyEvent event = data.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            int keyCode = event.getKeyCode();
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if ((keyCode == KeyEvent.KEYCODE_HEADSETHOOK)
                        || (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
                    mp.togglePlayPause();
                    updateDisplayText(null, null, false);
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                    mp.nextTrack();
                    updateDisplayText(null, null, false);
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                    mp.prevTrack();
                    updateDisplayText(null, null, false);
                }
            }
        }
    };

    // Don't send any accessibility events since this is a fully self voicing
    // app.
    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent evt) {
        return true;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = this;

        wasStartedByService = getIntent().getBooleanExtra(EXTRA_STARTED_BY_SERVICE, false);
        Log.e("wasStartedByService", wasStartedByService + "");

        // Start the service in case it is not already running
        startService(new Intent(this, ScreenOnHandlerService.class));

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(R.layout.main);

        mp = new MusicPlayer(this);

        final TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                // If the phone is not idle, immediately quit and let the
                // default lock screen handle it.
                if (state != TelephonyManager.CALL_STATE_IDLE) {
                    finish();
                    return;
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_BUTTON);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mediaButtonReceiver, filter);

        vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        uiAnimation = new AnimationLayer(this);

        gestureOverlay = new GestureOverlay(this, new GestureListener() {

            public void onGestureChange(int g) {
                isSeeking = false;
                vibe.vibrate(VIBE_PATTERN, -1);
                tts.playEarcon(TICK_EARCON, TextToSpeech.QUEUE_FLUSH, null);
                uiAnimation.setDirection(g);
                switch (g) {
                    case Gesture.UPLEFT:
                        updateDisplayText(
                                getString(R.string.previous_artist), mp.getPrevArtistName(), true);
                        break;
                    case Gesture.UP:
                        if (seekingStopped) {
                            updateDisplayText(
                                    getString(R.string.rewind), mp.getCurrentSongInfo(), false);
                            isSeeking = true;
                            new Thread(new Seeker(-1)).start();
                        }
                        break;
                    case Gesture.UPRIGHT:
                        updateDisplayText(
                                getString(R.string.next_artist), mp.getNextArtistName(), true);
                        break;
                    case Gesture.LEFT:
                        updateDisplayText(
                                getString(R.string.previous_track), mp.getPrevTrackName(), true);
                        break;
                    case Gesture.CENTER:
                        if (mp.isPlaying()) {
                            updateDisplayText(
                                    getString(R.string.pause), mp.getCurrentSongInfo(), true);
                        } else {
                            updateDisplayText(
                                    getString(R.string.play), mp.getCurrentSongInfo(), true);
                        }
                        break;
                    case Gesture.RIGHT:
                        updateDisplayText(
                                getString(R.string.next_track), mp.getNextTrackName(), true);
                        break;
                    case Gesture.DOWNLEFT:
                        updateDisplayText(
                                getString(R.string.previous_album), mp.getPrevAlbumName(), true);
                        break;
                    case Gesture.DOWN:
                        if (seekingStopped) {
                            updateDisplayText(getString(R.string.fast_forward),
                                    mp.getCurrentSongInfo(), false);
                            isSeeking = true;
                            new Thread(new Seeker(1)).start();
                        }
                        break;
                    case Gesture.DOWNRIGHT:
                        updateDisplayText(
                                getString(R.string.next_album), mp.getNextAlbumName(), true);
                        break;
                }
            }

            public void onGestureFinish(int g) {
                isSeeking = false;
                vibe.vibrate(VIBE_PATTERN, -1);
                tts.playEarcon(TICK_EARCON, TextToSpeech.QUEUE_FLUSH, null);
                uiAnimation.setDirection(-1);
                switch (g) {
                    case Gesture.UPLEFT:
                        mp.prevArtist();
                        break;
                    case Gesture.DOWNLEFT:
                        mp.prevAlbum();
                        break;
                    case Gesture.UPRIGHT:
                        mp.nextArtist();
                        break;
                    case Gesture.LEFT:
                        mp.prevTrack();
                        break;
                    case Gesture.CENTER:
                        mp.togglePlayPause();
                        break;
                    case Gesture.RIGHT:
                        mp.nextTrack();
                        break;
                    case Gesture.DOWNRIGHT:
                        mp.nextAlbum();
                        break;
                }
                updateDisplayText(null, null, false);
            }

            public void onGestureStart(int g) {
                poked = true;
                isSeeking = false;
                vibe.vibrate(VIBE_PATTERN, -1);
                tts.playEarcon(TICK_EARCON, TextToSpeech.QUEUE_FLUSH, null);
            }

        });

        contentFrame = (FrameLayout) findViewById(R.id.contentFrame);
        View textLayer = this.getLayoutInflater().inflate(R.layout.textlayer, null);
        dateText = (TextView) textLayer.findViewById(R.id.dateText);
        statusText = (TextView) textLayer.findViewById(R.id.statusText);
        infoText = (TextView) textLayer.findViewById(R.id.infoText);
        contentFrame.addView(uiAnimation);
        contentFrame.addView(textLayer);
        contentFrame.addView(gestureOverlay);

        tts = new TextToSpeech(this, new OnInitListener() {
            public void onInit(int arg0) {
                tts.addEarcon(TICK_EARCON, self.getPackageName(), R.raw.tick_snd);
                tts.playEarcon(TICK_EARCON, TextToSpeech.QUEUE_FLUSH, null);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        poked = false;
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_MONTH);
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM");
        String monthStr = monthFormat.format(cal.getTime());
        int year = cal.get(Calendar.YEAR);
        dateText.setText(monthStr + " " + Integer.toString(day) + ", " + year);
        new Thread(new PokeWatcher()).start();
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        mp.stop();
        menu.clear();
        int NONE = android.view.Menu.NONE;

        String togglePlaylistMode = getString(R.string.switch_to) + " "
                + getString(R.string.rock_lock_playlist);
        if (mp.getSongPickerType() == MusicPlayer.ROCKLOCK_PLAYLIST) {
            togglePlaylistMode = getString(R.string.switch_to) + " "
                    + getString(R.string.tagged_music_playlist);
        }
        menu.add(NONE, R.string.toggle_playlist, 0, togglePlaylistMode).setIcon(
                android.R.drawable.ic_menu_rotate);

        String preferences = getString(R.string.preferences);
        menu.add(NONE, R.string.preferences, 1, preferences).setIcon(
                android.R.drawable.ic_menu_preferences);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case R.string.toggle_playlist:
                int songPickerType = mp.cycleSongPicker();
                int songPickerTextResId = R.string.tagged_music_playlist;
                if (songPickerType == MusicPlayer.ROCKLOCK_PLAYLIST) {
                    songPickerTextResId = R.string.rock_lock_playlist;
                }
                updateDisplayText(
                        getString(R.string.app_name), getString(songPickerTextResId), true);
                break;
            case R.string.preferences:
                Intent i = new Intent(self, PrefsActivity.class);
                self.startActivity(i);
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismissSlideUnlockScreen();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        poked = true;
        mp.stop();
        tts.shutdown();
        unregisterReceiver(mediaButtonReceiver);
    }

    public void updateDisplayText(String status, String info, boolean speak) {
        if ((status == null) || (info == null)) {
            if (mp.isPlaying()) {
                statusText.setText(R.string.playing);
                infoText.setText(mp.getCurrentSongInfo());
            } else {
                statusText.setText(R.string.app_name);
                infoText.setText("The lock that rocks!");
            }
            return;
        }
        statusText.setText(status);
        infoText.setText(info);
        if (speak) {
            tts.speak(info, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private class Seeker implements Runnable {
        private int seekMode = 0;

        public Seeker(int seekDirection) {
            seekMode = seekDirection;
        }

        public void run() {
            while (isSeeking) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (seekMode == 1) {
                    mp.seekForward();
                } else if (seekMode == -1) {
                    mp.seekBackward();
                }
            }
            seekingStopped = true;
        }
    }

    private class PokeWatcher implements Runnable {
        public void run() {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (wasStartedByService && !poked && (mp != null) && !mp.isPlaying()) {
                finish();
            }
        }
    }

    private void dismissSlideUnlockScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        // finish() must be called in another thread or else the addFlags
        // call in the previous line will not take effect.
        new Thread(new Runnable() {
            public void run() {
                finish();
            }
        }).start();
    }
}
