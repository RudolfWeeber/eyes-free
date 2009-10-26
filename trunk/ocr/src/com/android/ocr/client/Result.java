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

import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * This class represents the result of text recognition. It includes the text
 * itself as well as the word confidence values.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class Result implements Parcelable {
  private Rect mBounds;
  private String mString;
  private int[] mConfidences;

  /**
   * Creates an OCR result.
   * 
   * @param result The result of text recognition.
   * @param confidences The array of word confidences.
   */
  public Result(Rect bounds, String result, int[] confidences) {
    mBounds = bounds;
    mString = result;
    mConfidences = confidences;
  }

  /**
   * Returns the bounding box containing the text result as a Rect.
   * 
   * @return The bounding box containing the text result.
   */
  public Rect getBounds() {
    return mBounds;
  }

  /**
   * Returns the result of text recognition as a String.
   * 
   * @return The result of text recognition.
   */
  public String getString() {
    return mString;
  }

  /**
   * Returns the array of word confidences. Each entry corresponds to a single
   * space-delimited word in the result string.
   * 
   * @return The array of word confidences.
   */
  public int[] getConfidences() {
    return mConfidences;
  }

  /**************************
   * Serializable functions *
   **************************/
/*
  private void writeObject(ObjectOutputStream dest) throws IOException {
    dest.writeObject(mString);
    dest.writeObject(mConfidences);
    dest.writeObject(mBounds);
  }

  private void readObject(ObjectInputStream src) throws IOException, ClassNotFoundException {
    mString = (String)src.readObject();
    mConfidences = (int[])src.readObject();
    mBounds = (Rect)src.readObject();
  }
*/
  /************************
   * Parcelable functions *
   ************************/

  private Result(Parcel src) {
    readFromParcel(src);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(mString);
    dest.writeIntArray(mConfidences);
    dest.writeParcelable(mBounds, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
  }

  private void readFromParcel(Parcel src) {
    mString = src.readString();
    mConfidences = src.createIntArray();
    mBounds = src.readParcelable(null);
  }

  public static final Parcelable.Creator<Result> CREATOR = new Parcelable.Creator<Result>() {
    public Result createFromParcel(Parcel in) {
      return new Result(in);
    }

    public Result[] newArray(int size) {
      return new Result[size];
    }
  };

}
