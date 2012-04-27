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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

import com.google.marvin.talkingdialer.ShakeDetector.ShakeListener;
import com.googlecode.eyesfree.compat.view.MotionEventCompatUtils;

/**
 * Implements the user interface for doing slide dialing.
 * 
 * @author clchen@google.com (Charles L. Chen) Created 8-2-2008
 * @author alanv@google.com (Alan Viverette)
 */
public class SlideDialView extends TextView {
    private static final int SOUND_RESOURCE_FOCUSED = R.raw.tick;
    private static final int SOUND_RESOURCE_TYPED = R.raw.keypress;
    private static final int SOUND_RESOURCE_DELETED = R.raw.delete;

    private static final long[] VIBRATE_PATTERN_FOCUSED = new long[] {
    0, 30 };
    private static final long[] VIBRATE_PATTERN_TYPED = new long[] {
    0, 40 };
    private static final long[] VIBRATE_PATTERN_DELETED = new long[] {
    0, 50 };

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

    private final FeedbackUtil mFeedback;
    private final SlideDial mParent;
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
    // private String mCurrentValue;
    private String mPreviousValue;

    /** Whether the dialed number has been confirmed. */
    private boolean mNumberConfirmed;

    public SlideDialView(SlideDial parent) {
        super(parent);

        mParent = parent;
        mDownEvent = null;
        mDialedNumber = "";
        mPreviousValue = "";
        mNumberConfirmed = false;
        mHandler = new SlideHandler(parent.getMainLooper());
        mFeedback = new FeedbackUtil(parent);

        mShakeDetector = new ShakeDetector(parent, new ShakeListener() {
                @Override
            public void onShakeDetected() {
                deleteNumber();
            }
        });

        mEdgeTolerance = (int) (EDGE_TOLERANCE_INCHES
                * getResources().getDisplayMetrics().densityDpi);
        mRadiusTolerance = (int) (RADIUS_TOLERANCE_INCHES
                * getResources().getDisplayMetrics().densityDpi);

        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);

        requestFocus();
    }

    public void shutdown() {
        mFeedback.release();
        mShakeDetector.shutdown();
    }

    public boolean onHoverEvent(MotionEvent event) {
        return onTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Inputting a new number invalidates the confirmation
        mNumberConfirmed = false;

        final int action = event.getAction();
        final float x = event.getX();
        final float y = event.getY();
        String digit = "";

        switch (action) {
            case MotionEventCompatUtils.ACTION_HOVER_EXIT:
            case MotionEvent.ACTION_UP:
                if (mDownEvent != null) {
                    mDownEvent.recycle();
                    mDownEvent = null;
                }

                digit = evalMotion(x, y);

                // Do some correction if the user lifts UP on deadspace
                if (digit.length() == 0) {
                    digit = mPreviousValue;
                }

                onDigitTyped(digit, false);

                mPreviousValue = "";
                break;

            case MotionEventCompatUtils.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_DOWN:
                mDownEvent = MotionEvent.obtain(event);
        //$FALL-THROUGH$
            case MotionEventCompatUtils.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_MOVE: {
            digit = evalMotion(x, y);

            // Do nothing since we want a deadzone here;
            // restore the state to the previous value.
            if (digit.length() == 0) {
                digit = mPreviousValue;
                break;
            }

            if (digit != mPreviousValue) {
                onDigitFocused(digit);
            }

            mPreviousValue = digit;
            break;
        }
        }

        return true;
    }

    /**
     * Called when a digit is focused. Handles haptic and auditory feedback.
     * 
     * @param digit The digit that was focused.
     */
    private void onDigitFocused(String digit) {
        mFeedback.playSound(SOUND_RESOURCE_FOCUSED);
        mFeedback.vibrate(VIBRATE_PATTERN_FOCUSED);

        speakDigitDelayed(digit, TextToSpeech.QUEUE_FLUSH, 50);

        invalidate();
    }

    /**
     * Called when a digit is typed. Handles haptic and auditory feedback.
     * 
     * @param digit The digit that was typed.
     */
    private void onDigitTyped(String digit, boolean announce) {
        if (DELETE.equals(digit)) {
            mFeedback.playSound(SOUND_RESOURCE_DELETED);
            mFeedback.vibrate(VIBRATE_PATTERN_DELETED);

            deleteNumber();
        } else {
            mDialedNumber = mDialedNumber + digit;

            mFeedback.playSound(SOUND_RESOURCE_TYPED);
            mFeedback.vibrate(VIBRATE_PATTERN_TYPED);

            if (announce) {
                speakDigit(digit, TextToSpeech.QUEUE_FLUSH, 100);
            }
        }

        mPreviousValue = "";

        invalidate();
    }

    public String evalMotion(double x, double y) {
        if (mDownEvent == null) {
            return "";
        }

        final float downX = mDownEvent.getX();
        final float downY = mDownEvent.getY();

        boolean movedFar = false;
        final double r = Math.sqrt(((downX - x) * (downX - x)) + ((downY - y) * (downY - y)));

        if (r < mRadiusTolerance) {
            return "5";
        }

        if (r > (6 * mRadiusTolerance)) {
            movedFar = true;
        }

        if ((x < mEdgeTolerance) || (x > (getWidth() - mEdgeTolerance)) || (y < mEdgeTolerance)
                || (y > (getHeight() - mEdgeTolerance))) {
        }

        final double theta = Math.atan2(downY - y, downX - x);

        if (Math.abs(theta - LEFT) < THETA_TOLERANCE) {
            return movedFar ? DELETE : "4";
        } else if (Math.abs(theta - UP_LEFT) < THETA_TOLERANCE) {
            return movedFar ? DELETE : "1";
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
        } else if ((theta > (RIGHT - THETA_TOLERANCE))
                || (theta < (RIGHT_WRAP + THETA_TOLERANCE))) {
            return "6";
        }

        // Off by more than the threshold, so it doesn't count
        return "";
    }

    @Override
    public void onDraw(Canvas canvas) {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        if (mDownEvent == null) {
            int x = getWidth() / 2;
            int y = (getHeight() / 2) - 35;
            paint.setTextSize(400);
            y -= paint.ascent() / 2;
            canvas.drawText(mPreviousValue, x, y, paint);

            x = 5;
            y = 30;
            paint.setTextSize(50);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText(mDialedNumber, x, y, paint);

            x = 5;
            y = getHeight() - 260;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText("Press MENU for phonebook.", x, y, paint);

            x = 5;
            y = getHeight() - 240;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText("Stroke the screen to dial.", x, y, paint);
            x = 5;

            y = getHeight() - 220;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText("Press CALL twice to confirm.", x, y, paint);
        } else {
            // TODO This is... a lot of code. Figure out a better way to handle
            // drawing the UI.

            final float downX = mDownEvent.getX();
            final float downY = mDownEvent.getY();

            final int offset = mRadiusTolerance * 3;
            final int regSize = 100;
            final int selectedSize = regSize * 2;

            final int x1 = (int) downX - offset;
            int y1 = (int) downY - offset;
            final int x2 = (int) downX;
            int y2 = (int) downY - offset;
            final int x3 = (int) downX + offset;
            int y3 = (int) downY - offset;
            final int x4 = (int) downX - offset;
            int y4 = (int) downY;
            final int x5 = (int) downX;
            int y5 = (int) downY;
            final int x6 = (int) downX + offset;
            int y6 = (int) downY;
            final int x7 = (int) downX - offset;
            int y7 = (int) downY + offset;
            final int x8 = (int) downX;
            int y8 = (int) downY + offset;
            final int x9 = (int) downX + offset;
            int y9 = (int) downY + offset;

            final int xDel = (int) downX - offset - offset;
            int yDel = (int) downY;
            final int xDel2 = (int) downX - offset - offset;
            int yDel2 = (int) downY - offset;
            final int xStar = (int) downX - offset - offset;
            int yStar = (int) downY + offset + offset;
            final int x0 = (int) downX;
            int y0 = (int) downY + offset + offset;
            final int xPound = (int) downX + offset + offset;
            int yPound = (int) downY + offset + offset;

            if (mPreviousValue.equals(DELETE)) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            yDel -= paint.ascent() / 2;
            canvas.drawText("<-", xDel, yDel, paint);

            if (mPreviousValue.equals(DELETE)) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            yDel2 -= paint.ascent() / 2;
            canvas.drawText("<-", xDel2, yDel2, paint);

            if (mPreviousValue.equals("1")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y1 -= paint.ascent() / 2;
            canvas.drawText("1", x1, y1, paint);

            if (mPreviousValue.equals("2")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y2 -= paint.ascent() / 2;
            canvas.drawText("2", x2, y2, paint);

            if (mPreviousValue.equals("3")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y3 -= paint.ascent() / 2;
            canvas.drawText("3", x3, y3, paint);

            if (mPreviousValue.equals("4")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y4 -= paint.ascent() / 2;
            canvas.drawText("4", x4, y4, paint);

            if (mPreviousValue.equals("5")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y5 -= paint.ascent() / 2;
            canvas.drawText("5", x5, y5, paint);

            if (mPreviousValue.equals("6")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y6 -= paint.ascent() / 2;
            canvas.drawText("6", x6, y6, paint);

            if (mPreviousValue.equals("7")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y7 -= paint.ascent() / 2;
            canvas.drawText("7", x7, y7, paint);

            if (mPreviousValue.equals("8")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y8 -= paint.ascent() / 2;
            canvas.drawText("8", x8, y8, paint);

            if (mPreviousValue.equals("9")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y9 -= paint.ascent() / 2;
            canvas.drawText("9", x9, y9, paint);

            if (mPreviousValue.equals("*")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            yStar -= paint.ascent() / 2;
            canvas.drawText("*", xStar, yStar, paint);

            if (mPreviousValue.equals("0")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            y0 -= paint.ascent() / 2;
            canvas.drawText("0", x0, y0, paint);

            if (mPreviousValue.equals("#")) {
                paint.setTextSize(selectedSize);
            } else {
                paint.setTextSize(regSize);
            }
            yPound -= paint.ascent() / 2;
            canvas.drawText("#", xPound, yPound, paint);
        }
    }

    public void callCurrentNumber() {
        if (!mNumberConfirmed) {
            if (mDialedNumber.length() == 0) {
                mParent.switchToContactsView();
            } else if (mDialedNumber.length() < 3) {
                // A number is considered invalid if less than 3 digits in
                // length.
                final String text = getContext().getString(R.string.invalid_number);
                speak(text, TextToSpeech.QUEUE_FLUSH, 100);
            } else {
                final StringBuilder builder = new StringBuilder();

                for (int i = 0; i < mDialedNumber.length(); i++) {
                    builder.append(adjustForSpeech("" + mDialedNumber.charAt(i)) + " ");
                }
                final String text;
                if (mParent.contactsPickerMode) {
                    text = getContext().getString(R.string.you_have_selected, builder.toString());
                } else {
                    text = getContext().getString(
                            R.string.you_are_about_to_dial, builder.toString());
                }
                speak(text, TextToSpeech.QUEUE_FLUSH, 100);
                mNumberConfirmed = true;
            }
        } else {
            mParent.returnResults(mDialedNumber);
        }
    }

    /**
     * @param letterEntered The letter to convert.
     * @return the phone dialer number equivalent for a given letter
     */
    private String convertLetterToNumber(char letterEntered) {
        switch (Character.toLowerCase(letterEntered)) {
            case 'a':
            case 'b':
            case 'c':
                return "2";
            case 'd':
            case 'e':
            case 'f':
                return "3";
            case 'g':
            case 'h':
            case 'i':
                return "4";
            case 'j':
            case 'k':
            case 'l':
                return "5";
            case 'm':
            case 'n':
            case 'o':
                return "6";
            case 'p':
            case 'q':
            case 'r':
            case 's':
                return "7";
            case 't':
            case 'u':
            case 'v':
                return "8";
            case 'w':
            case 'x':
            case 'y':
            case 'z':
                return "9";
            default:
                return null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final char keyLabel = event.getDisplayLabel();

        if (Character.isLetter(keyLabel)) {
            final String digit = convertLetterToNumber(keyLabel);
            onDigitTyped(digit, true);
            return true;
        } else if (Character.isDigit(keyLabel)) {
            String digit = null;
            if ((keyLabel == '3') && (event.isAltPressed() || event.isShiftPressed())) {
                digit = "#";
            } else if ((keyLabel == '8') && (event.isAltPressed() || event.isShiftPressed())) {
                digit = "*";
            } else {
                digit = "" + keyLabel;
            }
            onDigitTyped(digit, true);
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                mParent.switchToContactsView();
                return true;
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_CALL:
            case KeyEvent.KEYCODE_ENTER:
                callCurrentNumber();
                return true;
            case KeyEvent.KEYCODE_DEL:
                deleteNumber();
                return true;
        }

        mNumberConfirmed = false;
        return false;
    }

    private void speakDigitDelayed(String digit, int queueMode, int pitch) {
        mHandler.removeMessages(MSG_SPEAK_DIGIT);

        final Message msg = mHandler.obtainMessage(MSG_SPEAK_DIGIT);
        msg.obj = digit;
        msg.arg1 = queueMode;
        msg.arg2 = pitch;

        mHandler.sendMessageDelayed(msg, DELAY_SPEAK_DIGIT);
    }

    private void speakDigit(String digit, int queueMode, int pitch) {
        mHandler.removeMessages(MSG_SPEAK_DIGIT);
        final String text = adjustForSpeech(digit);
        speak(text, queueMode, pitch);
    }

    private void speak(String text, int queueMode, int pitch) {
        mHandler.removeMessages(MSG_SPEAK_DIGIT);

        if (!TextUtils.isEmpty(text)) {
            // TODO Add support for pitch adjustment.
            mParent.tts.speak(text, queueMode, null);
        }
    }

    @SuppressWarnings("unused")
    private static String convertPitch(int pitch) {
        if (pitch > 175) {
            return "x-high";
        } else if (pitch > 125) {
            return "high";
        } else if (pitch > 75) {
            return "medium";
        } else if (pitch > 25) {
            return "low";
        } else {
            return "x-low";
        }
    }

    /**
     * @param digit The digit to be spoken.
     * @return the text that should be spoken for a given digit
     */
    private String adjustForSpeech(String digit) {
        if ("*".equals(digit)) {
            return getContext().getString(R.string.star);
        } else if ("#".equals(digit)) {
            return getContext().getString(R.string.pound);
        } else if (DELETE.equals(digit)) {
            return getContext().getString(R.string.backspace);
        } else {
            return digit;
        }
    }

    private void deleteNumber() {
        mHandler.removeMessages(MSG_SPEAK_DIGIT);
        mNumberConfirmed = false;

        String text;

        if (mDialedNumber.length() == 0) {
            text = getContext().getString(R.string.nothing_to_delete);
        } else {
            final String substr = mDialedNumber.substring(mDialedNumber.length() - 1);
            final String deletedNum = adjustForSpeech(substr);

            text = getContext().getString(R.string.deleted, deletedNum);

            mDialedNumber = mDialedNumber.substring(0, mDialedNumber.length() - 1);
        }

        speak(text, TextToSpeech.QUEUE_FLUSH, 100);

        mPreviousValue = "";

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
                        speakDigit((String) msg.obj, msg.arg1, msg.arg2);
                    }
                    break;
            }
        }
    }
}
