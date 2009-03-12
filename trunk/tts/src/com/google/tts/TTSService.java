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
package com.google.tts;

import com.google.tts.ITTS.Stub;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.parsers.FactoryConfigurationError;

/**
 * Synthesizes speech from text. This is implemented as a service so that other
 * applications can call the TTS without needing to bundle the TTS in the build.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class TTSService extends Service implements OnCompletionListener {
  private static final String ACTION = "android.intent.action.USE_TTS";
  private static final String CATEGORY = "android.intent.category.TTS";
  private static final String PKGNAME = "com.google.tts";
  private static final String ESPEAK_SCRATCH_DIRECTORY = "/sdcard/espeak-data/scratch/";

  private TTSEngine engine;

  private Boolean isSpeaking;
  private ArrayList<String> speechQueue;
  private HashMap<String, SoundResource> utterances;
  private HashMap<String, SoundResource> cache;
  private MediaPlayer player;
  private TTSService self;

  private SharedPreferences prefs;
  private int speechRate = 140;
  private String language = "en-us";

  private final ReentrantLock speechQueueLock = new ReentrantLock();
  private final ReentrantLock synthesizerLock = new ReentrantLock();
  private SpeechSynthesis speechSynthesis = new SpeechSynthesis(language, 0, speechRate);

  @Override
  public void onCreate() {
    super.onCreate();
    Log.i("TTS", "TTS starting");
    // android.os.Debug.waitForDebugger();
    self = this;
    isSpeaking = false;

    prefs = PreferenceManager.getDefaultSharedPreferences(this);

    utterances = new HashMap<String, SoundResource>();
    cache = new HashMap<String, SoundResource>();

    speechQueue = new ArrayList<String>();
    player = null;

    if (espeakIsUsable()) {
      setEngine(TTSEngine.PRERECORDED_WITH_ESPEAK);
    } else {
      setEngine(TTSEngine.PRERECORDED_ONLY);
    }

    setLanguage(prefs.getString("lang_pref", "en-us"));
    setSpeechRate(Integer.parseInt(prefs.getString("rate_pref", "140")));
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    // Don't hog the media player
    cleanUpPlayer();
  }

  private void setSpeechRate(int rate) {
    if (prefs.getBoolean("override_pref", false)) {
      // This is set to the default here so that the preview in the prefs
      // activity will show the change without a restart, even if apps are
      // not allowed to change the defaults.
      rate = Integer.parseInt(prefs.getString("rate_pref", "140"));
    }
    // Clear cache so that the TTS will regenerate the
    // sounds with the new rate
    cache = new HashMap<String, SoundResource>();
    speechRate = rate;
    speechSynthesis.setSpeechRate(rate);
  }

  private void setLanguage(String lang) {
    if (prefs.getBoolean("override_pref", false)) {
      // This is set to the default here so that the preview in the prefs
      // activity will show the change without a restart, even if apps are
      // not
      // allowed to change the defaults.
      lang = prefs.getString("lang_pref", "en-us");
    }
    language = lang;
    // Clear cache so that the TTS will regenerate the
    // sounds in the new language
    cache = new HashMap<String, SoundResource>();
    // The eSpeak documentation for Cantonese seems to be wrong.
    // It seems like using "zhy" will cause all Chinese characters to be
    // spoken as "symbol blah blah blah". The solution is to actually use
    // zh and variant 3. In addition, "zhy" is not a standard IETF language
    // tag; the standard IETF language tag is "zh-yue".
    if (language.equals("zh-yue")) {
      speechSynthesis.setLanguage("zh", 5);
    } else {
      speechSynthesis.setLanguage(lang, 0);
    }
  }

  private void setEngine(TTSEngine selectedEngine) {
    utterances = new HashMap<String, SoundResource>();
    boolean fallbackToPrerecordedOnly = false;
    if (selectedEngine == TTSEngine.ESPEAK_ONLY) {
      if (!espeakIsUsable()) {
        fallbackToPrerecordedOnly = true;
      }
      engine = selectedEngine;
    } else if (selectedEngine == TTSEngine.PRERECORDED_WITH_ESPEAK) {
      if (!espeakIsUsable()) {
        fallbackToPrerecordedOnly = true;
      }
      loadUtterancesFromPropertiesFile();
      engine = TTSEngine.PRERECORDED_WITH_ESPEAK;
    } else {
      fallbackToPrerecordedOnly = true;
    }
    if (fallbackToPrerecordedOnly) {
      loadUtterancesFromPropertiesFile();
      engine = TTSEngine.PRERECORDED_ONLY;
    }
    // Load earcons
    utterances.put("[tock]", new SoundResource(PKGNAME, R.raw.tock_snd));
    utterances.put("[slnc]", new SoundResource(PKGNAME, R.raw.slnc_snd));
  }

  private void loadUtterancesFromPropertiesFile() {
    Resources res = getResources();
    InputStream fis = res.openRawResource(R.raw.soundsamples);

    try {
      Properties soundsamples = new Properties();
      soundsamples.load(fis);
      Enumeration<Object> textKeys = soundsamples.keys();
      while (textKeys.hasMoreElements()) {
        String text = textKeys.nextElement().toString();
        String name = "com.google.tts:raw/" + soundsamples.getProperty(text);
        TypedValue value = new TypedValue();
        getResources().getValue(name, value, false);
        utterances.put(text, new SoundResource(PKGNAME, value.resourceId));
      }
    } catch (FactoryConfigurationError e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    } catch (SecurityException e) {
      e.printStackTrace();
    }
  }

  private boolean espeakIsUsable() {
    if (!new File("/sdcard/").canWrite()) {
      return false;
    }

    if (!ConfigurationManager.allFilesExist()) {
      // This should have been taken care of when the TTS is launched
      // by the check in the TTS.java wrapper.
      return false;
    }
    clearScratchFiles();
    return true;
  }

  private void clearScratchFiles() {
    File scratchDir = new File(ESPEAK_SCRATCH_DIRECTORY);
    boolean directoryExists = scratchDir.isDirectory();
    if (directoryExists) {
      File[] scratchFiles = scratchDir.listFiles();
      for (int i = 0; i < scratchFiles.length; i++) {
        scratchFiles[i].delete();
      }
    } else {
      scratchDir.mkdir();
    }
    cache = new HashMap<String, SoundResource>();
  }

  /**
   * Adds a sound resource to the TTS.
   * 
   * @param text The text that should be associated with the sound resource
   * @param packageName The name of the package which has the sound resource
   * @param resId The resource ID of the sound within its package
   */
  private void addSpeech(String text, String packageName, int resId) {
    utterances.put(text, new SoundResource(packageName, resId));
  }

  /**
   * Adds a sound resource to the TTS.
   * 
   * @param text The text that should be associated with the sound resource
   * @param filename The filename of the sound resource. This must be a complete
   *        path like: (/sdcard/mysounds/mysoundbite.mp3).
   */
  private void addSpeech(String text, String filename) {
    utterances.put(text, new SoundResource(filename));
  }

  /**
   * Caches a generated utterance
   * 
   * @param text The text that should be associated with the sound resource
   * @param filename The filename of the sound resource. This must be a complete
   *        path like: (/sdcard/mysounds/mysoundbite.mp3).
   */
  private void cacheSpeech(String text, String filename) {
    cache.put(text, new SoundResource(filename));
  }

  /**
   * Speaks the given text using the specified queueing mode and parameters.
   * 
   * @param text The text that should be spoken
   * @param queueMode 0 for no queue (interrupts all previous utterances), 1 for
   *        queued
   * @param params An ArrayList of parameters. This is not implemented for all
   *        engines.
   */
  private void speak(String text, int queueMode, ArrayList<String> params) {
    if (isSpeaking && (queueMode == 0)) {
      stop();
    }
    if (engine == TTSEngine.PRERECORDED_WITH_ESPEAK) {
      speakPrerecordedWithEspeak(text, queueMode, params);
    } else if (engine == TTSEngine.ESPEAK_ONLY) {
      speakEspeakOnly(text, queueMode, params);
    } else {
      speakPrerecordedOnly(text, queueMode, params);
    }
  }

  private void speakEspeakOnly(final String text, int queueMode, ArrayList<String> params) {
    class synthThread implements Runnable {
      public void run() {
        if (!utterances.containsKey(text) && !isInCache(text)) {
          long time = System.currentTimeMillis();
          String ts = Long.toString(time);
          String filename = ESPEAK_SCRATCH_DIRECTORY + ts + ".wav";

          // eSpeak does not behave correctly if called from multiple
          // threads.
          // Using a lock here will cause some text to be dropped if too many
          // requests happen at once, but that is better than crashing.
          try {
            boolean synthAvailable = synthesizerLock.tryLock();
            if (!synthAvailable) {
              return;
            }
            speechSynthesis.synthesizeToFile(text, filename);
          } finally {
            synthesizerLock.unlock();
          }

          cacheSpeech(text, filename);
        }

        speechQueue.add(text);
        if (!isSpeaking) {
          processSpeechQueue();
        }
      }
    }
    (new Thread(new synthThread())).run();
  }

  private boolean isInCache(String text) {
    SoundResource sr = cache.get(text);
    if (sr == null) {
      return false;
    }
    if (!new File(sr.filename).isFile()) {
      cache.remove(text);
      return false;
    }
    return true;
  }

  private void speakPrerecordedOnly(String text, int queueMode, ArrayList<String> params) {
    // Apply voices if possible
    if ((params != null) && (params.size() > 0)) {
      String textWithVoice = text;
      if (params.get(0).equals(TTSParams.VOICE_ROBOT.toString())) {
        textWithVoice = textWithVoice + "[robot]";
      } else if (params.get(0).equals(TTSParams.VOICE_FEMALE.toString())) {
        textWithVoice = textWithVoice + "[fem]";
      }
      if (utterances.containsKey(textWithVoice)) {
        text = textWithVoice;
      }
    }

    if (!utterances.containsKey(text)) {
      if (text.length() > 1) {
        // Flush the queue first if needed
        if (queueMode == 0) {
          speak("", 0, null);
        }
        // Decompose this into a number if possible.
        // Remove this once full-fledged TTS is available.
        if (spokenAsNumber(text, params)) {
          return;
        }
        for (int i = 0; i < text.length(); i++) {
          String currentCharacter = text.substring(i, i + 1);
          if (currentCharacter.length() == 1) {
            speak(currentCharacter, 1, params);
          }
        }
        return;
      }
    }

    speechQueue.add(text);
    if (!isSpeaking) {
      processSpeechQueue();
    }
  }

  private void speakPrerecordedWithEspeak(String text, int queueMode, ArrayList<String> params) {
    // Apply voices if possible
    if ((params != null) && (params.size() > 0)) {
      String textWithVoice = text;
      if (params.get(0).equals(TTSParams.VOICE_ROBOT.toString())) {
        textWithVoice = textWithVoice + "[robot]";
      } else if (params.get(0).equals(TTSParams.VOICE_FEMALE.toString())) {
        textWithVoice = textWithVoice + "[fem]";
      }
      if (utterances.containsKey(textWithVoice)) {
        text = textWithVoice;
      }
    }

    if (!utterances.containsKey(text)) {
      if (text.length() > 0) {
        // Flush the queue first if needed
        if (queueMode == 0) {
          speak("", 0, null);
        }
        // Decompose this into a number if possible.
        // Remove this once full-fledged TTS is available.
        if (spokenAsNumber(text, params)) {
          return;
        }
        // This is an unknown utterance, handle it with eSpeak.
        speakEspeakOnly(text, queueMode, params);
      }
      return;
    }

    speechQueue.add(text);
    if (!isSpeaking) {
      processSpeechQueue();
    }
  }

  // Special algorithm to decompose numbers into speakable parts.
  // This will handle positive numbers up to 999.
  private boolean spokenAsNumber(String text, ArrayList<String> params) {
    try {
      int number = Integer.parseInt(text);
      // Handle cases that are between 100 and 999, inclusive
      if ((number > 99) && (number < 1000)) {
        int remainder = number % 100;
        number = number / 100;
        speak(Integer.toString(number), 1, params);
        speak("[slnc]", 1, params);
        speak("hundred", 1, params);
        speak("[slnc]", 1, params);
        if (remainder > 0) {
          speak(Integer.toString(remainder), 1, params);
        }
        return true;
      }

      // Handle cases that are less than 100
      int digit = 0;
      if ((number > 20) && (number < 100)) {
        if ((number > 20) && (number < 30)) {
          speak(Integer.toString(20), 1, params);
          speak("[slnc]", 1, params);
          digit = number - 20;
        } else if ((number > 30) && (number < 40)) {
          speak(Integer.toString(30), 1, params);
          speak("[slnc]", 1, params);
          digit = number - 30;
        } else if ((number > 40) && (number < 50)) {
          speak(Integer.toString(40), 1, params);
          speak("[slnc]", 1, params);
          digit = number - 40;
        } else if ((number > 50) && (number < 60)) {
          speak(Integer.toString(50), 1, params);
          speak("[slnc]", 1, params);
          digit = number - 50;
        } else if ((number > 60) && (number < 70)) {
          speak(Integer.toString(60), 1, params);
          speak("[slnc]", 1, params);
          digit = number - 60;
        } else if ((number > 70) && (number < 80)) {
          speak(Integer.toString(70), 1, params);
          speak("[slnc]", 1, params);
          digit = number - 70;
        } else if ((number > 80) && (number < 90)) {
          speak(Integer.toString(80), 1, params);
          speak("[slnc]", 1, params);
          digit = number - 80;
        } else if ((number > 90) && (number < 100)) {
          speak(Integer.toString(90), 1, params);
          speak("[slnc]", 1, params);
          digit = number - 90;
        }
        if (digit > 0) {
          speak(Integer.toString(digit), 1, params);
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

  /**
   * Stops all speech output and removes any utterances still in the queue.
   */
  private void stop() {
    speechQueue.clear();
    isSpeaking = false;
    if (player != null) {
      try {
        player.stop();
      } catch (IllegalStateException e) {
        // Do nothing, the player is already stopped.
      }
    }
  }

  public void onCompletion(MediaPlayer arg0) {
    if (speechQueue.size() > 0) {
      processSpeechQueue();
    } else {
      isSpeaking = false;
    }
  }

  private void processSpeechQueue() {
    boolean speechQueueAvailable = speechQueueLock.tryLock();
    if (!speechQueueAvailable) {
      return;
    }
    try {
      String text = speechQueue.get(0);
      isSpeaking = true;
      // Look in the predefined utterances first
      SoundResource sr = utterances.get(text);
      // If it's not there, then it was generated and should be in the cache
      if (sr == null) {
        sr = cache.get(text);
      }
      if (sr != null) {
        cleanUpPlayer();
        if (sr.sourcePackageName == PKGNAME) {
          // Utterance is part of the TTS library
          player = MediaPlayer.create(this, sr.resId);
        } else if (sr.sourcePackageName != null) {
          // Utterance is part of the app calling the library
          Context ctx;
          try {
            ctx = this.createPackageContext(sr.sourcePackageName, 0);
          } catch (NameNotFoundException e) {
            e.printStackTrace();
            speechQueue.remove(0); // Remove it from the queue and move on
            speechQueueLock.unlock();
            return;
          }
          player = MediaPlayer.create(ctx, sr.resId);
        } else {
          // Utterance is coming from a file
          player = MediaPlayer.create(this, Uri.parse(sr.filename));
        }

        // Check if Media Server is dead; if it is, clear the queue and
        // give up for now - hopefully, it will recover itself.
        if (player == null) {
          speechQueue.clear();
          isSpeaking = false;
          speechQueueLock.unlock();
          return;
        }
        player.setOnCompletionListener(this);
        try {
          player.start();
        } catch (IllegalStateException e) {
          speechQueue.clear();
          isSpeaking = false;
          cleanUpPlayer();
          speechQueueLock.unlock();
          return;
        }
        isSpeaking = true;
      }

      if (speechQueue.size() > 0) {
        speechQueue.remove(0);
      }
    } finally {
      speechQueueLock.unlock();
    }
  }

  private void cleanUpPlayer() {
    if (player != null) {
      player.release();
      player = null;
    }
  }

  /**
   * Synthesizes the given text using the specified queuing mode and parameters.
   * 
   * @param text The String of text that should be synthesized
   * @param params An ArrayList of parameters. The first element of this array
   *        controls the type of voice to use.
   * @param filename The string that gives the full output filename; it should
   *        be something like "/sdcard/myappsounds/mysound.wav".
   * @return A boolean that indicates if the synthesis succeeded
   */
  private boolean synthesizeToFile(String text, ArrayList<String> params, String filename) {
    stop();
    Log.i("TTS", "Synthesizing " + filename);

    try {
      boolean synthAvailable = synthesizerLock.tryLock();
      if (!synthAvailable) {
        return false;
      }
      // Don't allow a filename that is too long
      if (filename.length() > 250) {
        return false;
      }
      speechSynthesis.synthesizeToFile(text, filename);
    } finally {
      synthesizerLock.unlock();
    }

    Log.i("TTS", "Completed synthesis for " + filename);
    return true;
  }

  @Override
  public IBinder onBind(Intent intent) {

    if (ACTION.equals(intent.getAction())) {
      for (String category : intent.getCategories()) {
        if (category.equals(CATEGORY)) {
          return mBinder;
        }
      }
    }
    return null;
  }

  private final ITTS.Stub mBinder = new Stub() {
    /**
     * Speaks the given text using the specified queueing mode and parameters.
     * 
     * @param selectedEngine The TTS engine that should be used
     */
    public void setEngine(String selectedEngine) {
      TTSEngine theEngine;
      if (selectedEngine.equals(TTSEngine.ESPEAK_ONLY.toString())) {
        theEngine = TTSEngine.ESPEAK_ONLY;
      } else if (selectedEngine.equals(TTSEngine.PRERECORDED_ONLY.toString())) {
        theEngine = TTSEngine.PRERECORDED_ONLY;
      } else {
        theEngine = TTSEngine.PRERECORDED_WITH_ESPEAK;
      }
      self.setEngine(theEngine);
    }

    /**
     * Speaks the given text using the specified queueing mode and parameters.
     * 
     * @param text The text that should be spoken
     * @param queueMode 0 for no queue (interrupts all previous utterances), 1
     *        for queued
     * @param params An ArrayList of parameters. The first element of this array
     *        controls the type of voice to use.
     */
    public void speak(String text, int queueMode, String[] params) {
      ArrayList<String> speakingParams = new ArrayList<String>();
      if (params != null) {
        speakingParams = new ArrayList<String>(Arrays.asList(params));
      }
      self.speak(text, queueMode, speakingParams);
    }

    /**
     * Stops all speech output and removes any utterances still in the queue.
     */
    public void stop() {
      self.stop();
    }

    /**
     * Returns whether or not the TTS is speaking.
     * 
     * @return Boolean to indicate whether or not the TTS is speaking
     */
    public boolean isSpeaking() {
      return self.isSpeaking;
    }

    /**
     * Adds a sound resource to the TTS.
     * 
     * @param text The text that should be associated with the sound resource
     * @param packageName The name of the package which has the sound resource
     * @param resId The resource ID of the sound within its package
     */
    public void addSpeech(String text, String packageName, int resId) {
      self.addSpeech(text, packageName, resId);
    }

    /**
     * Adds a sound resource to the TTS.
     * 
     * @param text The text that should be associated with the sound resource
     * @param filename The filename of the sound resource. This must be a
     *        complete path like: (/sdcard/mysounds/mysoundbite.mp3).
     */
    public void addSpeechFile(String text, String filename) {
      self.addSpeech(text, filename);
    }

    /**
     * Sets the speech rate for the TTS. Note that this will only have an effect
     * on synthesized speech; it will not affect pre-recorded speech.
     * 
     * @param speechRate The speech rate that should be used
     */
    public void setSpeechRate(int speechRate) {
      self.setSpeechRate(speechRate);
    }

    /**
     * Sets the speech rate for the TTS. Note that this will only have an effect
     * on synthesized speech; it will not affect pre-recorded speech.
     * 
     * @param language The language to be used. The languages are specified by
     *        their IETF language tags as defined by BCP 47. This is the same
     *        standard used for the lang attribute in HTML. See:
     *        http://en.wikipedia.org/wiki/IETF_language_tag
     */
    public void setLanguage(String language) {
      self.setLanguage(language);
    }

    /**
     * Returns the version number of the TTS This version number is the
     * versionCode in the AndroidManifest.xml
     * 
     * @return The version number of the TTS
     */
    public int getVersion() {
      PackageInfo pInfo = new PackageInfo();
      try {
        PackageManager pm = self.getPackageManager();
        pInfo = pm.getPackageInfo(self.getPackageName(), 0);
      } catch (NameNotFoundException e) {
        // Ignore this exception - the packagename is itself, can't fail here
        e.printStackTrace();
      }
      return pInfo.versionCode;
    }

    /**
     * Speaks the given text using the specified queueing mode and parameters.
     * 
     * @param text The String of text that should be synthesized
     * @param params An ArrayList of parameters. The first element of this array
     *        controls the type of voice to use.
     * @param filename The string that gives the full output filename; it should
     *        be something like "/sdcard/myappsounds/mysound.wav".
     * @return A boolean that indicates if the synthesis succeeded
     */
    public boolean synthesizeToFile(String text, String[] params, String filename) {
      ArrayList<String> speakingParams = new ArrayList<String>();
      if (params != null) {
        speakingParams = new ArrayList<String>(Arrays.asList(params));
      }
      return self.synthesizeToFile(text, speakingParams, filename);
    }
  };

}
