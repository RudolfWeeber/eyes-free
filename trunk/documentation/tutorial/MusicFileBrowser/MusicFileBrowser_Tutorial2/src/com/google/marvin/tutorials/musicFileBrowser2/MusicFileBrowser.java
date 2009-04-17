package com.google.marvin.tutorials.musicFileBrowser2;

/**
 * Music File Browser with speech and gestures added.
 * 
 * File browsing code is based on a tutorial from anddev.org by Nicolas Gramlich
 * http://www.anddev.org/building_an_android_filebrowser_list-based_ -t67.html
 * 
 * @author clchen@google.com (Charles L. Chen)
 */

import com.google.marvin.widget.TouchGestureControlOverlay;
import com.google.marvin.widget.TouchGestureControlOverlay.Gesture;
import com.google.marvin.widget.TouchGestureControlOverlay.GestureListener;
import com.google.tts.TTS;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class MusicFileBrowser extends Activity {
  private static final long[] VIBE_PATTERN = {0, 10, 70, 80};
  private Vibrator vibe;
  private List<String> directoryEntries = new ArrayList<String>();
  private File currentDirectory = new File("/sdcard/");
  private MediaPlayer mp = new MediaPlayer();
  private int lastPosition;
  private String lastPlayedFilename = "";
  private TTS tts;
  private ListView myList;
  private FrameLayout myFrame;
  private TouchGestureControlOverlay myGestureOverlay;
  private boolean overlayActive;

  private TTS.InitListener ttsInitListener = new TTS.InitListener() {
    public void onInit(int version) {
      lastPosition = 0;
      vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      browseTo(new File("/sdcard/"));
      tts.speak("Music File Browser started.", 0, null);
    }
  };

  private GestureListener gestureListener = new GestureListener() {
    public void onGestureChange(Gesture arg0) {
    }

    public void onGestureFinish(Gesture arg0) {
      if (arg0 == Gesture.CENTER) {
        File musicFile = new File(lastPlayedFilename);
        if (musicFile.isFile()) {
          togglePlaying(musicFile);
          vibe.vibrate(VIBE_PATTERN, -1);
        }
      } else if (arg0 == Gesture.RIGHT) {
        playNext();
        vibe.vibrate(VIBE_PATTERN, -1);
      }
    }

    public void onGestureStart(Gesture arg0) {
    }
  };

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    myList = new ListView(this);
    myList.setOnItemClickListener(new OnItemClickListener() {
      public void onItemClick(AdapterView<?> l, View v, int position, long id) {
        if (position == 0) {
          playNext();
        } else if (position == 1) {
          upOneLevel();
        } else {
          lastPosition = position;
          File clickedFile =
              new File(currentDirectory.getAbsolutePath() + directoryEntries.get(position));
          if (clickedFile != null) {
            browseTo(clickedFile);
          }
        }
      }
    });
    myFrame = new FrameLayout(this);
    myFrame.addView(myList);
    myGestureOverlay = new TouchGestureControlOverlay(this, gestureListener);
    overlayActive = false;
    setContentView(myFrame);

    tts = new TTS(this, ttsInitListener, true);
  }

  /**
   * This function browses up one level according to the field: currentDirectory
   * Returns whether or not navigation occurred.
   */
  private boolean upOneLevel() {
    String parent = this.currentDirectory.getParent();
    if (parent.equals("/")) {
      browseTo(new File("/sdcard/"));
      return false;
    } else if (this.currentDirectory.getParent() != null) {
      this.browseTo(this.currentDirectory.getParentFile());
    }
    return true;
  }

  private void browseTo(final File aDirectory) {
    if (aDirectory.isDirectory()) {
      this.currentDirectory = aDirectory;
      fill(aDirectory.listFiles());
      lastPosition = 0;
      myList.setSelection(0);
    } else {
      togglePlaying(aDirectory);
    }
  }

  private void togglePlaying(final File aFile) {
    if (mp.isPlaying()) {
      mp.stop();
    } else {
      lastPlayedFilename = aFile.getAbsolutePath();
      mp = MediaPlayer.create(this, Uri.parse(lastPlayedFilename));
      mp.start();
    }
  }

  private void playNext() {
    if (mp.isPlaying()) {
      mp.stop();
    }
    int i = lastPosition;
    i++;
    String path = this.currentDirectory.getAbsolutePath();
    while (i < this.directoryEntries.size()) {
      String filename = this.directoryEntries.get(i);
      File currentFile = new File(path + filename);
      if (currentFile.isFile()) {
        lastPosition = i;
        togglePlaying(currentFile);
        return;
      }
      i++;
    }
    i = 0;
    while (i <= lastPosition) {
      String filename = this.directoryEntries.get(i);
      File currentFile = new File(path + filename);
      if (currentFile.isFile()) {
        lastPosition = i;
        togglePlaying(currentFile);
        return;
      }
      i++;
    }
    Toast.makeText(this, R.string.no_files, 1).show();
    tts.speak(getString(R.string.no_files), 0, null);
  }

  private void fill(File[] files) {
    this.directoryEntries.clear();

    // Add the top two choices
    this.directoryEntries.add(currentDirectory.getAbsolutePath());
    if (this.currentDirectory.getParent() != null) {
      this.directoryEntries.add(" up one level");
    }

    int currentPathStringLength = this.currentDirectory.getAbsolutePath().length();

    List<String> directories = new ArrayList<String>();
    for (File file : files) {
      if (file.isDirectory()) {
        String filename = file.getAbsolutePath().substring(currentPathStringLength);
        if (!filename.startsWith("/.")) {
          directories.add(filename);
        }
      }
    }
    Collections.sort(directories);
    this.directoryEntries.addAll(directories);

    List<String> audioFiles = new ArrayList<String>();
    for (File file : files) {
      if (file.getPath().endsWith(".mp3")) {
        String filename = file.getAbsolutePath().substring(currentPathStringLength);
        if (!filename.startsWith("/.")) {
          audioFiles.add(filename);
        }
      }
    }
    Collections.sort(audioFiles);
    this.directoryEntries.addAll(audioFiles);

    ArrayAdapter<String> directoryList =
        new ArrayAdapter<String>(this, R.layout.file_row, this.directoryEntries);

    myList.setAdapter(directoryList);
  }

  @Override
  public boolean onTrackballEvent(MotionEvent event) {
    int selectedId = myList.getSelectedItemPosition();
    if (selectedId == -1) {
      selectedId = 0;
    }
    if (lastPosition != selectedId) {
      lastPosition = selectedId;
      vibe.vibrate(VIBE_PATTERN, -1);
      String filename = directoryEntries.get(selectedId);
      tts.speak(filename.substring(1), 0, null);
    }
    return super.onTrackballEvent(event);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      // Pressing back should go up a level, not quit the app
      if (upOneLevel()) {
        return true;
      }
    } else if (keyCode == KeyEvent.KEYCODE_MENU) {
      if (overlayActive) {
        myFrame.removeView(myGestureOverlay);
        overlayActive = false;
        tts.speak("Gestures disabled.", 0, null);
      } else {
        myFrame.addView(myGestureOverlay);
        overlayActive = true;
        tts.speak("Gestures activated.", 0, null);
      }
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public void onDestroy(){
    tts.shutdown();
    mp.stop();
    super.onDestroy();
  }
}
