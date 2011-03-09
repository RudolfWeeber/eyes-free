/*
 * Copyright (C) 2010 Google Inc.
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

package com.googlecode.eyesfree.keyboardtutor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link KeyboardTutor} activity contains a single TextView that displays a
 * description of each key as is it typed. If the user has TalkBack enabled, the
 * letter or description of the key will be read to them.
 * 
 * @author clsimon@google.com (Cheryl Simon)
 * 
 */
public class KeyboardTutor extends Activity {

    private static final String LOG_TAG = KeyboardTutor.class.getSimpleName();

    private static final String ACTION_ACCESSIBILITY_SERVICE =
        "android.accessibilityservice.AccessibilityService";

    private static final String CATEGORY_FEEDBACK_SPOKEN =
        "android.accessibilityservice.category.FEEDBACK_SPOKEN";

    private static final String ACTION_ACCESSIBILITY_SETTINGS =
        "android.settings.ACCESSIBILITY_SETTINGS";

    private static final String STATUS_PROVIDER_URI_PREFIX = "content://";

    private static final String STATUS_PROVIDER_URI_SUFFIX = ".providers.StatusProvider";

    private static final Intent sScreenreaderIntent = new Intent();

    
    static {
        sScreenreaderIntent.setAction(ACTION_ACCESSIBILITY_SERVICE);
        sScreenreaderIntent.addCategory(CATEGORY_FEEDBACK_SPOKEN);

    }
    
    private final Map<String, String> mPunctuationSpokenEquivalentsMap
        = new HashMap<String, String>();
    
    private TextView mKeyDescriptionText;
    private int mLastKeyCode;
    
    // used to track if onUserLeaveHint is caused by starting an activity vs. pressing home.
    private boolean startingActivity = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildPunctuationSpokenEquivalentMap();
     
        setContentView(R.layout.main);
        mKeyDescriptionText = (TextView) findViewById(R.id.editText);
        mKeyDescriptionText.requestFocus();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        startingActivity = false;
    }

    private void buildPunctuationSpokenEquivalentMap() {
        mPunctuationSpokenEquivalentsMap.put("?",
            getString(R.string.punctuation_questionmark));
        mPunctuationSpokenEquivalentsMap.put(" ",
            getString(R.string.punctuation_space));
        mPunctuationSpokenEquivalentsMap.put(",",
            getString(R.string.punctuation_comma));
        mPunctuationSpokenEquivalentsMap.put(".",
            getString(R.string.punctuation_dot));
        mPunctuationSpokenEquivalentsMap.put("!",
            getString(R.string.punctuation_exclamation));
        mPunctuationSpokenEquivalentsMap.put("(",
            getString(R.string.punctuation_open_paren));
        mPunctuationSpokenEquivalentsMap.put(")",
            getString(R.string.punctuation_close_paren));
        mPunctuationSpokenEquivalentsMap.put("\"",
            getString(R.string.punctuation_double_quote));
        mPunctuationSpokenEquivalentsMap.put(";",
            getString(R.string.punctuation_semicolon));
        mPunctuationSpokenEquivalentsMap.put(":",
            getString(R.string.punctuation_colon));
    }

    /**
     * We want to capture all key events, so that we can read the key and not
     * leave the screen, unless the user presses home or back to exit the app.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(LOG_TAG, "global keydown " + keyCode);
        
        if (keyCode == KeyEvent.KEYCODE_BACK && mLastKeyCode == KeyEvent.KEYCODE_BACK) {
            finish();
        } else if (keyCode == KeyEvent.KEYCODE_MENU && mLastKeyCode == KeyEvent.KEYCODE_MENU) {
            return false;
        }

        String description = getKeyDescription(keyCode);
        if (description == null) {
            int unicodeChar = event.getUnicodeChar();
            // if value is 0, this means that it is not something meant to be displayed in unicode.
            // These tend to be special function keys that are phone specific, or keys that don't
            // have an alt function.
            // TODO(clsimon): find a way to describe what they do.
            if (unicodeChar == 0) {
                description = getString(R.string.unknown);
            } else {
                description = new String(new int[] { 
                        unicodeChar 
                    }, 0, 1);
                // If this is a punctuation, replace with the spoken equivalent.
                if (mPunctuationSpokenEquivalentsMap.containsKey(description)) {
                    description = mPunctuationSpokenEquivalentsMap.get(description);
                }
            }
        }
        displayAndSpeak(description);


        mLastKeyCode = keyCode;

        // allow volume to be adjusted
        return KeyEvent.KEYCODE_VOLUME_UP != keyCode
            && KeyEvent.KEYCODE_VOLUME_DOWN != keyCode;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {        
            ensureEnabledScreenReader();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!startingActivity) {
            displayAndSpeak(getString(R.string.home_message));
            // reset to empty text, so it doesnt say "Home, exiting .." the next time the user opens
            // the application
            mKeyDescriptionText.setText("");
        }
    }

    /**
     * Displays and speaks the given <code>text</code>.
     */
    private void displayAndSpeak(String text) {
        mKeyDescriptionText.setText(text);
        mKeyDescriptionText.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    /**
     * If the KeyEvent is a special key, return a string value describing the
     * key. Otherwise, return null.
     */
    private String getKeyDescription(int keyCode) {
        String keyText;
        switch (keyCode) {
        case KeyEvent.KEYCODE_ALT_LEFT:
        case KeyEvent.KEYCODE_ALT_RIGHT:
            keyText = getString(R.string.alt);
            break;
        case KeyEvent.KEYCODE_SHIFT_LEFT:
        case KeyEvent.KEYCODE_SHIFT_RIGHT:
            keyText = getString(R.string.shift);
            break;
        case KeyEvent.KEYCODE_SYM:
            keyText = getString(R.string.sym);
            break;
        case KeyEvent.KEYCODE_DEL:
            keyText = getString(R.string.delete);
            break;
        case KeyEvent.KEYCODE_ENTER:
            keyText = getString(R.string.enter);
            break;
        case KeyEvent.KEYCODE_SPACE:
            keyText = getString(R.string.space);
            break;
        case KeyEvent.KEYCODE_SEARCH:
            keyText = getString(R.string.search);
            break;
        case KeyEvent.KEYCODE_BACK:
            keyText = getString(R.string.back_message);
            break;
        case KeyEvent.KEYCODE_MENU:
            keyText = getString(R.string.menu);
            break;
        case KeyEvent.KEYCODE_CALL:
            keyText = getString(R.string.call);
            break;
        case KeyEvent.KEYCODE_ENDCALL:
            keyText = getString(R.string.end_call);
            break;
        case KeyEvent.KEYCODE_VOLUME_DOWN:
            keyText = getString(R.string.volume_down);
            break;
        case KeyEvent.KEYCODE_VOLUME_UP:
            keyText = getString(R.string.volume_up);
            break;
        case KeyEvent.KEYCODE_CAMERA:
            keyText = getString(R.string.camera);
            break;
        case KeyEvent.KEYCODE_DPAD_LEFT:
            keyText = getString(R.string.left);
            break;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            keyText = getString(R.string.right);
            break;
        case KeyEvent.KEYCODE_DPAD_UP:
            keyText = getString(R.string.up);
            break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
            keyText = getString(R.string.down);
            break;
        case KeyEvent.KEYCODE_DPAD_CENTER:
            keyText = getString(R.string.center);
            break;
        default:
            keyText = null;
        }
        Log.d("KeyboardTutor", keyText + ", " + keyCode);
        return keyText;
    }

    /**
     * Ensure there is an enabled screen reader. If no such is present we
     * open the accessibility preferences so the user can enabled it.
     */
    private void ensureEnabledScreenReader() {
        List<ResolveInfo> resolveInfos = getPackageManager().queryIntentServices(
                sScreenreaderIntent, 0);
        // if no screen readers installed we let the user know
        // and quit (this should the first check)
        if (resolveInfos.isEmpty()) {
            showNoInstalledScreenreadersWarning();
            return;
        }

        // check if accessibility is enabled and if not try to open accessibility
        // preferences so the user can enable it (this should be the second check)
        AccessibilityManager accessibilityManger =
            (AccessibilityManager) getSystemService(Service.ACCESSIBILITY_SERVICE);
        if (!accessibilityManger.isEnabled()) {
            showInactiveServiceAlert();
            return;
        }

        // find an enabled screen reader and if no such try to open accessibility
        // preferences so the user can enable one (this should be the third check)
        for (ResolveInfo resolveInfo : resolveInfos) {
            Uri uri = Uri.parse(STATUS_PROVIDER_URI_PREFIX + resolveInfo.serviceInfo.packageName
                    + STATUS_PROVIDER_URI_SUFFIX);
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor.moveToFirst() && cursor.getInt(0) == 1) {
                return;
            }
        }
        showInactiveServiceAlert();
    }

    /**
     * Show a dialog to announce the lack of accessibility settings on the device.
     */
    private void showInactiveServiceAlert() {
        new AlertDialog.Builder(this).setTitle(
                getString(R.string.title_no_active_screen_reader_alert)).setMessage(
                getString(R.string.message_no_active_screen_reader_alert)).setCancelable(false)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        /*
                         * There is no guarantee that an accessibility settings
                         * menu exists, so if the ACTION_ACCESSIBILITY_SETTINGS
                         * intent doesn't match an activity, simply start the
                         * main settings activity.
                         */
                        Intent launchSettings = new Intent(ACTION_ACCESSIBILITY_SETTINGS);
                        try {
                            startActivity(launchSettings);
                        } catch (ActivityNotFoundException ae) {
                            showNoAccessibilityWarning();
                        }
                        dialog.dismiss();
                    }
                }).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        KeyboardTutor.this.finish();
                    }
                }).create().show();
    }

    /**
     * Show a dialog to announce the lack of accessibility settings on the device.
     */
    private void showNoAccessibilityWarning() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.title_no_accessibility_alert))
                .setMessage(getString(R.string.message_no_accessibility_alert)).setPositiveButton(
                        android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                KeyboardTutor.this.finish();
                            }
                        }).create().show();
    }

    /**
     * Show a dialog to announce the lack of screen readers on the device.
     */
    private void showNoInstalledScreenreadersWarning() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.title_no_screen_reader_alert))
                .setMessage(getString(R.string.message_no_screen_reader_alert)).setPositiveButton(
                        android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                KeyboardTutor.this.finish();
                            }
                        }).create().show();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about_menu:
                showAbout();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
        
    }
    
    private void showAbout() {
        startingActivity = true;
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }
}
