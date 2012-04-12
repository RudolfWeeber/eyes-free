/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.marvin.webvox;

import com.marvin.webvox.R;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ParseException;
//import android.net.WebAddress;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Browser;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

public class AddBookmarkPage extends Activity {

    private final String LOGTAG = "Bookmarks";

    private EditText    mTitle;
    private EditText    mAddress;
    private TextView    mButton;
    private View        mCancelButton;
    private boolean     mEditingExisting;
    private Bundle      mMap;
    private String      mTouchIconUrl;
    private Bitmap      mThumbnail;
    private String      mOriginalUrl;

    // Message IDs
    private static final int SAVE_BOOKMARK = 100;

    private Handler mHandler;

    private View.OnClickListener mSaveBookmark = new View.OnClickListener() {
        public void onClick(View v) {
            if (save()) {
                finish();
            }
        }
    };

    private View.OnClickListener mCancel = new View.OnClickListener() {
        public void onClick(View v) {
            finish();
        }
    };

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.browser_add_bookmark);
        setTitle(R.string.save_to_bookmarks);
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.ic_list_bookmark);
        
        String title = null;
        String url = null;
        mMap = getIntent().getExtras();
        if (mMap != null) {
            Bundle b = mMap.getBundle("bookmark");
            if (b != null) {
                mMap = b;
                mEditingExisting = true;
                setTitle(R.string.edit_bookmark);
            }
            title = mMap.getString("title");
            url = mOriginalUrl = mMap.getString("url");
            mTouchIconUrl = mMap.getString("touch_icon_url");
            mThumbnail = (Bitmap) mMap.getParcelable("thumbnail");
        }

        mTitle = (EditText) findViewById(R.id.title);
        mTitle.setText(title);
        mAddress = (EditText) findViewById(R.id.address);
        mAddress.setText(url);

        View.OnClickListener accept = mSaveBookmark;
        mButton = (TextView) findViewById(R.id.OK);
        mButton.setOnClickListener(accept);

        mCancelButton = findViewById(R.id.cancel);
        mCancelButton.setOnClickListener(mCancel);
        
        if (!getWindow().getDecorView().isInTouchMode()) {
            mButton.requestFocus();
        }
    }

    private void createHandler() {
        if (mHandler == null) {
            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case SAVE_BOOKMARK:
                            // Unbundle bookmark data.
                            Bundle bundle = msg.getData();
                            String title = bundle.getString("title");
                            String url = bundle.getString("url");
                            boolean invalidateThumbnail = bundle.getBoolean("invalidateThumbnail");
                            Bitmap thumbnail = invalidateThumbnail
                                    ? null : (Bitmap) bundle.getParcelable("thumbnail");
                            String touchIconUrl = bundle.getString("touchIconUrl");

                            // Save to the bookmarks DB.
                            if (updateBookmarksDB(title, url, thumbnail, touchIconUrl)) {
                                Toast.makeText(AddBookmarkPage.this, R.string.bookmark_saved,
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(AddBookmarkPage.this, R.string.bookmark_not_saved,
                                        Toast.LENGTH_LONG).show();
                            }
                            break;
                    }
                }
            };
        }
    }

    private boolean updateBookmarksDB(String title, String url, Bitmap thumbnail, String touchIconUrl) {
        try {
            final ContentResolver cr = getContentResolver();
            Bookmarks.addBookmark(null, cr, url, title, thumbnail, true);
            if (touchIconUrl != null) {
                final Cursor c =
                        BrowserBookmarksAdapter.queryBookmarksForUrl(cr, null, url, true);
                new DownloadTouchIcon(cr, c, url).execute(mTouchIconUrl);
            }
        } catch (IllegalStateException e) {
            return false;
        }
        return true;
    }

    /**
     * Parse the data entered in the dialog and post a message to update the bookmarks database.
     */
    boolean save() {
        createHandler();

        String title = mTitle.getText().toString().trim();
        String unfilteredUrl = 
                BrowserActivity.fixUrl(mAddress.getText().toString());
        boolean emptyTitle = title.length() == 0;
        boolean emptyUrl = unfilteredUrl.trim().length() == 0;
        Resources r = getResources();
        if (emptyTitle || emptyUrl) {
            if (emptyTitle) {
                mTitle.setError(r.getText(R.string.bookmark_needs_title));
            }
            if (emptyUrl) {
                mAddress.setError(r.getText(R.string.bookmark_needs_url));
            }
            return false;
        }
        String url = unfilteredUrl;
        try {
            URI uriObj = new URI(url);
            String scheme = uriObj.getScheme();
            if (!("about".equals(scheme) || "data".equals(scheme)
                    || "javascript".equals(scheme)
                    || "file".equals(scheme) || "content".equals(scheme))) {
/*                
                WebAddress address;
                try {
                    address = new WebAddress(unfilteredUrl);
                } catch (ParseException e) {
                    throw new URISyntaxException("", "");
                }
                if (address.mHost.length() == 0) {
                    throw new URISyntaxException("", "");
                }
                url = address.toString();
*/                
            }
        } catch (URISyntaxException e) {
            mAddress.setError(r.getText(R.string.bookmark_url_not_valid));
            return false;
        }

        if (mEditingExisting) {
            mMap.putString("title", title);
            mMap.putString("url", url);
            mMap.putBoolean("invalidateThumbnail", !url.equals(mOriginalUrl));
            setResult(RESULT_OK, (new Intent()).setAction(
                    getIntent().toString()).putExtras(mMap));
        } else {
            // Post a message to write to the DB.
            Bundle bundle = new Bundle();
            bundle.putString("title", title);
            bundle.putString("url", url);
            bundle.putParcelable("thumbnail", mThumbnail);
            bundle.putBoolean("invalidateThumbnail", !url.equals(mOriginalUrl));
            bundle.putString("touchIconUrl", mTouchIconUrl);
            Message msg = Message.obtain(mHandler, SAVE_BOOKMARK);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
            setResult(RESULT_OK);
        }
        return true;
    }
}
