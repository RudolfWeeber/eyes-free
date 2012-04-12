package com.google.marvin.remindme;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;

public class ReminderSpeakerActivity extends Activity {

  private ReminderSpeakerActivity self;
  private boolean stopNow;



  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    self = this;
    stopNow = false;
    //if (reminderUriStr != null) {
      class AlarmPlayer implements Runnable {
        public void run() {
          new MediaPlayer();
		MediaPlayer mPlayer =
              MediaPlayer.create(self, Uri.parse("/sdcard/remindme/note00.amr"));
          mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
              try {
                Thread.sleep(3000);
              } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
              if (!stopNow) {
                new Thread(new AlarmPlayer()).start();
              }
            }
          });
          mPlayer.start();
        }
      }
      displayDismissDialog();
      new Thread(new AlarmPlayer()).start();
  //  } else {
//      finish();
//    }
  }

  private void displayDismissDialog() {
    Builder dismissDialog = new Builder(this);

    String titleText = "Alarm";
    dismissDialog.setTitle(titleText);
    dismissDialog.setMessage("Press the DISMISS button to stop the alarm.");

    dismissDialog.setNeutralButton("Dismiss", new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        stopNow = true;
        finish();
      }
    });
    dismissDialog.show();
  }



}
