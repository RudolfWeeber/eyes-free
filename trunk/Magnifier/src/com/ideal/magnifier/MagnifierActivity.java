/*
 * Copyright (C) 2010 The IDEAL Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ideal.magnifier;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.SurfaceHolder.Callback;
import android.widget.LinearLayout;

import java.io.IOException;
import java.util.List;

/**
 * Main activity for IDEAL Magnifier. Uses the phone's camera to turn Android
 * into a video magnifier. Volume buttons to zoom in/out Search button to turn
 * on the LED light Menu to bring up the color filter options If the image is
 * blurry, just tap the screen and it will refocus.
 */
public class MagnifierActivity extends Activity implements Callback {
    private SurfaceHolder mHolder;

    private MagnificationView mPreview;

    private Camera mCamera;

    private int mZoom = 0;

    private boolean mTorch = false;

    // This class lies about our actual screen size, thus giving us a 2x digital
    // zoom to start with even before we invoke the hardware zoom features of
    // the camera.
    public class MagnificationView extends SurfaceView {
        public MagnificationView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            Display display = getWindowManager().getDefaultDisplay();
            int width = display.getWidth();
            int height = display.getHeight();
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(width * 2, MeasureSpec.EXACTLY);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height * 2, MeasureSpec.EXACTLY);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        mPreview = new MagnificationView(this);
        LinearLayout rootView = (LinearLayout) findViewById(R.id.rootView);
        rootView.addView(mPreview);
        mHolder = mPreview.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mCamera != null) {
            Parameters params = mCamera.getParameters();
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                mZoom = mZoom + 1;
                if (mZoom > params.getMaxZoom()) {
                    mZoom = params.getMaxZoom();
                }
                params.setZoom(mZoom);
                setParams(params);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                mZoom = mZoom - 1;
                if (mZoom < 0) {
                    mZoom = 0;
                }
                params.setZoom(mZoom);
                setParams(params);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_SEARCH) {
                if (mTorch) {
                    params.setFlashMode(Parameters.FLASH_MODE_AUTO);
                    mTorch = false;
                } else {
                    params.setFlashMode(Parameters.FLASH_MODE_TORCH);
                    mTorch = true;
                }
                setParams(params);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mCamera != null) {
            mCamera.autoFocus(null);
        }
        return super.onTouchEvent(event);
    }

    private void setParams(Parameters params) {
        try {
            mCamera.setParameters(params);
            mCamera.autoFocus(null);
        } catch (RuntimeException e) {
            // Do nothing - sometimes the hardware seems to just be too slow.
            // Catching this exception will at least prevent a force close - the
            // user will just have to repeat the action.
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        mCamera = Camera.open();
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        mHolder.setFixedSize(w, h);

        Parameters params = mCamera.getParameters();
        mZoom = params.getMaxZoom() / 2;
        params.setZoom(mZoom);
        setParams(params);

        mCamera.startPreview();
    }

    public void showEffectsList() {
        if (mCamera == null) {
            return;
        }

        List<String> effectsList = mCamera.getParameters().getSupportedColorEffects();
        String[] effects = {
            ""
        };
        effects = effectsList.toArray(effects);
        final String[] items = effects;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Color Effect");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                Parameters params = mCamera.getParameters();
                params.setColorEffect(items[item]);
                setParams(params);
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
        alert.setOnCancelListener(new OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // Parameters for menu.add are:
        // group -- Not used here.
        // id -- Used only when you want to handle and identify the click
        // yourself.
        // title
        menu.add(0, 0, 0, "Choose color effect").setIcon(android.R.drawable.ic_menu_manage);
        menu.add(0, 1, 0, "More apps").setIcon(android.R.drawable.ic_menu_search);
        return true;
    }

    // Activity callback that lets your handle the selection in the class.
    // Return true to indicate that you've got it, false to indicate
    // that it should be handled by a declared handler object for that
    // item (handler objects are discouraged for reasons of efficiency).
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                showEffectsList();
                return true;
            case 1:
                String marketUrl = "market://search?q=pub:\"IDEAL Group, Inc. Android Development Team\"";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(marketUrl));
                startActivity(i);
                return true;
        }
        return false;
    }
}
