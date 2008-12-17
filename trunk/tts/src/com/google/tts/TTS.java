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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * Synthesizes speech from text. This abstracts away the complexities of using
 * the TTS service such as setting up the IBinder connection and handling
 * RemoteExceptions, etc.
 * 
 * The TTS should always be safe the use; if the user does not have the
 * necessary TTS apk installed, the behavior is that all calls to the TTS act as
 * no-ops.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class TTS {
  /**
   * Called when the TTS has initialized
   * 
   * The InitListener must implement the onInit function. onInit is passed the
   * version number of the TTS library that the user has installed; since this
   * is called when the TTS has started, it is a good time to make sure that the
   * user's TTS library is up to date.
   */
  public interface InitListener {
    public void onInit(int version);
  }

  private ServiceConnection serviceConnection; // Connection needed for the TTS
  private ITTS itts;
  private Context ctx;
  private InitListener cb = null;
  private int version = -1;
  private boolean started = false;
  private boolean showInstaller = false;
  private TTSVersionAlert versionAlert = null;

  /**
   * The constructor for the TTS.
   * 
   * @param context The context
   * @param callback The InitListener that should be called when the TTS has
   *        initialized successfully.
   * @param displayInstallMessage Boolean indicating whether or not an
   *        installation prompt should be displayed to users who do not have the
   *        TTS library. If this is true, a generic alert asking the user to
   *        install the TTS will be used. If you wish to specify the exact
   *        message of that prompt, please use TTS(Context context, InitListener
   *        callback, TTSVersionAlert alert) as the constructor instead.
   */
  public TTS(Context context, InitListener callback, boolean displayInstallMessage) {
    showInstaller = displayInstallMessage;
    ctx = context;
    if (dataFilesCheck()){
      initTts(context, callback);
    }
  }

  /**
   * The constructor for the TTS.
   * 
   * @param context The context
   * @param callback The InitListener that should be called when the TTS has
   *        initialized successfully.
   * @param alert The TTSVersionAlert to be displayed
   */
  public TTS(Context context, InitListener callback, TTSVersionAlert alert) {
    showInstaller = true;
    versionAlert = alert;
    ctx = context;
    if (dataFilesCheck()){
      initTts(context, callback);
    }
  }

  private boolean dataFilesCheck() {
    if (!ConfigurationManager.allFilesExist()) {
      try {
        int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
        Context myContext = ctx.createPackageContext("com.google.tts", flags);
        Class<?> appClass =
            myContext.getClassLoader().loadClass("com.google.tts.ConfigurationManager");
        Intent intent = new Intent(myContext, appClass);
        ctx.startActivity(intent);
        return false;
      } catch (NameNotFoundException e) {
        // Just let it fail through; this exception means that the
        // TTS apk has not been installed and this case will be handled
        // in the initTts function
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return true;
  }


  private void initTts(Context context, InitListener callback) {
    started = false;
    ctx = context;
    cb = callback;

    // Initialize the TTS, run the callback after the binding is successful
    serviceConnection = new ServiceConnection() {
      public void onServiceConnected(ComponentName name, IBinder service) {
        itts = ITTS.Stub.asInterface(service);
        try {
          version = itts.getVersion();
        } catch (RemoteException e) {
          initTts(ctx, cb);
          return;
        }
        started = true;
        // The callback can become null if the Android OS decides to restart the
        // TTS process as well as whatever is using it. In such cases, do
        // nothing - the error handling from the speaking calls will kick in
        // and force a proper restart of the TTS.
        if (cb != null) {
          cb.onInit(version);
        }
      }

      public void onServiceDisconnected(ComponentName name) {
        itts = null;
        cb = null;
        started = false;
      }
    };

    Intent intent = new Intent("android.intent.action.USE_TTS");
    intent.addCategory("android.intent.category.TTS");
    // Binding will fail only if the TTS doesn't exist;
    // the TTSVersionAlert will give users a chance to install
    // the needed TTS.
    if (!ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
      if (showInstaller) {
        if (versionAlert != null) {
          versionAlert.show();
        } else {
          new TTSVersionAlert(ctx, null, null, null).show();
        }
      }
    }
  }



  /**
   * Shuts down the TTS. It is good practice to call this in the onDestroy
   * method of the Activity that is using the TTS so that the TTS is stopped
   * cleanly.
   */
  public void shutdown() {
    ctx.unbindService(serviceConnection);
  }

  /**
   * Adds a mapping between a string of text and a sound resource in a package.
   * 
   * @see #TTS.speak(String text, int queueMode, String[] params)
   * 
   * @param text Example: <b><code>"south_south_east"</code></b><br/>
   * 
   * @param packagename Pass the packagename of the application that contains
   *        the resource. If the resource is in your own application (this is
   *        the most common case), then put the packagename of your application
   *        here.<br/>Example: <b>"com.google.marvin.compass"</b><br/> The
   *        packagename can be found in the AndroidManifest.xml of your
   *        application.
   *        <p>
   *        <code>&lt;manifest xmlns:android=&quot;...&quot;
   *      package=&quot;<b>com.google.marvin.compass</b>&quot;&gt;</code>
   *        </p>
   * 
   * @param resourceId Example: <b><code>R.raw.south_south_east</code></b>
   */
  public void addSpeech(String text, String packagename, int resourceId) {
    if (!started) {
      return;
    }
    try {
      itts.addSpeech(text, packagename, resourceId);
    } catch (RemoteException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    } catch (NullPointerException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    } catch (IllegalStateException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    }
  }

  /**
   * Adds a mapping between a string of text and a sound file. Using this, it is
   * possible to add custom pronounciations for text.
   * 
   * @param text The string of text
   * @param filename The full path to the sound file (for example:
   *        "/sdcard/mysounds/hello.wav")
   */
  public void addSpeech(String text, String filename) {
    if (!started) {
      return;
    }
    try {
      itts.addSpeechFile(text, filename);
    } catch (RemoteException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    } catch (NullPointerException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    } catch (IllegalStateException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    }
  }

  /**
   * Speaks the string using the specified queuing strategy and speech
   * parameters. Note that the speech parameters are not universally supported
   * by all engines and will be treated as a hint. The TTS library will try to
   * fulfill these parameters as much as possible, but there is no guarantee
   * that the voice used will have the properties specified.
   * 
   * @param text The string of text to be spoken.
   * @param queueMode The queuing strategy to use. Use 0 for no queuing, and 1
   *        for queuing.
   * @param params The array of speech parameters to be used. Currently, only
   *        params[0] is defined - it is for setting the type of voice if the
   *        engine allows it. Possible values are "VOICE_MALE", "VOICE_FEMALE",
   *        and "VOICE_ROBOT". Note that right now only the pre-recorded voice
   *        has this support - this setting has no effect on eSpeak.
   */
  public void speak(String text, int queueMode, String[] params) {
    if (!started) {
      return;
    }
    try {
      itts.speak(text, queueMode, params);
    } catch (RemoteException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    } catch (NullPointerException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    } catch (IllegalStateException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    }
  }

  /**
   * Returns whether or not the TTS is busy speaking.
   * 
   * @return Whether or not the TTS is busy speaking.
   */
  public boolean isSpeaking() {
    if (!started) {
      return false;
    }
    try {
      return itts.isSpeaking();
    } catch (RemoteException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    } catch (NullPointerException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    } catch (IllegalStateException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    }
    return false;
  }

  /**
   * Stops speech from the TTS.
   */
  public void stop() {
    if (!started) {
      return;
    }
    try {
      itts.stop();
    } catch (RemoteException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    } catch (NullPointerException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    } catch (IllegalStateException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    }
  }

  /**
   * Returns the version number of the TTS library that the user has installed.
   * 
   * @return The version number of the TTS library that the user has installed.
   */
  public int getVersion() {
    return version;
  }

  /**
   * Sets the TTS engine to be used.
   * 
   * @param selectedEngine The TTS engine that should be used.
   */
  public void setEngine(TTSEngine selectedEngine) {
    if (!started) {
      return;
    }
    try {
      itts.setEngine(selectedEngine.toString());
    } catch (RemoteException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    }
  }

  /**
   * Sets the speech rate for the TTS engine.
   * 
   * Note that the speech rate is not universally supported by all engines and
   * will be treated as a hint. The TTS library will try to use the specified
   * speech rate, but there is no guarantee.
   * 
   * Currently, this will change the speech rate for the espeak engine, but it
   * has no effect on any pre-recorded speech.
   * 
   * @param speechRate The speech rate for the TTS engine.
   */
  public void setSpeechRate(int speechRate) {
    if (!started) {
      return;
    }
    try {
      itts.setSpeechRate(speechRate);
    } catch (RemoteException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    }
  }

  /**
   * Sets the language for the TTS engine.
   * 
   * Note that the language is not universally supported by all engines and
   * will be treated as a hint. The TTS library will try to use the specified
   * language, but there is no guarantee.
   * 
   * Currently, this will change the language for the espeak engine, but it
   * has no effect on any pre-recorded speech.
   * 
   * @param language The language to be used. The languages are specified by 
   *        their IETF language tags as defined by BCP 47. This is the same
   *        standard used for the lang attribute in HTML. 
   *        See: http://en.wikipedia.org/wiki/IETF_language_tag
   */
  public void setLanguage(String language) {
    if (!started) {
      return;
    }
    try {
      itts.setLanguage(language);
    } catch (RemoteException e) {
      // TTS died; restart it.
      started = false;
      initTts(ctx, cb);
    }
  }

  /**
   * Displays an alert that prompts users to install the TTS that is available
   * on the Market. This is useful if the application expects a newer version of
   * the TTS than what the user has.
   */
  public void showVersionAlert() {
    if (!started) {
      return;
    }
    if (versionAlert != null) {
      versionAlert.show();
    } else {
      new TTSVersionAlert(ctx, null, null, null).show();
    }
  }


}
