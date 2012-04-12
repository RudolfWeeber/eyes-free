/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.android.marvin.talkback;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.WindowManager;

import com.google.android.marvin.utils.HighlightBoundsView;
import com.googlecode.eyesfree.widget.SimpleOverlay;

public class DeveloperOverlay extends SimpleOverlay {
    /** The minimum API level required to use this class. */
    public static final int MIN_API_LEVEL = 14;

    private static DeveloperOverlay sInstance;

    private final HighlightBoundsView mAnnounceBounds;
    private final HighlightBoundsView mBounds;

    public DeveloperOverlay(Context context) {
        super(context);

        final WindowManager.LayoutParams params = getParams();
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        setParams(params);

        setContentView(R.layout.developer_overlay);

        mAnnounceBounds = (HighlightBoundsView) findViewById(R.id.announce_bounds);
        mAnnounceBounds.setHighlightColor(Color.YELLOW);

        mBounds = (HighlightBoundsView) findViewById(R.id.bounds);
        mBounds.setHighlightColor(Color.RED);
    }

    @Override
    public void onShow() {
        sInstance = this;
    }

    @Override
    public void onHide() {
        sInstance = null;

        mBounds.clear();
        mAnnounceBounds.clear();
    }

    public static void removeInvalidNodes() {
        if (sInstance == null) {
            return;
        }

        sInstance.mBounds.removeInvalidNodes();
        sInstance.mBounds.postInvalidate();

        sInstance.mAnnounceBounds.removeInvalidNodes();
        sInstance.mAnnounceBounds.postInvalidate();
    }

    public static void updateNodes(AccessibilityNodeInfoCompat source, AccessibilityNodeInfoCompat announced) {
        if (sInstance == null) {
            return;
        }

        sInstance.mBounds.clear();
        sInstance.mBounds.add(source);
        sInstance.mBounds.postInvalidate();

        sInstance.mAnnounceBounds.clear();
        sInstance.mAnnounceBounds.add(announced);
        sInstance.mAnnounceBounds.postInvalidate();
    }
}
