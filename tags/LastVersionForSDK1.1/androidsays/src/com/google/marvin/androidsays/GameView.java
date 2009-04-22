/*
 * Copyright (C) 2008 Google Inc.
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
package com.google.marvin.androidsays;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Handles the game play for mem.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class GameView extends TextView {
  private Bitmap bgImg;
  private Bitmap greenImg;
  private Bitmap redImg;
  private Bitmap yellowImg;
  private Bitmap blueImg;
  private Bitmap touchedGreenImg;
  private Bitmap touchedRedImg;
  private Bitmap touchedYellowImg;
  private Bitmap touchedBlueImg;

  private int score;

  Rect fullScreen;
  Rect upperleft;
  Rect upperright;
  Rect lowerleft;
  Rect lowerright;

  private Vibrator vibe;
  public AndroidSays parent;
  private ArrayList<Integer> sequence;
  private int currentIndex;
  private Rect flash;
  public boolean waitingToStart;
  // These are timings used to control pauses between actions
  // All times are specified in ms
  private int initialWaitTime = 500;
  private int waitTimeBetweenTones = 310;
  private int flashDuration = 270;
  private Rect clipRect;

  // Used for locking the screen
  private boolean screenActive;
  private int inputCount;

  public GameView(Context context) {
    super(context);
    parent = (AndroidSays) context;
    // android.os.Debug.waitForDebugger();
    vibe = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    sequence = new ArrayList<Integer>();
    currentIndex = 0;
    screenActive = false;
    flash = null;
    waitingToStart = true;
    inputCount = 0;

    Theme customTheme = new Theme();
    // Apply a custom theme is there is one and it can be loaded successfully
    if ((parent.themeFilename != null) && customTheme.loadTheme(parent.themeFilename)) {
      bgImg = BitmapFactory.decodeFile(customTheme.backgroundImg);
      greenImg = BitmapFactory.decodeFile(customTheme.greenImg);
      redImg = BitmapFactory.decodeFile(customTheme.redImg);
      yellowImg = BitmapFactory.decodeFile(customTheme.yellowImg);
      blueImg = BitmapFactory.decodeFile(customTheme.blueImg);
      touchedGreenImg = BitmapFactory.decodeFile(customTheme.touchedGreenImg);
      touchedRedImg = BitmapFactory.decodeFile(customTheme.touchedRedImg);
      touchedYellowImg = BitmapFactory.decodeFile(customTheme.touchedYellowImg);
      touchedBlueImg = BitmapFactory.decodeFile(customTheme.touchedBlueImg);

      parent.sfx.loadSoundFile("[red]", customTheme.redSnd);
      parent.sfx.loadSoundFile("[green]", customTheme.greenSnd);
      parent.sfx.loadSoundFile("[yellow]", customTheme.yellowSnd);
      parent.sfx.loadSoundFile("[blue]", customTheme.blueSnd);
    } else {
      // Default theme
      Resources res = parent.getResources();
      bgImg = BitmapFactory.decodeResource(res, R.drawable.bg);
      greenImg = BitmapFactory.decodeResource(res, R.drawable.green);
      redImg = BitmapFactory.decodeResource(res, R.drawable.red);
      yellowImg = BitmapFactory.decodeResource(res, R.drawable.yellow);
      blueImg = BitmapFactory.decodeResource(res, R.drawable.blue);
      touchedGreenImg = BitmapFactory.decodeResource(res, R.drawable.flash);
      touchedRedImg = BitmapFactory.decodeResource(res, R.drawable.flash);
      touchedYellowImg = BitmapFactory.decodeResource(res, R.drawable.flash);
      touchedBlueImg = BitmapFactory.decodeResource(res, R.drawable.flash);

      parent.sfx.loadSoundResource("[red]", R.raw.red_snd);
      parent.sfx.loadSoundResource("[green]", R.raw.green_snd);
      parent.sfx.loadSoundResource("[yellow]", R.raw.yellow_snd);
      parent.sfx.loadSoundResource("[blue]", R.raw.blue_snd);
    }
    
    // Fall back to the default theme if anything went wrong
    if ( (bgImg == null) ||
         (greenImg == null) ||
         (redImg == null) ||
         (yellowImg == null) ||
         (blueImg == null) ||
         (touchedGreenImg == null) ||
         (touchedRedImg == null) ||
         (touchedYellowImg == null) ||
         (touchedBlueImg== null) ){
      // Default theme
      Resources res = parent.getResources();
      bgImg = BitmapFactory.decodeResource(res, R.drawable.bg);
      greenImg = BitmapFactory.decodeResource(res, R.drawable.green);
      redImg = BitmapFactory.decodeResource(res, R.drawable.red);
      yellowImg = BitmapFactory.decodeResource(res, R.drawable.yellow);
      blueImg = BitmapFactory.decodeResource(res, R.drawable.blue);
      touchedGreenImg = BitmapFactory.decodeResource(res, R.drawable.flash);
      touchedRedImg = BitmapFactory.decodeResource(res, R.drawable.flash);
      touchedYellowImg = BitmapFactory.decodeResource(res, R.drawable.flash);
      touchedBlueImg = BitmapFactory.decodeResource(res, R.drawable.flash);

      parent.sfx.loadSoundResource("[red]", R.raw.red_snd);
      parent.sfx.loadSoundResource("[green]", R.raw.green_snd);
      parent.sfx.loadSoundResource("[yellow]", R.raw.yellow_snd);
      parent.sfx.loadSoundResource("[blue]", R.raw.blue_snd);
    }

    gameStart();
  }

  @Override
  public boolean onTouchEvent(final MotionEvent event) {
    if (!screenActive) {
      return false;
    }
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      if (waitingToStart) {
        gameStart();
        return true;
      }
      inputCount++;
      if (inputCount >= sequence.size()) {
        screenActive = false;
      }
      // Screen can always be touched in noise maker mode
      if (parent.gameMode == 0) {
        screenActive = true;
      }
      float halfWidth = getWidth() / 2;
      float halfHeight = getHeight() / 2;
      float x = event.getX();
      float y = event.getY();

      long[] pattern = {0, 1, 40, 41};
      vibe.vibrate(pattern, -1);
      int input;
      if ((x < halfWidth) && (y < halfHeight)) {
        // Upper left
        flash = upperleft;
        postInvalidate(upperleft);
        playSoundEffect("[green]");
        input = 0;
      } else if ((x >= halfWidth) && (y < halfHeight)) {
        // Upper right
        flash = upperright;
        postInvalidate(upperright);
        playSoundEffect("[red]");
        input = 1;
      } else if ((x < halfWidth) && (y >= halfHeight)) {
        // Lower left
        flash = lowerleft;
        postInvalidate(lowerleft);
        playSoundEffect("[yellow]");
        input = 2;
      } else {
        // Lower right
        flash = lowerright;
        postInvalidate(lowerright);
        playSoundEffect("[blue]");
        input = 3;
      }

      // Don't bother evaluating input if the game is in noise maker mode
      if (parent.gameMode != 0) {
        evalInput(input);
      }

      return true;
    }
    return false;
  }

  private void postInvalidate(Rect r) {
    postInvalidate(r.left, r.top, r.right, r.bottom);
  }

  private void playSoundEffect(String sfx) {
    parent.sfx.play(sfx, 0);
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
        if (currentIndex >= sequence.size()) {
          return;
        }
        if (input == sequence.get(currentIndex)) {
          currentIndex++;
          if (currentIndex >= sequence.size()) {
            currentIndex = 0;
            playSoundEffect("[right]");
            score++;
            playSequence();
          }
        } else {
          screenActive = true;
          playSoundEffect("[wrong]");
          gameOver();
        }
      }
    };

    t.start();
  }

  public void gameStart() {
    waitingToStart = false;
    currentIndex = 0;
    score = 0;
    sequence = new ArrayList<Integer>();
    parent.sfx.play("Android says:", 0);

    // There are no sequences in noise maker mode
    if (parent.gameMode == 0) {
      screenActive = true;
      return;
    }
    // Load up an initial sequence for classic mode
    if (parent.gameMode == 1) {
      for (int i = 0; i < parent.sequenceLength - 1; i++) {
        int random = ((int) (Math.random() * 100)) % 4;
        sequence.add(random);
      }
    }
    playSequence();
  }

  private boolean gameover = false;

  private void gameOver() {
    parent.sfx.play("Game over. Your score is:", 1);
    parent.sfx.play(Integer.toString(score), 1);
    gameover = true;
    waitingToStart = true;
    postInvalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (gameover) {
      gameover = false;
      parent.recordScore(score);
    }

    if (fullScreen == null) {
      fullScreen = new Rect(0, 0, getWidth() - 1, getHeight() - 1);
      upperleft = new Rect(0, 0, getWidth() / 2, getHeight() / 2);
      upperright = new Rect(getWidth() / 2, 0, getWidth() - 1, getHeight() / 2);
      lowerleft = new Rect(0, getHeight() / 2, getWidth() / 2, getHeight() - 1);
      lowerright = new Rect(getWidth() / 2, getHeight() / 2, getWidth() - 1, getHeight() - 1);
      clipRect = new Rect();
    }

    boolean useClipRect = canvas.getClipBounds(clipRect);

    canvas.drawBitmap(bgImg, null, fullScreen, null);

    if (!useClipRect || Rect.intersects(upperleft, clipRect)) {
      if (flash == upperleft) {
        canvas.drawBitmap(touchedGreenImg, null, upperleft, null);
        unflashLater(upperleft);
      } else {
        canvas.drawBitmap(greenImg, null, upperleft, null);
      }
    }
    if (!useClipRect || Rect.intersects(upperright, clipRect)) {
      if (flash == upperright) {
        canvas.drawBitmap(touchedRedImg, null, upperright, null);
        unflashLater(upperright);
      } else {
        canvas.drawBitmap(redImg, null, upperright, null);
      }
    }
    if (!useClipRect || Rect.intersects(lowerleft, clipRect)) {
      if (flash == lowerleft) {
        canvas.drawBitmap(touchedYellowImg, null, lowerleft, null);
        unflashLater(lowerleft);
      } else {
        canvas.drawBitmap(yellowImg, null, lowerleft, null);
      }
    }
    if (!useClipRect || Rect.intersects(lowerright, clipRect)) {
      if (flash == lowerright) {
        canvas.drawBitmap(touchedBlueImg, null, lowerright, null);
        unflashLater(lowerright);
      } else {
        canvas.drawBitmap(blueImg, null, lowerright, null);
      }
    }

    String mytext = Integer.toString(score);
    int x = getWidth() / 2;
    int y = getHeight() / 2;
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.BLACK);
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTextSize(36);
    paint.setTypeface(Typeface.DEFAULT_BOLD);
    y -= paint.ascent() / 2;
    canvas.drawText(mytext, x, y, paint);
  }

  private void unflashLater(Rect r) {
    postInvalidateDelayed(flashDuration, r.left, r.top, r.right, r.bottom);
    flash = null;
  }

  private void playSequence() {
    (new Thread(new SequencePlayer())).start();
  }

  // Generates sequence either by adding one random number to the end (classic)
  // or by generating a brand new sequence of a set length (challenge).
  private void generateSequence() {
    currentIndex = 0;
    if (parent.gameMode == 1) {
      int random = ((int) (Math.random() * 100)) % 4;
      sequence.add(random);
    } else {
      sequence = new ArrayList<Integer>();
      for (int i = 0; i < parent.sequenceLength; i++) {
        int random = ((int) (Math.random() * 100)) % 4;
        sequence.add(random);
      }
    }
  }

  /**
   * Plays back the sequence This needs to be done in a different thread because
   * it uses sleep to keep the visual flashing in sync with the sounds.
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
        // TODO(clchen): - find a more graceful way of stopping the sound
        if (parent.halt) {
          inputCount = 0;
          screenActive = true;
          return;
        }
        int delay = parent.speedPrefDelay;
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
        if (sequence.get(i) == 0) {
          flash = upperleft;
          postInvalidate();
          playSoundEffect("[green]");
        } else if (sequence.get(i) == 1) {
          flash = upperright;
          postInvalidate();
          playSoundEffect("[red]");
        } else if (sequence.get(i) == 2) {
          flash = lowerleft;
          postInvalidate();
          playSoundEffect("[yellow]");
        } else {
          flash = lowerright;
          postInvalidate();
          playSoundEffect("[blue]");
        }
      }
      inputCount = 0;
      screenActive = true;
    }
  }


}
