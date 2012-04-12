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

import java.io.File;
import java.io.FileNotFoundException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

/**
 * Content Provider for serving JavaScript. JavaScript files must be stored
 * under: /sdcard/ideal-webaccess/js/ for example:
 * /sdcard/ideal-webaccess/js/MY_JS_FILE.js JavaScript files can be accessed by
 * in the WebView by using: content://com.ideal.webaccess.localjs/MY_JS_FILE.js
 */
public class LocalJsProvider extends ContentProvider {
    private static final String URI_PREFIX = "content://com.ideal.webaccess.localjs";

    public static String constructUri(String url) {
        Uri uri = Uri.parse(url);
        return uri.isAbsolute() ? url : URI_PREFIX + url;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String filename = uri.toString();
        if (filename.length() > URI_PREFIX.length()) {
            filename = filename.substring(URI_PREFIX.length() + 1);
            if ((filename.indexOf("//") != -1) || (filename.indexOf("..") != -1)) {
                return null;
            }
            filename = Environment.getExternalStorageDirectory() + "/ideal-webaccess/js/"
                    + filename;

            File file = new File(filename);
            ParcelFileDescriptor parcel = ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_READ_ONLY);
            return parcel;
        }
        return null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public int delete(Uri uri, String s, String[] as) {
        throw new UnsupportedOperationException("Not supported by this provider");
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException("Not supported by this provider");
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentvalues) {
        throw new UnsupportedOperationException("Not supported by this provider");
    }

    @Override
    public Cursor query(Uri uri, String[] as, String s, String[] as1, String s1) {
        throw new UnsupportedOperationException("Not supported by this provider");
    }

    @Override
    public int update(Uri uri, ContentValues contentvalues, String s, String[] as) {
        throw new UnsupportedOperationException("Not supported by this provider");
    }

}
