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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.SparseIntArray;
import android.util.TypedValue;

import java.util.HashMap;
import java.util.Map;

/**
 * @author alanv@google.com (Alan Viverette)
 */
public class CustomResourceMapper {
    /**
     * Contains a mapping of default preference key resource identifiers to
     * sound resource identifiers.
     */
    private final SparseIntArray mDefaultResourceMap = new SparseIntArray();

    /**
     * Contains a mapping of preference key resource identifiers to sound
     * resource identifiers.
     */
    private final Map<String, Integer> mCustomResourceMap = new HashMap<String, Integer>();

    /** Temporary holding variable for resolving resource identifiers. */
    private final TypedValue mResolvedValue = new TypedValue();

    /** The parent context. Used to retrieve resources. */
    private final Context mContext;

    /** Shared preferences. Used to retrieve custom mappings. */
    private final SharedPreferences mSharedPreferences;

    /** Resources. Used to map strings to resource identifiers. */
    private final Resources mResources;

    /**
     * Creates a new custom resource mapping and loads the default mappings.
     *
     * @param context The parent context.
     */
    public CustomResourceMapper(Context context) {
        mContext = context;
        mResources = context.getResources();
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);

        loadDefaults();
    }

    private void loadDefaults() {
        // Assignable sounds.
        loadDefault(R.id.sounds_hover, R.raw.def_sounds_hover);
        loadDefault(R.id.sounds_actionable, R.raw.def_sounds_actionable);
        loadDefault(R.id.sounds_clicked, R.raw.def_sounds_clicked);
        loadDefault(R.id.sounds_focused, R.raw.def_sounds_focused);
        loadDefault(R.id.sounds_selected, R.raw.def_sounds_selected);
        loadDefault(R.id.sounds_notification, R.raw.def_sounds_notification);
        loadDefault(R.id.sounds_window_state, R.raw.def_sounds_window_state);
        loadDefault(R.id.sounds_text_changed, R.raw.def_sounds_text_changed);
        loadDefault(R.id.sounds_scroll_for_more, R.raw.def_sounds_scroll_for_more);

        // Assignable patterns.
        loadDefault(R.id.patterns_hover, R.array.def_patterns_hover);
        loadDefault(R.id.patterns_actionable, R.array.def_patterns_actionable);
        loadDefault(R.id.patterns_clicked, R.array.def_patterns_clicked);
        loadDefault(R.id.patterns_focused, R.array.def_patterns_focused);
        loadDefault(R.id.patterns_selected, R.array.def_patterns_selected);
        loadDefault(R.id.patterns_notification, R.array.def_patterns_notification);
        loadDefault(R.id.patterns_window_state, R.array.def_patterns_window_state);
    }

    private void loadDefault(int keyResId, int defaultResId) {
        mResources.getValue(defaultResId, mResolvedValue, true);
        mDefaultResourceMap.put(keyResId, mResolvedValue.resourceId);
    }

    public int getResourceIdForPreference(int keyResId) {
        // First attempt to load from cache.
        final String key = mContext.getString(keyResId);
        final Integer resId = mCustomResourceMap.get(key);
        if (resId != null) {
            return resId;
        }

        // Next, attempt to load from preferences.
        final String resName = mSharedPreferences.getString(key, null);
        if (resName != null) {
            final int customId = mResources.getIdentifier(resName, null, mContext.getPackageName());
            mCustomResourceMap.put(key, customId);
            return customId;
        }

        // Finally, attempt to load from defaults.
        final Integer defaultResId = mDefaultResourceMap.get(keyResId);
        if (defaultResId != null) {
            return defaultResId;
        }

        return 0;
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener
            mPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(
                        SharedPreferences sharedPreferences, String key) {
                    mCustomResourceMap.remove(key);
                }
            };
}
