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

package com.marvin.webvox;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class ScriptListActivity extends ListActivity {
	
	public final static String TAG = ScriptListActivity.class.toString();
	
	private ScriptDatabase scriptdb = null;
	private SimpleCursorAdapter adapter = null;
	
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_scriptlist);

		this.scriptdb = new ScriptDatabase(this);

		final Cursor cur = scriptdb.getScripts();
		this.startManagingCursor(cur);
		
		adapter = new SimpleCursorAdapter(this, R.layout.item_script, cur, 
				new String[] { ScriptDatabase.FIELD_SCRIPT_NAME, ScriptDatabase.FIELD_SCRIPT_DESCRIP, ScriptDatabase.FIELD_SCRIPT_ENABLED }, 
				new int[] { R.id.script_title, R.id.script_descrip, R.id.script_enabled });
		
		adapter.setViewBinder(new ViewBinder() {
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				switch(view.getId()) {
				case R.id.script_enabled: {
					if(!(view instanceof CheckBox)) return false;
					int value = cursor.getInt(columnIndex);
					((CheckBox)view).setChecked((value == 1));
					return true;
					}
				}
				return false;
			}
		});
		
		this.getListView().setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
				// toggle checked status and refresh our cursor
				scriptdb.toggleEnabled(id);
				cur.requery();
				
			}
		});
		
		this.setListAdapter(adapter);
		this.registerForContextMenu(this.getListView());
	
	}
	
	public void onDestroy() {
		super.onDestroy();
		this.scriptdb.close();
	}
	

	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
		
		MenuItem delete = menu.add(R.string.manage_delete);
		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				scriptdb.deleteScript(info.id);
				adapter.getCursor().requery();
				return true;
			}
		});

	}
	
	/**
	 * File filter to show only user scripts (*.user.js)
	 */
	protected FilenameFilter userScriptFilter = new FilenameFilter() {
		public boolean accept(File path, String filename) {
			return filename.endsWith(".user.js");
		}
	};

	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		MenuItem find = menu.add(R.string.manage_find);
		find.setIcon(android.R.drawable.ic_menu_add);
		find.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// launch back around to specific url
				Intent intent = new Intent(ScriptListActivity.this, BrowserActivity.class);
				intent.putExtra(SearchManager.QUERY, "http://userscripts.org/scripts/search?q=android");
				ScriptListActivity.this.startActivity(intent);
				return true;
			}
		});
		
		MenuItem imp = menu.add(R.string.manage_import);
		imp.setIcon(android.R.drawable.ic_menu_upload);
		imp.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// show file browser to pick script
				
				// code adapted from connectbot project:
				// http://connectbot.googlecode.com/svn/trunk/connectbot/src/org/connectbot/PubkeyListActivity.java
				
				// TODO: replace this with well-known intent over to file browser
				// TODO: if browser not installed (?) then fallback to this simple method?
				
				// build list of all files in sdcard root
				final File sdcard = Environment.getExternalStorageDirectory();
				
				// Don't show a dialog if the SD card is completely absent.
				final String state = Environment.getExternalStorageState();
				if (Environment.MEDIA_REMOVED.equals(state)
						|| Environment.MEDIA_BAD_REMOVAL.equals(state)
						|| Environment.MEDIA_UNMOUNTABLE.equals(state)
						|| Environment.MEDIA_UNMOUNTED.equals(state)) {
					
					Toast.makeText(ScriptListActivity.this, R.string.manage_pick_error, Toast.LENGTH_SHORT).show();
					return true;
				}
				
				final String[] namesList = sdcard.list(userScriptFilter);

				// prompt user to select any file from the sdcard root
				new AlertDialog.Builder(ScriptListActivity.this)
					.setTitle(R.string.manage_pick)
					.setItems(namesList, new OnClickListener() {
						public void onClick(DialogInterface arg0, int arg1) {
							// find the exact file selected
							String name = namesList[arg1];
							try {
								String raw = Util.getFileString(new File(sdcard, name));
								scriptdb.insertScript(null, raw);
								Toast.makeText(ScriptListActivity.this, R.string.manage_import_success, Toast.LENGTH_SHORT).show();
							} catch(Exception e) {
								Log.e(TAG, "Problem while trying to import script", e);
								Toast.makeText(ScriptListActivity.this, R.string.manage_import_fail, Toast.LENGTH_SHORT).show();
							}
						}
					})
					.setNegativeButton(android.R.string.cancel, null).create().show();
				
				return true;
			}
		});

		return true;
	}

}
