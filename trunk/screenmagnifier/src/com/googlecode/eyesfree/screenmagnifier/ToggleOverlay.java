/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.screenmagnifier;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.graphics.PorterDuff.Mode;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

import com.googlecode.eyesfree.compat.view.WindowManagerCompatUtils;
import com.googlecode.eyesfree.widget.SimpleOverlay;

public class ToggleOverlay extends SimpleOverlay {
    /** Shared application preferences. */
    private final SharedPreferences mPrefs;

    /** Key string for the position preference. */
    private final String mPrefPosition;

    /** The zoom toggle button. */
    private final ImageView mImageView;

    private ToggleListener mListener;

    private int mCurrentPosition;

    public ToggleOverlay(Context context) {
        super(context);

        final WindowManager.LayoutParams params = getParams();
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.flags |= WindowManagerCompatUtils.LayoutParamsCompatUtils.FLAG_SPLIT_TOUCH;
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        setParams(params);

        setContentView(R.layout.toggle);

        mImageView = (ImageView) findViewById(R.id.toggle);
        mImageView.setOnClickListener(mOnClickListener);
        mImageView.setOnLongClickListener(mOnLongClickListener);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        final Resources res = context.getResources();
        final int defaultPosition = res.getInteger(R.integer.pref_position_default);

        mPrefPosition = res.getString(R.string.pref_position);

        mCurrentPosition = mPrefs.getInt(mPrefPosition, defaultPosition) - 1;

        setState(true);
        adjustPosition();
    }

    public void setState(boolean enabled) {
        mImageView.setEnabled(enabled);

        if (enabled) {
            mImageView.clearColorFilter();
        } else {
            mImageView.setColorFilter(0xFFFF8000, Mode.MULTIPLY);
        }

        if (mListener != null) {
            mListener.onToggled(enabled);
        }
    }

    private void adjustPosition() {
        mCurrentPosition = (mCurrentPosition + 1) % 4;

        final WindowManager.LayoutParams params = getParams();
        params.gravity =
                (mCurrentPosition == 0 || mCurrentPosition == 1) ? Gravity.TOP : Gravity.BOTTOM;
        params.gravity |=
                (mCurrentPosition == 1 || mCurrentPosition == 2) ? Gravity.RIGHT : Gravity.LEFT;
        setParams(params);

        final Editor editor = mPrefs.edit();
        editor.putInt(mPrefPosition, mCurrentPosition);
        editor.commit();
    }

    public void setToggleListener(ToggleListener listener) {
        mListener = listener;
    }

    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.toggle) {
                final boolean state = !mImageView.isEnabled();
                setState(state);
            }
        }
    };

    private final OnLongClickListener mOnLongClickListener = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            adjustPosition();

            return true;
        }
    };

    public interface ToggleListener {
        public void onToggled(boolean state);
    }
}
