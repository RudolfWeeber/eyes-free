/*
 * Copyright (C) 2009 Google Inc.
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
package com.google.marvin.worldreader;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import java.io.IOException;

/**
 * This object wraps the Camera service object and expects to be the only one
 * talking to it. The implementation encapsulates the steps needed to take
 * preview-sized images, which are used for both preview and decoding.
 * 
 * Based on code from ZXing licensed under Apache License, Version 2.0.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class CameraManager {
  private static final String TAG = "CameraManager";

  private static CameraManager mCameraManager;

  private Camera mCamera;
  private final Context mContext;
  private Point mScreenResolution;
  private Handler mPreviewHandler;
  private int mPreviewMessage;
  private Handler mAutoFocusHandler;
  private int mAutoFocusMessage;
  private Handler mTakePictureHandler;
  private int mTakePictureMessage;
  private boolean mInitialized;
  private boolean mPreviewing;
  private int mPictureWidth;
  private int mPictureHeight;

  public static CameraManager init(Context context) {
    if (mCameraManager == null) {
      mCameraManager = new CameraManager(context);
    }

    return mCameraManager;
  }

  public static CameraManager get() {
    return mCameraManager;
  }

  private CameraManager(Context context) {
    mContext = context;
    mCamera = null;
    mInitialized = false;
    mPreviewing = false;
  }

  public void openDriver(SurfaceHolder holder) throws IOException {
    Log.i(TAG, "Opening camera driver...");

    if (mCamera == null) {
      mCamera = Camera.open();
      mCamera.setPreviewDisplay(holder);

      if (!mInitialized) {
        mInitialized = true;
        getScreenResolution();
      }

      setPreviewParameters();
    }
  }

  public void closeDriver() {
    Log.i(TAG, "Closing camera driver...");

    if (mCamera != null) {
      mCamera.release();
      mCamera = null;
      mPreviewing = false;
    }
  }

  public void startPreview() {
    if (mCamera != null && !mPreviewing) {
      mCamera.startPreview();
      mPreviewing = true;
    }
  }

  public void stopPreview() {
    if (mCamera != null && mPreviewing) {
      mCamera.setPreviewCallback(null);
      mCamera.stopPreview();
      mPreviewHandler = null;
      mAutoFocusHandler = null;
      mPreviewing = false;
    }
  }

  /**
   * A single preview frame will be returned to the handler supplied. The data
   * will arrive as byte[] in the message.obj field, with width and height
   * encoded as message.arg1 and message.arg2, respectively.
   * 
   * @param handler The handler to send the message to.
   * @param message The what field of the message to be sent.
   */
  public void requestPreviewFrame(Handler handler, int message) {
    if (mCamera != null && mPreviewing) {
      mPreviewHandler = handler;
      mPreviewMessage = message;
      mCamera.setPreviewCallback(previewCallback);
    }
  }

  public void requestTakePicture(Handler handler, int message) {
    if (mCamera != null && mPreviewing) {
      if (mPictureWidth > 0 && mPictureHeight > 0) {
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPictureFormat(PixelFormat.JPEG);
        parameters.setPictureSize(mPictureWidth, mPictureHeight);
        parameters.set("orientation", "landscape");
        mCamera.setParameters(parameters);
      }

      mTakePictureHandler = handler;
      mTakePictureMessage = message;
      mCamera.takePicture(null, null, takePictureCallback);
    }
  }

  public void requestAutoFocus(Handler handler, int message) {
    if (mCamera != null && mPreviewing) {
      mAutoFocusHandler = handler;
      mAutoFocusMessage = message;
      mCamera.autoFocus(autoFocusCallback);
    }
  }

  /**
   * Preview frames are delivered here, which we pass on to the registered
   * handler. Make sure to clear the handler so it will only receive one
   * message.
   */
  private final Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
    public void onPreviewFrame(byte[] data, Camera camera) {
      camera.setPreviewCallback(null);
      if (mPreviewHandler != null) {
        Camera.Parameters parameters = camera.getParameters();
        Camera.Size size = parameters.getPreviewSize();
        Message message =
            mPreviewHandler.obtainMessage(mPreviewMessage, size.width, size.height, data);
        message.sendToTarget();
        mPreviewHandler = null;
      }
    }
  };

  private final Camera.PictureCallback takePictureCallback = new Camera.PictureCallback() {
    public void onPictureTaken(byte[] data, Camera camera) {
      if (mTakePictureHandler != null) {
        Camera.Parameters parameters = camera.getParameters();
        Camera.Size size = parameters.getPictureSize();
        Message message =
            mTakePictureHandler.obtainMessage(mTakePictureMessage, size.width, size.height, data);
        message.sendToTarget();
        mTakePictureHandler = null;
      }
    }
  };

  private final Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
    public void onAutoFocus(boolean success, Camera camera) {
      if (mAutoFocusHandler != null) {
        Message message = mAutoFocusHandler.obtainMessage(mAutoFocusMessage, success);
        message.sendToTarget();

        mAutoFocusHandler = null;
      }
    }
  };

  /**
   * Sets the camera up to take preview images which are used for both preview
   * and decoding. We're counting on the default YUV420 semi-planar data. If
   * that changes in the future, we'll need to specify it explicitly with
   * setPreviewFormat().
   */
  private void setPreviewParameters() {
    Camera.Parameters parameters = mCamera.getParameters();
    parameters.setPreviewFormat(PixelFormat.YCbCr_420_SP);
    parameters.setPreviewSize(mScreenResolution.x, mScreenResolution.y);
    mCamera.setParameters(parameters);
  }

  public void setPictureSize(int width, int height) {
    mPictureWidth = width;
    mPictureHeight = height;
  }

  private Point getScreenResolution() {
    if (mScreenResolution == null) {
      WindowManager manager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
      Display display = manager.getDefaultDisplay();
      mScreenResolution = new Point(display.getWidth(), display.getHeight());
    }

    return mScreenResolution;
  }
}
