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
package com.android.ocr.client;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Monitors the status of an OCR job and periodically sends progress updates
 * to a Handler using a specified action. If an error occurs, sends -1.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class StatusMonitor extends Thread {
  private static final String TAG = "StatusMonitor";
  
  private static final int PROGRESS_MAX = 100;
  
  private Ocr mOcr;
  private Handler mHandler;
  
  private int mAction;
  private long mInterval;
  private boolean mAlive;
  
  public StatusMonitor(Ocr ocr, Handler handler, int action, long interval) {
    mOcr = ocr;
    mHandler = handler;
    mAction = action;
    mInterval = interval;
    mAlive = true;
  }
  
  @Override
  public void run() {
    Log.i(TAG, "Running StatusMonitor...");
    
    int progress = 0;
    int previous = 0;

    while (mAlive) {
      try {
        progress = mOcr.getProgress();

        if (progress < 0) {
          // A negative value indicates an error
          // TODO This is unusual, just throw an exception
          mAlive = false;
        } else if (progress < 100 && progress != previous) {
          previous = progress;
          
          Message msg = mHandler.obtainMessage(mAction, progress, PROGRESS_MAX);
          msg.sendToTarget();
        }

        Thread.sleep(mInterval);
      } catch (NullPointerException e) {
        mAlive = false;
        Log.e(TAG, e.toString());
      } catch (InterruptedException e) {
        mAlive = false;
        Log.e(TAG, e.toString());
      }
      
      if (!mAlive && mHandler != null) {
        Message msg = mHandler.obtainMessage(mAction, -1, 0);
        msg.sendToTarget();
      }
    }
  }

  public void release() {
    mAlive = false;
    mHandler = null;
  }
}
