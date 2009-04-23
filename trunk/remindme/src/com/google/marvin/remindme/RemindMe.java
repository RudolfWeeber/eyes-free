package com.google.marvin.remindme;

import com.google.tts.TTS;

import java.util.Calendar;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

/**
 * An eyes-free, talking reminder alarm
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class RemindMe extends Activity {
  public TTS tts;

  public NumberEntryView numberEntryView;
  public RecordReminderView recordReminderView;
  public ConfirmationView confirmationView;
  public boolean alreadyQuit;

  private long triggerTime;

  private TTS.InitListener ttsInitListener = new TTS.InitListener() {
    public void onInit(int version) {
      showNumberEntryView();
    }
  };

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    tts = new TTS(this, ttsInitListener, true);
    alreadyQuit = false;
  }


  public void showNumberEntryView() {
    dismissViews();
    numberEntryView = new NumberEntryView(this);
    setContentView(numberEntryView);
    tts.speak("Remind you at?", 0, null);
  }

  private boolean validTime(String timeStr) {
    if (timeStr.length() != 4) {
      return false;
    }
    if ((timeStr.charAt(0) != '0') && (timeStr.charAt(0) != '1') && (timeStr.charAt(0) != '2')) {
      return false;
    }
    if ((timeStr.charAt(2) != '0') && (timeStr.charAt(2) != '1') && (timeStr.charAt(2) != '2')
        && (timeStr.charAt(2) != '3') && (timeStr.charAt(2) != '4') && (timeStr.charAt(2) != '5')) {
      return false;
    }
    return true;
  }

  public void setTime(String timeStr) {
    if (!validTime(timeStr)) {
      numberEntryView.reset();
      return;
    }
    Calendar cal = Calendar.getInstance();
    int hour = Integer.parseInt(timeStr.substring(0,2));
    int minute = Integer.parseInt(timeStr.substring(2));
    cal.setTimeInMillis(System.currentTimeMillis());
    cal.set(Calendar.HOUR_OF_DAY, hour);
    cal.set(Calendar.MINUTE, minute);
    cal.set(Calendar.SECOND, 0);
    triggerTime = cal.getTimeInMillis();
    if (triggerTime < System.currentTimeMillis()){
      triggerTime = triggerTime + 86400000; //Add 24 hours
    }
    // Set the alarm here using AlarmManager
    showRecordingView();
  }

  public void showRecordingView() {
    dismissViews();

    recordReminderView = new RecordReminderView(this);
    setContentView(recordReminderView);
    tts.speak("Touch the screen to begin recording; lift your finger up when you are done.", 0, null);
  }



  public void showConfirmationView() {
    dismissViews();

    confirmationView = new ConfirmationView(this);
    setContentView(confirmationView);
    new MediaPlayer();
	MediaPlayer mplayer = MediaPlayer.create(this, Uri.parse("/sdcard/remindme/note00.amr"));
    mplayer.setOnCompletionListener(new OnCompletionListener(){
      public void onCompletion(MediaPlayer arg0) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(triggerTime);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);
        if (alreadyQuit){
          return;
        }
        tts.speak("will be set for " + hour + " " + min + " Press call to confirm, back to cancel.", 0, null);
      }      
    });
    mplayer.start();    
  }

  public void confirmAlarm(){
    alreadyQuit = true;
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    Editor editor = prefs.edit();
    editor.commit();
    AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
    Intent intent = new Intent(this, ReminderSpeakerActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    PendingIntent speakerIntent = PendingIntent.getActivity(this, 0, intent, flags);
    am.set(AlarmManager.RTC_WAKEUP, triggerTime, speakerIntent);
    tts.stop();
    finish();
  }

  private void dismissViews() {
    if (numberEntryView != null) {
      numberEntryView.shutdown();
    }
    numberEntryView = null;
  }

  @Override
protected void onDestroy(){
    dismissViews();
    tts.shutdown();
    super.onDestroy();    
  }

}
