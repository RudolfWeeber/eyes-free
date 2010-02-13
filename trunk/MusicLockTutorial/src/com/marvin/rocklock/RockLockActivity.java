
package com.marvin.rocklock;

import com.google.marvin.widget.GestureOverlay;
import com.google.marvin.widget.GestureOverlay.Gesture;
import com.google.marvin.widget.GestureOverlay.GestureListener;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;
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

public class RockLockActivity extends Activity {
    private static final long[] VIBE_PATTERN = {
            0, 10, 70, 80
    };

    private RockLockActivity self;

    private KeyguardManager keyguardManager;

    private KeyguardLock keyguard;

    private FrameLayout contentFrame;

    private Button unlockButton;

    private MusicPlayer mp;

    private GestureOverlay gestureOverlay;

    private AnimationLayer uiAnimation;

    private TextView statusText;

    private TextView infoText;

    private Vibrator vibe;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);

        self = this;
        mp = new MusicPlayer(this);

        keyguardManager = (KeyguardManager) this.getSystemService(Context.KEYGUARD_SERVICE);
        keyguard = keyguardManager.newKeyguardLock("RockLock");

        final TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                // If the phone is ringing, immediately quit and let the default
                // lock screen handle it.
                if (state == 1) {
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

        vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        uiAnimation = new AnimationLayer(this);

        gestureOverlay = new GestureOverlay(self, new GestureListener() {

            @Override
            public void onGestureChange(int g) {
                vibe.vibrate(VIBE_PATTERN, -1);
                uiAnimation.setDirection(g);
            }

            @Override
            public void onGestureFinish(int g) {
                vibe.vibrate(VIBE_PATTERN, -1);
                uiAnimation.setDirection(-1);
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

            }

            @Override
            public void onGestureStart(int g) {
                vibe.vibrate(VIBE_PATTERN, -1);
            }

        });

        contentFrame = (FrameLayout) findViewById(R.id.contentFrame);
        contentFrame.addView(uiAnimation);
        View textLayer = this.getLayoutInflater().inflate(R.layout.textlayer, null);
        statusText = (TextView) textLayer.findViewById(R.id.statusText);
        infoText = (TextView) textLayer.findViewById(R.id.infoText);
        contentFrame.addView(textLayer);
        contentFrame.addView(gestureOverlay);
    }

    @Override
    public void onResume() {
        super.onResume();
        keyguard.disableKeyguard();
        Log.e("onResume", "keyguard disabled");
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

//    @Override
//    public void onPause() {
//        super.onPause();
//        int patternsetting = 1;
//        try {
//            patternsetting = android.provider.Settings.System.getInt(getContentResolver(),
//                    android.provider.Settings.System.LOCK_PATTERN_ENABLED, 0);
//        } finally {
//            if (patternsetting == 1) {
//                keyguard.reenableKeyguard();
//                Log.e("onPause", "keyguard reenabled");
//            }
//        }
//    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mp.stop();
        keyguardManager.exitKeyguardSecurely(null);
    }

}
