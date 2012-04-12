// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.marvin.shell;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.Browser;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

/**
 * @author credo@google.com (Tim Credo)
 */
public class BookmarkChooserActivity extends ListActivity {

    private Cursor mCursor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCursor = this.getContentResolver().query(
                android.provider.Browser.BOOKMARKS_URI, null, null, null, null);
        ListAdapter adapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2,
                mCursor,
                new String[] { Browser.BookmarkColumns.TITLE, Browser.BookmarkColumns.URL },
                new int[] { android.R.id.text1, android.R.id.text2 });
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        mCursor.moveToPosition(position);
        int titleIndex = mCursor.getColumnIndex(Browser.BookmarkColumns.TITLE);
        int urlIndex = mCursor.getColumnIndex(Browser.BookmarkColumns.URL);
        Intent data = new Intent();
        data.putExtra("TITLE", mCursor.getString(titleIndex));
        data.putExtra("URL", mCursor.getString(urlIndex));
        this.setResult(Activity.RESULT_OK, data);
        finish();
    }
}
