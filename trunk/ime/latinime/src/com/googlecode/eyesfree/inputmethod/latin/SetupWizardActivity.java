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

package com.googlecode.eyesfree.inputmethod.latin;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

public class SetupWizardActivity extends Activity {
    private static final int DIALOG_ENABLE_ACCESSIBILITY = 1;
    private static final int DIALOG_ENABLE_IME = 2;
    private static final int DIALOG_SET_DEFAULT_IME = 3;
    private static final int DIALOG_FAILED = 4;

    private static final int REQUEST_ENABLE_ACCESSIBILITY = 1;
    private static final int REQUEST_ENABLE_IME = 2;

    private static final Class<?> TARGET_IME = LatinIME.class;

    private final SetupWizardHandler mHandler = new SetupWizardHandler();

    private EditText mEditText;
    private InputMethodManager mInputMethodManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mEditText = new EditText(this);

        mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false,
                mContentObserver);

        setContentView(mEditText);

        checkStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        checkStatus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && AccessibilityUtils.isInputMethodDefault(this, TARGET_IME)) {
            mInputMethodManager.showSoftInput(mEditText, InputMethodManager.SHOW_FORCED);
            mHandler.startFinishTimeout();
        }
    }

    private void checkStatus() {
        if (!AccessibilityUtils.isAccessibilityEnabled(this)) {
            showDialog(DIALOG_ENABLE_ACCESSIBILITY);
        } else if (!AccessibilityUtils.isInputMethodEnabled(this, TARGET_IME)) {
            showDialog(DIALOG_ENABLE_IME);
        } else if (!AccessibilityUtils.isInputMethodDefault(this, TARGET_IME)) {
            showDialog(DIALOG_SET_DEFAULT_IME);
        } else {
            // Do nothing, since the input method will open when the window
            // receives focus.
        }
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case DIALOG_ENABLE_ACCESSIBILITY:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.need_accessibility_title)
                        .setMessage(R.string.need_accessibility_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.need_accessibility_positive,
                                mNeedAccessibilityListener).create();
            case DIALOG_ENABLE_IME:
                return new AlertDialog.Builder(this).setTitle(R.string.need_enable_title)
                        .setMessage(R.string.need_enable_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.need_enable_positive, mNeedEnableListener)
                        .create();
            case DIALOG_SET_DEFAULT_IME:
                return new AlertDialog.Builder(this).setTitle(R.string.need_default_title)
                        .setMessage(R.string.need_default_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.need_default_positive, mNeedDefaultListener)
                        .create();
            case DIALOG_FAILED:
                return new AlertDialog.Builder(this).setTitle(R.string.wizard_failed_title)
                        .setMessage(R.string.wizard_failed_message)
                        .setNegativeButton(android.R.string.ok, mWizardFailedListener).create();

        }

        return super.onCreateDialog(id, args);
    }

    private final OnClickListener mNeedAccessibilityListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            final Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivityForResult(intent, REQUEST_ENABLE_ACCESSIBILITY);
        }
    };

    private final OnClickListener mNeedEnableListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            final Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivityForResult(intent, REQUEST_ENABLE_IME);
        }
    };

    private final OnClickListener mNeedDefaultListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            // Since we can't listen for an activity result, just display the
            // dialog again and close it when we know the input method is set
            // correctly.
            showDialog(DIALOG_SET_DEFAULT_IME);

            mInputMethodManager.showInputMethodPicker();
        }
    };

    private final OnClickListener mWizardFailedListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            finish();
        }
    };

    private final ContentObserver mContentObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            if (AccessibilityUtils.isInputMethodDefault(SetupWizardActivity.this, TARGET_IME)) {
                removeDialog(DIALOG_SET_DEFAULT_IME);
            }
        }
    };

    private class SetupWizardHandler extends Handler {
        private static final int MESSAGE_FINISH = 1;

        private static final int TIMEOUT_FINISH = 1000;
        private static final int INTERVAL_FINISH = 100;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_FINISH:
                    final int duration = msg.arg1;
                    checkFinishTimeout(duration);
                    break;
            }
        }

        private void checkFinishTimeout(int duration) {
            if (mInputMethodManager.isActive(mEditText)) {
                finish();
                return;
            }

            if (duration >= TIMEOUT_FINISH) {
                showDialog(DIALOG_FAILED);
                return;
            }

            duration += INTERVAL_FINISH;

            final Message msg = obtainMessage(MESSAGE_FINISH, duration, 0);
            sendMessageDelayed(msg, INTERVAL_FINISH);
        }

        public void startFinishTimeout() {
            final Message msg = obtainMessage(MESSAGE_FINISH, 0, 0);
            sendMessage(msg);
        }
    }
}
