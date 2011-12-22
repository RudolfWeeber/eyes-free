/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.eyesfree.inputmethod.latin.tutorial;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.view.WindowManager;

import com.googlecode.eyesfree.inputmethod.latin.R;

/**
 * Shows an alert dialog asking whether the user would like to run the tutorial.
 * This activity is only necessary because a Service cannot show a dialog.
 */
public class LatinTutorialDialog extends Activity {
    @Override
    protected void onResume() {
        super.onResume();

        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.tutorial_name)
                .setMessage(R.string.tutorial_first_time)
                .setCancelable(true).setNegativeButton(android.R.string.no, dialogClickListener)
                .setPositiveButton(android.R.string.ok, dialogClickListener)
                .setOnCancelListener(dialogCancelListener)
                .create();
        
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        dialog.show();
    }

    private final OnCancelListener dialogCancelListener = new OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
            finish();
        }
    };

    private final OnClickListener dialogClickListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    startActivity(new Intent(LatinIMETutorial.ACTION));
                    break;
            }

            finish();
        }
    };
}
