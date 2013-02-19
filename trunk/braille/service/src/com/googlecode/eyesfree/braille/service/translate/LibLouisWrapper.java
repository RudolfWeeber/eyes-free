/*
 * Copyright 2012 Google Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.googlecode.eyesfree.braille.service.translate;

import android.util.Log;

import com.googlecode.eyesfree.braille.translate.TranslationResult;

/**
 * Wraps the liblouis functions to translate to and from braille.
 *
 * NOTE: Braille translation involves reading tables from disk and can
 * therefore be blocking.  In addition, translation by all instances
 * of this class is serialized because of the underlying implementation,
 * which increases the possibility of translations blocking on I/O if multiple
 * translators are used.
 */
public class LibLouisWrapper {
    private static final String LOG_TAG =
            LibLouisWrapper.class.getSimpleName();

    /**
     * This method should be called before any other method is
     * called.  {@code path} should point to a location in the file system
     * under which the liblouis translation tables can be found.
     */
    public static void setTablesDir(String path) {
        synchronized (LibLouisWrapper.class) {
            setTablesDirNative(path);
        }
    }

    /**
     * Compiles the given table and makes sure it is valid.
     */
    public static boolean checkTable(String tableName) {
        synchronized (LibLouisWrapper.class) {
            if (!checkTableNative(tableName)) {
                Log.w(LOG_TAG, "Table not found or invalid: " + tableName);
                return false;
            }
            return true;
        }
    }

    /**
     * Translates a string into the corresponding dot patterns and returns the
     * resulting byte array.
     */
    public static TranslationResult translate(String text, String tableName,
            int cursorPosition) {
        synchronized (LibLouisWrapper.class) {
            return translateNative(text, tableName, cursorPosition);
        }
    }

    public static String backTranslate(byte[] cells, String tableName) {
        synchronized (LibLouisWrapper.class) {
            return backTranslateNative(cells, tableName);
        }
    }

    // Native methods.  Since liblouis is neither reentrant, nor
    // thread-safe, all native methods are called inside synchronized
    // blocks on the class object, allowing multiple translators
    // to exist.

    private static native TranslationResult translateNative(String text,
            String tableName, int cursorPosition);
    private static native String backTranslateNative(byte[] dotPatterns,
            String tableName);
    private static native boolean checkTableNative(String tableName);
    private static native void setTablesDirNative(String path);
    private static native void classInitNative();

    static {
        System.loadLibrary("louiswrap");
        classInitNative();
    }
}
