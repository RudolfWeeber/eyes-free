package com.google.marvin.magnify;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ZoomButton;

public class ZoomActivity extends Activity {
  private static final String TAG = "ZoomActivity";

  private ContrastView mContrast;
  private ZoomButton mZoomIn;
  private ZoomButton mZoomOut;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    Log.i(TAG, "Started " + TAG);

    setContentView(R.layout.zoom);

    mContrast = (ContrastView) findViewById(R.id.contrast);
    mContrast.setContrast(2.0f);
    mContrast.setBitmap(CaptureActivity.mBitmap); // HACKHACKHACK
    
    mZoomIn = (ZoomButton) findViewById(R.id.btn_zoom_up);
    mZoomOut = (ZoomButton) findViewById(R.id.btn_zoom_down);
    
    mZoomIn.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        int x = mContrast.getWidth() / 2;
        int y = mContrast.getHeight() / 2;
        mContrast.performZoom(x, y, 2.0f);
      }
    });
    
    mZoomOut.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        int x = mContrast.getWidth() / 2;
        int y = mContrast.getHeight() / 2;
        mContrast.performZoom(x, y, 0.5f);
      }
    });
  }
}
