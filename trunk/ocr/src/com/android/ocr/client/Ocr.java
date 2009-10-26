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


import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * Recognizes text in images. This abstracts away the complexities of using the
 * OCR service such as setting up the IBinder connection and handling
 * RemoteExceptions, etc.
 * 
 * Specifically, this class initializes the OCR service and pushes recognization
 * requests across IPC for processing in the service thread.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class Ocr {
  // This is the minimum version of the Ocr service that is needed by this
  // version of the library stub.
  private final static int MIN_VER = 1;
  private final static String TAG = "Ocr";

  public static final int STATUS_SUCCESS = 0;
  public static final int STATUS_FAILURE = 1;
  public static final int STATUS_MISSING = 2;

  /**
   * Handles the callback when the Ocr service has initialized.
   */
  public static interface InitCallback {
    public abstract void onInitialized(int status);
  }

  /**
   * Handles the callback for when recognition is completed.
   */
  public static interface CompletionCallback {
    public abstract void onCompleted(Result[] results);
  }

  /**
   * Handles the callback for a single mid-recognition result.
   */
  public static interface ResultCallback {
    public abstract void onResult(Result result);
  }

  private int mVersion = -1;

  private IOcr mIOcr;
  private ServiceConnection mServiceConnection;
  private Activity mContext;
  private Language[] mSupportedLang;

  /**
   * The constructor for the OCR service client. Initializes the service if
   * necessary and calls initListener when it's ready.
   * 
   * @param context The context
   */
  public Ocr(Activity context, InitCallback init) {
    mContext = context;

    connectOcrService(init);
  }

  @Override
  protected void finalize() {
    release();
  }

  /**
   * Enqueues a recognition job to the OCR service.
   * 
   * @param config The OCR configuration parameters
   * @param onResult callback with individual result data, may be null
   * @param onCompleted callback after ocr is completed
   * 
   * @return whether the job queued successfully
   */
  public boolean recognizeText(Config config, final ResultCallback onResult,
      final CompletionCallback onCompleted) {
    if (mIOcr == null) {
      Log.e(TAG, "Attempted to enqueue job with null interface.");
      return false;
    }

    final IOcrCallback callback = new IOcrCallback.Stub() {
      @Override
      public void onCompleted(Result[] results) {
        Log.i(TAG, "onCompleted()");
        if (onCompleted != null) {
          onCompleted.onCompleted(results);
        }
      }

      @Override
      public void onResult(Result result) {
        Log.i(TAG, "onResult()");
        if (onResult != null) {
          onResult.onResult(result);
        }
      }
    };

    Log.i(TAG, "Enqueuing job...");

    try {
      mIOcr.enqueueJob(config, callback);
    } catch (DeadObjectException e) {
      Log.e(TAG, "Exception caught in recognizeText(): " + e.toString());
      return false;
    } catch (RemoteException e) {
      Log.e(TAG, "Exception caught in recognizeText(): " + e.toString());
      return false;
    }

    Log.e(TAG, "Successfully enqueued job.");

    return true;
  }

  /**
   * @return current recognition progress between 0 and 100
   */
  public int getProgress() {
    if (mIOcr == null) {
      Log.e(TAG, "Attempted to call getProgress() without a connection to Ocr service.");
      return -1;
    }

    int progress = 0;

    try {
      progress = mIOcr.getProgress();
    } catch (DeadObjectException e) {
      Log.e(TAG, "Exception caught in getProgress(): " + e.toString());
      progress = -1;
    } catch (RemoteException e) {
      Log.e(TAG, "Exception caught in getProgress(): " + e.toString());
      progress = -1;
    }

    return progress;
  }

  /**
   * Disconnects from the Ocr service.
   * <p>
   * It is recommended that you call this as soon as you're done with the Ocr
   * object.
   * </p>
   */
  public void release() {
    try {
      mContext.unbindService(mServiceConnection);
    } catch (IllegalArgumentException e) {
      // Do nothing and fail silently since an error here indicates that
      // binding never succeeded in the first place.
    }
    mIOcr = null;
  }

  private void connectOcrService(final InitCallback init) {
    Log.i(TAG, "Connecting to OCR service...");

    // Initialize the OCR service, run the callback after the binding is
    // successful
    mServiceConnection = new ServiceConnection() {
      public void onServiceConnected(ComponentName name, IBinder service) {
        Log.i(TAG, "onServiceConnected()");

        mIOcr = IOcr.Stub.asInterface(service);

        try {
          mVersion = mIOcr.getVersion();

          // The Ocr service must be at least the min version needed by the
          // library stub. Do not try to run the older Ocr with the newer
          // library stub as the newer library may reference methods which
          // are unavailable and cause a crash.

          if (mVersion < MIN_VER) {
            Log.e(TAG, "OCR service too old (version " + mVersion + " < " + MIN_VER + ")");

            VersionAlert.createUpdateAlert(mContext, null).show();

            if (init != null) {
              init.onInitialized(STATUS_MISSING);
            }

            return;
          }

          mSupportedLang = mIOcr.getLanguages();

          if (mSupportedLang == null || mSupportedLang.length == 0) {
            OnClickListener onClick = new OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                if (init != null) {
                  init.onInitialized(STATUS_MISSING);
                }
              }
            };
            
            VersionAlert.createLanguagesAlert(mContext, onClick, onClick, -1).show();

            return;
          }

        } catch (RemoteException e) {
          Log.e(TAG, "Exception caught in onServiceConnected(): " + e.toString());

          if (init != null) {
            init.onInitialized(STATUS_FAILURE);
          }

          return;
        }

        Log.i(TAG, "Connected to OCR service.");

        if (init != null) {
          init.onInitialized(STATUS_SUCCESS);
        }
      }

      public void onServiceDisconnected(ComponentName name) {
        Log.e(TAG, "Disconnected from OCR service.");

        mIOcr = null;
      }
    };

    Intent intent = new Intent(Intents.Service.ACTION);
    intent.addCategory(Intent.CATEGORY_DEFAULT);

    // Binding will fail only if the Ocr doesn't exist;
    // the OcrVersionAlert will give users a chance to install
    // the needed Ocr.

    if (!mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
      Log.e(TAG, "Cannot bind to OCR service, assuming not installed.");

      VersionAlert.createInstallAlert(mContext, null).show();

      if (init != null) {
        init.onInitialized(STATUS_MISSING);
      }
    }
  }

  /**
   * Returns the array of languages supported by the OCR service.
   * 
   * @return The array of supported languages.
   */
  public Language[] getLanguages() {
    return mSupportedLang;
  }
  
  public void reloadLanguages() {
    if (mIOcr == null) {
      Log.e(TAG, "Attempted to call reloadLanguages() without a connection to Ocr service.");
      return;
    }
    
    try {
      mIOcr.reloadLanguages();
      mSupportedLang = mIOcr.getLanguages();
    } catch (DeadObjectException e) {
      Log.e(TAG, "Exception caught in reloadLanguages(): " + e.toString());
    } catch (RemoteException e) {
      Log.e(TAG, "Exception caught in reloadLanguages(): " + e.toString());
    }
  }

  /**
   * Cancels the active Ocr.
   */
  public void stop() {
    if (mIOcr == null) {
      Log.e(TAG, "Attempted to call stop() without a connection to Ocr service.");
      return;
    }

    try {
      mIOcr.cancel();
    } catch (DeadObjectException e) {
      Log.e(TAG, "Exception caught in stop(): " + e.toString());
    } catch (RemoteException e) {
      Log.e(TAG, "Exception caught in stop(): " + e.toString());
    }
  }

  /**
   * Returns the version number of the Ocr library that the user has installed.
   * 
   * @return The version number of the Ocr library that the user has installed.
   */
  public int getVersion() {
    return mVersion;
  }

  /**
   * Checks if the Ocr service is installed or not
   * 
   * @return A boolean that indicates whether the Ocr service is installed
   */
  public static boolean isInstalled(Context ctx) {
    PackageManager pm = ctx.getPackageManager();

    Intent intent = new Intent("com.android.ocr.SERVICE");
    intent.addCategory(Intent.CATEGORY_DEFAULT);

    ResolveInfo info = pm.resolveService(intent, 0);

    if (info == null) {
      return false;
    } else {
      return true;
    }
  }
}
