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
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

/**
 * The {@link KeyboardTutor} activity contains a single TextView that displays a
 * description of each key as is it typed. If the user has TalkBack enabled, the
 * letter or description of the key will be read to them.
 * 
 * @author clsimon@google.com (Cheryl Simon)
 * 
 */
public class KeyboardTutor extends Activity {

    private int lastKeyCode = 0;
    private TextToSpeech tts;
    private boolean ttsInited = false;

    /**
     * This key listener listens for key press events from the physical keyboard
     * and displays a single letter or key description at a time in the
     * editText.
     */
    private TextKeyListener editTextKeyListener = new TextKeyListener(
            Capitalize.NONE, false) {

        @Override
        public boolean onKeyDown(View view, Editable text, int keyCode,
                KeyEvent event) {
            // Allow the TextKeyListener to append the character it wants to
            // the end of the
            // editable, text. The TextKeyListener accounts for modifier
            // keys.
            Log.d("KeyboardTutor", "before " + text);
            super.onKeyDown(view, text, keyCode, event);

            Log.d("KeyboardTutor", "after " + text);

            String keyText = getKeyDescription(keyCode, event);
            // if the key pressed was a "special" key (alt, menu, arrow,
            // etc),
            // display the description text
            if (keyText != null) {
                text.clear();
                text.append(keyText);
            } else if (text.length() > 0) {
                // otherwise, display the last character that was added.
                char last = text.charAt(text.length() - 1);
                text.clear();
                text.append(last);
            }

            if (KeyEvent.KEYCODE_BACK == keyCode) {
                if (lastKeyCode == KeyEvent.KEYCODE_BACK) {
                    Log.d("KeyboardTutor", "exiting app");
                    finish();
                } else {
                    if (ttsInited) {
                        tts.speak(getString(R.string.back_message),
                                TextToSpeech.QUEUE_ADD, null);
                    }
                }
            } else {
                sendAccessibilityEvent(text.toString());
            }

            lastKeyCode = keyCode;
            return true;
        }
    };

    /**
     * The text watcher is used to catch changes from the IME. It simply removes
     * all of the old text, so that only one character is displayed at a time.
     * TODO(clsimon) Use this task watcher to add support for text entry via
     * IMEs.
     */
    TextWatcher textWatcher = new TextWatcher() {

        // Used to prevent infinte loops while editing the text content
        private boolean doNothing;

        // The new char sequence that was added.
        private CharSequence changed;

        public void onTextChanged(CharSequence s, int start, int before,
                int count) {
            if (!doNothing) {
                changed = s.subSequence(start, start + count);
            }
        }

        public void beforeTextChanged(CharSequence s, int start, int count,
                int after) {
        }

        public void afterTextChanged(Editable s) {
            if (!doNothing) {
                doNothing = true;
                s.clear();
                s.append(changed);
                Log.i("KeyboardTutor",
                        "Updated edit text through TextWatcher");
                doNothing = false;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, 0);

        final TextView editText = (TextView) findViewById(R.id.editText);
        editText.requestFocus();

        // add an on click listener to detect when the view is clicked. This
        // seems like the only
        // way to detect a middle d-pad click.
        editText.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                editText.setText(R.string.center);
                sendAccessibilityEvent(editText.getText().toString());
            }
        });

        // The KeyListener receives KeyEvents when buttons on the hardware
        // keyboard
        // are pressed.
        editText.setKeyListener(editTextKeyListener);
    }

    /**
     * onUserLeaveHint is called when the user requests that we exit the
     * application. Assume this means that the home key was pressed, and give a
     * final message as we exit.
     */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (tts != null && ttsInited) {
            tts.speak(getString(R.string.home_message),
                    TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    /**
     * If the KeyEvent is a special key, return a string value describing the
     * key. Otherwise, return null.
     */
    private String getKeyDescription(int keyCode, KeyEvent event) {
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
            keyText = getString(R.string.back);
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
     * We want to capture all key events, so that we can read the key and not
     * leave the screen, unless the user presses home or back to exit the app.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d("KeyboardTutor", "global keydown " + keyCode);
        // allow volume to be adjusted.
        return KeyEvent.KEYCODE_VOLUME_UP != keyCode
                && KeyEvent.KEYCODE_VOLUME_DOWN != keyCode;
    }

    private void sendAccessibilityEvent(String text) {
        Log.d("KeyboardTutor", "sending event " + text);
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (!manager.isEnabled()) {
            Log.d("KeyboardTutor", "manager not enabled");
            return;
        }
        AccessibilityEvent event = AccessibilityEvent
                .obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
        event.setFromIndex(0);
        event.setRemovedCount(0);
        event.setAddedCount(text.length());
        event.setContentDescription(text);

        manager.sendAccessibilityEvent(event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
            tts = new TextToSpeech(this, new OnInitListener() {
                public void onInit(int status) {
                    ttsInited = true;
                }
            });
        } else {
            Intent installIntent = new Intent();
            installIntent
                    .setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            startActivity(installIntent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null && ttsInited) {
            tts.shutdown();
        }
    }
}
