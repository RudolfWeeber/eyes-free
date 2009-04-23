/*
 * Copyright (C) 2008 Google Inc.
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

package com.google.marvin.remindme;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.widget.TextView;

/**
 * Implements the user interface for doing slide dialing.
 * 
 * @author clchen@google.com (Charles L. Chen) Created 8-2-2008
 */
public class ConfirmationView extends TextView {
  public RemindMe parent;
  private Vibrator vibe;

  public ConfirmationView(Context context) {
    super(context);
    parent = (RemindMe) context;
    vibe = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);
    requestFocus();
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
    canvas.drawText("Press CALL to confirm", x, y, paint);
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
      case KeyEvent.KEYCODE_CALL:
        parent.tts.playEarcon("[tock]", 0, null);
        long[] pattern = {0, 1, 40, 41};
        vibe.vibrate(pattern, -1);
        parent.confirmAlarm();
        return true;
      case KeyEvent.KEYCODE_BACK:
        parent.showNumberEntryView();
        return true;

    }
    return false;
  }

}
