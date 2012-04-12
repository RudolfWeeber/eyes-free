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
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Controls the playback of sound effects.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class SfxController implements OnCompletionListener {

  private ArrayList<String> soundQueue;
  private MediaPlayer player;
  private HashMap<String, SoundResource> sounds;
  private boolean isPlaying;
  private Context ctx;

  private final ReentrantLock soundQueueLock = new ReentrantLock();

  public SfxController(Context contextObject) {
    ctx = contextObject;
    soundQueue = new ArrayList<String>();
    sounds = new HashMap<String, SoundResource>();
    player = null;
    isPlaying = false;
    sounds.put("[right]", new SoundResource(R.raw.right_snd));
    sounds.put("[wrong]", new SoundResource(R.raw.wrong_snd));
    sounds.put("Game over. Your score is:", new SoundResource(R.raw.game_over));
    sounds.put("Android says:", new SoundResource(R.raw.android_says));
    sounds.put("0", new SoundResource(R.raw.num0_robot_));
    sounds.put("1", new SoundResource(R.raw.num1_robot_));
    sounds.put("2", new SoundResource(R.raw.num2_robot_));
    sounds.put("3", new SoundResource(R.raw.num3_robot_));
    sounds.put("4", new SoundResource(R.raw.num4_robot_));
    sounds.put("5", new SoundResource(R.raw.num5_robot_));
    sounds.put("6", new SoundResource(R.raw.num6_robot_));
    sounds.put("7", new SoundResource(R.raw.num7_robot_));
    sounds.put("8", new SoundResource(R.raw.num8_robot_));
    sounds.put("9", new SoundResource(R.raw.num9_robot_));
    sounds.put("10", new SoundResource(R.raw.num10_robot_));
    sounds.put("11", new SoundResource(R.raw.num11_robot_));
    sounds.put("12", new SoundResource(R.raw.num12_robot_));
    sounds.put("13", new SoundResource(R.raw.num13_robot_));
    sounds.put("14", new SoundResource(R.raw.num14_robot_));
    sounds.put("15", new SoundResource(R.raw.num15_robot_));
    sounds.put("16", new SoundResource(R.raw.num16_robot_));
    sounds.put("17", new SoundResource(R.raw.num17_robot_));
    sounds.put("18", new SoundResource(R.raw.num18_robot_));
    sounds.put("19", new SoundResource(R.raw.num19_robot_));
    sounds.put("20", new SoundResource(R.raw.num20_robot_));
    sounds.put("30", new SoundResource(R.raw.num30_robot_));
    sounds.put("40", new SoundResource(R.raw.num40_robot_));
    sounds.put("50", new SoundResource(R.raw.num50_robot_));
    sounds.put("60", new SoundResource(R.raw.num60_robot_));
    sounds.put("70", new SoundResource(R.raw.num70_robot_));
    sounds.put("80", new SoundResource(R.raw.num80_robot_));
    sounds.put("90", new SoundResource(R.raw.num90_robot_));
    sounds.put("hundred", new SoundResource(R.raw.hundred_robot_));
    sounds.put("[slnc]", new SoundResource(R.raw.slnc_snd));
    sounds.put("[red]", new SoundResource(R.raw.red_snd));
    sounds.put("[green]", new SoundResource(R.raw.green_snd));
    sounds.put("[yellow]", new SoundResource(R.raw.yellow_snd));
    sounds.put("[blue]", new SoundResource(R.raw.blue_snd));
  }


  public void play(String soundName, int queueMode) {
    if (!sounds.containsKey(soundName)) {
      if (soundName.length() > 1) {
        // Flush the queue first if needed
        if (queueMode == 0) {
          play("", 0);
        }
        // Decompose this into a number if possible.
        // Remove this once full-fledged TTS is available.
        if (spokenAsNumber(soundName)) {
          return;
        }
        for (int i = 0; i < soundName.length(); i++) {
          String currentCharacter = soundName.substring(i, i + 1);
          if (currentCharacter.length() == 1) {
            play(currentCharacter, 1);
          }
        }
      }
      return;
    }

    if (isPlaying && (queueMode == 0)) {
      stop();
    }

    soundQueue.add(soundName);

    if (!isPlaying) {
      processSoundQueue();
    }
  }


  // Special algorithm to decompose numbers into speakable parts.
  // This will handle positive numbers up to 999.
  private boolean spokenAsNumber(String text) {
    try {
      int number = Integer.parseInt(text);
      // Handle cases that are between 100 and 999, inclusive
      if ((number > 99) && (number < 1000)) {
        int remainder = number % 100;
        number = number / 100;
        play(Integer.toString(number), 1);
        play("[slnc]", 1);
        play("[slnc]", 1);
        play("hundred", 1);
        play("[slnc]", 1);
        play("[slnc]", 1);
        if (remainder > 0) {
          play(Integer.toString(remainder), 1);
        }
        return true;
      }

      // Handle cases that are less than 100
      int digit = 0;
      if ((number > 20) && (number < 100)) {
        if ((number > 20) && (number < 30)) {
          play(Integer.toString(20), 1);
          play("[slnc]", 1);
          play("[slnc]", 1);
          digit = number - 20;
        } else if ((number > 30) && (number < 40)) {
          play(Integer.toString(30), 1);
          play("[slnc]", 1);
          play("[slnc]", 1);
          digit = number - 30;
        } else if ((number > 40) && (number < 50)) {
          play(Integer.toString(40), 1);
          play("[slnc]", 1);
          play("[slnc]", 1);
          digit = number - 40;
        } else if ((number > 50) && (number < 60)) {
          play(Integer.toString(50), 1);
          play("[slnc]", 1);
          play("[slnc]", 1);
          digit = number - 50;
        } else if ((number > 60) && (number < 70)) {
          play(Integer.toString(60), 1);
          play("[slnc]", 1);
          play("[slnc]", 1);
          digit = number - 60;
        } else if ((number > 70) && (number < 80)) {
          play(Integer.toString(70), 1);
          play("[slnc]", 1);
          play("[slnc]", 1);
          digit = number - 70;
        } else if ((number > 80) && (number < 90)) {
          play(Integer.toString(80), 1);
          play("[slnc]", 1);
          play("[slnc]", 1);
          digit = number - 80;
        } else if ((number > 90) && (number < 100)) {
          play(Integer.toString(90), 1);
          play("[slnc]", 1);
          play("[slnc]", 1);
          digit = number - 90;
        }
        if (digit > 0) {
          play(Integer.toString(digit), 1);
          return true;
        }
      }
      // Any other cases are either too large to handle
      // or have an utterance that is directly mapped.
      return false;
    } catch (NumberFormatException nfe) {
      return false;
    }
  }

  public void stop() {
    soundQueue.clear();
    isPlaying = false;
    if (player != null) {
      try {
        player.stop();
      } catch (IllegalStateException e) {
        // Do nothing, the player is already stopped.
      }
    }
  }

  public void loadSoundFile(String soundName, String fileName) {
    sounds.put(soundName, new SoundResource(fileName));
  }

  public void loadSoundResource(String soundName, int resId) {
    sounds.put(soundName, new SoundResource(resId));
  }

  public void onCompletion(MediaPlayer arg0) {
    if (soundQueue.size() > 0) {
      processSoundQueue();
    } else {
      isPlaying = false;
    }
  }

  private void processSoundQueue() {
    boolean speechQueueAvailable = soundQueueLock.tryLock();
    if (!speechQueueAvailable) {
      return;
    }
    String soundName = soundQueue.get(0);
    isPlaying = true;
    if (sounds.containsKey(soundName)) {
      SoundResource sr = sounds.get(soundName);
      cleanUpPlayer();

      if (sr.resId != -1) {
        player = MediaPlayer.create(ctx, sr.resId);
      } else {
        player = MediaPlayer.create(ctx, Uri.parse(sr.filename));
      }

      // Check for if Media Server is dead;
      // if it is, clear the queue and give
      // up for now - hopefully, it will recover itself.
      if (player == null) {
        soundQueue.clear();
        isPlaying = false;
        soundQueueLock.unlock();
        return;
      }
      player.setOnCompletionListener(this);
      try {
        player.start();
      } catch (IllegalStateException e) {
        soundQueue.clear();
        isPlaying = false;
        cleanUpPlayer();
        soundQueueLock.unlock();
        return;
      }
      isPlaying = true;
    }

    if (soundQueue.size() > 0) {
      soundQueue.remove(0);
    }
    soundQueueLock.unlock();
  }

  private void cleanUpPlayer() {
    if (player != null) {
      player.release();
      player = null;
    }
  }

}
