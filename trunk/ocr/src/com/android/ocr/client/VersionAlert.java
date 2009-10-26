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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;

/**
 * Creates an alert dialog that directs the user to the App Market.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class VersionAlert {
  private final static String MARKET_URI = "market://search?q=pname:com.android.ocr";

  private final static String INSTALL =
      "This application requires the Mobile OCR library for text recognition.";
  private final static String INSTALL_TITLE = "Install OCR Library";
  private final static String INSTALL_POSITIVE = "Install";
  private final static String INSTALL_NEGATIVE = "Do not install";

  private final static String UPDATE =
      "This application requires a newer version of the Mobile OCR library.";
  private final static String UPDATE_TITLE = "Update OCR Library";
  private final static String UPDATE_POSITIVE = "Update";
  private final static String UPDATE_NEGATIVE = "Do not update";

  private final static String LANGUAGES = "Please install at least one Mobile OCR language pack.";
  private final static String LANGUAGES_TITLE = "Install OCR Languages";
  private final static String LANGUAGES_POSITIVE = "Select language";
  private final static String LANGUAGES_NEGATIVE = "Do not install";

  private VersionAlert() {
    // Do nothing
  }

  /**
   * Returns an alert dialog.
   * 
   * @param context
   */
  public static AlertDialog createInstallAlert(final Context context, OnClickListener onNegative) {
    OnClickListener onPositive = new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Uri marketUri = Uri.parse(MARKET_URI);
        Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
        context.startActivity(marketIntent);
      }
    };

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setMessage(INSTALL);
    builder.setTitle(INSTALL_TITLE);
    builder.setPositiveButton(INSTALL_POSITIVE, onPositive);
    builder.setNegativeButton(INSTALL_NEGATIVE, onNegative);

    return builder.create();
  }

  public static AlertDialog createUpdateAlert(final Context context, OnClickListener onNegative) {
    OnClickListener onPositive = new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Uri marketUri = Uri.parse(MARKET_URI);
        Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
        context.startActivity(marketIntent);
      }
    };

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setMessage(UPDATE);
    builder.setTitle(UPDATE_TITLE);
    builder.setPositiveButton(UPDATE_POSITIVE, onPositive);
    builder.setNegativeButton(UPDATE_NEGATIVE, onNegative);

    return builder.create();
  }

  /**
   * Creates an alert requesting that the user install a language.
   * 
   * @param context
   * @param onPositive action to perform if the user accepts
   * @param onNegative action to perform if the user declines
   * @param requestCode request code for LanguageActivity to return or -1 for no
   *        return
   * @return alert dialog
   */
  public static AlertDialog createLanguagesAlert(final Activity context,
      final OnClickListener onPositive, OnClickListener onNegative, final int requestCode) {
    OnClickListener onRealPositive = new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Intent languagesIntent = new Intent(Intents.Languages.ACTION);
        if (requestCode > 0) {
          context.startActivityForResult(languagesIntent, requestCode);
        } else {
          context.startActivity(languagesIntent);
        }
        onPositive.onClick(dialog, which);
      }
    };

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setMessage(LANGUAGES);
    builder.setTitle(LANGUAGES_TITLE);
    builder.setPositiveButton(LANGUAGES_POSITIVE, onRealPositive);
    builder.setNegativeButton(LANGUAGES_NEGATIVE, onNegative);

    return builder.create();
  }
}
