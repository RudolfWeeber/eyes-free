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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.MediaRecorder;
import android.view.MotionEvent;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

/**
 * The main View for IDEAL Item ID Label Maker. Handles the touch
 * events and does the recording.
 */
public class VoiceRecorderRecordingView extends TextView {
    public VoiceRecorderActivity parent;

    private MediaRecorder recorder;

    boolean screenIsBeingTouched = false;

    public VoiceRecorderRecordingView(Context context) {
        super(context);
        parent = (VoiceRecorderActivity) context;
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        screenIsBeingTouched = false;
        parent.mTts
                .speak(
                        "Touch the screen, speak your message, and let go of the screen when you are done.",
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
                screenIsBeingTouched = true;
                parent.mTts.playEarcon("[tock]", 0, null);
                parent.mVibe.vibrate(pattern, -1);
                startRecording();
                break;
            case MotionEvent.ACTION_UP:
                saveRecording();
                parent.mTts.playEarcon("[tock]", 0, null);
                parent.mVibe.vibrate(pattern, -1);
                parent.showConfirmationView();
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    private void startRecording() {
        File outputDir = new File("/sdcard/idealItemId/");
        outputDir.mkdirs();
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFile("/sdcard/idealItemId/" + parent.FILE_TIMESTAMP + ".amr");
        try {
            recorder.prepare();
            recorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveRecording() {
        recorder.stop();
        recorder.release();
    }

    @Override
    public void onDraw(Canvas canvas) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        int x = getWidth() / 2;
        int y = (getHeight() / 2) - 35;
        if (!screenIsBeingTouched) {
            x = 5;
            y = getHeight() - 40;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText("Touch screen to record.", x, y, paint);
            x = 5;
            y = getHeight() - 20;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText("Lift up when done.", x, y, paint);
        } else {
            x = 5;
            y = getHeight() - 40;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText("Lift up when you are done.", x, y, paint);
        }
    }

}
