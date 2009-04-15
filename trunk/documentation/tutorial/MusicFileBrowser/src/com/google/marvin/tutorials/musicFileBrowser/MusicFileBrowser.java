package com.google.marvin.tutorials.musicFileBrowser;

// Based on the Filebrowser tutorial from anddev.org by Nicolas Gramlich
// http://www.anddev.org/building_an_android_filebrowser_list-based_-t67.html

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.ListActivity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class MusicFileBrowser extends ListActivity {

	private List<String> directoryEntries = new ArrayList<String>();
	private File currentDirectory = new File("/sdcard/");
	private MediaPlayer mp = new MediaPlayer();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		// setContentView() gets called within the next line,
		// so we do not need it here.
		browseToRoot();
	}

	/**
	 * This function browses to the root-directory of the file-system.
	 */
	private void browseToRoot() {
		browseTo(new File("/sdcard/"));
	}

	/**
	 * This function browses up one level according to the field:
	 * currentDirectory
	 */
	private void upOneLevel() {
		String parent = this.currentDirectory.getParent();
		if (parent.equals("/")) {
			browseToRoot();
		} else if (this.currentDirectory.getParent() != null) {
			this.browseTo(this.currentDirectory.getParentFile());
		}
	}

	private void browseTo(final File aDirectory) {
		if (aDirectory.isDirectory()) {
			this.currentDirectory = aDirectory;
			fill(aDirectory.listFiles());
		} else {
			if (mp.isPlaying()){
				mp.stop();
			} else {
			mp = MediaPlayer.create(this, Uri.parse(aDirectory.getAbsolutePath()));
			mp.start();
			}
		}
	}

	private void fill(File[] files) {
		this.directoryEntries.clear();

		try {
			Thread.sleep(10);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		int currentPathStringLength = this.currentDirectory.getAbsolutePath()
				.length();
		for (File file : files) {
			if (file.isDirectory() || file.getPath().endsWith(".mp3")) {
				String filename = file.getAbsolutePath().substring(
						currentPathStringLength);
				if (!filename.startsWith("/.")) {
					this.directoryEntries.add(filename);
				}
			}
		}

		Collections.sort(directoryEntries);

		// Add the top two choices in reverse order
		if (this.currentDirectory.getParent() != null) {
			this.directoryEntries.add(0, "up one level");
		}
		this.directoryEntries.add(0, currentDirectory.getAbsolutePath());

		ArrayAdapter<String> directoryList = new ArrayAdapter<String>(this,
				R.layout.file_row, this.directoryEntries);

		this.setListAdapter(directoryList);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		int selectionRowID = this.getSelectedItemPosition();

		if (selectionRowID == 0) {
			// Refresh
			this.browseTo(this.currentDirectory);
		} else if (selectionRowID == 1) {
			this.upOneLevel();
		} else {
			File clickedFile = null;

			clickedFile = new File(this.currentDirectory.getAbsolutePath()
					+ this.directoryEntries.get(selectionRowID));

			if (clickedFile != null) {
				this.browseTo(clickedFile);
			}
		}
	}

}