/*
 * Copyright (C) 2012 Google Inc.
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

package com.googlecode.eyesfree.brailleback;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;

/**
 * Activity used to set BrailleBack's service preferences.
 */
public class BrailleBackPreferencesActivity extends PreferenceActivity {
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        assignKeyBindingsIntent();
    }

    /**
     * Assigns the appropriate intent to the key bindings preference.
     */
    private void assignKeyBindingsIntent() {
        final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                R.string.pref_category_device_key);
        final Preference pref = findPreferenceByResId(R.string.pref_key_bindings_key);

        if ((category == null) || (pref == null)) {
            return;
        }

        // TODO: If no device is connected, we should probably remove the
        // preference.

        // if (!isDeviceConnected) {
        // category.removePreference(pref);
        // return;
        // }

        final Intent intent = new Intent(this, KeyBindingsActivity.class);

        pref.setIntent(intent);
    }

    /**
     * Returns the preference associated with the specified resource identifier.
     *
     * @param resId A string resource identifier.
     * @return The preference associated with the specified resource identifier.
     */
    @SuppressWarnings("deprecation")
    private Preference findPreferenceByResId(int resId) {
        return findPreference(getString(resId));
    }
}
