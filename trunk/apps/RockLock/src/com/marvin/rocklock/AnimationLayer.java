/*
 * Copyright (C) 2010 Google Inc.
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

package com.marvin.rocklock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Draws an arrow to give the user some visual indication of where they are
 * stroking.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class AnimationLayer extends View {
    private int dir = 5;

    private Path arrow = new Path();

    private Paint paint = new Paint();

    public AnimationLayer(Context context) {
        super(context);
        arrow.moveTo(0, -50);
        arrow.lineTo(-20, 60);
        arrow.lineTo(0, 50);
        arrow.lineTo(20, 60);
        arrow.close();
    }

    public void setDirection(int direction) {
        dir = direction;
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);

        int w = canvas.getWidth();
        int h = canvas.getHeight();
        int cx = w / 2;
        int cy = (h * 2) / 3;

        paint.setColor(Color.WHITE);

        canvas.translate(cx, cy);
        switch (dir) {
            case 1:
                canvas.rotate(-45);
                break;
            case 2:
                canvas.rotate(0);
                break;
            case 3:
                canvas.rotate(45);
                break;
            case 4:
                canvas.rotate(-90);
                break;
            case 5:
                canvas.rotate(0);
                break;
            case 6:
                canvas.rotate(90);
                break;
            case 7:
                canvas.rotate(-135);
                break;
            case 8:
                canvas.rotate(180);
                break;
            case 9:
                canvas.rotate(135);
                break;
        }

        if ((dir >= 0) && (dir != 5)) {
            canvas.drawPath(arrow, paint);
        }
    }

}
