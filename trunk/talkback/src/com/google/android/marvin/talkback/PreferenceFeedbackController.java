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

package com.google.android.marvin.talkback;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;

import com.google.android.marvin.utils.PackageManagerUtils;
import com.google.android.marvin.utils.SecureSettingsUtils;
import com.googlecode.eyesfree.utils.FeedbackController;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;

/**
 * Provides auditory and haptic feedback.
 *
 * @author alanv@google.com (Alan Viverette)
 */
class PreferenceFeedbackController extends FeedbackController {
    private final SharedPreferences mPrefs;
    private final ContentResolver mResolver;
    /**
     * Constructs a new preference-aware feedback controller.
     *
     * @param context The parent context.
     */
    public PreferenceFeedbackController(Context context) {
        super(context);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mPrefs.registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);

        final Uri servicesUri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        mResolver = context.getContentResolver();
        mResolver.registerContentObserver(servicesUri, false, mContentObserver);

        updatePreferences(mPrefs, null);
    }

    @Override
    public void shutdown() {
        super.shutdown();

        mPrefs.unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        mResolver.unregisterContentObserver(mContentObserver);
    }

    /**
     * Updates preferences from an instance of {@link SharedPreferences}.
     * Optionally specify a key to update only that preference.
     *
     * @param prefs An instance of {@link SharedPreferences}.
     * @param key The key to update, or {@code null} to update all preferences.
     */
    private void updatePreferences(SharedPreferences prefs, String key) {
        final Context context = getContext();
        final Resources res = context.getResources();

        // TODO: Only reload the preference specified by the key.
        final int volumePref =
                SharedPreferencesUtils.getIntFromStringPref(prefs, res,
                        R.string.pref_soundback_volume_key,
                        R.string.pref_soundback_volume_default);

        final int kickBackVersionCode = PackageManagerUtils.getVersionCode(
                context, TalkBackService.KICKBACK_PACKAGE);
        final int soundBackVersionCode = PackageManagerUtils.getVersionCode(
                context, TalkBackService.SOUNDBACK_PACKAGE);

        final boolean vibrationPref;
        final boolean soundbackPref;

        if (kickBackVersionCode >= TalkBackService.KICKBACK_REQUIRED_VERSION) {
            vibrationPref = SecureSettingsUtils.isAccessibilityServiceEnabled(
                    context, TalkBackService.KICKBACK_PACKAGE);
        } else {
            vibrationPref = SharedPreferencesUtils.getBooleanPref(
                    prefs, res, R.string.pref_vibration_key, R.bool.pref_vibration_default);
        }

        if (soundBackVersionCode >= TalkBackService.SOUNDBACK_REQUIRED_VERSION) {
            soundbackPref = SecureSettingsUtils.isAccessibilityServiceEnabled(
                    context, TalkBackService.SOUNDBACK_PACKAGE);
        } else {
            soundbackPref = SharedPreferencesUtils.getBooleanPref(
                    prefs, res, R.string.pref_soundback_key, R.bool.pref_soundback_default);
        }

        setHapticEnabled(vibrationPref);
        setAuditoryEnabled(soundbackPref);
        setVolume(volumePref);
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                        String key) {
                    updatePreferences(sharedPreferences, key);
                }
            };

    private final ContentObserver mContentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (selfChange) {
                // Don't handle self-changes.
                return;
            }

            final SharedPreferences sharedPreferences = PreferenceManager
                    .getDefaultSharedPreferences(getContext());

            updatePreferences(sharedPreferences, null);
        }
    };
}
