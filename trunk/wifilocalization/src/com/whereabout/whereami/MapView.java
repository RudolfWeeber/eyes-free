/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.whereabout.whereami;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Typeface;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

/**
 * View class that loads the map image, displays the requested location as a dot
 * and also reports back the location indicated by the user by touching the map.
 * 
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 */
public class MapView extends ImageView {
    private static final String TAG = "MapView";
    
    private Bitmap bmp;

    private float hx, hy, downx, downy;

    private int curBmpL, curBmpT, curBmpR, curBmpB;

    private int positionX = -1, positionY = -1;

    private int posScrX, posScrY;

    private int bmpWidth, bmpHeight, maxScrollWidth, maxScrollHeight;

    private long downTime;

    private boolean showAllLocations = false, showConnectivity = false,
            showHighlightPoints = false, showRoute = false, showError = true,
            showDirection = false, showLocation = false;

    private boolean longPressed = false;
    
    private String mapImageFile = "";

    private ArrayList<Point> allPoints;
    
    private ArrayList<String> allPointsIds;
    
    private ArrayList<Point> route;
    
    private HashMap<String, HashSet<String>> connectivity;
    
    private HashMap<String, Point> pointIdMap = new HashMap<String, Point>();

    private int selectedPointIndex;
    
    private int errorCircleRadius = 0;
    
    private float direction = 0;
    
    private ArrayList<Integer> highlightPoints;

    private WifiMapActionListener mapActionListener;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    public MapView(Context context, String mapImagePath) {
        super(context);
        File file = new File(mapImagePath);
        if (!file.exists()) {
            Log.e(TAG, "Specified image file not found.");
            return;
        }
        mapImageFile = mapImagePath;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        try {
            bmp = BitmapFactory.decodeFile(mapImagePath);
        } catch (OutOfMemoryError e) {
            return;
        }
        setImageBitmap(bmp);
        setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        setScaleType(ScaleType.CENTER);
        bmpWidth = bmp.getWidth();
        bmpHeight = bmp.getHeight();
        paint.setColor(Color.RED);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            hx = downx = event.getX();
            hy = downy = event.getY();
            downTime = System.currentTimeMillis();
            longPressed = false;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (System.currentTimeMillis() - downTime < 200) {
                curBmpL = (bmpWidth / 2) + getScrollX() - (getWidth() / 2);
                curBmpT = (bmpHeight / 2) + getScrollY() - (getHeight() / 2);
                positionX = curBmpL + (int) event.getX();
                positionY = curBmpT + (int) event.getY();
                showLocation(positionX, positionY);
                if (mapActionListener != null) {
                    mapActionListener.onClickListener(new Point(positionX, positionY));
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            float x = event.getX();
            float y = event.getY();
            int hscroll = (int) (hx - x);
            int vscroll = (int) (hy - y);
            maxScrollWidth = bmpWidth / 2 - getWidth() / 2;
            maxScrollHeight = bmpHeight / 2 - getHeight() / 2;
            //hscroll = Math.abs(getScrollX() + hscroll) < maxScrollWidth ? hscroll : 0;
            //vscroll = Math.abs(getScrollY() + vscroll) < maxScrollHeight ? vscroll : 0;
            scrollBy(hscroll, vscroll);
            hx = x;
            hy = y;
            // Check for long press
            int cx = (int) event.getX();
            int cy = (int) event.getY();
            if (System.currentTimeMillis() - downTime > 1000 && Math.abs(cx - downx) < 20
                    && Math.abs(cy - downy) < 20) {
                curBmpL = (bmpWidth / 2) + getScrollX() - (getWidth() / 2);
                curBmpT = (bmpHeight / 2) + getScrollY() - (getHeight() / 2);
                cx = curBmpL + cx;
                cy = curBmpT + cy;
                if (!longPressed && mapActionListener != null) {
                    mapActionListener.onLongPressListener(new Point(cx, cy));
                    longPressed = true;
                }
            }
        }
        return true;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (showError && errorCircleRadius > 0) {
            paint.setColor(Color.LTGRAY);
            paint.setARGB(150, 200, 200, 200);
            canvas.drawCircle(posScrX, posScrY, errorCircleRadius, paint);
        }
        if (showConnectivity && connectivity != null && !connectivity.isEmpty()) {
            paint.setColor(Color.GRAY);
            paint.setStrokeWidth(5);
            Set<String> ids = connectivity.keySet();
            for (String id : ids) {
                Point from = pointIdMap.get(id);
                HashSet<String> toIds = connectivity.get(id);
                for (String toId : toIds) {
                    Point to = pointIdMap.get(toId);
                    if (from.x > curBmpL || from.y > curBmpT ||
                        from.x < curBmpR || from.y < curBmpB ||
                        to.x > curBmpL || to.y > curBmpT ||
                        to.x < curBmpR || to.y < curBmpB) {
                        canvas.drawLine(from.x - maxScrollWidth, from.y - maxScrollHeight,
                                to.x - maxScrollWidth, to.y - maxScrollHeight, paint);
                    }
                }
            }
        }
        if (showRoute && route != null && !route.isEmpty()) {
            int len = route.size();
            paint.setColor(Color.GRAY);
            paint.setStrokeWidth(5);
            paint.setStyle(Paint.Style.STROKE);
            Path path = new Path();
            for (int i = 0; i < len; i++) {
                Point p1 = route.get(i);
                if (i == 0) {
                    path.moveTo(p1.x - maxScrollWidth, p1.y - maxScrollHeight);
                } else {
                    path.lineTo(p1.x - maxScrollWidth, p1.y - maxScrollHeight);
                }
            }
            canvas.drawPath(path, paint);
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(route.get(0).x - maxScrollWidth, route.get(0).y - maxScrollHeight,
                    10, paint);
            paint.setColor(Color.GREEN);
            canvas.drawCircle(route.get(len - 1).x - maxScrollWidth,
                    route.get(len - 1).y - maxScrollHeight, 10, paint);
        }
        if (positionX != -1 && positionY != -1 && showLocation) {
            paint.setColor(Color.GREEN);
            canvas.drawCircle(posScrX, posScrY, 7, paint);
            if (showDirection) {
                double dir = (direction  + 90) * Math.PI / 180;
                int x = (int) (20 * Math.cos(dir) + posScrX);
                int y = (int) (posScrY - 20 * Math.sin(dir));
                paint.setStrokeWidth(5);
                paint.setColor(Color.BLUE);
                canvas.drawLine(posScrX, posScrY, x, y, paint);
            }
        }
        if (showHighlightPoints && !highlightPoints.isEmpty()) {
            paint.setColor(Color.GREEN);
            for (int index : highlightPoints) {
                Point p = pointIdMap.get(Integer.toString(index));
                canvas.drawCircle(p.x - maxScrollWidth, p.y - maxScrollHeight, 13, paint);
            }
        }
        if (showAllLocations && !allPoints.isEmpty()) {
            int i = 0;
            for (Point p : allPoints) {
                int radius = 10;
                paint.setColor(Color.RED);
                if (selectedPointIndex == i) {
                    radius = 15;
                    paint.setColor(Color.BLUE);
                }
                canvas.drawCircle(p.x - maxScrollWidth, p.y - maxScrollHeight, radius, paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(15);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText(allPointsIds.get(i), p.x - maxScrollWidth - (i < 10 ? 4 : 9), p.y
                        - maxScrollHeight + 5, paint);
                i++;
            }
        }
    }

    public boolean setImageBitmap(String mapImagePath) {
        if (!(new File(mapImagePath).exists())) {
            return false;
        }
        if (mapImageFile.equals(mapImagePath)) {
            return true;
        }
        bmp.recycle();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        bmp = BitmapFactory.decodeFile(mapImagePath);
        setImageBitmap(bmp);
        return true;
    }
    
    public void setWifiMapActionListener(WifiMapActionListener listener) {
        mapActionListener = listener;
    }

    public int getLocationX() {
        return positionX;
    }

    public int getLocationY() {
        return positionY;
    }

    public int getMapImageWidth() {
        return bmpWidth;
    }

    public int getMapImageHeight() {
        return bmpHeight;
    }

    public void highlightPoints(ArrayList<Integer> points) {
        if (points == null) {
            showHighlightPoints = false;
            return;
        }
        highlightPoints = points;
        showHighlightPoints = true;
        invalidate();
    }
    
    public void showLocation(int x, int y) {
        if (x == -1 && y == -1) {
            showLocation = false;
        }
        showLocation = true;
        positionX = x;
        positionY = y;
        computeCurrentBitmapEdges();
        posScrX = positionX - maxScrollWidth;
        posScrY = positionY - maxScrollHeight;
        maxScrollWidth = bmpWidth / 2 - getWidth() / 2;
        maxScrollHeight = bmpHeight / 2 - getHeight() / 2;
        if (x < curBmpL || y < curBmpT || x > curBmpR || y > curBmpB) {
            scrollBy(x - (curBmpL + (getWidth() / 2)), y - (curBmpT + (getHeight() / 2)));
        }
        invalidate();
    }
    
    public void showLocationWithErrorCirle(int x, int y, int error) {
        showError = true;
        errorCircleRadius = error;
        showLocation(x, y);
    }
    
    public void hideErrorCircle() {
        showError = false;
        errorCircleRadius = 0;
    }

    private void computeCurrentBitmapEdges() {
        curBmpL = (bmpWidth / 2) + getScrollX() - (getWidth() / 2);
        curBmpT = (bmpHeight / 2) + getScrollY() - (getHeight() / 2);
        curBmpR = curBmpL + getWidth();
        curBmpB = curBmpT + getHeight();
    }

    public void showMultipleLocations(ArrayList<Point> points, ArrayList<String> ids,
            int selected) {
        allPoints = points;
        allPointsIds = ids;
        pointIdMap.clear();
        for (int i = 0; i < allPoints.size(); i++) {
            pointIdMap.put(allPointsIds.get(i), allPoints.get(i));
        }
        showAllLocations = true;
        selectedPointIndex = selected;
        invalidate();
    }
    
    public void showDirection(float dir) {
        showDirection = true;
        direction = dir;
    }
    
    public void showConnectivity(HashMap<String, HashSet<String>> connectivity) {
        showConnectivity = connectivity != null && !connectivity.isEmpty();
        this.connectivity = connectivity;
        invalidate();
    }
    
    public void showRoute(ArrayList<Point> route) {
        showRoute = route != null && !route.isEmpty();
        this.route = route;
        if (route == null || route.isEmpty()) {
            return;
        }
        Point p = route.get(0);
        showLocation(p.x, p.y);
        invalidate();
    }
    
    public void destroy() {
        if (bmp != null) {
            bmp.recycle();
        }
    }

    public static interface WifiMapActionListener {
        public void onLongPressListener(Point p);
        public void onClickListener(Point p);
    }
}
