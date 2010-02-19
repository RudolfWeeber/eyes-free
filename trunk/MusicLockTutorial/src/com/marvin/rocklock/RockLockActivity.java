
package com.marvin.rocklock;

import com.google.marvin.widget.GestureOverlay;
import com.google.marvin.widget.GestureOverlay.Gesture;
import com.google.marvin.widget.GestureOverlay.GestureListener;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class RockLockActivity extends Activity {
    public static final String EXTRA_STARTED_BY_SERVICE = "STARTED_BY_SERVICE";
    
    private static final long[] VIBE_PATTERN = {
            0, 10, 70, 80
    };

    private RockLockActivity self;

    private boolean poked = false;

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
                    updateUi();
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                    mp.nextTrack();
                    updateUi();
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                    mp.prevTrack();
                    updateUi();
                }
            }
        }
    };

    private KeyguardManager keyguardManager;

    private KeyguardLock keyguard;

    private FrameLayout contentFrame;

    private Button unlockButton;

    private MusicPlayer mp;

    private boolean isSeeking = false;

    private boolean seekingStopped = true;

    private GestureOverlay gestureOverlay;

    private AnimationLayer uiAnimation;

    private TextView dateText;

    private TextView statusText;

    private TextView infoText;

    private Vibrator vibe;

    private TextToSpeech tts;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Start the service in case it is not already running
        startService(new Intent(this, BootHandlerService.class));

        self = this;
        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        keyguard = keyguardManager.newKeyguardLock("RockLock");

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        // Need to use FLAG_TURN_SCREEN_ON to make sure that the status bar
        // stays locked
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

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
                finish();
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_BUTTON);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mediaButtonReceiver, filter);

        vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        uiAnimation = new AnimationLayer(this);

        gestureOverlay = new GestureOverlay(self, new GestureListener() {

            @Override
            public void onGestureChange(int g) {
                isSeeking = false;
                vibe.vibrate(VIBE_PATTERN, -1);
                uiAnimation.setDirection(g);

                switch (g) {
                    case Gesture.UPLEFT:
                        statusText.setText(R.string.previous_artist);
                        infoText.setText(mp.getPrevArtistName());
                        tts.speak(mp.getPrevArtistName(), 0, null);
                        break;
                    case Gesture.UP:
                        statusText.setText(R.string.previous_album);
                        infoText.setText(mp.getPrevAlbumName());
                        tts.speak(mp.getPrevAlbumName(), 0, null);
                        break;
                    case Gesture.UPRIGHT:
                        statusText.setText(R.string.next_artist);
                        infoText.setText(mp.getNextArtistName());
                        tts.speak(mp.getNextArtistName(), 0, null);
                        break;
                    case Gesture.LEFT:
                        statusText.setText(R.string.next_track);
                        infoText.setText(mp.getPrevTrackName());
                        tts.speak(mp.getPrevTrackName(), 0, null);
                        break;
                    case Gesture.CENTER:
                        if (mp.isPlaying()) {
                            statusText.setText(R.string.pause);
                            infoText.setText(mp.getCurrentSongInfo());
                        } else {
                            statusText.setText(R.string.play);
                            infoText.setText(mp.getCurrentSongInfo());
                        }
                        break;
                    case Gesture.RIGHT:
                        statusText.setText(R.string.next_track);
                        infoText.setText(mp.getNextTrackName());
                        tts.speak(mp.getNextTrackName(), 0, null);
                        break;
                    case Gesture.DOWNLEFT:
                        if (seekingStopped) {
                            statusText.setText(R.string.rewind);
                            infoText.setText(mp.getCurrentSongInfo());
                            isSeeking = true;
                            new Thread(new Seeker(-1)).start();
                        }
                        break;
                    case Gesture.DOWN:
                        statusText.setText(R.string.next_album);
                        infoText.setText(mp.getNextAlbumName());
                        tts.speak(mp.getNextAlbumName(), 0, null);
                        break;
                    case Gesture.DOWNRIGHT:
                        if (seekingStopped) {
                            statusText.setText(R.string.fast_forward);
                            infoText.setText(mp.getCurrentSongInfo());
                            isSeeking = true;
                            new Thread(new Seeker(1)).start();
                        }
                        break;
                }
            }

            @Override
            public void onGestureFinish(int g) {
                isSeeking = false;
                vibe.vibrate(VIBE_PATTERN, -1);
                uiAnimation.setDirection(-1);
                tts.stop();
                switch (g) {
                    case Gesture.UPLEFT:
                        mp.prevArtist();
                        break;
                    case Gesture.UP:
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
                    case Gesture.DOWN:
                        mp.nextAlbum();
                        break;
                }
                updateUi();
            }

            @Override
            public void onGestureStart(int g) {
                poked = true;
                isSeeking = false;
                vibe.vibrate(VIBE_PATTERN, -1);
            }

        });

        contentFrame = (FrameLayout) findViewById(R.id.contentFrame);
        contentFrame.addView(uiAnimation);
        View textLayer = this.getLayoutInflater().inflate(R.layout.textlayer, null);
        dateText = (TextView) textLayer.findViewById(R.id.dateText);
        statusText = (TextView) textLayer.findViewById(R.id.statusText);
        infoText = (TextView) textLayer.findViewById(R.id.infoText);
        contentFrame.addView(textLayer);
        contentFrame.addView(gestureOverlay);

        tts = new TextToSpeech(this, null);
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
        keyguard.disableKeyguard();
        new Thread(new PokeWatcher()).start();
    }

    @Override
    public void onPause() {
        super.onPause();
        keyguard.reenableKeyguard();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            finish();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            mp.stop();
            int songPickerType = mp.cycleSongPicker();
            int songPickerTextId = R.string.tagged_music_playlist;
            if (songPickerType == MusicPlayer.ROCKLOCK_PLAYLIST) {
                songPickerTextId = R.string.rock_lock_playlist;
            } 
            statusText.setText(R.string.app_name);
            infoText.setText(songPickerTextId);
            tts.speak(getString(songPickerTextId), 0, null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mp.stop();
        tts.shutdown();
        unregisterReceiver(mediaButtonReceiver);
        keyguardManager.exitKeyguardSecurely(null);
    }

    public void updateUi() {
        if (mp.isPlaying()) {
            statusText.setText(R.string.playing);
            infoText.setText(mp.getCurrentSongInfo());
        } else {
            statusText.setText(R.string.app_name);
            infoText.setText("The lock that rocks!");
        }
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
                    // TODO Auto-generated catch block
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
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (!poked && (mp != null) && !mp.isPlaying()) {
                keyguard.reenableKeyguard();
                finish();
            }
        }
    }
}
