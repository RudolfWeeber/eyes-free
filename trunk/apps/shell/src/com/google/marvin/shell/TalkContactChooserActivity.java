// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.marvin.shell;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

/**
 * @author credo@google.com (Tim Credo)
 */
public class TalkContactChooserActivity extends ListActivity {

    private Cursor mCursor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String imWhere = ContactsContract.CommonDataKinds.Im.PROTOCOL + " = "
                + ContactsContract.CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK;
        mCursor = this.getContentResolver().query(
                android.provider.ContactsContract.Data.CONTENT_URI, null, null, null, null);
        ListAdapter adapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2,
                mCursor, new String[] { ContactsContract.CommonDataKinds.Im.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Im.DATA },
                new int[] { android.R.id.text1, android.R.id.text2 });
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        mCursor.moveToPosition(position);
        int dataIndex = mCursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA);
        int nameIndex = mCursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.DISPLAY_NAME);
        Intent data = new Intent();
        data.putExtra("address", mCursor.getString(dataIndex));
        data.putExtra("address", mCursor.getString(nameIndex));
        this.setResult(Activity.RESULT_OK, data);
        finish();
    }
}
