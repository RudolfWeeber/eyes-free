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
import android.util.Log;

import com.android.ocr.client.Config;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Provides Java interface to the native implementation of Tesseract.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class OcrLib {
  private static final String TAG = "OcrLib";
  
  private final int SHARD_THRESHOLD = 50;

  /**
   * Used by the native implementation of the class.
   */
  @SuppressWarnings("unused")
  private int mNativeData;
  private static String[] mLanguages;

  static {
    System.loadLibrary("lept");
    System.loadLibrary("ocr");
    classInitNative();
    reloadLanguages();
  }

  private OcrLib() {
    initializeNativeDataNative();
  }

  /**
   * Called by the GC to clean up the native data that we set up when we
   * construct the object.
   */
  @Override
  protected void finalize() throws Throwable {
    Log.i(TAG, "finalize()");
    
    mLib = null;
    
    try {
      cleanupNativeDataNative();
    } finally {
      super.finalize();
    }
  }

  private static OcrLib mLib;

  /**
   * Returns an instance of the Ocr library.
   * @return an instance of the Ocr library
   */
  public static OcrLib getOcrLib() {
    if (mLib == null) {
      mLib = new OcrLib();
    }

    return mLib;
  }

  public static String[] getLanguages() {
    return mLanguages;
  }
  
  public static void reloadLanguages() {
    mLanguages = getLanguagesNative();
    if (mLanguages == null) {
      mLanguages = new String[0];
    } else {
      Arrays.sort(mLanguages);
    }
  }

  /**
   * Set the value of an internal "variable" (of either old or new types).
   * Supply the name of the variable and the value as a string, just as
   * you would in a config file.
   * 
   * Example:
   *   setVariable(VAR_TESSEDIT_CHAR_BLACKLIST, "xyz"); to ignore x, y and z.
   *   setVariable(VAR_BLN_NUMERICMODE, "1"); to set numeric-only mode.
   *
   * setVariable() may be used before open(), but settings will revert to
   * defaults on close().
   * 
   * @param var name of the variable
   * @param value value to set
   * @return false if the name lookup failed
   */
  public boolean setVariable(String var, String value) {
    Log.i(TAG, "setVariable(\"" + var + "\", \"" + value + "\")");
    
    return setVariableNative(var, value);
  }

  /**
   * Start OCR engine. Returns zero on success and -1 on failure.
   * NOTE that the only function that may be called before open() is
   * setVariable()
   * 
   * It is entirely safe (and eventually will be efficient too) to call
   * optn() multiple times on the same instance to change language, or just
   * to reset the classifier.
   * 
   * WARNING: On changing languages, all variables are reset back to their
   * default values.
   * 
   * @param lang the language to load
   * @return true if successful
   */
  public boolean open(String lang) {
    Log.i(TAG, "open(" + lang + ")");
    
    mCurrentShard = 0;
    mLang = lang;
    
    return openInternal(lang);
  }

  /**
   * Sets the image to recognize.
   * 
   * Greyscale of 8 and color of 24 or 32 bits per pixel may be given.
   * Palette color images will not work properly and must be converted to
   * 24 bit.
   * 
   * Binary images of 1 bit per pixel may also be given but they must be
   * byte packed with the MSB of the first byte being the first pixel, and a
   * one pixel is WHITE. For binary images set bpp=1.
   * 
   * @param image byte representation of the image to recognize
   * @param width
   * @param height
   * @param bpp bits per pixel
   * @return true if image set successfully
   */
  public boolean loadPix(byte[] image, int width, int height, int bpp) {
    Log.i(TAG, "loadPix()");
    
    if (mOpenStatus != OPEN_STATUS_OPENED) {
      Log.e(TAG, "Ocr engine is not opened.");
      return false;
    }

    if (mImageStatus == IMAGE_STATUS_SET) {
      Log.e(TAG, "Image has already been set.");
      return false;
    }
    
    int bytes = width * height * bpp;

    if (image == null || image.length < bytes) {
      Log.e(TAG, "Image failed size sanity check.");
      return false;
    }

    loadPixNative(image, width, height, bpp);
    
    mImageStatus = IMAGE_STATUS_LOADED;
    
    return true;
  }
  
  /**
   * Uses Leptonica library to load an image. Currently understands
   * JPEG, PNG, and BMP. Must call nextPix() before recognition.
   * 
   * @param image The bytes representing the image.
   * @return Returns true if the image is successfully set.
   */
  public boolean loadPix(byte[] image) {
    Log.i(TAG, "loadPix()");
    
    if (mOpenStatus != OPEN_STATUS_OPENED) {
      Log.e(TAG, "Ocr engine is not opened.");
      return false;
    }

    if (mImageStatus == IMAGE_STATUS_SET) {
      Log.e(TAG, "Image has already been set.");
      return false;
    }

    if (image == null || image.length < 8) {
      Log.e(TAG, "Image failed size sanity check.");
      return false;
    }

    // mImageStatus may only be INIT or RECOGNIZED. Either way, after we
    // set the image, the mImageStatus becomes INIT.

    loadPixNative(image);
    
    mImageStatus = IMAGE_STATUS_LOADED;

    return true;
  }
  
  public boolean alignText() {
    Log.i(TAG, "alignText()");
    
    if (mImageStatus != IMAGE_STATUS_LOADED) {
      Log.e(TAG, "Image has not been loaded.");
      return false;
    }

    alignTextNative();
    
    return true;
  }
  
  /**
   * Uses Leptonica library to normalize the background of the image. Also
   * converts the image to 8 bpp grayscale.
   * 
   * Notes:
   *   (1) This is a top-level interface for normalizing the image intensity
   *       by mapping the image so that the background is near the input
   *       value 'bgval'.
   *   (2) For each component in the input image, the background value
   *       is estimated using a grayscale closing; hence the 'Morph' in the
   *       function name.
   *   (3) The map is computed at reduced size (given by 'reduction') from
   *       the image. At this scale, the image is closed to remove the
   *       background, using a square Sel of odd dimension. The product of
   *       reduction * size should be large enough to remove most of the
   *       text foreground.
   *   (4) No convolutional smoothing needs to be done on the map before
   *       inverting it.
   *        
   * @param reduction Reduction from source image size at which the background
   *        color map is computer. Should be between 2 and 16.
   * @param size Image closure size. Should be an odd number.
   * @param bgval Desired background brightness. Between 0 and 255, typically
   *        >128. Values close to 255 may result in clipping.
   * @return True if the background was normalized successfully.
   */
  public boolean normalizeBg(int reduction, int size, int bgval) {
    Log.i(TAG, "normalizeBg()");
    
    if (mImageStatus != IMAGE_STATUS_LOADED) {
      Log.e(TAG, "Image has not been loaded.");
      return false;
    }

    normalizeBgNative(reduction, size, bgval);
    
    return true;
  }
  
  /**
   * Sets the Pix image. Only call after loadPix.
   */
  public boolean nextPix() {
    Log.i(TAG, "nextPix()");
    
    if (mImageStatus == IMAGE_STATUS_EMPTY) {
      Log.e(TAG, "Image has not been loaded.");
      return false;
    }
    
    boolean success = nextPixNative();
    
    if (success) {
      mImageStatus = IMAGE_STATUS_SET;
    } else {
      releaseImage();
    }
    
    return success;
  }

  /**
   * Close down OCR engine and free up all memory. Once close() has been
   * called, none of the other API functions may be used other than open()
   * and anything declared above it in the class definition.
   */
  public void close() {
    Log.i(TAG, "close()");
    
    closeInternal();
    mLang = null;
  }

  /**
   * Returns the number of shards associated with a language. Shards are used
   * to break large language files (ex. chi_sim, jpn) into small chunks that
   * will fit in memory.
   * 
   * @param lang The language code.
   * @return The number of shards associated with the language.
   */
  private static int getShards(String lang) {
    return getShardsNative(lang);
  }

  /**
   * Initialized the native OCR engine for a specific language.
   * 
   * @param lang The language to load.
   * @return Returns true on success.
   */
  private boolean openInternal(String lang) {
    boolean res = false;
    
    switch (mOpenStatus) {
      case OPEN_STATUS_INIT:
        Log.i(TAG, "Initializing Ocr engine for " + lang);
        mShards = getShards(lang);
        if (mShards >= 0) {
          if (mShards == 0) {
            res = openNative(lang);
          } else {
            res = openNative(lang + mCurrentShard);
          }
          if (res) {
            mLang = lang;
            mOpenStatus = OPEN_STATUS_OPENED;
          }
        } else
          Log.e(TAG, "Invalid or unknown language " + lang);
        break;
      case OPEN_STATUS_OPENED:
        Log.e(TAG, "Ocr engine is alredy initialized for " + mLang);
        break;
      default:
        break;
    }

    return res;
  }

  /**
   * Closes the native OCR engine.
   */
  private void closeInternal() {
    if (mOpenStatus == OPEN_STATUS_OPENED) {
      Log.i(TAG, "shutting down Ocr engine");
      closeNative();
      mOpenStatus = OPEN_STATUS_INIT;
    }
  }

  /**
   * Loads a specific language shard.
   * 
   * @param shard The number of the shard to load.
   * @return Returns true on success.
   */
  private boolean setShard(int shard) {
    // TODO: Restore variables
    Log.i(TAG, "language " + mLang + ": switching to shard " + shard);
    if (openNative(mLang + shard)) {
      if (mImageStatus != IMAGE_STATUS_EMPTY) {
        Log.i(TAG, "setting image again");
        //releaseImageNative();
        //setImageNative(mImage, mImageWidth, mImageHeight, mBpp);
        Log.i(TAG, "setting rectangle again");
        //setRectangleNative(mRectangleLeft, mRectangleTop, mRectangleWidth, mRectangleHeight);
        setPageSegMode(mPageSegMode);
      }
      return true;
    }
    return false;
  }

  /**
   * Loads the next shard.
   * 
   * @return Returns true on success.
   */
  private boolean getNextShard() {
    if (mOpenStatus == OPEN_STATUS_OPENED) {
      if (mCurrentShard < mShards - 1) {
        return setShard(++mCurrentShard);
      } else
        Log.i(TAG, "no more shards for language " + mLang);
    } else
      Log.e(TAG, "cannot get next shard, no language is selected.");
    return false;
  }

  /**
   * Resets the shard counter.
   */
  private void resetShard() {
    if (mCurrentShard > 0) {
      mCurrentShard = 0;
      setShard(mCurrentShard);
    }
  }
 
  public ArrayList<Rect> detectText() {
    Log.i(TAG, "detectText()");
    
    if (mImageStatus != IMAGE_STATUS_LOADED) {
      Log.e(TAG, "Image has not been loaded.");
      return null;
    }

    int[] coords = detectTextNative();
    ArrayList<Rect> boxes = new ArrayList<Rect>(coords.length / 4);
    
    int t, l, b, r;
    for (int i = 0; i < coords.length; i += 4) {
      t = coords[i];
      l = coords[i + 1];
      b = t + coords[i + 2];
      r = l + coords[i + 3];
      boxes.add(new Rect(t, l, b, r));
    }
    
    return boxes;
  }
  
  /**
   * Free memory used by the native library to store the image.
   */
  public void releaseImage() {
    if (mImageStatus != IMAGE_STATUS_EMPTY) {
      Log.i(TAG, "Releasing image");
      releaseImageNative();
      mImageStatus = IMAGE_STATUS_EMPTY;
    } else {
      Log.i(TAG, "Image was already released");
    }
  }
  
  /**
   * Free up recognition results and any stored image data, without actually
   * freeing any recognition data that would be time-consuming to reload.
   * Afterwards, you must call setImage before doing a recognize() operation.
   * 
   * Call between pages or documents etc to free up memory and forget
   * adaptive data.
   */
  public void clearResults() {
    clearResultsNative();
  }

  /**
   * Restrict recognition to a sub-rectangle of the image. Call after setImage.
   * Each setRectangle clears the recogntion results so multiple rectangles
   * can be recognized with the same image.
   * @param left
   * @param top
   * @param width
   * @param height
   * @return True if the rectangle was set.
   */
  public boolean setRectangle(int left, int top, int width, int height) {
    if (mOpenStatus != OPEN_STATUS_OPENED) {
      Log.e(TAG, "Ocr engine is not opened.");
      return false;
    }

    if (mImageStatus == IMAGE_STATUS_EMPTY) {
      Log.e(TAG, "Image has not been set.");
      return false;
    }

    Log.i(TAG, "Setting rectangle");
    setRectangleNative(left, top, width, height);
    
    return true;
  }

  /**
   * Runs text recognition on the image. Automatically handles sharded
   * languages.
   * 
   * @returns The OCR result as a string.
   */
  public String recognize() {
    Log.i(TAG, "recognize()");
    
    if (mOpenStatus != OPEN_STATUS_OPENED) {
      Log.e(TAG, "Ocr engine is not opened.");
      return null;
    }

    if (mImageStatus == IMAGE_STATUS_EMPTY ||
        mImageStatus == IMAGE_STATUS_LOADED) {
      Log.e(TAG, "Image has not been set.");
      return null;
    }
    
    mImageStatus = IMAGE_STATUS_RECOGNIZED;
    
    String text = null;
    
    if (mShards > 0) {
      int conf = -1;
      
      String curStr;
      int curConf;

      do {
        curStr = recognizeNative();
        curConf = meanConfidenceNative();
        
        if(curConf > conf) {
          conf = curConf;
          text = curStr;
        }
        
        if (curConf > SHARD_THRESHOLD
            && curStr.length() > 0) {
          break;
        }
      } while (getNextShard());
      
      resetShard();
    } else {
      text = recognizeNative();
    }
    
    // We shouldn't return null text...
    if (text == null) {
      text = "";
    }
    
    Log.i(TAG, "result:\n" + text);
    
    return text;
  }
  
  /**
   * Returns the OCR progress from 0 to 100.
   * @return The OCR progress from 0 to 100.
   */
  public int getProgress() {
    return getProgressNative();
  }
  
  /**
   * Stops the current OCR job. If stop() is called while recognize()
   * is running, recognize() will return an empty string. 
   */
  public void stop() {
    Log.i(TAG, "stop()");
    
    stopNative();
  }

  /**
   * Returns the mean confidence of text recognition.
   *  
   * @return The mean confidence.
   */
  public int meanConfidence() {
    if (mOpenStatus != OPEN_STATUS_OPENED) {
      Log.e(TAG, "Ocr engine is not opened.");
      return -1;
    }

    if (mImageStatus != IMAGE_STATUS_RECOGNIZED) {
      Log.e(TAG, "Image has not been OCRed!");
      return -1;
    }

    Log.i(TAG, "mean confidence");
    return meanConfidenceNative();
  }

  /**
   * Returns an array of confidence values, one for each space-delimited word
   * in the recognition output.
   * 
   * @return An array of confidence values.
   */
  public int[] wordConfidences() {
    if (mOpenStatus != OPEN_STATUS_OPENED) {
      Log.e(TAG, "Ocr engine is not opened.");
      return null;
    }

    if (mImageStatus != IMAGE_STATUS_RECOGNIZED) {
      Log.e(TAG, "Image has not been OCRed!");
      return null;
    }

    Log.i(TAG, "word confidences");
    
    return new int[0];
    /*
    int[] conf = wordConfidencesNative();
    
    // We shouldn't return null confidences...
    if (conf == null) {
      conf = new int[0];
    }
    
    return conf; 
    */
  }

  /**
   * Sets the page segmentation mode. This controls how much processing the
   * OCR engine will perform before recognizing text.
   * 
   * @param mode The page segmentation mode to set. See PSM_ in Config. 
   * @return Returns true on success.
   */
  public boolean setPageSegMode(int mode) {
    if (mode >= Config.PSM_NUM) {
      Log.e(TAG, "Invalid page-segmentation mode");
      return false;
    }

    Log.e(TAG, "Setting page-segmentation mode to " + mode);
    setPageSegModeNative(mode);
    mPageSegMode = mode;
    return true;
  }
  
  /**
   * Sets the language to recognize. Fails if the language is not supported.
   * 
   * @param lang The ISO 639-2 code of the language to recognize.
   * @return Returns true on success.
   */
  public boolean setLanguage(String lang) {
    if (lang.equals(mLang)) {
      Log.i(TAG, "Language already set");
      return true;
    }
    
    boolean containsLanguage = false;
    for (int i = 0; !containsLanguage && i < mLanguages.length; i++) {
      containsLanguage = mLanguages[i].equals(lang);
    }
    
    if (!containsLanguage) {
      return false;
    }
    
    Log.e(TAG, "Setting language to " + lang);
    openNative(lang);
    mLang = lang;
    return true;
  }
  
  public void setDebug(boolean debug) {
    Log.i(TAG, "setDebug(" + debug + ")");
    
    setDebugNative(debug);
  }

  /* member variables */

  private static final int OPEN_STATUS_INIT = 0;
  private static final int OPEN_STATUS_OPENED = 1;

  private static final int IMAGE_STATUS_EMPTY = 0;
  private static final int IMAGE_STATUS_LOADED = 1;
  private static final int IMAGE_STATUS_SET = 2;
  private static final int IMAGE_STATUS_RECOGNIZED = 3;

  private int mOpenStatus = OPEN_STATUS_INIT;
  
  // All subsequent member variables are valid only when mOpenStatus !=
  // OPEN_STATUS_INIT.
  
  private int mImageStatus = IMAGE_STATUS_EMPTY;
  
  // The next two member variables are valid only when mImageStatus !=
  // IMAGE_STATUS_EMPTY.

  private String mLang;
  private int mCurrentShard;
  private int mShards;

  private int mPageSegMode;

  /* Native methods */

  private native static void classInitNative();

  private native void initializeNativeDataNative();

  private native void cleanupNativeDataNative();

  private native boolean openNative(String lang);
  
  /* Leptonica-dependent */
  
  private native void loadPixNative(byte[] image, int width, int height, int bpp);
  
  private native void loadPixNative(byte[] image);
  
  private native int[] detectTextNative();
  
  private native void alignTextNative();
  
  private native void normalizeBgNative(int reduction, int size, int bgval);
  
  private native boolean nextPixNative();
  
  /* End Leptonica-dependent */

  private native void releaseImageNative();

  private native void setRectangleNative(int left, int top, int width, int height);

  private native String recognizeNative();
  
  private native int getProgressNative();
  
  private native void stopNative();

  private native void clearResultsNative();

  private native void closeNative();

  private native int meanConfidenceNative();

  @SuppressWarnings("unused")
  private native int[] wordConfidencesNative();
  
  private native void setDebugNative(boolean debug);

  private native boolean setVariableNative(String var, String value);

  private native void setPageSegModeNative(int mode);

  private static native String[] getLanguagesNative();

  private static native int getShardsNative(String lang);
}
