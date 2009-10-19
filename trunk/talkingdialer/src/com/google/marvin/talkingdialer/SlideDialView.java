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

package com.google.marvin.talkingdialer;

import com.google.marvin.talkingdialer.ShakeDetector.ShakeListener;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Vibrator;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/**
 * Implements the user interface for doing slide dialing.
 * 
 * @author clchen@google.com (Charles L. Chen) Created 8-2-2008
 */
public class SlideDialView extends TextView {
  private final double left = 0;
  private final double upleft = Math.PI * .25;
  private final double up = Math.PI * .5;
  private final double upright = Math.PI * .75;
  private final double downright = -Math.PI * .75;
  private final double down = -Math.PI * .5;
  private final double downleft = -Math.PI * .25;
  private final double right = Math.PI;
  private final double rightWrap = -Math.PI;

  public SlideDial parent;
  private double downX;
  private double downY;
  private String currentValue;
  private Vibrator vibe;

  private String dialedNumber;
  private boolean confirmed;

  boolean screenIsBeingTouched = false;
  boolean screenVisible = true;
  
  private ShakeDetector shakeDetector;

  public SlideDialView(Context context) {
    super(context);
    // android.os.Debug.waitForDebugger();
    downX = 0;
    downY = 0;
    dialedNumber = "";
    currentValue = "";
    vibe = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    confirmed = false;
    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);
    requestFocus();
    screenVisible = true;
    screenIsBeingTouched = false;
    shakeDetector = new ShakeDetector(context, new ShakeListener(){
      public void onShakeDetected() {
        deleteNumber();
      }      
    });
  }
  
  public void shutdown(){
    shakeDetector.shutdown();
  }


  @Override
  public boolean onTouchEvent(MotionEvent event) {
    confirmed = false; // Inputting a new number invalidates the confirmation
    int action = event.getAction();
    float x = event.getX();
    float y = event.getY();
    String prevVal = "";
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        screenIsBeingTouched = true;
        downX = x;
        downY = y;
        currentValue = "";
        break;
      case MotionEvent.ACTION_UP:
        screenIsBeingTouched = false;
        prevVal = currentValue;
        currentValue = evalMotion(x, y);
        // Do some correction if the user lifts up on deadspace
        if (currentValue.length() == 0) {
          currentValue = prevVal;
        }
        parent.tts.speak(currentValue, 0, null);
        dialedNumber = dialedNumber + currentValue;
        break;
      default:
        screenIsBeingTouched = true;
        prevVal = currentValue;
        currentValue = evalMotion(x, y);
        // Do nothing since we want a deadzone here;
        // restore the state to the previous value.
        if (currentValue.length() == 0) {
          currentValue = prevVal;
          break;
        }
        if (prevVal != currentValue) {
          parent.tts.speak("[tock]", 0, null);
          long[] pattern = {0, 1, 40, 41};
          vibe.vibrate(pattern, -1);
        }
        break;
    }
    invalidate();
    return true;
  }

  public String evalMotion(double x, double y) {
    float rTolerance = 25;
    double thetaTolerance = (Math.PI / 12);

    boolean movedFar = false;

    double r = Math.sqrt(((downX - x) * (downX - x)) + ((downY - y) * (downY - y)));

    if (r < rTolerance) {
      return "5";
    }
    if (r > 6 * rTolerance) {
      movedFar = true;
    }


    double theta = Math.atan2(downY - y, downX - x);

    if (Math.abs(theta - left) < thetaTolerance) {
      return "4";
    } else if (Math.abs(theta - upleft) < thetaTolerance) {
      return "1";
    } else if (Math.abs(theta - up) < thetaTolerance) {
      return "2";
    } else if (Math.abs(theta - upright) < thetaTolerance) {
      return "3";
    } else if (Math.abs(theta - downright) < thetaTolerance) {
      return movedFar ? "#" : "9";
    } else if (Math.abs(theta - down) < thetaTolerance) {
      return movedFar ? "0" : "8";
    } else if (Math.abs(theta - downleft) < thetaTolerance) {
      return movedFar ? "*" : "7";
    } else if ((theta > right - thetaTolerance) || (theta < rightWrap + thetaTolerance)) {
      return "6";
    }

    // Off by more than the threshold, so it doesn't count
    return "";
  }

  @Override
  public void onDraw(Canvas canvas) {
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.WHITE);
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTypeface(Typeface.DEFAULT_BOLD);

    if (!screenIsBeingTouched) {
      int x = getWidth() / 2;
      int y = (getHeight() / 2) - 35;
      paint.setTextSize(400);
      y -= paint.ascent() / 2;
      canvas.drawText(currentValue, x, y, paint);

      x = 5;
      y = 30;
      paint.setTextSize(50);
      paint.setTextAlign(Paint.Align.LEFT);
      y -= paint.ascent() / 2;
      canvas.drawText(dialedNumber, x, y, paint);

      x = 5;
      y = getHeight() - 60;
      paint.setTextSize(20);
      paint.setTextAlign(Paint.Align.LEFT);
      y -= paint.ascent() / 2;
      canvas.drawText("Press MENU for phonebook.", x, y, paint);

      x = 5;
      y = getHeight() - 40;
      paint.setTextSize(20);
      paint.setTextAlign(Paint.Align.LEFT);
      y -= paint.ascent() / 2;
      canvas.drawText("Stroke the screen to dial.", x, y, paint);
      x = 5;
      y = getHeight() - 20;
      paint.setTextSize(20);
      paint.setTextAlign(Paint.Align.LEFT);
      y -= paint.ascent() / 2;
      canvas.drawText("Press CALL twice to confirm.", x, y, paint);

    } else {

      int offset = 130;
      int regSize = 100;
      int selectedSize = regSize * 2;


      int x1 = (int) downX - offset;
      int y1 = (int) downY - offset;
      int x2 = (int) downX;
      int y2 = (int) downY - offset;
      int x3 = (int) downX + offset;
      int y3 = (int) downY - offset;
      int x4 = (int) downX - offset;
      int y4 = (int) downY;
      int x5 = (int) downX;
      int y5 = (int) downY;
      int x6 = (int) downX + offset;
      int y6 = (int) downY;
      int x7 = (int) downX - offset;
      int y7 = (int) downY + offset;
      int x8 = (int) downX;
      int y8 = (int) downY + offset;
      int x9 = (int) downX + offset;
      int y9 = (int) downY + offset;


      int xStar = (int) downX - offset - offset;
      int yStar = (int) downY + offset + offset;
      int x0 = (int) downX;
      int y0 = (int) downY + offset + offset;
      int xPound = (int) downX + offset + offset;
      int yPound = (int) downY + offset + offset;


      if (currentValue.equals("1")) {
        paint.setTextSize(selectedSize);
      } else {
        paint.setTextSize(regSize);
      }
      y1 -= paint.ascent() / 2;
      canvas.drawText("1", x1, y1, paint);

      if (currentValue.equals("2")) {
        paint.setTextSize(selectedSize);
      } else {
        paint.setTextSize(regSize);
      }
      y2 -= paint.ascent() / 2;
      canvas.drawText("2", x2, y2, paint);

      if (currentValue.equals("3")) {
        paint.setTextSize(selectedSize);
      } else {
        paint.setTextSize(regSize);
      }
      y3 -= paint.ascent() / 2;
      canvas.drawText("3", x3, y3, paint);

      if (currentValue.equals("4")) {
        paint.setTextSize(selectedSize);
      } else {
        paint.setTextSize(regSize);
      }
      y4 -= paint.ascent() / 2;
      canvas.drawText("4", x4, y4, paint);

      if (currentValue.equals("5")) {
        paint.setTextSize(selectedSize);
      } else {
        paint.setTextSize(regSize);
      }
      y5 -= paint.ascent() / 2;
      canvas.drawText("5", x5, y5, paint);

      if (currentValue.equals("6")) {
        paint.setTextSize(selectedSize);
      } else {
        paint.setTextSize(regSize);
      }
      y6 -= paint.ascent() / 2;
      canvas.drawText("6", x6, y6, paint);

      if (currentValue.equals("7")) {
        paint.setTextSize(selectedSize);
      } else {
        paint.setTextSize(regSize);
      }
      y7 -= paint.ascent() / 2;
      canvas.drawText("7", x7, y7, paint);

      if (currentValue.equals("8")) {
        paint.setTextSize(selectedSize);
      } else {
        paint.setTextSize(regSize);
      }
      y8 -= paint.ascent() / 2;
      canvas.drawText("8", x8, y8, paint);

      if (currentValue.equals("9")) {
        paint.setTextSize(selectedSize);
      } else {
        paint.setTextSize(regSize);
      }
      y9 -= paint.ascent() / 2;
      canvas.drawText("9", x9, y9, paint);


      if (currentValue.equals("*")) {
        paint.setTextSize(selectedSize);
      } else {
        paint.setTextSize(regSize);
      }
      yStar -= paint.ascent() / 2;
      canvas.drawText("*", xStar, yStar, paint);

      if (currentValue.equals("0")) {
        paint.setTextSize(selectedSize);
      } else {
        paint.setTextSize(regSize);
      }
      y0 -= paint.ascent() / 2;
      canvas.drawText("0", x0, y0, paint);

      if (currentValue.equals("#")) {
        paint.setTextSize(selectedSize);
      } else {
        paint.setTextSize(regSize);
      }
      yPound -= paint.ascent() / 2;
      canvas.drawText("#", xPound, yPound, paint);
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    boolean newNumberEntered = false;
    switch (keyCode) {
      case KeyEvent.KEYCODE_MENU:
        parent.switchToContactsView();
        return true;

      case KeyEvent.KEYCODE_SEARCH:
        if (!confirmed) {
          parent.tts.speak("You are about to dial", 1, null);
          for (int i = 0; i < dialedNumber.length(); i++) {
            String digit = dialedNumber.charAt(i) + "";
            parent.tts.speak(digit, 1, null);
          }
          confirmed = true;
          return true;
        } else {
          parent.returnResults(dialedNumber);
          return true;
        }
      case KeyEvent.KEYCODE_CALL:
        if (!confirmed) {
          parent.tts.speak("You are about to dial", 1, null);
          for (int i = 0; i < dialedNumber.length(); i++) {
            String digit = dialedNumber.charAt(i) + "";
            parent.tts.speak(digit, 1, null);
          }
          confirmed = true;
          return true;
        } else {
          parent.returnResults(dialedNumber);
          return true;
        }

      case KeyEvent.KEYCODE_0:
        newNumberEntered = true;
        break;
      case KeyEvent.KEYCODE_1:
        newNumberEntered = true;
        break;
      case KeyEvent.KEYCODE_2:
        newNumberEntered = true;
        break;
      case KeyEvent.KEYCODE_3:
        newNumberEntered = true;
        break;
      case KeyEvent.KEYCODE_4:
        newNumberEntered = true;
        break;
      case KeyEvent.KEYCODE_5:
        newNumberEntered = true;
        break;
      case KeyEvent.KEYCODE_6:
        newNumberEntered = true;
        break;
      case KeyEvent.KEYCODE_7:
        newNumberEntered = true;
        break;
      case KeyEvent.KEYCODE_8:
        newNumberEntered = true;
        break;
      case KeyEvent.KEYCODE_9:
        newNumberEntered = true;
        break;
      case KeyEvent.KEYCODE_DEL:
        deleteNumber();
        return true;
    }
    if (newNumberEntered) {
      confirmed = false;
      KeyCharacterMap kmap = KeyCharacterMap.load(event.getDeviceId());
      currentValue = kmap.getNumber(keyCode) + "";
      if ((currentValue.equals("3")) && (event.isAltPressed() || event.isShiftPressed())) {
        currentValue = "#";
      } else if ((currentValue.equals("8")) && (event.isAltPressed() || event.isShiftPressed())) {
        currentValue = "*";
      }
      parent.tts.speak(currentValue, 0, null);
      dialedNumber = dialedNumber + currentValue;
      invalidate();
      return true;
    }
    confirmed = false;
    return false;
  }

  private void deleteNumber() {
    confirmed = false;
    String deletedNum;
    if (dialedNumber.length() > 0) {
      deletedNum = "" + dialedNumber.charAt(dialedNumber.length() - 1);
      dialedNumber = dialedNumber.substring(0, dialedNumber.length() - 1);
    } else {
      deletedNum = "";
    }
    if (!deletedNum.equals("")) {
      parent.tts.speak(deletedNum, 0, null);
      parent.tts.speak("deleted", 1, null);
    } else {
      parent.tts.speak("[tock]", 0, null);
      parent.tts.speak("[tock]", 1, null);
    }
    currentValue = "";
    invalidate();
  }

  @Override
  protected void onWindowVisibilityChanged(int visibility) {
    if (visibility == View.VISIBLE) {
      screenVisible = true;
    } else {
      screenVisible = false;
    }
  }
}
