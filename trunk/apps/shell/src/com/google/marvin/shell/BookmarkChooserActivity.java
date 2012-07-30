// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.marvin.shell;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Browser;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

/**
 * @author credo@google.com (Tim Credo)
 * @author sainsley@google.com (Sam Ainsley)
 */
public class BookmarkChooserActivity extends ListActivity {

    private Cursor mCursorChrome, mCursorBrowser;
    private MergeCursor mMergedCursor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Only want bookmarks (not history items)
        String filter = Browser.BookmarkColumns.BOOKMARK + " = 1 AND " + Browser.BookmarkColumns.URL + " NOT NULL ";
        // Grab bookmarks from Chrome
        Uri chromeBookMarks = Uri.parse(
                "content://com.google.android.apps.chrome.browser/bookmarks");
        mCursorChrome = this.getContentResolver().query(
                chromeBookMarks,
                null, filter, null, null);
        // Grab bookmarks from browser
        Uri browserBookMarks = android.provider.Browser.BOOKMARKS_URI;
        mCursorBrowser = this.getContentResolver().query(
                browserBookMarks,
                null, filter, null, null);
        boolean hasFirst = mCursorBrowser.moveToFirst();
        Cursor[] cursors = {
                mCursorChrome, mCursorBrowser };
        mMergedCursor = new MergeCursor(cursors);
        // This will fail if user does not have new Chrome Beta: just return
        // default bookmarks
        try {
            ListAdapter adapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2,
                    mMergedCursor,
                    new String[] {
                            Browser.BookmarkColumns.TITLE, Browser.BookmarkColumns.URL },
                    new int[] {
                            android.R.id.text1, android.R.id.text2 });
            setListAdapter(adapter);
        } catch (java.lang.IllegalArgumentException e) {
            ListAdapter adapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2,
                    mCursorBrowser,
                    new String[] {
                            Browser.BookmarkColumns.TITLE, Browser.BookmarkColumns.URL },
                    new int[] {
                            android.R.id.text1, android.R.id.text2 });
            setListAdapter(adapter);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        mMergedCursor.moveToPosition(position);
        int titleIndex = mMergedCursor.getColumnIndex(Browser.BookmarkColumns.TITLE);
        int urlIndex = mMergedCursor.getColumnIndex(Browser.BookmarkColumns.URL);
        Intent data = new Intent();
        data.putExtra("TITLE", mMergedCursor.getString(titleIndex));
        data.putExtra("URL", mMergedCursor.getString(urlIndex));
        this.setResult(Activity.RESULT_OK, data);
        finish();
    }
}
