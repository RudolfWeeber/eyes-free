/*
 * Copyright (C) 2010 The IDEAL Group
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

package com.ideal.itemid;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * The confirmation View for IDEAL Item ID Label Maker. Handles the touch
 * events and confirms the recording that the user has made.
 */
public class VoiceRecorderConfirmationView extends TextView {
    private VoiceRecorderActivity parent;

    private MediaPlayer mPlayer;

    public VoiceRecorderConfirmationView(Context context) {
        super(context);
        parent = (VoiceRecorderActivity) context;
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        parent.mTts.speak("Touch and hold the screen to preview your recording, let go when done.",
                0, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        long[] pattern = {
                0, 1, 40, 41
        };
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                parent.mVibe.vibrate(pattern, -1);
                mPlayer = MediaPlayer.create(parent, Uri.parse("/sdcard/idealItemId/"
                        + parent.FILE_TIMESTAMP + ".amr"));
                mPlayer.start();
                break;
            case MotionEvent.ACTION_UP:
                if (mPlayer != null) {
                    mPlayer.stop();
                    mPlayer.release();
                }
                parent.mTts
                        .speak(
                                "Press back to re-record, press call or search to confirm, then enter the e-mail address you wish to send the QR code label to.",
                                0, null);
                parent.mVibe.vibrate(pattern, -1);
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    @Override
    public void onDraw(Canvas canvas) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        int x = 5;
        int y = getHeight() - 40;
        paint.setTextSize(20);
        paint.setTextAlign(Paint.Align.LEFT);
        y -= paint.ascent() / 2;
        canvas.drawText("Press CALL/SEARCH to confirm", x, y, paint);
        x = 5;
        y = getHeight() - 20;
        paint.setTextSize(20);
        paint.setTextAlign(Paint.Align.LEFT);
        y -= paint.ascent() / 2;
        canvas.drawText("Press BACK to try again", x, y, paint);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_CALL:
                parent.mTts.playEarcon("[tock]", 0, null);
                long[] pattern = {
                        0, 1, 40, 41
                };
                parent.mVibe.vibrate(pattern, -1);

                final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
                emailIntent.setType("text/html");
                emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] {
                    ""
                });
                emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                        "Your printable QR code label for IDEAL Item ID");
                emailIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                        "http://qrcode.kaywa.com/img.php?s=8&d=audio%3A%2F%2F"
                                + parent.FILE_TIMESTAMP + ".amr" + "\n\n" + "<img src='"
                                + "http://qrcode.kaywa.com/img.php?s=8&d=audio%3A%2F%2F"
                                + parent.FILE_TIMESTAMP + ".amr" + "'>");

                parent.startActivity(emailIntent);
                parent.finish();
                return true;
            case KeyEvent.KEYCODE_BACK:
                parent.showRecordingView();
                return true;
        }
        return false;
    }
}
