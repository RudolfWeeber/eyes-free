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

package com.ideal.webaccess;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import android.app.Activity;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.provider.BaseColumns;
import android.provider.Browser;
import android.webkit.WebView;

/**
 * Main activity for IDEAL Web Access. Adds the bookmarklet + unzips the
 * necessary JavaScript files, then displays the tutorial HTML WebView.
 */
public class StartIdealWebAccess extends Activity {
    private static final String[] mColumnStrings = {
            BaseColumns._ID, Browser.BookmarkColumns.TITLE, Browser.BookmarkColumns.URL,
            Browser.BookmarkColumns.BOOKMARK, Browser.BookmarkColumns.VISITS
    };

    private static final String bookmarkletTitle = "IDEAL Web Access";

    private static final String bookmarkletUrl = "javascript:(function(){loaderScript=document.createElement('SCRIPT');loaderScript.type='text/javascript';loaderScript.src='content://com.ideal.webaccess.localjs/ideal-loader.js';document.getElementsByTagName('head')[0].appendChild(loaderScript);})();";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.installing);
        loadBookmarklet();
        runInstaller();
    }

    private void runInstaller() {
        try {
            Resources res = getResources();
            // Yes, it is weird to name the js zip file as .mp3, but that
            // seems to be the only way to keep AAPT from compressing it
            // and screwing up the build.
            // http://code.google.com/p/android/issues/detail?id=3122
            AssetFileDescriptor jsZipFd = res.openRawResourceFd(R.raw.ideal_js);
            InputStream stream = jsZipFd.createInputStream();
            (new Thread(new dataCheckAndUnzip(stream))).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class dataCheckAndUnzip implements Runnable {
        public InputStream stream;

        public dataCheckAndUnzip(InputStream is) {
            stream = is;
        }

        public void run() {
            File idealLoaderScript = new File(Environment.getExternalStorageDirectory()
                    + "/ideal-webaccess/js/ideal-webaccess.user.js");
            boolean result = idealLoaderScript.exists();
            if (!result) {
                result = Unzipper.doDataCheckAndUnzip(stream);
            }
            if (result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showTutorial();
                    }
                });
            }
        }
    }

    private void showTutorial() {
        setContentView(R.layout.main);
        final String mimeType = "text/html";
        final String encoding = "UTF-8";

        try {
            InputStream is = getResources().openRawResource(R.raw.tutorial);

            byte[] buffer = new byte[4096];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            while (true) {
                int read = is.read(buffer);

                if (read == -1) {
                    break;
                }

                baos.write(buffer, 0, read);
            }

            baos.close();
            is.close();

            String data = baos.toString();

            WebView tutorialWebView = (WebView) findViewById(R.id.tutorial);
            tutorialWebView.getSettings().setJavaScriptEnabled(true);
            tutorialWebView.loadData(data, mimeType, encoding);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void loadBookmarklet() {
        boolean hasBookmarklet = false;
        Cursor c = getContentResolver().query(Browser.BOOKMARKS_URI, mColumnStrings, null, null,
                null);
        if (c != null) {
            boolean keepGoing = c.moveToFirst();
            while (keepGoing) {
                if ((c.getString(3) != null) && (c.getString(3).equals("1"))
                        && (c.getString(1) != null) && (c.getString(1).equals(bookmarkletTitle))
                        && (c.getString(2) != null) && (c.getString(2).equals(bookmarkletUrl))) {
                    hasBookmarklet = true;
                }
                keepGoing = c.moveToNext();
            }
        }
        if (!hasBookmarklet) {
            ContentValues bookmarkletContent = new ContentValues();
            bookmarkletContent.put(Browser.BookmarkColumns.TITLE, bookmarkletTitle);
            bookmarkletContent.put(Browser.BookmarkColumns.URL, bookmarkletUrl);
            bookmarkletContent.put(Browser.BookmarkColumns.BOOKMARK, 1);
            bookmarkletContent.put(Browser.BookmarkColumns.VISITS, 9999);
            getContentResolver().insert(Browser.BOOKMARKS_URI, bookmarkletContent);
        }
    }

}
