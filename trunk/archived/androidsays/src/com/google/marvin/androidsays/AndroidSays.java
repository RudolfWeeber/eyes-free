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
package com.google.marvin.androidsays;


import com.scoreninja.adapter.ScoreNinjaAdapter;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

/**
 * mem A memory game for the Android Platform
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class AndroidSays extends Activity {
  protected static final int PREFS_UPDATED = 42;
  private static final String THEME_FILE_EXTENSION = ".mem";
  private static final String THEMES_BASE_PATH = "/sdcard/mem/themes/";

  private GameView gView;

  public SfxController sfx;
  public int speedPrefDelay;
  public int gameMode;
  public int sequenceLength;
  public boolean halt;

  private String[] filenames;
  public String themeFilename;
  
  
  private ScoreNinjaAdapter scoreNinjaAdapter;
  private String appId = "androidsays";
  private String privateKey = "450CA446A7885E7F7B75F19E8F5A39E8";



  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    halt = false;
    themeFilename = null;
    scoreNinjaAdapter = new ScoreNinjaAdapter(this, appId, privateKey);
    loadPrefs();
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    startApp();
  }

  private void startApp() {
    if (gView != null) {
      gView.setVisibility(View.GONE);
      gView = null;
    }
    sfx = new SfxController(this);
    gView = new GameView(this);
    setContentView(gView);
  }



  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(0, R.string.settings, 0, R.string.settings).setIcon(
        android.R.drawable.ic_menu_preferences);
    menu.add(0, R.string.themes, 0, R.string.themes).setIcon(android.R.drawable.ic_menu_edit);
    menu.add(0, R.string.about, 0, R.string.about).setIcon(android.R.drawable.ic_menu_info_details);
    menu.add(0, R.string.hiscores, 0, R.string.hiscores).setIcon(
        android.R.drawable.btn_star_big_off);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Intent intent;
    switch (item.getItemId()) {
      case R.string.settings:
        intent = new Intent(this, PrefsActivity.class);
        startActivityForResult(intent, PREFS_UPDATED);
        break;
      case R.string.themes:
        displayThemeSelector();
        break;
      case R.string.about:
        displayAbout();
        break;
      case R.string.hiscores:
        displayHiscores();
        break;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == PREFS_UPDATED) {
      loadPrefs();
    }
    super.onActivityResult(requestCode, resultCode, data);
    scoreNinjaAdapter.onActivityResult(requestCode, resultCode, data);
  }

  private void loadPrefs() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    // From the game mode setting
    String modeStr = prefs.getString("game_mode_pref", "1");
    gameMode = Integer.parseInt(modeStr);
    String sequenceLengthStr = prefs.getString("sequence_length_pref", "1");
    sequenceLength = Integer.parseInt(sequenceLengthStr);
    // From the speed setting
    String delayStr = prefs.getString("speed_pref", "-1");
    speedPrefDelay = Integer.parseInt(delayStr);
    // Load the theme
    themeFilename = prefs.getString("theme_pref", "");
  }

  @Override
  protected void onStop() {
    halt = true;
    sfx.stop();
    super.onStop();
  }

  @Override
  protected void onRestart() {
    reloadGameView();
    super.onRestart();
  }

  private void reloadGameView() {
    halt = false;
    if (gView != null) {
      gView.setVisibility(View.GONE);
      gView = null;
    }
    gView = new GameView(this);
    setContentView(gView);
  }

  private void displayThemeSelector() {
    Builder themeFilesDialog = new Builder(this);

    String titleText = "Select a theme";
    themeFilesDialog.setTitle(titleText);

    File androidSaysDir = new File(THEMES_BASE_PATH);
    filenames = androidSaysDir.list(new FilenameFilter() {
      public boolean accept(File dir, String filename) {
        return filename.endsWith(THEME_FILE_EXTENSION);
      }
    });

    // Read the available themes from the SD card
    ArrayList<String> filenamesArrayList = new ArrayList<String>();
    filenamesArrayList.add("Default");
    if (filenames != null) {
      for (int i = 0; i < filenames.length; i++) {
        filenamesArrayList.add(filenames[i].substring(0, filenames[i].length()
            - THEME_FILE_EXTENSION.length()));
      }
    } else {
      filenames = new String[0];
    }
    filenames = filenamesArrayList.toArray(filenames);

    themeFilesDialog.setSingleChoiceItems(filenames, -1, new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        if (which == 0) {
          themeFilename = null;
        } else {
          themeFilename = THEMES_BASE_PATH + filenames[which] + THEME_FILE_EXTENSION;
        }
      }
    });

    final Activity self = this;
    themeFilesDialog.setPositiveButton("Apply theme", new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(self);
        Editor editor = prefs.edit();
        editor.putString("theme_pref", themeFilename);
        editor.commit();
        reloadGameView();
        dialog.dismiss();
      }
    });

    themeFilesDialog.setNeutralButton("Get new themes", new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        if (!new File("/sdcard/").canWrite()) {
          dialog.dismiss();
          displayMissingSdCardToast();
        } else {
          Intent i = new Intent();
          ComponentName comp =
              new ComponentName("com.android.browser", "com.android.browser.BrowserActivity");
          i.setComponent(comp);
          i.setAction("android.intent.action.VIEW");
          i.addCategory("android.intent.category.BROWSABLE");
          Uri uri = Uri.parse("http://groups.google.com/group/mem-game/files");
          i.setData(uri);
          self.startActivity(i);
        }
      }
    });

    themeFilesDialog.setNegativeButton("Cancel", new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
      }
    });

    themeFilesDialog.show();
  }

  private void displayMissingSdCardToast() {
    Toast.makeText(this, "Please insert an SD card before trying to download themes.",
        Toast.LENGTH_LONG).show();
  }

  private void displayAbout() {
    Builder about = new Builder(this);

    String titleText = "mem";

    about.setTitle(titleText);
    String message = "by Charles L. Chen (clchen@google.com)";

    about.setMessage(message);

    final Activity self = this;

    about.setPositiveButton("Visit website", new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Intent i = new Intent();
        ComponentName comp =
            new ComponentName("com.android.browser", "com.android.browser.BrowserActivity");
        i.setComponent(comp);
        i.setAction("android.intent.action.VIEW");
        i.addCategory("android.intent.category.BROWSABLE");
        Uri uri = Uri.parse("http://groups.google.com/group/mem-game");
        i.setData(uri);
        self.startActivity(i);
      }
    });

    about.setNegativeButton("Close", new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {

      }
    });

    about.show();
  }

  public void recordScore(int score) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    String modeStr = prefs.getString("game_mode_pref", "1");
    gameMode = Integer.parseInt(modeStr);
    String sequenceLengthStr = prefs.getString("sequence_length_pref", "1");
    String scorePrefStr = "";
    String subboard = " (" + sequenceLengthStr + ")";
    if (gameMode == 1) {
      scorePrefStr = "classic_";
      subboard = "Classic Mode" + subboard;
    } else {
      scorePrefStr = "challenge_";
      subboard = "Challenge Mode" + subboard;
    }
    scorePrefStr = scorePrefStr + sequenceLengthStr;

    int prevHiScore = Integer.parseInt(prefs.getString(scorePrefStr, "0"));
    int diff = score - prevHiScore;
    if (diff > 0) {
      Editor editor = prefs.edit();
      editor.putString(scorePrefStr, Integer.toString(score));
      editor.commit();

      Toast.makeText(this, "You beat your old record by " + diff + "!", Toast.LENGTH_LONG).show();
    }
    
    scoreNinjaAdapter.show(score, "Top Scores for " + subboard, subboard);
  }


  private void displayHiscores() {
    Builder hiscores = new Builder(this);

    String titleText = "High Scores";

    hiscores.setTitle(titleText);

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    String message = "Classic mode   (1):  " + prefs.getString("classic_1", "0") + "\n";
    message = message + "Classic mode   (5):  " + prefs.getString("classic_5", "0") + "\n";
    message = message + "Classic mode (10):  " + prefs.getString("classic_10", "0") + "\n";
    message = message + "Classic mode (20):  " + prefs.getString("classic_20", "0") + "\n";
    message = message + "Classic mode (30):  " + prefs.getString("classic_30", "0") + "\n" + "\n";
    message = message + "Challenge mode   (1):  " + prefs.getString("challenge_1", "0") + "\n";
    message = message + "Challenge mode   (5):  " + prefs.getString("challenge_5", "0") + "\n";
    message = message + "Challenge mode (10):  " + prefs.getString("challenge_10", "0") + "\n";
    message = message + "Challenge mode (20):  " + prefs.getString("challenge_20", "0") + "\n";
    message = message + "Challenge mode (30):  " + prefs.getString("challenge_30", "0");


    hiscores.setMessage(message);

    hiscores.setPositiveButton("Erase high scores", new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        displayScoreResetConfirmation();
        dialog.dismiss();
      }
    });

    hiscores.setNegativeButton("Close", new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
      }
    });

    hiscores.show();
  }


  private void displayScoreResetConfirmation() {
    Builder confirmation = new Builder(this);

    String titleText = "Erase high scores?";

    confirmation.setTitle(titleText);

    String message = "Are you sure you want to erase the high scores?";

    confirmation.setMessage(message);

    final Activity self = this;

    confirmation.setPositiveButton("Yes", new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(self);
        Editor editor = prefs.edit();
        editor.putString("classic_1", "0");
        editor.putString("classic_5", "0");
        editor.putString("classic_10", "0");
        editor.putString("classic_20", "0");
        editor.putString("classic_30", "0");
        editor.putString("challenge_1", "0");
        editor.putString("challenge_5", "0");
        editor.putString("challenge_10", "0");
        editor.putString("challenge_20", "0");
        editor.putString("challenge_30", "0");
        editor.commit();
        dialog.dismiss();
      }
    });

    confirmation.setNegativeButton("No", new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
      }
    });

    confirmation.show();
  }
  

  

}
