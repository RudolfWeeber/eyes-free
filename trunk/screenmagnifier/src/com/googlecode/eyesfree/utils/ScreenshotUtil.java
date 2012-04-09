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

package com.googlecode.eyesfree.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.googlecode.eyesfree.compat.view.DisplayCompatUtils;
import com.googlecode.eyesfree.compat.view.SurfaceCompatUtils;

/**
 * A utility for taking screenshots.
 */
public class ScreenshotUtil {
    private static final String TAG = ScreenshotUtil.class.getSimpleName();

    public static boolean hasScreenshotPermission(Context context) {
        if (context.checkCallingOrSelfPermission(Manifest.permission.READ_FRAME_BUFFER)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        return true;
    }

    /**
     * Returns a screenshot of the current display contents.
     */
    public static Bitmap createScreenshot(Context context) {
        final WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final Display display = windowManager.getDefaultDisplay();
        final DisplayMetrics displayMetrics = new DisplayMetrics();

        // We need to orient the screenshot correctly (and the Surface api seems
        // to take screenshots only in the natural orientation of the device :!)
        DisplayCompatUtils.getRealMetrics(display, displayMetrics);

        final Bitmap bitmap = SurfaceCompatUtils.screenshot(0, 0);

        // Bail if we couldn't take the screenshot
        if (bitmap == null) {
            Log.e(TAG, "Failed to take screenshot");
            return null;
        }

        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int rotation = display.getRotation();

        Log.d(TAG, "Took screenshot with resolution (" + width + "," + height + ")");

        final int outWidth;
        final int outHeight;
        final float degrees;

        switch (rotation) {
            case Surface.ROTATION_90:
                outWidth = height;
                outHeight = width;
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                outWidth = width;
                outHeight = height;
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                outWidth = height;
                outHeight = width;
                degrees = 270;
                break;
            default:
                return bitmap;
        }

        // Rotate the screenshot to the screen orientation
        final Bitmap rotatedBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.RGB_565);
        final Canvas c = new Canvas(rotatedBitmap);

        c.translate(outWidth / 2.0f, outHeight / 2.0f);
        c.rotate(-degrees);
        c.translate(-width / 2.0f, -height / 2.0f);
        c.drawBitmap(bitmap, 0, 0, null);

        bitmap.recycle();

        return rotatedBitmap;
    }
}
