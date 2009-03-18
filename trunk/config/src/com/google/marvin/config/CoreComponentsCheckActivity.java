package com.google.marvin.config;

import com.google.tts.ConfigurationManager;
import com.google.tts.TTS;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;

public class CoreComponentsCheckActivity extends Activity {
  private static final int ttsCheckCode = 42;
  private static final int shellCheckCode = 43;

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
      if (TTS.isInstalled(this)) {
        if (checkShell(this)) {
          startConfig();
        }
      } else {
        displayMissingComponentError("Text-To-Speech Library");
      }
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
    if (!TTS.isInstalled(activity)) {
      Uri marketUri = Uri.parse("market://search?q=pname:com.google.tts");
      Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
      activity.startActivityForResult(marketIntent, ttsCheckCode);
      return false;
    }
    if (!ConfigurationManager.allFilesExist()) {
      int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
      Context myContext;
      try {
        myContext = createPackageContext("com.google.tts", flags);
        Class<?> appClass =
            myContext.getClassLoader().loadClass("com.google.tts.ConfigurationManager");
        Intent intent = new Intent(myContext, appClass);
        startActivityForResult(intent, ttsCheckCode);
      } catch (NameNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return false;
    }
    return true;
  }

  /** Checks to make sure that the shell is there */
  private boolean checkShell(Activity activity) {
    if (!shellInstalled()) {
      Uri marketUri = Uri.parse("market://search?q=pname:com.google.marvin.shell");
      Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
      activity.startActivityForResult(marketIntent, shellCheckCode);
      return false;
    }
    return true;
  }

  private boolean shellInstalled() {
    try {
      Context myContext = createPackageContext("com.google.marvin.shell", 0);
    } catch (NameNotFoundException e) {
      return false;
    }
    return true;
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
    Intent mainConfigIntent = new Intent(this, InstallerActivity.class);
    startActivityForResult(mainConfigIntent, 0);
  }

}
