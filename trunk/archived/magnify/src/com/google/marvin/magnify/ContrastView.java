package com.google.marvin.magnify;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Matrix.ScaleToFit;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector.SimpleOnGestureListener;

public class ContrastView extends View {
  @SuppressWarnings("unused")
  private static final String TAG = "ContrastView";
  
  private Paint mPaint = new Paint();
  private Bitmap mBitmap;
  private Matrix mMatrix;
  private RectF mBounds;
  private RectF mSource;
  
  private SimpleOnGestureListener mSimple;
  private GestureDetector mGesture;
  private OnTouchListener onTouch;
  
  public ContrastView(Context context, AttributeSet attrs) {
    super(context, attrs);
    
    mBounds = new RectF(0, 0, 0, 0);
    mSource = new RectF(0, 0, 0, 0);
    mMatrix = new Matrix();
    
    mSimple = new SimpleOnGestureListener() {
      @Override
      public boolean onSingleTapConfirmed(MotionEvent e) {
        performZoom((int) e.getX(), (int) e.getY(), 2);
        
        return false;
      }

      @Override
      public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
          float velocityY) {
        return false;
      }

      @Override
      public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
          float distanceY) {
        performDrag((int) distanceX, (int) distanceY);
        
        return true;
      }
    };
    
    mGesture = new GestureDetector(context, mSimple);
    
    onTouch = new OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        mGesture.onTouchEvent(event);
        
        return true;
      }
    };
    
    setOnTouchListener(onTouch);
  }
  
  public void performZoom(int centerX, int centerY, float zoom) {
    /*
    int w = mSource.width();
    int h = mSource.height();
    int x = mSource.left + w * centerX / getWidth();
    int y = mSource.top + h * centerY / getHeight();
    
    zoom *= 2;
    
    mSource.left = x - (int)(w / zoom);
    mSource.top = y - (int)(h / zoom);
    mSource.right = x + (int)(w / zoom);
    mSource.bottom = y + (int)(h / zoom);
    
    Log.i(TAG, "Zoomed in to " + mSource.left + "," + mSource.top + "," + mSource.right + "," + mSource.bottom);
    */
    
    mMatrix.postScale(zoom, zoom, centerX, centerY);
    postInvalidate();
  }
  
  public void performDrag(int deltaX, int deltaY) {
    /*
    int w = mSource.width();
    int h = mSource.height();
    int x = mSource.left + w * deltaX / getWidth();
    int y = mSource.top + h * deltaY / getHeight();
    
    int nl = mSource.left + deltaX;
    int nr = mSource.right + deltaX;
    int nt = mSource.top + deltaY;
    int nb = mSource.bottom + deltaY;

    if (nl < 0) {
      deltaX = 0 - mSource.left;
    } else if (nr > mBitmap.getWidth()) {
      deltaX = mBitmap.getWidth() - mSource.right;
    }
  
    if (nt < 0) {
      deltaY = 0 - mSource.top;
    } else if (nb > mBitmap.getHeight()) {
      deltaY = mBitmap.getHeight() - mSource.bottom;
    }
    
    mSource.offset(deltaX, deltaY);
    
    Log.i(TAG, "Dragged to " + mSource.left + "," + mSource.top + "," + mSource.right + "," + mSource.bottom);
    */
    mMatrix.postTranslate(-deltaX, -deltaY);
    postInvalidate();
  }
  
  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    mBounds.set(0, 0, w, h);
    mMatrix.setRectToRect(mSource, mBounds, ScaleToFit.CENTER);
  }

  public void setBitmap(Bitmap bitmap) {
    mSource.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
    mMatrix.setRectToRect(mSource, mBounds, ScaleToFit.CENTER);
    mBitmap = bitmap;
    postInvalidate();
  }

  public void setContrast(float contrast) {
    float scale = contrast + 1.f;
    float translate = (-.5f * scale + .5f) * 255.f;
    float[] array = new float[] {
        scale, 0, 0, 0, translate,
        0, scale, 0, 0, translate,
        0, 0, scale, 0, translate,
        0, 0, 0, 1, 0};
    ColorMatrix matrix = new ColorMatrix(array);
    ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
    mPaint.setColorFilter(filter);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (mBitmap != null) {
      canvas.drawBitmap(mBitmap, mMatrix, mPaint);
    }
  }
}
