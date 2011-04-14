/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.widget;

import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

/**
 * @author alanv@google.com (Alan Viverette)
 */
public abstract class FlickGestureListener extends SimpleOnGestureListener {
    private final int FLICK_TIMEOUT = 250;

    // constants for flick directions
    public static final int FLICK_UP = 0;
    public static final int FLICK_RIGHT = 1;
    public static final int FLICK_LEFT = 2;
    public static final int FLICK_DOWN = 3;

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        float y = e.getY();
        float x = e.getX();
        float rawX = e.getRawX();
        float rawY = e.getRawY();

        return onSingleTap(x, y, rawX, rawY);
    }

    protected abstract boolean onSingleTap(float x, float y, float rawX, float rawY);

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        float y = e.getY();
        float x = e.getX();
        float rawX = e.getRawX();
        float rawY = e.getRawY();

        return onDoubleTap(x, y, rawX, rawY);
    }

    protected abstract boolean onDoubleTap(float x, float y, float rawX, float rawY);

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        long duration = e2.getEventTime() - e1.getDownTime();

        if (duration < FLICK_TIMEOUT) {
            return false;
        }

        float y = e2.getY();
        float x = e2.getX();
        float rawX = e2.getRawX();
        float rawY = e2.getRawY();

        return onMove(x, y, rawX, rawY);
    }

    protected abstract boolean onMove(float x, float y, float rawX, float rawY);

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        long duration = e2.getEventTime() - e1.getDownTime();

        if (duration > FLICK_TIMEOUT) {
            return false;
        }

        float y = e1.getY();
        float x = e1.getX();
        float rawX = e1.getRawX();
        float rawY = e1.getRawY();

        float distanceY = e2.getY() - y;
        float distanceX = e2.getX() - x;

        boolean a = (distanceY > distanceX);
        boolean b = (distanceY > -distanceX);

        int direction = (a ? 2 : 0) | (b ? 1 : 0);

        return onFlick(x, y, rawX, rawY, direction);
    }

    protected abstract boolean onFlick(float x, float y, float rawX, float rawY, int direction);
}
