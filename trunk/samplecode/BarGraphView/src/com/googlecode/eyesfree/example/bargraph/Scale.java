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

package com.googlecode.eyesfree.example.bargraph;

/**
 * Scales map an input domain to an output range. Methods of a Scale object
 * typically return the scale itself, so that methods calls may be chained.
 * <p>
 * For example,
 *
 * <pre>
 * Scale myScale = new Scale.Linear().range(0, 1).domain(0, 100);
 * float myValue = myScale.transform(0.5);
 * </pre>
 * <p>
 * This class is modeled after the d3.scale API, available at:
 * https://github.com/mbostock/d3/wiki/Scales
 */
public abstract class Scale {
    /** The input domain used to scale values. */
    protected float[] mDomain = new float[] { 0, 1 };

    /** The output range used to scale values. */
    protected float[] mRange = new float[] { 0, 1 };

    /**
     * Specifies the input domain.
     */
    public final Scale domain(float start, float end) {
        mDomain[0] = start;
        mDomain[1] = end;
        update();
        return this;
    }

    /**
     * Specifies the output range.
     */
    public final Scale range(float start, float end) {
        mRange[0] = start;
        mRange[1] = end;
        update();
        return this;
    }

    /**
     * Transforms multiple values using the scaling function.
     *
     * @see #transform(float)
     */
    public final float[] transform(float... values) {
        final float[] result = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = transform(values[i]);
        }

        return result;
    }

    /**
     * Transforms multiple values using the scaling function.
     *
     * @see #invert(float)
     */
    public final float[] invert(float... values) {
        final float[] result = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = invert(values[i]);
        }

        return result;
    }

    /**
     * Given a value <code>x</code> in the input domain, returns the
     * corresponding value in the output range.
     */
    public abstract float transform(float x);

    /**
     * Given a value <code>y</code> in the output range, returns the
     * corresponding value in the input domain.
     */
    public abstract float invert(float y);

    /**
     * Optional helper method called when the range or domain is updated.
     */
    protected void update() {
        // Default implementation does nothing.
    }

    /**
     * A scale that represents a linear mapping. The output range value
     * <code>y</code> can be expressed as a linear function of the input domain
     * value <code>x</code> as <code>y = mx + b</code>.
     * <p>
     * The input domain is typically a dimension of the data, such as the
     * minimum and maximum values in a data array. The output range is
     * typically a dimension of the resulting visualization, such as the left
     * and right bounds of a {@link android.view.View}.
     */
    public static class Linear extends Scale {
        private float mScale = 1;

        /**
         * Constructs a new linear scale with a default domain of
         * <code>[0,1]</code> and a default range of <code>[0,1]</code>.
         */
        public Linear() {
            super();
        }

        @Override
        public float transform(float x) {
            return (((x - mDomain[0]) * mScale) + mRange[0]);
        }

        @Override
        public float invert(float y) {
            return (((y - mRange[0]) / mScale) + mDomain[0]);
        }

        @Override
        protected void update() {
            mScale = (mRange[1] - mRange[0]) / (mDomain[1] - mDomain[0]);
        }
    }
}
