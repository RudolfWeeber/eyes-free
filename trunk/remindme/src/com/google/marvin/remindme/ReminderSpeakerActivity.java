package com.google.marvin.remindme;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.google.tts.TTS;

public class ReminderSpeakerActivity extends Activity {

  private ReminderSpeakerActivity self;



  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    self = this;
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    String reminderUriStr = prefs.getString("REMINDER", null);
    if (reminderUriStr != null) {
      class AlarmPlayer implements Runnable {
        public void run() {
          MediaPlayer mPlayer =
              new MediaPlayer().create(self, Uri.parse("/sdcard/remindme/note00.amr"));
          mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
              try {
                Thread.sleep(3000);
              } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
              new Thread(new AlarmPlayer()).start();
            }
          });
          mPlayer.start();
        }
      }
      displayDismissDialog();
      new Thread(new AlarmPlayer()).start();
    } else {
      finish();
    }
  }

  private void displayDismissDialog() {
    Builder dismissDialog = new Builder(this);

    String titleText = "Alarm";
    dismissDialog.setTitle(titleText);
    dismissDialog.setMessage("Press the DISMISS button to stop the alarm.");
    
    dismissDialog.setNeutralButton("Dismiss", new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        finish();
      }
    });
    dismissDialog.show();
  }



}
