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

import com.google.tts.TTS.InitListener;

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
public interface AbstractITTSWrapper {
  
  public void init(Context context, int minVer, InitListener callback, boolean displayInstallMessage);
  public void init(Context context, int minVer, InitListener callback, TTSVersionAlert alert);
  public int getVersion();
  public void addSpeech(String text, String packagename, int resourceId);
  public void addSpeech(String text, String filename);
  public void speak(String text, int queueMode, String[] params);
  public boolean isSpeaking();
  public void stop();
  public void shutdown();
  public void setEngine(TTSEngine selectedEngine);
  public void setSpeechRate(int speechRate);
  public void setLanguage(String language);
  public void showVersionAlert();

}
