/*
 * Copyright (C) 2013 Google Inc.
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

package com.googlecode.eyesfree.utils;

import android.graphics.PointF;

/**
 * Utilities for math operations.
 */
public class MathUtils {
    /**
     * Performs modulo division using Knuth's floored division algorithm.
     * Returns a result with the same sign as the divisor.
     * <p>
     * This is distinct from Java's modulo (%) operator, which uses truncated
     * division and returns a result with the same sign as the dividend.
     *
     * @param dividend The number to divide.
     * @param divisor The number by which to divide.
     * @return The remainder of division of the {@code dividend} by the
     *         {@code divisor} with the same sign as the {@code divisor}.
     */
    public static int flooredModulo(int dividend, int divisor) {
        return (dividend - (divisor * (dividend / divisor)));
    }

    /**
     * Constrains an amount between upper and lower bounds, inclusive.
     *
     * @param amount The amount to constrain.
     * @param lower The lower bound, inclusive.
     * @param upper The upper bound, inclusive.
     * @return The closest amount between the {@code upper} and {@code lower}
     *         bounds.
     */
    public static int constrain(int amount, int lower, int upper) {
        return ((amount < lower) ? lower : ((amount > upper) ? upper : amount));
    }

    /**
     * Constrains an amount between upper and lower bounds, inclusive.
     *
     * @param amount The amount to constrain.
     * @param lower The lower bound, inclusive.
     * @param upper The upper bound, inclusive.
     * @return The closest amount between the {@code upper} and {@code lower}
     *         bounds.
     */
    public static long constrain(long amount, long lower, long upper) {
        return ((amount < lower) ? lower : ((amount > upper) ? upper : amount));
    }

    /**
     * Constrains an amount between upper and lower bounds, inclusive.
     *
     * @param amount The amount to constrain.
     * @param lower The lower bound, inclusive.
     * @param upper The upper bound, inclusive.
     * @return The closest amount between the {@code upper} and {@code lower}
     *         bounds.
     */
    public static float constrain(float amount, float lower, float upper) {
        return ((amount < lower) ? lower : ((amount > upper) ? upper : amount));
    }

    /**
     * Returns the squared distance between a point and an (x,y) coordinate.
     *
     * @param p The point.
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @return The squared distance between the point and (x,y) coordinate.
     */
    public static float distSq(PointF p, float x, float y) {
        final float dX = (x - p.x);
        final float dY = (y - p.y);
        return ((dX * dX) + (dY * dY));
    }

    /**
     * Returns the length of an arc defined by an angle and radius.
     *
     * @param angle The angle of the arc, in degrees.
     * @param radius The radius of the arc.
     * @return The length of the arc.
     */
    public static float arcLength(float angle, float radius) {
        return ((2.0f * (float) Math.PI * radius) * (angle / 360.0f));
    }

    /**
     * Returns the angle of an arc defined by a length and radius.
     *
     * @param length The length of the arc.
     * @param radius The radius of the arc.
     * @return The angle of the arc, in degrees.
     */
    public static float arcAngle(float length, float radius) {
        return (360.0f * (length / (2.0f * (float) Math.PI * radius)));
    }
}
