package com.google.marvin.magnify;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class ZoomView extends View implements View.OnTouchListener,
    View.OnClickListener {
  private static final String TAG = "ZoomView";
  private static final int MIN_SIZE = 20;
  
  private OnZoomListener mOnZoomListener;
  private Paint mPaint;
  private Rect mRect;
  private Rect mLast;
  private int anchorX;
  private int anchorY;
  
  public interface OnZoomListener {
    public void onResize(View v, Rect r);
    public void onSelect(View v, Rect r);
  }

  public ZoomView(Context context, AttributeSet attrs) {
    super(context, attrs);
    
    Resources resources = getResources();
    int color = resources.getColor(R.color.zoom_box);
    
    mPaint = new Paint();
    mPaint.setAntiAlias(true);
    mPaint.setStyle(Style.FILL);
    mPaint.setColor(color);
    
    mRect = null;
    
    setOnTouchListener(this);
  }
  
  @Override
  public boolean onTouch(View v, MotionEvent event) {
    int w = v.getWidth();
    int h = v.getHeight();
    int x = (int)event.getX();
    int y = (int)event.getY();

    int action = event.getAction();
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        Log.i(TAG, "Received onTouch event (action DOWN).");
        mLast = mRect;
        mRect = new Rect(x, y, x, y);
        anchorX = x;
        anchorY = y;
        setWithAspect(x, y);
        return false;
      case MotionEvent.ACTION_MOVE:
        setWithAspect(x, y);
        return true;
      case MotionEvent.ACTION_UP:
        Log.i(TAG, "Received onTouch event (action UP).");
        return fireOnZoom();
      default:
        Log.i(TAG, "Received onTouch event (action " + action + ").");
        return false;
    }
  }
  
  @Override
  protected void onDraw(Canvas canvas) {
    if (mRect != null
        && mRect.height() > MIN_SIZE
        && mRect.width() > MIN_SIZE) {
      canvas.drawRect(mRect, mPaint);
    }
  }
  
  private void setWithAspect(int r, int b) {
    int l = anchorX;
    int t = anchorY;
    
    if (r < l || b < t) {
      l = r;
      t = b;
      r = anchorX;
      b = anchorY;
    }
    
    int w = r - l;
    int h = b - t;
    
    
    if (w != 0 && h != 0) {
      float aspect = (w * getHeight()) / (float) (h * getWidth());
      
      if (aspect < 1) {
        w /= aspect;
      } else if (aspect > 1) {
        h *= aspect;
      }
    }
    
    mRect.set(l, t, l + w, t + h);
    postInvalidate();
  }
  
  public void setOnZoomListener(OnZoomListener onZoomListener) {
    mOnZoomListener = onZoomListener;
  }
  
  private boolean fireOnZoom() {
    if (mOnZoomListener != null
        && mRect.width() > MIN_SIZE
        && mRect.height() > MIN_SIZE) {
      mOnZoomListener.onResize(this, mRect);
      return true;
    }
    
    Log.i(TAG, "Didn't consume ACTION_UP.");
    
    return false;
  }

  @Override
  public void onClick(View v) {
    if (mLast != null) {
      mOnZoomListener.onSelect(this, mLast);
    }
  }
}
