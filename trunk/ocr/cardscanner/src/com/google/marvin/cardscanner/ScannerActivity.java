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
package com.google.marvin.cardscanner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.Contacts.ContactMethodsColumns;
import android.provider.Contacts.People;
import android.provider.Contacts.PhonesColumns;
import android.provider.Contacts.Intents.Insert;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import com.android.ocr.client.Config;
import com.android.ocr.client.Intents;
import com.android.ocr.client.Result;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Demonstration application for using Intents. Can load text from the camera
 * and copy it to the clipboard.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class ScannerActivity extends Activity {
  private static final String TAG = "ScannerActivity";

  private static final int REQUEST_CAPTURE = 0;
  private static final int REQUEST_RECOGNIZE = 1;
  private static final int REQUEST_REVIEW = 2;

  private static final int DEFAULT_WIDTH = 1024;
  private static final int DEFAULT_HEIGHT = 768;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "Creating ScannerActivity...");

    super.onCreate(savedInstanceState);

    requestCapture();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK: {
        requestCapture();
        return true;
      }
    }

    return super.onKeyDown(keyCode, event);
  }

  public void requestCapture() {
    Intent capture = new Intent(Intents.Capture.ACTION);
    capture.putExtra(Intents.Capture.WIDTH, DEFAULT_WIDTH);
    capture.putExtra(Intents.Capture.HEIGHT, DEFAULT_HEIGHT);

    startActivityForResult(capture, REQUEST_CAPTURE);
  }

  public void requestRecognize(Intent data) {
    Parcelable extra = data.getParcelableExtra(Intents.Capture.CONFIG);

    if (!(extra instanceof Config)) {
      Log.e(TAG, "requestRecognize received wrong parcelable type (was " + extra + ")");
      return;
    }

    Config config = (Config) extra;
    config.pageSegMode = Config.PSM_AUTO;
    config.options |= Config.OPT_NORMALIZE_BG;

    if (config.image != null) {
      Intent recognize = new Intent(Intents.Recognize.ACTION);
      recognize.putExtra(Intents.Recognize.CONFIG, config);
      startActivityForResult(recognize, REQUEST_RECOGNIZE);
    } else {
      Log.e(TAG, "requestRecognize received null image");
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case REQUEST_CAPTURE: {
        if (resultCode == RESULT_OK) {
          requestRecognize(data);
        } else if (resultCode == RESULT_CANCELED) {
          // Maintain the illusion that Capture is the main activity
          finish();
        } else {
          Toast.makeText(this, R.string.capture_failed, 5).show();
          Log.e(TAG, "REQUEST_CAPTURE received unexpected resultCode (" + resultCode + ")");
          requestCapture();
        }
        break;
      }
      case REQUEST_RECOGNIZE: {
        if (resultCode == RESULT_OK) {
          handleCompleted(data);
        } else if (resultCode == RESULT_CANCELED) {
          Toast.makeText(this, R.string.recognize_canceled, 3).show();
          Log.i(TAG, "REQUEST_RECOGNIZED received RESULT_CANCELED");
          requestCapture();
        } else {
          Toast.makeText(this, R.string.recognize_failed, 5).show();
          Log.e(TAG, "REQUEST_RECOGNIZE received unexpected resultCode (" + resultCode + ")");
          requestCapture();
        }
        break;
      }
      case REQUEST_REVIEW: {
        if (resultCode == RESULT_OK) {
          Toast.makeText(this, R.string.contact_added, 3).show();
        }
        requestCapture();
        break;
      }
      default: {
        Log.i(TAG, "Received unknown activity request code (" + requestCode + ")");
        super.onActivityResult(requestCode, resultCode, data);
      }
    }
  }

  /**
   * Pipes output from OcrActivity into the EditContactActivity of the built-in
   * Contact manager application.
   * 
   * @param data Result data returned as an Intent from OcrActivity.
   */
  private void handleCompleted(Intent data) {
    Parcelable[] results = data.getParcelableArrayExtra(Intents.Recognize.RESULTS);
    Bundle extras = extractInformation(results);

    if (extras.isEmpty()) {
      Toast.makeText(this, "Sorry, could not recognize card.", 5).show();
      requestCapture();
    } else {
      Intent intent = new Intent();
      intent.setAction(Intent.ACTION_INSERT);
      intent.addCategory(Intent.CATEGORY_DEFAULT);
      intent.setData(People.CONTENT_URI);
      intent.putExtras(extras);

      // TODO: Figure out how to keep EditContactActivity from saving the
      // contact if the user presses KEYCODE_BACK.
      startActivityForResult(intent, REQUEST_REVIEW);
    }
  }

  /**
   * Extracts contact information from a string using regular expressions. This
   * function is relatively tolerant of poor OCR, as long as it maintains
   * letters and characters.
   * 
   * @param results The result array from which to extract contact information.
   * @return Returns a Bundle containing extracted Contact.Insert fields.
   */
  private Bundle extractInformation(Parcelable[] results) {
    Bundle extras = new Bundle();

    if (results == null || results.length == 0) {
      return extras;
    }

    String str = "";
    for (Parcelable result : results) {
      str += ((Result) result).getString() + "\n";
    }

    Pattern p;
    Matcher m;

    /*
     * Name-matching Expression - Matches: T.V. Raman Alan Viverette Charles L.
     * Chen Julie Lythcott-Haimes - Does not match: Google Google User
     * Experience Team 650-720-5555 cell
     */
    p = Pattern.compile("^([A-Z]([a-z]*|\\.) *){1,2}([A-Z][a-z]+-?)+$", Pattern.MULTILINE);
    m = p.matcher(str);

    if (m.find()) {
      extras.putCharSequence(Insert.NAME, m.group());
    }

    /*
     * Address-matching Expression - Matches: 2600 Amphitheatre Pkwy. P.O. Box
     * 26000 1600 Pennsylvania Avenue 1 Geary - Does not match: Google T.V.
     * Raman 650-720-5555 cell
     */
    p =
        Pattern.compile("^(\\d+ ([A-Z][a-z]+.? +)*[A-Z][a-z]+.?|P.?O.? *Box +\\d+)$",
            Pattern.MULTILINE);
    m = p.matcher(str);

    if (m.find()) {
      extras.putCharSequence(Insert.POSTAL, m.group());
      extras.putInt(Insert.POSTAL_TYPE, ContactMethodsColumns.TYPE_WORK);
      extras.putBoolean(Insert.POSTAL_ISPRIMARY, true);
    }

    /*
     * Address-matching Expression 2 - Matches: Mountain View, CA 94304 Houston
     * TX 77069 Stanford, CA 94309-2901 Salt Lake City, UT 12345 - Does not
     * match: Cell 650-720-5555 Ext. 54085 Google 12345
     */
    p = Pattern.compile("^([A-Z][a-z]+.? *)+ *.? *[A-Z]{2}? *\\d{5}(-\\d[4])?", Pattern.MULTILINE);
    m = p.matcher(str);

    if (m.find()) {
      CharSequence address;
      if ((address = extras.getCharSequence(Insert.POSTAL)) == null)
        address = m.group();
      else
        address = address + ", " + m.group();
      extras.putCharSequence(Insert.POSTAL, address);
      extras.putInt(Insert.POSTAL_TYPE, ContactMethodsColumns.TYPE_WORK);
      extras.putBoolean(Insert.POSTAL_ISPRIMARY, true);
    }

    /*
     * Email-matching Expression - Matches: email: raman@google.com
     * spam@google.co.uk v0nn3gu7@ice9.org name @ host.com - Does not match:
     * #@/.cJX Google c@t
     */
    p = Pattern.compile("([A-Za-z0-9]+ *@ *[A-Za-z0-9]+(\\.[A-Za-z]{2,4})+)$", Pattern.MULTILINE);
    m = p.matcher(str);

    if (m.find()) {
      extras.putCharSequence(Insert.EMAIL, m.group(1));
      extras.putInt(Insert.EMAIL_TYPE, ContactMethodsColumns.TYPE_WORK);
      extras.putBoolean(Insert.EMAIL_ISPRIMARY, true);
    }

    /*
     * Phone-matching Expression - Matches: 1234567890 (650) 720-5678
     * 650-720-5678 650.720.5678 - Does not match: 12345 12345678901 720-5678
     */
    p = Pattern.compile("(?:^|\\D)(\\d{3})[)\\-. ]*?(\\d{3})[\\-. ]*?(\\d{4})(?:$|\\D)");
    m = p.matcher(str);

    if (m.find()) {
      String phone = "(" + m.group(1) + ") " + m.group(2) + "-" + m.group(3);
      extras.putCharSequence(Insert.PHONE, phone);
      extras.putInt(Insert.PHONE_TYPE, PhonesColumns.TYPE_WORK);
      extras.putBoolean(Insert.PHONE_ISPRIMARY, true);
    }

    return extras;
  }
}
