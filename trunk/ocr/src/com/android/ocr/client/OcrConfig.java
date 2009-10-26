/*
 * Copyright (C) 2008 Google Inc.
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

import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class represents the configuration data for an OCR job. At the very
 * least, it needs the following fields set: 1) If the image is JPEG encoded: -
 * rawImage = jpeg image - isRawEncoded = true 2) If the image is not encoded -
 * rawImage = image image - width = width of the image - height = height of the
 * image - pixelBytes = image per pixel
 * 
 * The options field contains processing options as OR'ed values. For example,
 * to normalize the image background, set options = options | OPT_NORMALIZE_BG.
 * 
 * The variables field contains a map of OCR engine variables. For example, to
 * recognize only digits, set variables.put(VAR_CHAR_WHITELIST, "0123456789").
 * 
 * The pageSegMode determines how much page segmentation the OCR engine will
 * attempt to perform. For example, for a full magazine page, set pageSegMode =
 * PSM_AUTO. For a single paragraph, set pageSegMode = PSM_BLOCK.
 * 
 * The language field tells the OCR engine what character set and dictionary to
 * use. It uses ISO 639-2 languages codes. If left null, the OCR engine will
 * default to the user's preference. For example, to recognize Russian, set
 * language = "rus".
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class OcrConfig implements Parcelable {
  private static final String TAG = "OcrConfig";
  
  public static final int STATE_BUSY = 0;
  public static final int STATE_COMPLETE = 1;

  public static final int STATUS_LOADING = 0;
  public static final int STATUS_PROCESSING = 1;
  public static final int STATUS_RECOGNIZING = 2;
  public static final int STATUS_COMPLETED = 3;

  // Whitelist of characters to recognize
  public static final String VAR_CHAR_WHITELIST = "tessedit_char_whitelist";
  // Blacklist of characters to not recognize
  public static final String VAR_CHAR_BLACKLIST = "tessedit_char_blacklist";
  // Accuracy versus speed setting
  public static final String VAR_ACCURACYVSPEED = "tessedit_accuracyvspeed";

  // Default accuracy versus speed mode
  public static final int AVS_FASTEST = 0;
  // Slowest and most accurate mode
  public static final int AVS_MOST_ACCURATE = 100;

  // Normalize background to eliminate shadows
  public static final int OPT_NORMALIZE_BG = 1;
  // Detect text in image using TextDetect
  public static final int OPT_DETECT_TEXT = 2;
  // Aligns horizontal text in an image
  public static final int OPT_ALIGN_TEXT = 4;

  // Fully automatic page segmentation. (Default.)
  public static final int PSM_AUTO = 0;
  // Assume a single column of text of variable sizes.
  public static final int PSM_SINGLE_COLUMN = 1;
  // Assume a single uniform block of text.
  public static final int PSM_SINGLE_BLOCK = 2;
  // Treat the image as a single text line.
  public static final int PSM_SINGLE_LINE = 3;
  // Treat the image as a single word.
  public static final int PSM_SINGLE_WORD = 4;
  // Treat the image as a single character.
  public static final int PSM_SINGLE_CHAR = 5;
  // Treat the image as a single character.
  public static final int PSM_NUM = 6;
  
  public static final int FORMAT_RAW = 0;
  public static final int FORMAT_JPEG = 1;
  public static final int FORMAT_PNG = 2;
  public static final int FORMAT_BMP = 3;

  public byte[] image;
  public int width;
  public int height;
  public int bpp;
  public int format;
  public long options;
  public ArrayList<Rect> bounds;
  public HashMap<String, String> variables;
  public String language;
  public int pageSegMode;
  public boolean debug;

  public OcrConfig() {
    image = new byte[0];
    bounds = new ArrayList<Rect>();
    variables = new HashMap<String, String>();
    language = null;
    pageSegMode = PSM_AUTO;
    debug = false;
  }

  private String writeToDisk() {
    Log.i(TAG, "Image too big, writing to disk...");
    
    File sdcard = new File(OcrConfig.SD_CARD);
    
    if (!sdcard.isDirectory()) {
      Log.e(TAG, "Could not access SD card!");
      
      return null;
    }

    try {
      File temp = File.createTempFile(OCR_PREFIX, OCR_SUFFIX, sdcard);
      FileOutputStream stream = new FileOutputStream(temp);
      stream.write(image);
      stream.close();
      
      String file = temp.getAbsolutePath();
      
      Log.i(TAG, "Wrote to disk: " + file);
      
      return file;
    } catch(IOException e) {
      Log.e(TAG, e.toString());
      
      return null;
    }
  }
  
  private boolean readFromDisk(String file) {
    Log.i(TAG, "Image too big, reading from disk: " + file);
    
    if (file == null) return false;

    try {
      File temp = new File(file);
      FileInputStream stream = new FileInputStream(temp);
      
      image = new byte[(int) temp.length()];
      stream.read(image);
      stream.close();
      
      temp.delete();
      
      Log.i(TAG, "Read from disk.");
      
      return true;
    } catch(IOException e) {
      return false;
    }
  }
  
  private static final String SD_CARD = "/sdcard/";
  private static final String OCR_PREFIX = "ocr_";
  private static final String OCR_SUFFIX = ".tmp";
  private static final int DISK_THRESHOLD = 1000000;


  /************************
   * Parcelable functions *
   ************************/

  private OcrConfig(Parcel src) {
    readFromParcel(src);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    Log.i(TAG, "Writing to Parcel...");
    
    dest.writeInt(image.length);
    
    if (image.length > DISK_THRESHOLD) {
      String file = writeToDisk();
      dest.writeString(file);
    } else {
      dest.writeByteArray(image);
    }

    dest.writeInt(width);
    dest.writeInt(height);
    dest.writeInt(bpp);
    dest.writeInt(format);
    dest.writeLong(options);

    dest.writeTypedList(bounds);
    dest.writeMap(variables);

    dest.writeString(language);
    dest.writeInt(pageSegMode);

    dest.writeByte((byte) (debug ? 1 : 0));
  }

  private void readFromParcel(Parcel src) {
    Log.i(TAG, "Reading from Parcel..." + src);
    
    int length = src.readInt();
    
    if (length > DISK_THRESHOLD) {
      String file = src.readString();
      readFromDisk(file);
    } else {
      image = src.createByteArray();
    }

    width = src.readInt();
    height = src.readInt();
    bpp = src.readInt();
    format = src.readInt();
    options = src.readLong();

    bounds = new ArrayList<Rect>();
    src.readTypedList(bounds, Rect.CREATOR);

    variables = new HashMap<String, String>();
    src.readMap(variables, HashMap.class.getClassLoader());

    language = src.readString();
    pageSegMode = src.readInt();

    debug = (src.readByte() == 1);

    src.recycle();
  }

  public static final Parcelable.Creator<OcrConfig> CREATOR = new Parcelable.Creator<OcrConfig>() {
    public OcrConfig createFromParcel(Parcel in) {
      return new OcrConfig(in);
    }

    public OcrConfig[] newArray(int size) {
      return new OcrConfig[size];
    }
  };
}
