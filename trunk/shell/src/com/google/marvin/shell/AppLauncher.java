package com.google.marvin.shell;

import com.google.tts.TTS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;

public class AppLauncher extends Activity {
  public AppLauncher self;
  public TTS tts;
  private PackageManager pm;

  private TTS.InitListener ttsInitListener = new TTS.InitListener() {
    public void onInit(int version) {
      //tts.speak("Applications loaded.", 0, null);
    }
  };



  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    self = this;
    pm = getPackageManager();
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    tts = new TTS(this, ttsInitListener, true);
    new ProcessTask().execute();
    logTime();
  }

  @Override
  protected void onDestroy() {
    tts.shutdown();
    super.onDestroy();
  }

  public void launchApp(AppEntry theApp) {
    try {
      int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
      Context myContext = createPackageContext(theApp.getPackageName(), flags);
      Class<?> appClass = myContext.getClassLoader().loadClass(theApp.getClassName());
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

  
  
  
  private class ProcessTask extends UserTask<Void, Void, ArrayList<AppEntry>> {
    @Override
    public ArrayList<AppEntry> doInBackground(Void... params) {
        // search for all launchable apps
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        
        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
        ArrayList<AppEntry> appList = new ArrayList<AppEntry>();
        for (ResolveInfo info : apps) {
          String title = info.loadLabel(pm).toString();
          if (title.length() == 0) {
            title = info.activityInfo.name.toString();
          }

          AppEntry entry = new AppEntry(title, info, null);
          appList.add(entry);
        }

        class appEntrySorter implements Comparator {
          public int compare(Object arg0, Object arg1) {
            String title0 = ((AppEntry) arg0).getTitle();
            String title1 = ((AppEntry) arg1).getTitle();
            return title0.compareTo(title1);
          }
        }
        Collections.sort(appList, new appEntrySorter());
        
        // now that app tree is built, pass along to adapter
        return appList;
    }

    @Override
    public void end(ArrayList<AppEntry> appList) {
      setContentView(new AppLauncherView(self, appList));
      tts.speak("Applications loaded.", 0, null);
      logTime();
    }
}
  
  long lastLogTime = 0;
  
  private void logTime() {
      long time = System.currentTimeMillis(); 
      long diff = time - lastLogTime;
      lastLogTime = time; 
      Log.i("debug time logger", diff + " ");
  }

}
