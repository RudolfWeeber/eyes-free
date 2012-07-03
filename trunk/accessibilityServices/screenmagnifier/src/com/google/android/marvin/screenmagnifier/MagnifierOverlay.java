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

package com.google.android.marvin.screenmagnifier;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.google.android.marvin.screenmagnifier.FilteredImageView.MagnifierListener;
import com.googlecode.eyesfree.utils.ScreenshotUtil;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;
import com.googlecode.eyesfree.widget.SimpleOverlay;

public class MagnifierOverlay extends SimpleOverlay {
    private final FilteredImageView mContent;
    private final SharedPreferences mPrefs;

    public MagnifierOverlay(Context context) {
        super(context);

        final WindowManager.LayoutParams params = getParams();
        params.format = PixelFormat.OPAQUE;
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        params.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        setParams(params);

        setContentView(R.layout.magnifier);

        getRootView().setOnKeyListener(mOnKeyListener);

        mContent = (FilteredImageView) findViewById(R.id.magnifier);
        mContent.setListener(mMagnifierListener);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mPrefs.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        refreshPreferences(mPrefs, null);
    }

    @Override
    public void onShow() {
        final Context context = getContext();
        final Bitmap bitmap = ScreenshotUtil.createScreenshot(context);
        final BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);

        drawable.setTargetDensity(bitmap.getDensity());
        mContent.setImageDrawable(drawable);
    }

    private void refreshPreferences(SharedPreferences sharedPreferences, String key) {
        final Resources res = getContext().getResources();

        final boolean contrast =
                SharedPreferencesUtils.getBooleanPref(sharedPreferences, res,
                        R.string.pref_contrast_key, R.bool.pref_contrast_default);
        final boolean invert =
                SharedPreferencesUtils.getBooleanPref(sharedPreferences, res,
                        R.string.pref_invert_key, R.bool.pref_invert_default);

        mContent.setEnhanceContrast(contrast);
        mContent.setInvertBrightness(invert);
    }

    private final OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
            new OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                        String key) {
                    refreshPreferences(sharedPreferences, key);
                }
            };

    private final View.OnKeyListener mOnKeyListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            hide();

            return false;
        }
    };

    private final MagnifierListener mMagnifierListener = new MagnifierListener() {
        @Override
        public void onMagnificationStarted(FilteredImageView view) {
            // Do nothing.
        }

        @Override
        public void onMagnificationFinished(FilteredImageView view) {
            hide();
        }

        @Override
        public void onSingleTap(FilteredImageView view, MotionEvent downEvent, MotionEvent upEvent) {
            hide();
        }

        @Override
        public void onInvalidated(FilteredImageView view) {
            hide();
        }
    };
}
