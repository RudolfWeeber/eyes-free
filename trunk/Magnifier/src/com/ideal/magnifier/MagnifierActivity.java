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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
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

    /**
     * The user interaction cool down period in milliseconds. The camera's
     * autoFocus method is invoked after this delay if no further user
     * interactions occur.
     */
    public static final long FOCUS_INTERACTION_TIMEOUT_THRESHOLD = 750;

    /**
     * Runnable used to defer camera focusing until user interaction has stopped
     * for FOCUS_INTERACTION_TIMEOUT_THRESHOLD milliseconds.
     */
    private Runnable mFocuserLocked;

    /**
     * A flag indicating whether the camera is currently performing an
     * auto-focus operation.
     */
    private boolean mCameraFocusing = false;

    /**
     * A flag indicating whether or not a user interaction occurred during the
     * last camera focus event. Used to defer updating of camera parameters
     * until the focus operation completes.
     */
    private boolean mParameterSettingDeferred = false;

    /**
     * The set of camera parameters to be applied after a focus operation
     * completes.
     */
    private Parameters mDeferredParameters = null;

    /**
     * Surface used to project the camera preview.
     */
    private SurfaceHolder mHolder = null;

    /**
     * Abstracted SurfaceView used to further magnify the camera preview frames.
     */
    private MagnificationView mPreview = null;

    /**
     * The system's camera hardware
     */
    private Camera mCamera;

    /**
     * The current zoom level
     */
    private int mZoom = 0;

    /**
     * Flag indicating the current state of the camera flash LED.
     */
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
            // TODO: This method miscalculates the dimensions of the projected
            // screen, causing a horizontal stretch effect.
            Display display = getWindowManager().getDefaultDisplay();
            int width = display.getWidth();
            int height = display.getHeight();
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);            
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
        mPreview.setKeepScreenOn(true);
        mHolder = mPreview.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mFocuserLocked = new Runnable() {
            @Override
            public void run() {
                // This Runnable will focus the camera, and guarantee that calls
                // to Camera.autoFocus are synchronous.
                if (mCamera != null && !mCameraFocusing) {
                    mCameraFocusing = true;
                    mCamera.autoFocus(new AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
                            mCameraFocusing = false;
                            if (mParameterSettingDeferred) {
                                // The user attempted to change a camera
                                // parameter during the focus event, set the
                                // parameters again so the user's change
                                // takes effect.
                                setParams(mDeferredParameters);
                                mParameterSettingDeferred = false;
                            }
                        }
                    });
                }
            }
        };
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
        mFocuserLocked.run();
        return super.onTouchEvent(event);
    }

    private void setParams(Parameters params) {
        // Setting the parameters of the camera is only a safe operation if the
        // camera is not presently focusing. Only focus the camera after the
        // user interaction has quieted down, verified by a delay period.
        mPreview.removeCallbacks(mFocuserLocked);
        if (!mCameraFocusing) {
            // On some phones such as the Motorola Droid, the preview needs to
            // be stopped and then restarted.
            // Failing to do so will result in an ANR (application not
            // responding) error.
            //
            // TODO: Check if it is possible to detect when a restart is needed
            // by checking isSmoothZoomSupported in the Camera Parameters.
            //
            // Nexus One: False (does not need a restart)
            // Motorola Droid: True (must have preview restarted)
            //             
            // Log.e("smooth zoom?", params.isSmoothZoomSupported() + "");
            mCamera.stopPreview();
            mCamera.startPreview();
            mCamera.setParameters(params);
        } else {
            mParameterSettingDeferred = true;
            mDeferredParameters = params;
        }
        mPreview.postDelayed(mFocuserLocked, FOCUS_INTERACTION_TIMEOUT_THRESHOLD);
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

        mCamera.startPreview();

        // Note: Parameters are not safe to set until AFTER the preview is up.
        // One some phones, it is OK (such as the Nexus One), but on others,
        // (such as the Motorola Droid), this will cause the parameters to not
        // actually change/may lead to an ANR on a subsequent set attempt.
        Parameters params = mCamera.getParameters();
        mZoom = params.getMaxZoom() / 2;
        
        params.setZoom(mZoom);
        setParams(params);
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
        builder.setTitle(getString(R.string.color_effect_dialog_title));
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                Parameters params = mCamera.getParameters();
                params.setColorEffect(items[item]);
                setParams(params);
            }
        });
        builder.create().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // Parameters for menu.add are:
        // group -- Not used here.
        // id -- Used only when you want to handle and identify the click
        // yourself.
        // title
        menu.add(0, 0, 0, getString(R.string.color_effect_button_text)).setIcon(
                android.R.drawable.ic_menu_manage);
        menu.add(0, 1, 0, getString(R.string.more_apps_button_text)).setIcon(
                android.R.drawable.ic_menu_search);
        return true;
    }
    
    /**
     * Activity callback that lets your handle the selection in the class.
     * Return true to indicate that you've got it, false to indicate that it
     * should be handled by a declared handler object for that item (handler
     * objects are discouraged for reasons of efficiency).
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                showEffectsList();
                return true;
            case 1:
                String marketUrl = 
                    "market://search?q=pub:\"IDEAL Group, Inc. Android Development Team\"";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(marketUrl));
                try {
                    startActivity(i);
                } catch (ActivityNotFoundException anf) {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.market_launch_error_title))
                            .setMessage(getString(R.string.market_launch_error_text))
                            .setNeutralButton(getString(R.string.market_launch_error_button), null)
                            .show();
                }
                return true;
        }
        return false;
    }
}
