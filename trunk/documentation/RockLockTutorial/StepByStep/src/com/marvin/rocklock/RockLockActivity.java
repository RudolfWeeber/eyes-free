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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
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

    private boolean poked = false;

    private FrameLayout contentFrame;

    private Button unlockButton;

    private MusicPlayer mp;

    private boolean isSeeking = false;

    private boolean seekingStopped = true;

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
                    updateDisplayText(null, null);
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                    mp.nextTrack();
                    updateDisplayText(null, null);
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                    mp.prevTrack();
                    updateDisplayText(null, null);
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

        unlockButton = (Button) findViewById(R.id.unlockButton);
        unlockButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                dismissSlideUnlockScreen();
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_BUTTON);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mediaButtonReceiver, filter);

        contentFrame = (FrameLayout) findViewById(R.id.contentFrame);
        View buttonLayer = this.getLayoutInflater().inflate(R.layout.buttonlayer, null);
        dateText = (TextView) buttonLayer.findViewById(R.id.dateText);
        statusText = (TextView) buttonLayer.findViewById(R.id.statusText);
        infoText = (TextView) buttonLayer.findViewById(R.id.infoText);

        Button prevArtistButton = (Button) buttonLayer.findViewById(R.id.prevArtistButton);
        prevArtistButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                poked = true;
                mp.prevArtist();
                updateDisplayText(null, null);
            }
        });

        Button prevAlbumButton = (Button) buttonLayer.findViewById(R.id.prevAlbumButton);
        prevAlbumButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                poked = true;
                mp.prevAlbum();
                updateDisplayText(null, null);
            }
        });

        Button nextArtistButton = (Button) buttonLayer.findViewById(R.id.nextArtistButton);
        nextArtistButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                poked = true;
                mp.nextArtist();
                updateDisplayText(null, null);
            }
        });

        Button prevTrackButton = (Button) buttonLayer.findViewById(R.id.prevTrackButton);
        prevTrackButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                poked = true;
                mp.prevTrack();
                updateDisplayText(null, null);
            }
        });

        Button nextTrackButton = (Button) buttonLayer.findViewById(R.id.nextTrackButton);
        nextTrackButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                poked = true;
                mp.nextTrack();
                updateDisplayText(null, null);
            }
        });

        Button rewButton = (Button) buttonLayer.findViewById(R.id.rewButton);
        rewButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                poked = true;
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (seekingStopped) {
                        updateDisplayText(getString(R.string.rewind), mp.getCurrentSongInfo());
                        isSeeking = true;
                        new Thread(new Seeker(-1)).start();
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    isSeeking = false;
                    updateDisplayText(null, null);
                }
                return true;
            }
        });

        Button nextAlbumButton = (Button) buttonLayer.findViewById(R.id.nextAlbumButton);
        nextAlbumButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                poked = true;
                mp.nextAlbum();
                updateDisplayText(null, null);
            }
        });

        Button ffButton = (Button) buttonLayer.findViewById(R.id.ffButton);
        ffButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                poked = true;
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (seekingStopped) {
                        updateDisplayText(getString(R.string.fast_forward), mp.getCurrentSongInfo());
                        isSeeking = true;
                        new Thread(new Seeker(1)).start();
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    isSeeking = false;
                    updateDisplayText(null, null);
                }
                return true;
            }
        });

        Button playPauseButton = (Button) buttonLayer.findViewById(R.id.playPauseButton);
        playPauseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                poked = true;
                mp.togglePlayPause();
                updateDisplayText(null, null);
            }
        });

        contentFrame.addView(buttonLayer);
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismissSlideUnlockScreen();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            mp.stop();
            int songPickerType = mp.cycleSongPicker();
            int songPickerTextResId = R.string.tagged_music_playlist;
            if (songPickerType == MusicPlayer.ROCKLOCK_PLAYLIST) {
                songPickerTextResId = R.string.rock_lock_playlist;
            }
            updateDisplayText(getString(R.string.app_name), getString(songPickerTextResId));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        poked = true;
        mp.stop();
        unregisterReceiver(mediaButtonReceiver);
    }

    public void updateDisplayText(String status, String info) {
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
    }

    private class Seeker implements Runnable {
        private int seekMode = 0;

        public Seeker(int seekDirection) {
            seekMode = seekDirection;
        }

        @Override
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
        @Override
        public void run() {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (!poked && (mp != null) && !mp.isPlaying()) {
                finish();
            }
        }
    }
    
    private void dismissSlideUnlockScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        // finish() must be called in another thread or else the addFlags
        // call in the previous line will not take effect.
        new Thread(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }).start();
    }
}
