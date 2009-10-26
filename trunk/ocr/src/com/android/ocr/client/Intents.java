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

/**
 * This class enumerates the Intents made available to application developers
 * through the OCR Service.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public final class Intents {
  private Intents() {

  }

  public static final class Service {
    /**
     * Use this to bind to the OCR service. Typically this will only be used by
     * the Ocr object.
     */
    public static final String ACTION = "com.android.ocr.intent.SERVICE";

    private Service() {

    }
  }

  public static final class Languages {
    /**
     * Use this to bind to the OCR service. Typically this will only be used by
     * the Ocr object.
     */
    public static final String ACTION = "com.android.ocr.intent.LANGUAGES";

    private Languages() {

    }
  }

  public static final class Capture {
    /**
     * Send this intent to open the image capture screen and receive an OCR
     * configuration object in return.
     */
    public static final String ACTION = "com.android.ocr.intent.CAPTURE";

    /**
     * The desired picture width as an integer. Use Intent.putExtra(WIDTH,
     * width) where width is the desired picture width. If set, you must also
     * set the picture height.
     */
    public static final String WIDTH = "WIDTH";

    /**
     * The desired picture height as an integer. Use Intent.putExtra(HEIGHT,
     * height) where height is the desired picture height. If set, you must also
     * set the picture width.
     */
    public static final String HEIGHT = "HEIGHT";

    /**
     * The configuration output as a Config object. The capture activity will
     * automatically fill the image, width, height, and format fields. These
     * will not necessarily match the desired width and heights.
     */
    public static final String CONFIG = "CONFIG";

    private Capture() {

    }
  }

  public static final class Recognize {
    /**
     * Send this intent to process an OCR configuration object and receive OCR
     * results in return.
     */
    public static final String ACTION = "com.android.ocr.intent.RECOGNIZE";

    /**
     * The configuration input as a Config object. For raw image data, you must
     * at least set image, format (as RAW), width, height, and bpp. For JPEG
     * data from the Camera, you must at least set image, format (as JPEG),
     * width, and height.
     */
    public static final String CONFIG = "CONFIG";
    
    /**
     * The results of recognition as an array of Result objects.
     */
    public static final String RESULTS = "RESULTS";

    private Recognize() {

    }
  }
}
