package com.google.marvin.shell;

import com.google.tts.TTS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.os.Bundle;

public class AppLauncher extends Activity {
  public AppLauncher self;
  public TTS tts;

  private TTS.InitListener ttsInitListener = new TTS.InitListener() {
    public void onInit(int version) {
      setContentView(new AppLauncherView(self));
      tts.speak("Applications loaded.", 0, null);
    }
  };



  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    self = this;
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    tts = new TTS(this, ttsInitListener, true);
  }


  public void launchApp(AppEntry theApp) {
    try {
      int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
      Context myContext = createPackageContext(theApp.getPackageName(), flags);
      Class<?> appClass =
          myContext.getClassLoader().loadClass(theApp.getClassName());
      Intent intent = new Intent(myContext, appClass);
      startActivity(intent);
    } catch (NameNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }


}
