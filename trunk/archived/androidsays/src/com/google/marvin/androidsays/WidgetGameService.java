
package com.google.marvin.androidsays;

import java.util.ArrayList;

import com.google.marvin.androidsays.GameView.SequencePlayer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

public class WidgetGameService extends Service {

    private WidgetGameService self;

    private boolean needReset = true;

    private Vibrator vibe;

    private long[] pattern = {
            0, 1, 40, 41
    };

    private int currentIndex;

    private ArrayList<Integer> sequence;

    private int score;

    private SfxController sfx;

    // These are timings used to control pauses between actions
    // All times are specified in ms
    private int initialWaitTime = 550;

    private int waitTimeBetweenTones = 310;

    private int flashDuration = 250;

    // Used for locking the screen
    private boolean screenActive;

    private int inputCount;
    
    

    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        this.setForeground(true);

        if (needReset) {
            self = this;
            sfx = new SfxController(this);
            // Hard code this for now
            sfx.loadSoundResource("[0]", R.raw.green_snd);
            sfx.loadSoundResource("[1]", R.raw.red_snd);
            sfx.loadSoundResource("[2]", R.raw.yellow_snd);
            sfx.loadSoundResource("[3]", R.raw.blue_snd);
            vibe = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
            sequence = new ArrayList<Integer>();
            currentIndex = 0;
            screenActive = false;
            score = 0;
            inputCount = 0;
            needReset = false;
            sfx.play("Android says:", 0);
            playSequence();
        } else {
            if (screenActive) {
                inputCount++;
                if (inputCount >= sequence.size()) {
                  screenActive = false;
                }
                String input = intent.getStringExtra("input");
                input = input.replaceAll("com.google.marvin.androidsays.0", "");
                sfx.play("[" + input + "]", 0);
                vibe.vibrate(pattern, -1);
                evalInput(Integer.parseInt(input));
            }
        }
    }

    private void evalInput(final int input) {
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // This should not get interrupted
                    e.printStackTrace();
                }
                Log.e("evalInput", "input is: " + input);
                if (currentIndex >= sequence.size()) {
                    return;
                }
                if (input == sequence.get(currentIndex)) {
                    currentIndex++;
                    if (currentIndex >= sequence.size()) {
                        currentIndex = 0;
                        sfx.play("[right]", 1);
                        score++;
                        playSequence();
                    }
                } else {
                    sfx.play("[wrong]", 1);
                    gameOver();
                }
            }
        };

        t.start();
    }

    private void playSequence() {
        (new Thread(new SequencePlayer())).start();
    }

    // Generates sequence either by adding one random number to the end
    // (classic)
    // or by generating a brand new sequence of a set length (challenge).
    private void generateSequence() {
        currentIndex = 0;
        int random = ((int)(Math.random() * 100)) % 4;
        sequence.add(random);
    }

    /**
     * Plays back the sequence This needs to be done in a different thread
     * because it uses sleep to keep the visual flashing in sync with the
     * sounds.
     */
    public class SequencePlayer implements Runnable {
        public void run() {
            screenActive = false;
            generateSequence();
            try {
                Thread.sleep(initialWaitTime);
            } catch (InterruptedException e) {
                // Nothing needs to be done if the sleep is interrupted.
                e.printStackTrace();
            }
            for (int i = 0; i < sequence.size(); i++) {
                // TODO(clchen): - find a more graceful way of stopping the
                // sound
                // if (parent.halt) {
                // inputCount = 0;
                // screenActive = true;
                // return;
                // }
                // TODO: Use prefs here!
                int delay = -1; // parent.speedPrefDelay;
                // Negative speed_pref_delay means scaling
                if (delay < 0) {
                    delay = 300 - (sequence.size() * 10);
                }
                // Scaled delay must be 0 or positive
                if (delay < 0) {
                    delay = 0;
                }
                try {
                    Thread.sleep(waitTimeBetweenTones + delay);
                } catch (InterruptedException e) {
                    // Nothing needs to be done if the sleep is interrupted.
                    e.printStackTrace();
                }

                Log.e("SequencePlayer", sequence.get(i) + "");

                Intent flashIntent;
                if (sequence.get(i) == 0) {
                    flashIntent = new Intent("com.google.marvin.androidsays.flash.00");
                    sfx.play("[0]", 1);
                } else if (sequence.get(i) == 1) {
                    flashIntent = new Intent("com.google.marvin.androidsays.flash.01");
                    sfx.play("[1]", 1);
                } else if (sequence.get(i) == 2) {
                    flashIntent = new Intent("com.google.marvin.androidsays.flash.02");
                    sfx.play("[2]", 1);
                } else {
                    flashIntent = new Intent("com.google.marvin.androidsays.flash.03");
                    sfx.play("[3]", 1);
                }
                self.sendBroadcast(flashIntent);

                try {
                    Thread.sleep(flashDuration);
                } catch (InterruptedException e) {
                    // Nothing needs to be done if the sleep is interrupted.
                    e.printStackTrace();
                }
                Intent unflashIntent = new Intent("com.google.marvin.androidsays.unflash");
                self.sendBroadcast(unflashIntent);

            }
            inputCount = 0;
            screenActive = true;
        }
    }

    private void gameOver() {
        String scoreStr = Integer.toString(score);
        sfx.play("Game over. Your score is:", 1);
        sfx.play(scoreStr, 1);
        Intent showScoreIntent = new Intent("com.google.marvin.androidsays.showScore");
        showScoreIntent.putExtra("score", scoreStr);
        self.sendBroadcast(showScoreIntent);
        needReset = true;
        screenActive = true;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // Not binding to the service
        return null;
    }
}
