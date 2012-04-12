/*
 * Copyright 2011 Google Inc.
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

package com.google.android.marvin.talkback;

import android.content.Context;
import android.content.res.Resources;
import android.preference.ListPreference;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

import com.googlecode.eyesfree.utils.LogUtils;

/**
 * Extends {@link ListPreference} by providing a preview button for sounds and
 * vibration patterns.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class PlayableListPreference extends ListPreference {
    private static final String TYPE_SOUND = "raw/";
    private static final String TYPE_VIBRATION = "array/";

    private final PreferenceFeedbackController mFeedbackController;

    public PlayableListPreference(Context context) {
        this(context, null);
    }

    public PlayableListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setWidgetLayoutResource(R.layout.play_button);

        mFeedbackController = new PreferenceFeedbackController(context);
    }

    @Override
    protected void onBindView(View view) {
        final ImageView playButton = (ImageView) view.findViewById(R.id.play_button);

        if (playButton != null) {
            playButton.setOnClickListener(mClickListener);
        }

        super.onBindView(view);
    }

    private void playCurrentValue() {
        final String value = getValue();

        if (value == null || !value.matches("\\w+/\\w+")) {
            LogUtils.log(PlayableListPreference.class, Log.ERROR, "Invalid resource format: "
                    + value);
            return;
        }

        final Context context = getContext();
        final Resources res = context.getResources();
        final int resId = res.getIdentifier(value, null, context.getPackageName());

        if (resId <= 0) {
            LogUtils.log(PlayableListPreference.class, Log.ERROR,
                    "Unable to load resource identifier: " + value);
            return;
        }

        if (value.startsWith(TYPE_SOUND)) {
            mFeedbackController.playSound(resId);
        } else if (value.startsWith(TYPE_VIBRATION)) {
            mFeedbackController.playVibration(resId);
        } else {
            LogUtils.log(PlayableListPreference.class, Log.ERROR, "Unknown resource type: "
                    + value);
            return;
        }
    }

    private final OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            playCurrentValue();
        }
    };

    public static class SilentImageView extends ImageView {
        public SilentImageView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public void sendAccessibilityEvent(int eventType) {
            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
                    || eventType == AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER
                    || eventType == AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT) {
                return;
            }

            super.sendAccessibilityEvent(eventType);
        }
    }
}
