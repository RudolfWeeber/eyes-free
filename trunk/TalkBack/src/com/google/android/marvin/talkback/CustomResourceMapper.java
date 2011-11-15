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
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * @author alanv@google.com (Alan Viverette)
 */
public class CustomResourceMapper {
    /**
     * Contains a mapping of preference key resource identifier to sound
     * resource identifier.
     */
    private final Map<Integer, Integer> mCustomResourceMap = new HashMap<Integer, Integer>();

    /**
     * Creates a new custom resource mapping and loads the default mappings.
     */
    public CustomResourceMapper() {
        loadDefaults();
    }

    private void loadDefaults() {
        // TODO(alanv): This should be an abstract class, this code is
        // implementation-specific. Maybe we can specify this in XML?
        mCustomResourceMap.put(R.string.pref_sounds_hover_key, R.raw.hover);
        mCustomResourceMap.put(R.string.pref_sounds_actionable_key, R.raw.actionable);
        mCustomResourceMap.put(R.string.pref_patterns_hover_key, R.array.view_hovered_pattern);
        mCustomResourceMap.put(R.string.pref_patterns_actionable_key, R.array.view_actionable_pattern);
    }

    /**
     * Loads resource mappings from preferences. Each preference should contain
     * a string in the format <code>@[type]/[name]</code>.
     * 
     * @param context The parent context.
     * @param prefs The shared preferences from which to load mappings.
     */
    public void loadFromPreferences(Context context, SharedPreferences prefs) {
        final Resources res = context.getResources();

        for (Integer keyResId : mCustomResourceMap.keySet()) {
            final String key = res.getString(keyResId);
            final String resName = prefs.getString(key, null);

            if (resName == null) {
                // No preference set, continue to use default.
                continue;
            } else if (!resName.matches("\\w+/\\w+")) {
                // Failed to parse resource reference.
                LogUtils.log(CustomResourceMapper.class, Log.ERROR, "Invalid reference: %s",
                        resName);
                continue;
            }

            final int resId =
                    res.getIdentifier(resName, null, context.getPackageName());

            mCustomResourceMap.put(keyResId, resId);
        }
    }

    public int getResourceIdForPreference(int key) {
        final Integer resId = mCustomResourceMap.get(key);

        if (resId == null) {
            return 0;
        }

        return resId;
    }
}
