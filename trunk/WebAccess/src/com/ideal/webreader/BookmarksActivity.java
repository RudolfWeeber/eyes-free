/*
 * Copyright (C) 2010 The IDEAL Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ideal.webreader;

import com.ideal.webaccess.R;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.Browser;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * This class lists the users bookmarks.
 */
public class BookmarksActivity extends ListActivity {
    private Cursor mCur;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bookmarks);
        String[] projection = new String[] {
                BaseColumns._ID, Browser.BookmarkColumns.BOOKMARK, Browser.BookmarkColumns.TITLE,
                Browser.BookmarkColumns.URL
        };
        String[] displayFields = new String[] {
                Browser.BookmarkColumns.TITLE, Browser.BookmarkColumns.URL
        };
        int[] displayViews = new int[] {
                android.R.id.text1, android.R.id.text2
        };

        String whereClause = Browser.BookmarkColumns.BOOKMARK + " = '1'";

        mCur = managedQuery(android.provider.Browser.BOOKMARKS_URI, projection, whereClause, null,
                null);
        setListAdapter(new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, mCur,
                displayFields, displayViews));

        ListView lv = this.getListView();

        registerForContextMenu(lv);

        this.setResult(Activity.RESULT_CANCELED);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add("Delete");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        getContentResolver().delete(
                Uri.parse(android.provider.Browser.BOOKMARKS_URI.toString() + "/" + info.id), null,
                null);
        return true;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        mCur.moveToPosition(position);
        String url = mCur.getString(3);
        Intent data = new Intent();
        data.putExtra("URL", url);
        this.setResult(Activity.RESULT_OK, data);
        finish();
    }
}
