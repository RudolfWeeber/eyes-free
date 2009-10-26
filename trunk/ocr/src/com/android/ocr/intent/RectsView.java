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
package com.android.ocr.intent;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.LinkedList;

/**
 * Displays colored bounding boxes scaled to screen resolution.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class RectsView extends View {
  private static final String TAG = "RectsView";

  private Matrix mMatrix;
  private Paint mPaint;
  private LinkedList<Pair> mRects;

  public RectsView(Context context, AttributeSet attrs) {
    super(context, attrs);

    mRects = new LinkedList<Pair>();
    mPaint = new Paint();
    mPaint.setStyle(Paint.Style.STROKE);
    mPaint.setStrokeWidth(5.0f);
  }

  public void addRect(Rect rect) {
    if (rect != null) {
      RectF rectf = new RectF(rect);
      int argb = (int) (Math.random() * Integer.MAX_VALUE) | 0xFF000000;
      Pair entry = new Pair(rectf, argb);

      mMatrix.mapRect(rectf);
      mRects.add(entry);
      postInvalidate();
    }
  }

  public void setScaling(int srcW, int srcH, int dstW, int dstH) {
    float scaleX = dstW / (float) srcW;
    float scaleY = dstH / (float) srcH;
    float scale = Math.min(scaleX, scaleY);

    float scaledW = scale * srcW;
    float scaledH = scale * srcH;

    float deltaX = (dstW - scaledW) / 2.0f;
    float deltaY = (dstH - scaledH) / 2.0f;

    mMatrix = new Matrix();
    mMatrix.postScale(scale, scale);
    mMatrix.postTranslate(deltaX, deltaY);

    Log.i(TAG, "Set scaling to scale " + scale + " transform (" + deltaX + "," + deltaY + ")");
  }

  @Override
  public void onDraw(Canvas canvas) {
    for (Pair entry : mRects) {
      mPaint.setColor(entry.argb);
      canvas.drawRect(entry.rectf, mPaint);
    }
  }

  private class Pair {
    public RectF rectf;
    public int argb;

    public Pair(RectF rectf, int argb) {
      this.rectf = rectf;
      this.argb = argb;
    }
  }
}
