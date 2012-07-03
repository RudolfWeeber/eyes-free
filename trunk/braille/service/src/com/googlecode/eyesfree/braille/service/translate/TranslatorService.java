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

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.googlecode.eyesfree.braille.service.R;
import com.googlecode.eyesfree.braille.translate.ITranslatorService;
import com.googlecode.eyesfree.braille.translate.ITranslatorServiceCallback;
import com.googlecode.eyesfree.braille.translate.TranslatorManager;
import com.googlecode.eyesfree.braille.utils.ZipResourceExtractor;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * The service for the {@link TranslatorManager} client.
 */
public class TranslatorService extends Service {
    private static final String LOG_TAG =
            TranslatorService.class.getSimpleName();

    private static final int FILES_ERROR = -1;
    private static final int FILES_NOT_EXTRACTED = 0;
    private static final int FILES_EXTRACTED = 1;
    // Written in main thread, read in binder threads.
    private volatile int mDataFileState = FILES_NOT_EXTRACTED;
    private final ServiceImpl mServiceImpl = new ServiceImpl();
    private final TranslatorServiceHandler mHandler =
            new TranslatorServiceHandler();
    private final Set<ITranslatorServiceCallback> mPendingCallbacks =
            new HashSet<ITranslatorServiceCallback>();

    @Override
    public void onCreate() {
        super.onCreate();
        extractDataFiles();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // TODO: Deallocate native data.
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mServiceImpl;
    }

    private void extractDataFiles() {
        File tablesDir = getDir("translator", MODE_PRIVATE);
        LibLouisWrapper.setTablesDir(tablesDir.getPath());
        ZipResourceExtractor extractor = new ZipResourceExtractor(
            this, R.raw.translationtables, tablesDir) {
            @Override
            protected void onPostExecute(Integer result) {
                if (result == RESULT_OK) {
                    mDataFileState = FILES_EXTRACTED;
                } else {
                    Log.e(LOG_TAG, "Couldn't extract data files");
                    mDataFileState = FILES_ERROR;
                }
                callPendingOnInits();
            }
        };
        extractor.execute();
    }

    private boolean checkDataFiles() {
        return (mDataFileState == FILES_EXTRACTED);
    }

    private void callPendingOnInits() {
        for (ITranslatorServiceCallback callback : mPendingCallbacks) {
            callOnInit(callback);
        }
        mPendingCallbacks.clear();
    }

    private void callOnInit(ITranslatorServiceCallback callback) {
        try {
            callback.onInit(mDataFileState == FILES_EXTRACTED
                    ? TranslatorManager.SUCCESS
                    : TranslatorManager.ERROR);
        } catch (RemoteException ex) {
            // The client died before we initialized.  This is rare and
            // harmless for us.
        }
    }

    private class ServiceImpl extends ITranslatorService.Stub {

        @Override
        public void setCallback(ITranslatorServiceCallback callback) {
            if (callback == null) {
                Log.e(LOG_TAG, "Received null callback");
                return;
            }
            mHandler.setCallback(callback);
        }

        @Override
        public boolean checkTable(String tableName) {
            if (tableName == null) {
                Log.e(LOG_TAG, "Received null table name in checkTable");
                return false;
            }
            if (!checkDataFiles()) {
                return false;
            }
            return LibLouisWrapper.checkTable(tableName);
        }

        @Override
        public byte[] translate(String text, String tableName) {
            if (text == null) {
                Log.e(LOG_TAG, "Received null text in translate");
                return null;
            }
            if (tableName == null) {
                Log.e(LOG_TAG, "Received null table name in translate");
                return null;
            }
            if (!checkDataFiles()) {
                return null;
            }
            return LibLouisWrapper.translate(text, tableName);
        }

        @Override
        public String backTranslate(byte[] cells, String tableName) {
            if (cells == null) {
                Log.e(LOG_TAG, "Received null text in backTranslate");
                return null;
            }
            if (tableName == null) {
                Log.e(LOG_TAG, "Received null table name in translate");
                return null;
            }
            if (!checkDataFiles()) {
                return null;
            }
            return LibLouisWrapper.backTranslate(cells, tableName);
        }
    }

    private class TranslatorServiceHandler extends Handler {
        private static final int MSG_SET_CALLBACK = 1;

        public void setCallback(ITranslatorServiceCallback callback) {
            obtainMessage(MSG_SET_CALLBACK, callback).sendToTarget();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SET_CALLBACK:
                    handleSetCallback((ITranslatorServiceCallback) msg.obj);
                    break;
            }
        }

        private void handleSetCallback(ITranslatorServiceCallback callback) {
            if (mDataFileState == FILES_NOT_EXTRACTED) {
                mPendingCallbacks.add(callback);
            } else {
                callOnInit(callback);
            }
        }
    }
}
