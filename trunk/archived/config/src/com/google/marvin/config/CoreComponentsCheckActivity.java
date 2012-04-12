package com.google.marvin.config;

import com.google.tts.ConfigurationManager;
import com.google.tts.TextToSpeechBeta;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;

/**
 * Does a quick sanity check to make sure that critical core components (the TTS
 * and the Eyes-Free Shell) are both installed before starting the main config
 * screen.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class CoreComponentsCheckActivity extends Activity {
  private static final int ttsCheckCode = 42;
  private static final int shellCheckCode = 43;
  private static final String ttsPackageName = "com.google.tts";
  private static final String shellPackageName = "com.google.marvin.shell";

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Do not proceed unless both the TTS and the Shell have been installed.
    if (checkTtsRequirements(this)) {
      if (checkShell(this)) {
        startConfig();
      }
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == ttsCheckCode) {
      //if (TTS.isInstalled(this)) {
        if (checkShell(this)) {
          startConfig();
        }
     // } else {
     //   displayMissingComponentError("Text-To-Speech Library");
     // }
    } else if (requestCode == shellCheckCode) {
      if (shellInstalled()) {
        startConfig();
      } else {
        displayMissingComponentError("Eyes-Free Shell");
      }
    } else {
      finish();
    }
  }

  /** Checks to make sure that all the requirements for the TTS are there */
  private boolean checkTtsRequirements(Activity activity) {
   // if (!TTS.isInstalled(activity)) {
   //   activity.startActivityForResult(Utils.getMarketIntent(ttsPackageName), ttsCheckCode);
  //    return false;
  //  }
    if (!ConfigurationManager.allFilesExist()) {
      Intent intent =
          Utils.getAppStartIntent(this, ttsPackageName, "com.google.tts.ConfigurationManager");
      startActivityForResult(intent, ttsCheckCode);
      return false;
    }
    return true;
  }

  /** Checks to make sure that the shell is there */
  private boolean checkShell(Activity activity) {
    if (!shellInstalled()) {
      activity.startActivityForResult(Utils.getMarketIntent(shellPackageName), shellCheckCode);
      return false;
    }
    return true;
  }

  private boolean shellInstalled() {
    return Utils.applicationInstalled(this, shellPackageName);
  }

  private void displayMissingComponentError(String component) {
    AlertDialog errorDialog = new Builder(this).create();
    errorDialog.setTitle("Unable to continue");
    errorDialog.setMessage(component + " is a required component. Please install it first.");
    errorDialog.setButton("Quit", new OnClickListener() {
      public void onClick(DialogInterface arg0, int arg1) {
        finish();
      }
    });
    errorDialog.show();
  }

  private void startConfig() {
    Intent mainConfigIntent = new Intent(this, MainConfigActivity.class);
    startActivityForResult(mainConfigIntent, 0);
  }

}
