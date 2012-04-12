package com.google.marvin.magnify;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.IOException;

public class CaptureActivity extends Activity implements SurfaceHolder.Callback,
    Camera.PictureCallback, Camera.AutoFocusCallback, View.OnClickListener {
  private static final String TAG = "CaptureActivity";

  private SurfaceHolder mHolder;
  private SurfaceView mPreview;
  private Camera mCamera;
  
  protected static Bitmap mBitmap;
  
  int[] mImage;
  int mWidth;
  int mHeight;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    Log.i(TAG, "Started " + TAG);

    setContentView(R.layout.main);

    mPreview = (SurfaceView) findViewById(R.id.preview);
    mPreview.setOnClickListener(this);

    mHolder = mPreview.getHolder();
    mHolder.addCallback(this);
    mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    try {
      mCamera = Camera.open();
      mCamera.setPreviewDisplay(holder);
    } catch (IOException e) {
      Log.e(TAG, e.toString());
    }
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    mCamera.stopPreview();
    mCamera.release();
    mCamera = null;
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    mHolder.setFixedSize(w, h);
    
    Camera.Parameters parameters = mCamera.getParameters();
    parameters.setPreviewSize(w, h);
    parameters.setPictureSize(2048, 1536);
    mCamera.setParameters(parameters);
    mCamera.startPreview();
  }

  @Override
  public void onClick(View v) {
    if (v == mPreview) {
      mCamera.autoFocus(this);
    }
  }

  @Override
  public void onPictureTaken(byte[] data, Camera camera) {
    if (mBitmap != null) {
      mBitmap.recycle();
    }
    
    mBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
    
    Intent intent = new Intent(this, ZoomActivity.class);
    startActivity(intent);
  }

  @Override
  public void onAutoFocus(boolean success, Camera camera) {
    mCamera.takePicture(null, null, this);
  }
}
