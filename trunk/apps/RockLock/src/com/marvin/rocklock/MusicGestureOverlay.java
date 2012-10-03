/*
 * Copyright (C) 2012 Google Inc.
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

package com.marvin.rocklock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

import com.googlecode.eyesfree.compat.view.MotionEventCompatUtils;

import java.lang.reflect.Method;

/**
 * A transparent overlay which catches all touch events and uses a call back to
 * return the gesture that the user performed. Also handles direct-drawing of
 * text to avoid conflicts with touch exploration.
 *
 * @author sainsley@google.com (Sam Ainsley)
 * @author clchen@google.com (Charles L. Chen)
 */

public class MusicGestureOverlay extends TextView {

    /**
     * Handlers for touch exploration
     */
    private AccessibilityManager accessibilityManager;
    private static Method AccessibilityManager_isTouchExplorationEnabled;

    static {
        initCompatibility();
    }

    private static void initCompatibility() {
        try {
            AccessibilityManager_isTouchExplorationEnabled = AccessibilityManager.class
                    .getMethod("isTouchExplorationEnabled");
            /* success, this is a newer device */
        } catch (NoSuchMethodException nsme) {
            /* failure, must be older device */
        }
    }

    /**
     * Constants for the types of stroke gestures
     */
    public static class Gesture {

        public static final int CENTER = 0;

        public static final int UPLEFT = 1;

        public static final int UP = 2;

        public static final int UPRIGHT = 3;

        public static final int RIGHT = 4;

        public static final int DOWNRIGHT = 5;

        public static final int DOWN = 6;

        public static final int DOWNLEFT = 7;

        public static final int LEFT = 8;

        public static final int DOUBLE_TAP = 9;

        public static final int NONE = -1;
    }

    /**
     * The callback interface to be used when a gesture is detected.
     */
    public interface GestureListener {

        // One finger gesture handlers
        public void onGestureStart(int g);

        public void onGestureChange(int gCur, int gPrev);

        public void onGestureFinish(int g);

        public void onGestureHold(int g);

        // Two finger gesture handlers
        public void onGesture2Change(int gCur, int gPrev);

        public void onGesture2Finish(int g);

        public void onGestureHold2(int g);
    }

    // Drawing constants
    private static final int KEYBOARD_OFFSET = 90;

    // Note: all letter wheel logic is used for drawing
    // TODO(sainsley): move all wheel logic to one class or the other
    private static final int GROUP_AE = 0;
    private static final int GROUP_IM = 1;
    private static final int GROUP_QU = 2;
    private static final int GROUP_Y = 4;
    private static final int GROUP_NONE = 5;

    private static final double LEFT_THETA = 0;
    private static final double UPLEFT_THETA = Math.PI * .25;
    private static final double UP_THETA = Math.PI * .5;
    private static final double UPRIGHT_THETA = Math.PI * .75;
    private static final double DOWNRIGHT_THETA = -Math.PI * .75;
    private static final double DOWN_THETA = -Math.PI * .5;
    private static final double DOWNLEFT_THETA = -Math.PI * .25;
    private static final double RIGHT_THETA = Math.PI;
    private static final double RIGHTWRAP_THETA = -Math.PI;

    // tolerances
    private static final double THETA_TOL = (Math.PI / 12);
    private static final int CHANGE_TOL = 15;
    private static final int LINEAR_TOL = 30;
    private static final long DOUBLE_TAP_WINDOW = 400;

    // detection options
    boolean mIsTouchExploring = false;
    boolean mHasCircularGesture;

    // classification types
    private boolean mIsMultiTouch;
    private boolean mIsCircular;
    private boolean mShowKeyboard;

    // gesture bookkeeping
    private int mCurrentGesture;

    private double mDownX;
    private double mDownY;
    private double mDownX2;
    private double mDownY2;
    private boolean mIsTap;
    private long mLastTapTime;

    private int mCurrentWheel;
    private String mCurrentCharacter;

    private GestureListener mCallback;
    private final GestureHandler mGestureHandler;

    /**
     * Creates the gesture overlay
     *
     * @param context the parent context
     * @param attrs the attribute set from the parent
     */
    public MusicGestureOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Check is touch exploration is on
        accessibilityManager = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);

        try {
            if (AccessibilityManager_isTouchExplorationEnabled != null) {
                Object retobj = AccessibilityManager_isTouchExplorationEnabled
                        .invoke(accessibilityManager);
                mIsTouchExploring = (Boolean) retobj;
            }
        } catch (Exception e) {
            Log.e("MusicGestureOverlay", "Failed to get Accessibility Manager " + e.toString());
        }

        mGestureHandler = new GestureHandler();
    }

    /**
     * Sets the gesture listener for this view
     *
     * @param callback the listener to handle gestures returned from this class
     * @param circularGesture whether or not we support circular gestures
     */
    public void setGestureListener(GestureListener callback, boolean circularGesture) {
        mCallback = callback;
        mHasCircularGesture = circularGesture;
        mShowKeyboard = false;

        mLastTapTime = 0;
        mCurrentWheel = -1;
    }

    /**
     * Forward hover events for compatibility with explore by touch
     *
     * @return result of onTouchEvent
     */
    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return onTouchEvent(event);
    }

    /**
     * Handles gestures as MotionEvents
     *
     * @return true if successful
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mCallback == null) {
            return false;
        }

        int prevGesture = Gesture.NONE;

        int action = event.getAction();
        float x = event.getX();
        float y = event.getY();

        int count = getPointerCount(event);
        int pointerIdx = (action & MotionEvent.ACTION_POINTER_INDEX_MASK)
                >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;

        // Detect multi-touch gestures
        if (count > 1) {
            mIsMultiTouch = true;
            mCurrentWheel = -1;
            invalidate();
            if (pointerIdx > 0 && action == MotionEvent.ACTION_MOVE) {
                return true;
            }
        }

        switch (action & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_POINTER_UP:
                // remap down x and y if we lose a pointer
                if (pointerIdx == 0) {
                    mDownX = mDownX2;
                    mDownY = mDownY2;
                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // keep track of down x and y for second finger
                mDownX2 = event.getX(pointerIdx);
                mDownY2 = event.getY(pointerIdx);
                break;

            case MotionEventCompatUtils.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_DOWN:
                // initiate motion
                mGestureHandler.startLongPressTimeout();
                mDownX = x;
                mDownY = y;
                mIsTap = true;
                mCurrentGesture = evalMotion(x, y);
                mIsCircular = false;
                if (mCallback != null) {
                    mCallback.onGestureStart(mCurrentGesture);
                }
                break;

            case MotionEventCompatUtils.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_MOVE:
                // evaluate change
                prevGesture = mCurrentGesture;
                mCurrentGesture = evalMotion(x, y);

                // Do some correction if the user lifts up on deadspace
                if (mCurrentGesture == Gesture.NONE) {
                    mCurrentGesture = prevGesture;
                    break;
                }
                if (prevGesture != mCurrentGesture) {
                    // Cancel long press or preview since we are not holding
                    // or tapping within a preview window
                    mGestureHandler.cancelLongPress();
                    if (mCallback != null) {
                        if (!mIsMultiTouch) {
                            mCallback.onGestureChange(mCurrentGesture, prevGesture);
                        } else {
                            mCallback.onGesture2Change(mCurrentGesture, prevGesture);
                        }
                    }
                }
                break;

            case MotionEventCompatUtils.ACTION_HOVER_EXIT:
            case MotionEvent.ACTION_UP:
                prevGesture = mCurrentGesture;
                mCurrentGesture = evalMotion(x, y);
                // Do some correction if the user lifts up on deadspace
                if (mCurrentGesture == Gesture.NONE) {
                    mCurrentGesture = prevGesture;
                }
                // Finish current gesture
                if (mCallback != null) {
                    // Handle double-tap vs. tap
                    if (mIsTap) {
                        boolean isDoubleTap = !mIsTouchExploring
                                && (mLastTapTime + DOUBLE_TAP_WINDOW > System.currentTimeMillis());
                        isDoubleTap = isDoubleTap || mIsTouchExploring
                                && action == MotionEvent.ACTION_UP;
                        if (isDoubleTap) {
                            mCurrentGesture = Gesture.DOUBLE_TAP;
                            mGestureHandler.cancelDoubleTapWindow();
                        } else if (!mIsTouchExploring) {
                            mLastTapTime = System.currentTimeMillis();
                            mGestureHandler.startDoubleTapWindow();
                            mGestureHandler.cancelLongPress();
                            return true;
                        }
                    }
                    if (!mIsMultiTouch) {
                        mCallback.onGestureFinish(mCurrentGesture);
                    } else {
                        mCallback.onGesture2Finish(mCurrentGesture);
                    }

                    mGestureHandler.cancelLongPress();
                }
                mIsMultiTouch = false;
                invalidate();
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    /**
     * Evaluates the current pointer position relative to the touchdown position
     *
     * @param x
     * @param y
     * @return the current gesture
     */
    public int evalMotion(double x, double y) {
        float rTolerance = CHANGE_TOL;

        double deltaX = (mDownX - x) * (mDownX - x);
        double deltaY = (mDownY - y) * (mDownY - y);
        double r = Math.sqrt(deltaX + deltaY);

        double theta = Math.atan2(mDownY - y, mDownX - x);

        // Check if gesture is close to down point
        if (r < rTolerance) {
            return Gesture.CENTER;
        }

        // We have moved: tap not possible
        mIsTap = false;

        // Check if our gesture is linear, otherwise expect a circle
        rTolerance = LINEAR_TOL;
        boolean isLinear = deltaY < rTolerance || deltaX < rTolerance;
        if (!isLinear && mHasCircularGesture && !mShowKeyboard) {
            mIsCircular = true;
        } else if (mIsCircular) {
            // We are now axis-aligned with our original point and can
            // update our center accordingly
            mDownX = (mDownX + x) / 2.0;
            mDownY = (mDownY + y) / 2.0;
        }

        if (Math.abs(theta - LEFT_THETA) < THETA_TOL) {
            return Gesture.LEFT;
        } else if (Math.abs(theta - UPLEFT_THETA) < THETA_TOL) {
            return Gesture.UPLEFT;
        } else if (Math.abs(theta - UP_THETA) < THETA_TOL) {
            return Gesture.UP;
        } else if (Math.abs(theta - UPRIGHT_THETA) < THETA_TOL) {
            return Gesture.UPRIGHT;
        } else if (Math.abs(theta - DOWNRIGHT_THETA) < THETA_TOL) {
            return Gesture.DOWNRIGHT;
        } else if (Math.abs(theta - DOWN_THETA) < THETA_TOL) {
            return Gesture.DOWN;
        } else if (Math.abs(theta - DOWNLEFT_THETA) < THETA_TOL) {
            return Gesture.DOWNLEFT;
        } else if ((theta > RIGHT_THETA - THETA_TOL)
                || (theta < RIGHTWRAP_THETA + THETA_TOL)) {
            return Gesture.RIGHT;
        }
        // Off by more than the threshold, so it doesn't count
        return Gesture.NONE;
    }

    /**
     * Returns number of fingers making the current gesture, taking touch
     * exploration into account
     *
     * @param event
     * @return count
     */
    private int getPointerCount(MotionEvent event) {

        // No touch exploration : we are good
        if (!mIsTouchExploring) {
            return event.getPointerCount();
        }

        // Hover events are always single-touch
        if (event.getAction() == MotionEventCompatUtils.ACTION_HOVER_ENTER
                || event.getAction() == MotionEventCompatUtils.ACTION_HOVER_EXIT
                || event.getAction() == MotionEventCompatUtils.ACTION_HOVER_MOVE) {
            return event.getPointerCount();
        }

        // All other events subtract one, so add to compensate
        return event.getPointerCount() + 1;
    }

    //
    // DRAW METHODS
    //

    /**
     * Toggles weather or not we draw the eyes-free keyboard
     */
    public void toggleKeyboard() {
        mShowKeyboard = !mShowKeyboard;
    }

    public void updateKeyboard(int currentWheel, String currentCharacter) {
        this.mCurrentWheel = currentWheel;
        this.mCurrentCharacter = currentCharacter;
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(50);

        if (!mShowKeyboard) {
            return;
        }

        final Point p1 = new Point((int) mDownX - KEYBOARD_OFFSET, (int) mDownY - KEYBOARD_OFFSET);
        final Point p2 = new Point((int) mDownX, (int) mDownY - KEYBOARD_OFFSET);
        final Point p3 = new Point((int) mDownX + KEYBOARD_OFFSET, (int) mDownY - KEYBOARD_OFFSET);
        final Point p4 = new Point((int) mDownX - KEYBOARD_OFFSET, (int) mDownY);
        final Point p6 = new Point((int) mDownX + KEYBOARD_OFFSET, (int) mDownY);
        final Point p7 = new Point((int) mDownX - KEYBOARD_OFFSET, (int) mDownY + KEYBOARD_OFFSET);
        final Point p8 = new Point((int) mDownX, (int) mDownY + KEYBOARD_OFFSET);
        final Point p9 = new Point((int) mDownX + KEYBOARD_OFFSET, (int) mDownY + KEYBOARD_OFFSET);

        p1.y -= paint.ascent() / 2;
        p2.y -= paint.ascent() / 2;
        p3.y -= paint.ascent() / 2;
        p4.y -= paint.ascent() / 2;
        p6.y -= paint.ascent() / 2;
        p7.y -= paint.ascent() / 2;
        p8.y -= paint.ascent() / 2;
        p9.y -= paint.ascent() / 2;

        switch (mCurrentWheel) {
            case GROUP_AE:
                paint.setColor(Color.RED);
                drawCharacter("A", p1.x, p1.y, canvas, paint);
                drawCharacter("B", p2.x, p2.y, canvas, paint);
                drawCharacter("C", p3.x, p3.y, canvas, paint);
                drawCharacter("H", p4.x, p4.y, canvas, paint);
                drawCharacter("D", p6.x, p6.y, canvas, paint);
                drawCharacter("G", p7.x, p7.y, canvas, paint);
                drawCharacter("F", p8.x, p8.y, canvas, paint);
                drawCharacter("E", p9.x, p9.y, canvas, paint);
                break;
            case GROUP_IM:
                paint.setColor(Color.BLUE);
                drawCharacter("P", p1.x, p1.y, canvas, paint);
                drawCharacter("I", p2.x, p2.y, canvas, paint);
                drawCharacter("J", p3.x, p3.y, canvas, paint);
                drawCharacter("O", p4.x, p4.y, canvas, paint);
                drawCharacter("K", p6.x, p6.y, canvas, paint);
                drawCharacter("N", p7.x, p7.y, canvas, paint);
                drawCharacter("M", p8.x, p8.y, canvas, paint);
                drawCharacter("L", p9.x, p9.y, canvas, paint);
                break;
            case GROUP_QU:
                paint.setColor(Color.GREEN);
                drawCharacter("W", p1.x, p1.y, canvas, paint);
                drawCharacter("X", p2.x, p2.y, canvas, paint);
                drawCharacter("Q", p3.x, p3.y, canvas, paint);
                drawCharacter("V", p4.x, p4.y, canvas, paint);
                drawCharacter("R", p6.x, p6.y, canvas, paint);
                drawCharacter("U", p7.x, p7.y, canvas, paint);
                drawCharacter("T", p8.x, p8.y, canvas, paint);
                drawCharacter("S", p9.x, p9.y, canvas, paint);
                break;
            case GROUP_Y:
                paint.setColor(Color.YELLOW);
                drawCharacter(",", p1.x, p1.y, canvas, paint);
                drawCharacter("!", p2.x, p2.y, canvas, paint);
                drawCharacter("<-", p4.x, p4.y, canvas, paint);
                drawCharacter("Y", p6.x, p6.y, canvas, paint);
                drawCharacter(".", p7.x, p7.y, canvas, paint);
                drawCharacter("?", p8.x, p8.y, canvas, paint);
                drawCharacter("Z", p9.x, p9.y, canvas, paint);
                break;
            case GROUP_NONE:
                paint.setColor(Color.RED);
                canvas.drawText("A", p1.x, p1.y, paint);
                canvas.drawText("E", p9.x, p9.y, paint);
                paint.setColor(Color.BLUE);
                canvas.drawText("I", p2.x, p2.y, paint);
                canvas.drawText("M", p8.x, p8.y, paint);
                paint.setColor(Color.GREEN);
                canvas.drawText("Q", p3.x, p3.y, paint);
                canvas.drawText("U", p7.x, p7.y, paint);
                paint.setColor(Color.YELLOW);
                canvas.drawText("Y", p6.x, p6.y, paint);
                canvas.drawText("<-", p4.x, p4.y, paint);
                break;
        }
    }

    private void drawCharacter(String character, int x, int y, Canvas canvas, Paint paint) {
        final int regSize = 50;
        final int selectedSize = regSize * 2;

        if (character.equals(mCurrentCharacter)) {
            paint.setTextSize(selectedSize);
        } else {
            paint.setTextSize(regSize);
        }

        canvas.drawText(character, x, y, paint);
    }

    /**
     * Handles long presses following gestures that indicate the user wants to
     * hear an audio preview before executing the command. Also handles the
     * preview window after the long press during which time the user can
     * execute the preview gesture with a tap. Lastly, handles single taps that
     * could potentially be double taps before the window of time closes.
     */
    private class GestureHandler extends Handler {
        // Long press handler
        private static final int MSG_LONG_PRESS_TIMEOUT = 1;
        private static final int LONG_PRESS_DELAY = 1500;

        // Double-tap window handler
        private static final int MSG_DOUBLE_TAP_TIMEOUT = 3;

        /**
         * Handles a message For long-press timeout : Call onGestureHold on
         * callback For double-tap timeout : Call onGestureFinish on callback
         */
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_LONG_PRESS_TIMEOUT) {
                if (mIsMultiTouch) {
                    mCallback.onGestureHold2(mCurrentGesture);
                } else {
                    mCallback.onGestureHold(mCurrentGesture);
                }
            } else if (msg.what == MSG_DOUBLE_TAP_TIMEOUT) {
                if (mIsMultiTouch) {
                    mCallback.onGesture2Finish(mCurrentGesture);
                } else {
                    mCallback.onGestureFinish(mCurrentGesture);
                }
            }
        }

        /**
         * Sends the delayed double-tap timeout message
         */
        public void startDoubleTapWindow() {
            sendEmptyMessageDelayed(MSG_DOUBLE_TAP_TIMEOUT, DOUBLE_TAP_WINDOW);
        }

        /**
         * Cancels the double-tap message
         */
        public void cancelDoubleTapWindow() {
            removeMessages(MSG_DOUBLE_TAP_TIMEOUT);
        }

        /**
         * Sends the long-press timeout message
         */
        public void startLongPressTimeout() {
            removeMessages(MSG_LONG_PRESS_TIMEOUT);
            sendEmptyMessageDelayed(MSG_LONG_PRESS_TIMEOUT, LONG_PRESS_DELAY);
        }

        /**
         * Cancels the long-press message
         */
        public void cancelLongPress() {
            removeMessages(MSG_LONG_PRESS_TIMEOUT);
        }
    }
}
