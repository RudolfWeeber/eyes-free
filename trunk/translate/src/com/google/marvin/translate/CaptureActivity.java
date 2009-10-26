package com.google.marvin.translate;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;

import com.android.ocr.client.Language;

import java.io.IOException;

public class CaptureActivity extends Activity implements SurfaceHolder.Callback,
    Camera.AutoFocusCallback, Camera.PictureCallback {
  private static final String TAG = "CaptureActivity";

  public static final String EXTRA_IMAGE = "image";
  public static final String EXTRA_WIDTH = "width";
  public static final String EXTRA_HEIGHT = "height";
  public static final String EXTRA_FORMAT = "format";
  public static final String EXTRA_OCR_SOURCE = "ocr_source";

  private static final int DEFAULT_WIDTH = 1024;
  private static final int DEFAULT_HEIGHT = 768;

  private int mParamWidth;
  private int mParamHeight;

  private Camera mCameraDevice;
  private SurfaceView mSurfaceView;
  private SurfaceHolder mSurfaceHolder = null;
  private Spinner mOcrSource;
  private ImageButton mShutter;
  private boolean mBusy;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // To reduce startup time, we open camera device in another thread.
    // We make sure the camera is opened at the end of onCreate.
    Thread openCameraThread = new Thread(new Runnable() {
      public void run() {
        try {
          mCameraDevice = Camera.open();
        } catch (RuntimeException e) {
          Log.e(TAG, "Failed to open camera: " + e.toString());
        }
      }
    });
    openCameraThread.start();

    Window win = getWindow();
    win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.capture);

    mSurfaceView = (SurfaceView) findViewById(R.id.preview);
    mSurfaceHolder = mSurfaceView.getHolder();
    mSurfaceHolder.addCallback(this);
    mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

    mOcrSource = (Spinner) findViewById(R.id.ocr_source);

    View.OnClickListener onClick = new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        takePicture();
      }
    };
    mShutter = (ImageButton) findViewById(R.id.btn_shutter);
    mShutter.setOnClickListener(onClick);

    processIntent(getIntent());

    try {
      openCameraThread.join();
    } catch (InterruptedException e) {
      Log.e(TAG, e.toString());
    }
  }

  @Override
  protected void onDestroy() {
    mCameraDevice.release();

    super.onDestroy();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_CAMERA:
        takePicture();
        return true;
    }

    return super.onKeyDown(keyCode, event);
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    Camera.Parameters parameters = mCameraDevice.getParameters();
    parameters.setPreviewSize(w, h);
    parameters.setPictureSize(mParamHeight, mParamWidth);
    mCameraDevice.setParameters(parameters);

    if (holder.isCreating()) {
      mCameraDevice.startPreview();
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    try {
      mCameraDevice.setPreviewDisplay(holder);
    } catch (IOException e) {
      Log.e(TAG, e.toString());
    }
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    mCameraDevice.stopPreview();
  }

  @Override
  public void onAutoFocus(boolean success, Camera camera) {
    Camera.Parameters parameters = camera.getParameters();
    parameters.setPictureSize(mParamWidth, mParamHeight);
    parameters.set("orientation", "landscape");

    camera.setParameters(parameters);
    camera.takePicture(null, null, this);
  }

  @Override
  public void onPictureTaken(byte[] image, Camera camera) {
    Language ocr_source = (Language) mOcrSource.getSelectedItem();
    Camera.Parameters params = camera.getParameters();
    Size size = params.getPictureSize();

    Intent data = new Intent();
    data.putExtra(CaptureActivity.EXTRA_IMAGE, image);
    data.putExtra(CaptureActivity.EXTRA_WIDTH, size.width);
    data.putExtra(CaptureActivity.EXTRA_HEIGHT, size.height);
    data.putExtra(CaptureActivity.EXTRA_FORMAT, params.getPictureFormat());
    data.putExtra(CaptureActivity.EXTRA_OCR_SOURCE, ocr_source.iso_639_2);

    setResult(RESULT_OK, data);
    finish();

    mBusy = false;
  }

  private void processIntent(Intent intent) {
    mParamWidth = intent.getIntExtra(EXTRA_WIDTH, DEFAULT_WIDTH);
    mParamHeight = intent.getIntExtra(EXTRA_HEIGHT, DEFAULT_HEIGHT);

    OnItemSelectedListener onSelected = new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
        Translate.mSource.setSelection(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
        // do... nothing!
      }
    };

    Spinner mSource = Translate.mSource;
    mOcrSource.setAdapter(mSource.getAdapter());
    mOcrSource.setSelection(mSource.getSelectedItemPosition());
    mOcrSource.setOnItemSelectedListener(onSelected);
  }

  private void takePicture() {
    if (mBusy) return;

    mBusy = true;
    mCameraDevice.autoFocus(this);
  }
}
