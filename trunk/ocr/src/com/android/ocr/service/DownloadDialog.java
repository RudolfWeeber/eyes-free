/*
 * Copyright (C) 2009 Google Inc.
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
package com.android.ocr.service;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.ocr.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and extracts zip files while displaying a progress dialog.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class DownloadDialog extends ProgressDialog {
  private static final String TAG = "DownloadDialog";

  private static final int ACTION_PROGRESS = 0;
  private static final int ACTION_MESSAGE = 1;
  private static final int ACTION_DISMISS = 2;
  private static final int ACTION_MAX = 3;

  private URL mUrl;
  private String mTarget;
  private String mSource;

  private OnCompleteListener mComplete;
  private OnFailListener mFail;
  private OnCancelListener mCancel;

  private boolean mCanceled;

  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case ACTION_PROGRESS: {
          setProgress(msg.arg1);
          break;
        }
        case ACTION_MESSAGE: {
          setMessage(msg.obj.toString());
          break;
        }
        case ACTION_DISMISS: {
          dismiss();
          break;
        }
        case ACTION_MAX: {
          setMax(msg.arg1);
          break;
        }
      }
    }
  };

  public interface OnCompleteListener {
    public void onComplete(DownloadDialog dialog);
  }

  public interface OnFailListener {
    public abstract void onFail(DownloadDialog dialog);
  }

  public DownloadDialog(Context context, String path, String file, String target) {
    super(context);

    mSource = file;

    try {
      mUrl = new URL(path + file);
    } catch (MalformedURLException e) {
      mUrl = null;
    }

    mTarget = target;
    mCanceled = false;

    String host = mUrl.getHost();

    setIndeterminate(true);
    setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    String str = getContext().getString(R.string.downloading, file, host);
    setMessage(str);
    setCancelable(true);

    super.setOnCancelListener(new OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        mCanceled = true;
      }
    });
  }

  @Override
  public void setOnCancelListener(OnCancelListener listener) {
    mCancel = listener;
  }

  public void setOnCompleteListener(OnCompleteListener listener) {
    mComplete = listener;
  }

  public void setOnFailListener(OnFailListener listener) {
    mFail = listener;
  }

  @Override
  public void onStart() {
    int priority = Thread.currentThread().getPriority() - 1;

    Downloader downloader = new Downloader();
    downloader.setPriority(priority);
    downloader.start();

    super.onStart();
  }

  private class Downloader extends Thread {
    @Override
    public void run() {
      try {
        download();
      } catch (IOException e) {
        Log.e(TAG, e.toString());

        if (mFail != null) mFail.onFail(DownloadDialog.this);

        Message msg = mHandler.obtainMessage(ACTION_DISMISS);
        msg.sendToTarget();
      }
    }

    private void download() throws IOException {
      Message msg;
      String str;

      setProgress(0);
      setIndeterminate(false);

      File dataDir = new File(mTarget);
      dataDir.mkdir();

      URL fileUrl = mUrl;
      URLConnection urlConn = fileUrl.openConnection();
      InputStream inStream = urlConn.getInputStream();
      ZipInputStream zipStream = new ZipInputStream(inStream);

      int length = urlConn.getContentLength();

      Log.i(TAG, "Began downloading " + length + " bytes...");

      File outFile;
      int readBytes;
      ZipEntry entry;
      FileOutputStream out;
      byte buffer[] = new byte[0x10000];

      while (!mCanceled && (entry = zipStream.getNextEntry()) != null) {
        outFile = new File(mTarget + entry.getName());

        Log.i(TAG, "Found file: " + outFile);

        if (entry.isDirectory()) {
          outFile.mkdir();
        } else {
          int progress = 0;

          msg = mHandler.obtainMessage(ACTION_MAX, (int) entry.getSize(), 0);
          msg.sendToTarget();

          msg = mHandler.obtainMessage(ACTION_PROGRESS, progress, 0);
          msg.sendToTarget();

          str = getContext().getString(R.string.extracting, outFile.getName(), mSource);
          msg = mHandler.obtainMessage(ACTION_MESSAGE, str);
          msg.sendToTarget();

          outFile.createNewFile();

          out = new FileOutputStream(outFile);

          while (!mCanceled && (readBytes = zipStream.read(buffer, 0, buffer.length)) > 0) {
            out.write(buffer, 0, readBytes);

            progress += readBytes;

            msg = mHandler.obtainMessage(ACTION_PROGRESS, progress, 0);
            msg.sendToTarget();
          }

          out.close();

          if (mCanceled) outFile.delete();
        }
      }

      str = getContext().getString(R.string.cleaning);
      msg = mHandler.obtainMessage(ACTION_MESSAGE, str);
      msg.sendToTarget();

      Log.i(TAG, "Finished downloading.");

      zipStream.close();

      if (mCanceled) {
        if (mCancel != null) mCancel.onCancel(DownloadDialog.this);
      } else {
        if (mComplete != null) mComplete.onComplete(DownloadDialog.this);
      }

      Log.i(TAG, "Dismissing");

      msg = mHandler.obtainMessage(ACTION_DISMISS);
      msg.sendToTarget();
    }
  }
}
