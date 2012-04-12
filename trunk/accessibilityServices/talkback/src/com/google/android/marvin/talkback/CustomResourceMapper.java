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
    private final Map<Integer, Integer> mDefaultResourceMap = new HashMap<Integer, Integer>();

    /**
     * Contains a mapping of preference key resource identifiers to sound
     * resource identifiers.
     */
    private final Map<String, Integer> mCustomResourceMap = new HashMap<String, Integer>();

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
        // TODO(alanv): This should be an abstract class, this code is
        // implementation-specific. Maybe we can specify this in XML?
        mDefaultResourceMap.put(R.string.pref_sounds_hover_key, R.raw.view_hover_enter);
        mDefaultResourceMap.put(R.string.pref_sounds_actionable_key,
                R.raw.view_hover_enter_actionable);
        mDefaultResourceMap.put(R.string.pref_sounds_explore_begin_key, R.raw.explore_begin);
        mDefaultResourceMap.put(R.string.pref_sounds_explore_end_key, R.raw.explore_end);
        mDefaultResourceMap.put(R.string.pref_sounds_clicked_key, R.raw.view_text_changed);
        mDefaultResourceMap.put(R.string.pref_sounds_focused_key, R.raw.view_focused);
        mDefaultResourceMap.put(R.string.pref_sounds_notification_state_key,
                R.raw.notification_state_changed);
        mDefaultResourceMap.put(R.string.pref_sounds_text_changed_key, R.raw.view_text_changed);

        mDefaultResourceMap.put(R.string.pref_patterns_hover_key, R.array.view_hovered_pattern);
        mDefaultResourceMap.put(R.string.pref_patterns_actionable_key,
                R.array.view_actionable_pattern);
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
            final int customResId =
                    mResources.getIdentifier(resName, null, mContext.getPackageName());

            // Cache the value in the custom resource map.
            mCustomResourceMap.put(key, customResId);

            return customResId;
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
