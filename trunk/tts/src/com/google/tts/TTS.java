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


import java.io.File;

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
 * @author clchen@google.com (Charles L. Chen)
 */
public class TTS {
  /**
   * Called when the TTS has initialized
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

  public TTS(Context context, InitListener callback) {
    File espeakDataDir = new File("/sdcard/espeak-data/");
    boolean directoryExists = espeakDataDir.isDirectory();
    if (!directoryExists) {
      try {
        int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
        Context myContext = context.createPackageContext("com.google.tts", flags);
        Class<?> appClass = myContext.getClassLoader().loadClass("com.google.tts.ConfigurationManager");
        Intent intent = new Intent(myContext, appClass);
        context.startActivity(intent);
      } catch (NameNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

    initTts(context, callback);
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
        cb.onInit(version);
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
      new TTSVersionAlert(ctx).show();
    }
  }

  public void shutdown() {
    ctx.unbindService(serviceConnection);
  }

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

  public int getVersion() {
    return version;
  }

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

  public void showVersionAlert() {
    if (!started) {
      return;
    }
    new TTSVersionAlert(ctx).show();
  }

}
