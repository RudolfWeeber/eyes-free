/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.marvin.talkback;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Message;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import com.googlecode.eyesfree.utils.WeakReferenceHandler;
import com.googlecode.eyesfree.widget.SimpleOverlay;

/**
 * Displays text-to-speech text on the screen.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class TextToSpeechOverlay extends SimpleOverlay {
    private static final int MSG_CLEAR_TEXT = 1;

    private TextView mText;

    public TextToSpeechOverlay(Context context) {
        super(context);

        final WindowManager.LayoutParams params = getParams();
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        params.format = PixelFormat.TRANSPARENT;
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        setParams(params);

        mText = new TextView(context);
        mText.setBackgroundColor(0x60FF0000);
        mText.setPadding(10, 10, 10, 10);
        mText.setGravity(Gravity.CENTER);

        setContentView(mText);
    }

    public void speak(String text) {
        show();

        final long displayTime = Math.max(2000, text.length() * 100);

        mHandler.removeMessages(MSG_CLEAR_TEXT);
        mText.setText(text.trim());
        mHandler.sendEmptyMessageDelayed(MSG_CLEAR_TEXT, displayTime);
    }

    private final OverlayHandler mHandler = new OverlayHandler(this);

    private static class OverlayHandler extends WeakReferenceHandler<TextToSpeechOverlay> {
        public OverlayHandler(TextToSpeechOverlay parent) {
            super(parent);
        }

        @Override
        protected void handleMessage(Message msg, TextToSpeechOverlay parent) {
            switch (msg.what) {
                case MSG_CLEAR_TEXT:
                    parent.mText.setText("");
                    parent.hide();
                    break;
            }
        }
    }
}
