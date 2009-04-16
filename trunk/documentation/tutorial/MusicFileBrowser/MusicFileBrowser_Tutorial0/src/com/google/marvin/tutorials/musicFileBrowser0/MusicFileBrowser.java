package com.google.marvin.tutorials.musicFileBrowser0;

/**
 * Basic Music File Browser - No speech, no gestures.
 * 
 * File browsing code is based on a tutorial from anddev.org by Nicolas Gramlich
 * http://www.anddev.org/building_an_android_filebrowser_list-based_ -t67.html
 * 
 * @author clchen@google.com (Charles L. Chen)
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.ListActivity;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class MusicFileBrowser extends ListActivity {
  private static final long[] VIBE_PATTERN = {0, 10, 70, 80};
  private Vibrator vibe;
  private List<String> directoryEntries = new ArrayList<String>();
  private File currentDirectory = new File("/sdcard/");
  private MediaPlayer mp = new MediaPlayer();
  private int lastPosition;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    lastPosition = 0;
    vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    // setContentView() gets called within the next line,
    // so we do not need it here.
    browseTo(new File("/sdcard/"));
  }

  /**
   * This function browses up one level according to the field: currentDirectory
   */
  private void upOneLevel() {
    String parent = this.currentDirectory.getParent();
    if (parent.equals("/")) {
      browseTo(new File("/sdcard/"));
    } else if (this.currentDirectory.getParent() != null) {
      this.browseTo(this.currentDirectory.getParentFile());
    }
  }

  private void browseTo(final File aDirectory) {
    if (aDirectory.isDirectory()) {
      this.currentDirectory = aDirectory;
      fill(aDirectory.listFiles());
      lastPosition = 0;
      this.setSelection(0);
    } else {
      togglePlaying(aDirectory);
    }
  }

  private void togglePlaying(final File aFile) {
    if (mp.isPlaying()) {
      mp.stop();
    } else {
      mp = MediaPlayer.create(this, Uri.parse(aFile.getAbsolutePath()));
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
  }

  private void fill(File[] files) {
    this.directoryEntries.clear();

    // Add the top two choices
    this.directoryEntries.add(currentDirectory.getAbsolutePath());
    if (this.currentDirectory.getParent() != null) {
      this.directoryEntries.add("up one level");
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

    this.setListAdapter(directoryList);
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    if (position == 0) {
      playNext();
    } else if (position == 1) {
      this.upOneLevel();
    } else {
      lastPosition = position;
      File clickedFile =
          new File(currentDirectory.getAbsolutePath() + directoryEntries.get(position));
      if (clickedFile != null) {
        this.browseTo(clickedFile);
      }
    }
  }

  @Override
  public boolean onTrackballEvent(MotionEvent event) {
    int selectedId = getSelectedItemPosition();
    if (selectedId == -1) {
      selectedId = 0;
    }
    if (lastPosition != selectedId) {
      lastPosition = selectedId;
      vibe.vibrate(VIBE_PATTERN, -1);
    }
    return super.onTrackballEvent(event);
  }
}
