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
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * Implements the user interface for doing slide dialing.
 *
 * @author clchen@google.com (Charles L. Chen) Created 8-2-2008
 * @author alanv@google.com (Alan Viverette)
 */
public class SlideDialView extends TextView {
    /** Vibrate pattern to play when the user enters a digit. */
    private static final long[] VIBRATE_PATTERN_DIGIT = {
            0, 1, 40, 41
    };

    /**
     * Edge touch tolerance in inches. Used for edge-based commands like delete.
     */
    private static final double EDGE_TOLERANCE_INCHES = 0.25;

    /**
     * Radius tolerance in inches. Used for calculating distance from center.
     */
    private static final double RADIUS_TOLERANCE_INCHES = 0.15;
    private static final double THETA_TOLERANCE = (Math.PI / 12.0);

    /**
     * Delay in milliseconds to wait before speaking a digit. Used to prevent
     * clutter when swiping across several digits.
     */
    private static final long DELAY_SPEAK_DIGIT = 250;

    // Command constants
    private static final String DELETE = "delete";

    // Handler constants
    private static final int MSG_SPEAK_DIGIT = 1;

    // Angle constants
    private static final double LEFT = 0;
    private static final double UP_LEFT = Math.PI * .25;
    private static final double UP = Math.PI * .5;
    private static final double UP_RIGHT = Math.PI * .75;
    private static final double DOWN_RIGHT = -Math.PI * .75;
    private static final double DOWN = -Math.PI * .5;
    private static final double DOWN_LEFT = -Math.PI * .25;
    private static final double RIGHT = Math.PI;
    private static final double RIGHT_WRAP = -Math.PI;

    public SlideDial mParent;

    private final Vibrator mVibrator;
    private final ShakeDetector mShakeDetector;
    private final SlideHandler mHandler;

    /** Scaled edge tolerance in pixels. Used for edge commands like delete. */
    private final int mEdgeTolerance;

    /** Scaled radius tolerance in pixels. Used for outer commands like star. */
    private final int mRadiusTolerance;

    /** The initial touch DOWN action. */
    private MotionEvent mDownEvent;

    /** Currently entered phone number. */
    private String mDialedNumber;

    /** Currently entered digit or command. */
    private String mCurrentValue;

    /** Whether the dialed number has been confirmed. */
    private boolean mNumberConfirmed;

    public SlideDialView(Context context) {
        super(context);

        mDownEvent = null;
        mDialedNumber = "";
        mCurrentValue = "";
        mNumberConfirmed = false;
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mHandler = new SlideHandler(context.getMainLooper());

        mShakeDetector = new ShakeDetector(context, new ShakeListener() {
            @Override
            public void onShakeDetected() {
                deleteNumber();
            }
        });

        mEdgeTolerance = (int) (EDGE_TOLERANCE_INCHES * getResources().getDisplayMetrics().densityDpi);
        mRadiusTolerance = (int) (RADIUS_TOLERANCE_INCHES * getResources().getDisplayMetrics().densityDpi);

        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);

        requestFocus();
    }

    public void shutdown() {
        mShakeDetector.shutdown();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Inputting a new number invalidates the confirmation
        mNumberConfirmed = false;

        int action = event.getAction();
        float x = event.getX();
        float y = event.getY();
        String prevVal = "";

        switch (action) {
            case MotionEvent.ACTION_UP:
                if (mDownEvent != null) {
                    mDownEvent.recycle();
                    mDownEvent = null;
                }
                prevVal = mCurrentValue;
                mCurrentValue = evalMotion(x, y);
                // Do some correction if the user lifts UP on deadspace
                if (mCurrentValue.length() == 0) {
                    mCurrentValue = prevVal;
                }
                if (DELETE.equals(mCurrentValue)) {
                    deleteNumber();
                } else {
                    speakDigit(mCurrentValue);
                    mDialedNumber = mDialedNumber + mCurrentValue;
                }
                break;
            case MotionEvent.ACTION_DOWN:
                mDownEvent = MotionEvent.obtain(event);
                //$FALL-THROUGH$
            case MotionEvent.ACTION_MOVE:
                prevVal = mCurrentValue;
                mCurrentValue = evalMotion(x, y);

                // Do nothing since we want a deadzone here;
                // restore the state to the previous value.
                if (mCurrentValue.length() == 0) {
                    mCurrentValue = prevVal;
                    break;
                }

                if (prevVal != mCurrentValue) {
                    mParent.tts.playEarcon(mParent.getString(R.string.earcon_tock),
                            TextToSpeech.QUEUE_FLUSH, null);
                    speakDigitDelayed(mCurrentValue);
                    mVibrator.vibrate(VIBRATE_PATTERN_DIGIT, -1);
                }
                break;
        }

        invalidate();

        return true;
    }

    public String evalMotion(double x, double y) {
        if (mDownEvent == null) {
            return "";
        }

        final float downX = mDownEvent.getX();
        final float downY = mDownEvent.getY();

        boolean movedFar = false;
        boolean nearEdge = false;

        double r = Math.sqrt(((downX - x) * (downX - x)) + ((downY - y) * (downY - y)));

        if (r < mRadiusTolerance) {
            return "5";
        }

        if (r > 6 * mRadiusTolerance) {
            movedFar = true;
        }

        if (x < mEdgeTolerance || x > (getWidth() - mEdgeTolerance) || y < mEdgeTolerance
                || y > (getHeight() - mEdgeTolerance)) {
            nearEdge = true;
        }

        double theta = Math.atan2(downY - y, downX - x);

        if (Math.abs(theta - LEFT) < THETA_TOLERANCE) {
            return movedFar && nearEdge ? DELETE : "4";
        } else if (Math.abs(theta - UP_LEFT) < THETA_TOLERANCE) {
            return "1";
        } else if (Math.abs(theta - UP) < THETA_TOLERANCE) {
            return "2";
        } else if (Math.abs(theta - UP_RIGHT) < THETA_TOLERANCE) {
            return "3";
        } else if (Math.abs(theta - DOWN_RIGHT) < THETA_TOLERANCE) {
            return movedFar ? "#" : "9";
        } else if (Math.abs(theta - DOWN) < THETA_TOLERANCE) {
            return movedFar ? "0" : "8";
        } else if (Math.abs(theta - DOWN_LEFT) < THETA_TOLERANCE) {
            return movedFar ? "*" : "7";
        } else if ((theta > RIGHT - THETA_TOLERANCE) || (theta < RIGHT_WRAP + THETA_TOLERANCE)) {
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

        if (mDownEvent == null) {
            int x = getWidth() / 2;
            int y = (getHeight() / 2) - 35;
            paint.setTextSize(400);
            y -= paint.ascent() / 2;
            canvas.drawText(mCurrentValue, x, y, paint);

            x = 5;
            y = 30;
            paint.setTextSize(50);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText(mDialedNumber, x, y, paint);

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
            // TODO This is... a lot of code. Figure out a better way to handle
            // drawing the UI.

            final float downX = mDownEvent.getX();
            final float downY = mDownEvent.getY();

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

            int xDel = (int) downX - offset - offset;
            int yDel = (int) downY;
            int xStar = (int) downX - offset - offset;
            int yStar = (int) downY + offset + offset;
            int x0 = (int) downX;
            int y0 = (int) downY + offset + offset;
            int xPound = (int) downX + offset + offset;
            int yPound = (int) downY + offset + offset;

            if (mCurrentValue.equals(DELETE)) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            yDel -= paint.ascent() / 2;
            canvas.drawText("DEL", xDel, yDel, paint);

            if (mCurrentValue.equals("1")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y1 -= paint.ascent() / 2;
            canvas.drawText("1", x1, y1, paint);

            if (mCurrentValue.equals("2")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y2 -= paint.ascent() / 2;
            canvas.drawText("2", x2, y2, paint);

            if (mCurrentValue.equals("3")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y3 -= paint.ascent() / 2;
            canvas.drawText("3", x3, y3, paint);

            if (mCurrentValue.equals("4")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y4 -= paint.ascent() / 2;
            canvas.drawText("4", x4, y4, paint);

            if (mCurrentValue.equals("5")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y5 -= paint.ascent() / 2;
            canvas.drawText("5", x5, y5, paint);

            if (mCurrentValue.equals("6")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y6 -= paint.ascent() / 2;
            canvas.drawText("6", x6, y6, paint);

            if (mCurrentValue.equals("7")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y7 -= paint.ascent() / 2;
            canvas.drawText("7", x7, y7, paint);

            if (mCurrentValue.equals("8")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y8 -= paint.ascent() / 2;
            canvas.drawText("8", x8, y8, paint);

            if (mCurrentValue.equals("9")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y9 -= paint.ascent() / 2;
            canvas.drawText("9", x9, y9, paint);

            if (mCurrentValue.equals("*")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            yStar -= paint.ascent() / 2;
            canvas.drawText("*", xStar, yStar, paint);

            if (mCurrentValue.equals("0")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y0 -= paint.ascent() / 2;
            canvas.drawText("0", x0, y0, paint);

            if (mCurrentValue.equals("#")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            yPound -= paint.ascent() / 2;
            canvas.drawText("#", xPound, yPound, paint);
        }
    }

    private void callCurrentNumber() {
        if (!mNumberConfirmed) {
            if (mDialedNumber.length() == 0) {
                mParent.switchToContactsView();
            } else if (mDialedNumber.length() < 3) {
                // A number is considered invalid if less than 3 digits in
                // length.
                mParent.tts.speak(mParent.getString(R.string.invalid_number), 1, null);
            } else {
                mParent.tts.speak(mParent.getString(R.string.you_are_about_to_dial), 1, null);
                for (int i = 0; i < mDialedNumber.length(); i++) {
                    String digit = mDialedNumber.charAt(i) + "";
                    speakDigit(digit, TextToSpeech.QUEUE_ADD);
                }
                mNumberConfirmed = true;
            }
        } else {
            mParent.returnResults(mDialedNumber);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean newNumberEntered = false;
        boolean newLetterEntered = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                mParent.switchToContactsView();
                return true;
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_CALL:
            case KeyEvent.KEYCODE_ENTER:
                callCurrentNumber();
                return true;
            case KeyEvent.KEYCODE_0:
            case KeyEvent.KEYCODE_1:
            case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_5:
            case KeyEvent.KEYCODE_6:
            case KeyEvent.KEYCODE_7:
            case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_9:
                newNumberEntered = true;
                break;
            case KeyEvent.KEYCODE_DEL:
                deleteNumber();
                return true;
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_B:
            case KeyEvent.KEYCODE_C:
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_E:
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_G:
            case KeyEvent.KEYCODE_H:
            case KeyEvent.KEYCODE_I:
            case KeyEvent.KEYCODE_J:
            case KeyEvent.KEYCODE_K:
            case KeyEvent.KEYCODE_L:
            case KeyEvent.KEYCODE_M:
            case KeyEvent.KEYCODE_N:
            case KeyEvent.KEYCODE_O:
            case KeyEvent.KEYCODE_P:
            case KeyEvent.KEYCODE_Q:
            case KeyEvent.KEYCODE_R:
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_T:
            case KeyEvent.KEYCODE_U:
            case KeyEvent.KEYCODE_V:
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_X:
            case KeyEvent.KEYCODE_Y:
            case KeyEvent.KEYCODE_Z:
                newLetterEntered = true;
                break;
        }
        if (newNumberEntered) {
            mNumberConfirmed = false;
            KeyCharacterMap kmap = KeyCharacterMap.load(event.getDeviceId());
            mCurrentValue = kmap.getNumber(keyCode) + "";
            if ((mCurrentValue.equals("3")) && (event.isAltPressed() || event.isShiftPressed())) {
                mCurrentValue = "#";
            } else if ((mCurrentValue.equals("8"))
                    && (event.isAltPressed() || event.isShiftPressed())) {
                mCurrentValue = "*";
            }
            speakDigit(mCurrentValue);
            mDialedNumber = mDialedNumber + mCurrentValue;
            invalidate();
            return true;
        } else if (newLetterEntered) {
            KeyCharacterMap kmap = KeyCharacterMap.load(event.getDeviceId());
            char letterEntered = Character.toLowerCase(kmap.getDisplayLabel(keyCode));
            switch (letterEntered) {
                case 'a':
                case 'b':
                case 'c':
                    mCurrentValue = "2";
                    break;
                case 'd':
                case 'e':
                case 'f':
                    mCurrentValue = "3";
                    break;
                case 'g':
                case 'h':
                case 'i':
                    mCurrentValue = "4";
                    break;
                case 'j':
                case 'k':
                case 'l':
                    mCurrentValue = "5";
                    break;
                case 'm':
                case 'n':
                case 'o':
                    mCurrentValue = "6";
                    break;
                case 'p':
                case 'q':
                case 'r':
                case 's':
                    mCurrentValue = "7";
                    break;
                case 't':
                case 'u':
                case 'v':
                    mCurrentValue = "8";
                    break;
                case 'w':
                case 'x':
                case 'y':
                case 'z':
                    mCurrentValue = "9";
                    break;
            }
            speakDigit(mCurrentValue);
            mDialedNumber = mDialedNumber + mCurrentValue;
            invalidate();
            return true;
        }
        mNumberConfirmed = false;
        return false;
    }

    private void speakDigitDelayed(String digit) {
        mHandler.removeMessages(MSG_SPEAK_DIGIT);
        Message msg = mHandler.obtainMessage(MSG_SPEAK_DIGIT, digit);
        mHandler.sendMessageDelayed(msg, DELAY_SPEAK_DIGIT);
    }

    private void speakDigit(String digit) {
        speakDigit(digit, TextToSpeech.QUEUE_FLUSH);
    }

    private void speakDigit(String digit, int queueMode) {
        String text = adjustForSpeech(digit);
        mParent.tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }

    private String adjustForSpeech(String digit) {
        if ("*".equals(digit)) {
            return "star.";
        } else if ("#".equals(digit)) {
            return "pound.";
        } else {
            return digit + ".";
        }
    }

    private void deleteNumber() {
        mNumberConfirmed = false;

        String deletedNum;

        if (mDialedNumber.length() > 0) {
            deletedNum = adjustForSpeech("" + mDialedNumber.charAt(mDialedNumber.length() - 1));
            mDialedNumber = mDialedNumber.substring(0, mDialedNumber.length() - 1);
        } else {
            deletedNum = "";
        }

        if (!deletedNum.equals("")) {
            mParent.tts.speak(deletedNum, TextToSpeech.QUEUE_FLUSH, null);
            mParent.tts.speak(mParent.getString(R.string.deleted), 1, null);
        } else {
            mParent.tts.playEarcon(mParent.getString(R.string.earcon_tock), 0, null);
            mParent.tts.playEarcon(mParent.getString(R.string.earcon_tock), 1, null);
        }

        mCurrentValue = "";
        invalidate();
    }

    private class SlideHandler extends Handler {
        public SlideHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SPEAK_DIGIT:
                    if (msg.obj instanceof String) {
                        speakDigit((String) msg.obj);
                    }
                    break;
            }
        }
    }
}
