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

import android.graphics.Rect;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.ocr.client.IOcrCallback;
import com.android.ocr.client.Config;
import com.android.ocr.client.Result;

import java.util.Set;
import java.util.Map.Entry;

/**
 * Represents a single recognition job that can be cancelled. Contains methods
 * that interact with the OCR library to produce a recognition result.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class Job {
  private final static String TAG = "Job";

  public int jobId;
  public Config config;
  public IOcrCallback callback;
  public IBinder.DeathRecipient deathcall;

  public boolean alive;
  public boolean canceled;

  public Job(int jobId, Config config, IOcrCallback callback) {
    this.jobId = jobId;
    this.config = config;
    this.callback = callback;

    alive = false;
    canceled = false;
  }

  public void run(OcrLib ocr) {
    if (canceled) {
      return;
    }

    alive = true;

    prepareOcrLibrary(ocr);

    Result[] results = recognizeBitmap(ocr);

    cleanupOcrLibrary(ocr);

    onCompleted(results);

    alive = false;
  }

  public void cancel(OcrLib ocr) {
    if (canceled) {
      Log.e(TAG, "Attempted to cancel dead job " + jobId);
    }

    canceled = true;
    ocr.stop();
  }

  public void cancelImmediate() {
    if (canceled) {
      Log.e(TAG, "Attempted to immediate cancel dead job " + jobId);
    }

    canceled = true;
    onCompleted(null);
  }

  /**
   * Opens the configured language and sets variables.
   */
  private void prepareOcrLibrary(OcrLib ocr) {
    ocr.open(config.language);
    ocr.setDebug(config.debug);

    if (config.variables != null) {
      Set<Entry<String, String>> entries = config.variables.entrySet();
      for (Entry<String, String> entry : entries) {
        ocr.setVariable(entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Loads the image, runs any processing options, and runs OCR on all bounding
   * rectangles. Finally, releases the image.
   */
  private Result[] recognizeBitmap(OcrLib ocr) {
    if (canceled) return null;

    boolean detected = false;

    boolean normalize = ((config.options & Config.OPT_NORMALIZE_BG) != 0);
    boolean align = ((config.options & Config.OPT_ALIGN_TEXT) != 0);
    boolean detect = ((config.options & Config.OPT_DETECT_TEXT) != 0);
    boolean detect_only = ((config.options & Config.OPT_DETECT_TEXT_ONLY) != 0);

    if (config.format == Config.FORMAT_RAW) {
      ocr.loadPix(config.image, config.width, config.height, config.bpp);
    } else {
      ocr.loadPix(config.image);
    }

    ocr.setPageSegMode(config.pageSegMode);

    // If the client doesn't provide bounds, a null bound will recognize
    // the entire image.
    if (config.bounds.isEmpty()) {
      Rect bound = new Rect(0, 0, config.width, config.height);
      config.bounds.add(bound);
    }

    if (canceled) {
      return null;
    }

    // Background normalization runs first, since it won't make any
    // difference if it runs after text detection. Coincidentally,
    // it will probably mess up text detection if it runs first.
    if (normalize) {
      ocr.normalizeBg(8, 3, 200);
    }

    if (canceled) {
      return null;
    }

    // Text detection overrides manual bounding box and page
    // segmentation parameters.
    // TODO: Figure out a good way to still allow manual box selection.
    if (detect || detect_only) {
      config.bounds = ocr.detectText();
      config.pageSegMode = Config.PSM_SINGLE_LINE;
      detected = true;

      if (detect_only) {
        Log.i(TAG, "Returning bounding boxes only");
        Result[] bounds = new Result[config.bounds.size()];
        for (int i = 0; i < config.bounds.size(); i++) {
          bounds[i] = new Result(config.bounds.get(i), null, null);
        }
        return bounds;
      }
    }

    if (canceled) {
      return null;
    }

    // Text alignment can align detected text, so it runs after text
    // detection. Really, though, it has little impact on results.
    if (align) {
      ocr.alignText();
    }

    if (canceled) {
      return null;
    }

    if (!detected) {
      ocr.nextPix();
    }

    Result[] results = new Result[config.bounds.size()];

    int i;
    for (i = 0; !canceled && i < results.length; i++) {
      results[i] = recognizeRect(ocr, config.bounds.get(i), detected);
      onResult(results[i]);
    }

    // If processing was canceled during rectangle recognition, we might
    // need to shrink the size of the results array.
    // TODO: Alternatively, maybe we should just return null results.
    if (canceled && i < results.length) {
      Result[] temp = new Result[i];
      System.arraycopy(results, 0, temp, 0, i);
      results = temp;
    }

    return results;
  }

  /**
   * Runs OCR on a single bounding rectangle.
   * 
   * @param bound The bounding rectangle to recognize.
   * @return The result of OCR.
   */
  private Result recognizeRect(OcrLib ocr, Rect bound, boolean detected) {
    if (detected) {
      ocr.nextPix();
    } else if (bound != null) {
      ocr.setRectangle(bound.left, bound.top, bound.width(), bound.height());
    }

    String string = ocr.recognize();
    int[] confidences = ocr.wordConfidences();
    Result result = new Result(bound, string, confidences);

    return result;
  }

  private void cleanupOcrLibrary(OcrLib ocr) {
    ocr.releaseImage();
    ocr.clearResults();
    ocr.close();
  }

  private void onResult(Result result) {
    if (callback == null) {
      return;
    }

    try {
      callback.onResult(result);
    } catch (RemoteException e) {
      Log.e(TAG, "Exception caught in onResult(): " + e.toString());
    }
  }

  private void onCompleted(Result[] results) {
    if (callback == null) {
      return;
    }

    try {
      callback.onCompleted(results);
      callback.asBinder().unlinkToDeath(deathcall, 0);
      callback = null;
    } catch (RemoteException e) {
      Log.e(TAG, "Exception caught in onComplete(): " + e.toString());
    }
  }

  public boolean equals(Job other) {
    if (other == null) {
      return false;
    }

    if (jobId == other.jobId) {
      return true;
    } else {
      return false;
    }
  }
}
