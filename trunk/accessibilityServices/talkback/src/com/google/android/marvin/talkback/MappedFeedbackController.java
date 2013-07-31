/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.preference.PreferenceManager;

import com.google.android.marvin.utils.MappedSoundPool;
import com.google.android.marvin.utils.MappedVibrator;
import com.google.android.marvin.utils.SecureSettingsUtils;
import com.googlecode.eyesfree.utils.MidiUtils;
import com.googlecode.eyesfree.utils.PackageManagerUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;


/**
 * Feedback controller that maps sounds and vibrations to arbitrary identifiers.
 */
public class MappedFeedbackController {
    private static final String PREFS_NAME = "custom_feedback";

    // Preference types
    private static final String TYPE_PATTERN = "pattern";
    private static final String TYPE_RESOURCE = "resId";
    private static final String TYPE_PATH = "path";

    private static MappedFeedbackController sSharedInstance;

    /**
     * Initializes and returns a new shared instance of the feedback controller.
     *
     * @param context The service context.
     * @return A new instance of the feedback controller.
     */
    public static MappedFeedbackController initialize(Context context) {
        sSharedInstance = new MappedFeedbackController(context);

        return sSharedInstance;
    }

    /**
     * @return A previously initialized shared instance of the feedback
     *         controller.
     */
    public static MappedFeedbackController getInstance() {
        if (sSharedInstance == null) {
            throw new RuntimeException("Mapped feedback controller must be initialized");
        }

        return sSharedInstance;
    }

    /** The parent context. */
    private final Context mContext;

    /** The resources for this context, used to map IDs to names. */
    private final Resources mResources;

    /** A sound pool that maps arbitrary IDs to playable sounds. */
    private final MappedSoundPool mSoundPool;

    /** A vibrator that maps arbitrary IDs to playable patterns. */
    private final MappedVibrator mVibrator;

    /** Preferences for sound and vibration mapping. */
    private final SharedPreferences mMapPrefs;

    private final boolean mUseCompatKickBack;
    private final boolean mUseCompatSoundBack;

    private float mVolumeAdjustment = 1.0f;

    private boolean mAuditoryEnabled;
    private boolean mHapticEnabled;

    private MappedFeedbackController(Context context) {
        mContext = context;
        mResources = context.getResources();
        mSoundPool = new MappedSoundPool(context);
        mVibrator = new MappedVibrator(context);

        mMapPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mMapPrefs.registerOnSharedPreferenceChangeListener(mMapPreferenceListener);

        // TODO: Do we really need to check compatibility on versions >ICS?
        mUseCompatKickBack = shouldUseCompatKickBack();
        mUseCompatSoundBack = shouldUseCompatSoundBack();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(mPreferenceListener);
        updatePreferences(prefs, null);

        applyAllMapPreferences();
    }

    /**
     * @return A new instance of a theme loader for this controller.
     */
    public MappedThemeLoader getThemeLoader() {
        return new MappedThemeLoader(mContext, mSoundPool, mVibrator);
    }

    /**
     * @param enabled Whether haptic feedback should be enabled.
     */
    public void setHapticEnabled(boolean enabled) {
        mHapticEnabled = enabled;
    }

    /**
     * @param enabled Whether auditory feedback should be enabled.
     */
    public void setAuditoryEnabled(boolean enabled) {
        mAuditoryEnabled = enabled;
    }

    /**
     * Sets the current volume adjustment for auditory feedback.
     *
     * @param adjustment Volume value (range 0..1).
     */
    public void setVolumeAdjustment(float adjustment) {
        mVolumeAdjustment = adjustment;
    }

    /**
     * Plays the vibration pattern assigned to the given identifier.
     *
     * @param id The vibration pattern's identifier.
     * @return {@code true} if successful
     */
    public boolean playHaptic(int id) {
        return playHaptic(id, -1);
    }

    /**
     * Plays the vibration pattern assigned to the given identifier using the
     * specified repeat index (see
     * {@link android.os.Vibrator#vibrate(long[], int)}).
     *
     * @param id The vibration pattern's identifier.
     * @param repeatIndex The index at which to repeat, or {@code -1} to not
     *            repeat.
     * @return {@code true} if successful
     */
    public boolean playHaptic(int id, int repeatIndex) {
        if (!mHapticEnabled) {
            return false;
        }

        return mVibrator.play(id, repeatIndex);
    }

    /**
     * Plays the auditory feedback assigned to the given identifier using the
     * default rate, volume, and panning.
     *
     * @param id The auditory feedback's identifier.
     * @return {@code true} if successful
     */
    public boolean playAuditory(int id) {
        return playAuditory(id, 1.0f /* rate */, 1.0f /* volume */, 0.0f /* pan */);
    }

    /**
     * Plays the auditory feedback assigned to the given identifier using the
     * specified rate, volume, and panning.
     *
     * @param id The auditory feedback's identifier.
     * @param rate The playback rate adjustment (range 0..2).
     * @param volume The volume adjustment (range 0..1).
     * @param pan The panning adjustment (range -1..1).
     * @return {@code true} if successful
     */
    public boolean playAuditory(int id, float rate, float volume, float pan) {
        if (!mAuditoryEnabled) {
            return false;
        }

        return mSoundPool.play(id, rate, (volume * mVolumeAdjustment), pan);
    }

    /**
     * Interrupts all ongoing feedback.
     */
    public void interrupt() {
        mSoundPool.interrupt();
        mVibrator.interrupt();
    }

    /**
     * Releases all resources held by the feedback controller and clears the
     * shared instance. No calls should be made to this class after calling this
     * method.
     */
    public void shutdown() {
        sSharedInstance = null;

        mSoundPool.shutdown();
        mVibrator.shutdown();
    }

    private boolean shouldUseCompatKickBack() {
        if (!PackageManagerUtils.hasPackage(mContext, TalkBackUpdateHelper.KICKBACK_PACKAGE)) {
            return false;
        }

        final int kickBackVersionCode = PackageManagerUtils.getVersionCode(
                mContext, TalkBackUpdateHelper.KICKBACK_PACKAGE);
        if (kickBackVersionCode < TalkBackUpdateHelper.KICKBACK_REQUIRED_VERSION) {
            return false;
        }

        return true;
    }

    private boolean shouldUseCompatSoundBack() {
        if (!PackageManagerUtils.hasPackage(mContext, TalkBackUpdateHelper.SOUNDBACK_PACKAGE)) {
            return false;
        }

        final int kickBackVersionCode = PackageManagerUtils.getVersionCode(
                mContext, TalkBackUpdateHelper.SOUNDBACK_PACKAGE);
        if (kickBackVersionCode < TalkBackUpdateHelper.SOUNDBACK_REQUIRED_VERSION) {
            return false;
        }

        return true;
    }

    /**
     * Updates preferences from an instance of {@link SharedPreferences}.
     * Optionally specify a key to update only that preference.
     *
     * @param prefs An instance of {@link SharedPreferences}.
     * @param key The key to update, or {@code null} to update all preferences.
     */
    private void updatePreferences(SharedPreferences prefs, String key) {
        if (key == null) {
            updateHapticFromPreference(prefs);
            updateAuditoryFromPreference(prefs);
            updateVolumeAdjustmentFromPreference(prefs);
        } else if (key.equals(mContext.getString(R.string.pref_vibration_key))) {
            updateHapticFromPreference(prefs);
        } else if (key.equals(mContext.getString(R.string.pref_soundback_key))) {
            updateAuditoryFromPreference(prefs);
        } else if (key.equals(mContext.getString(R.string.pref_soundback_volume_key))) {
            updateVolumeAdjustmentFromPreference(prefs);
        }
    }

    private void updateVolumeAdjustmentFromPreference(SharedPreferences prefs) {
        final int adjustment = SharedPreferencesUtils.getIntFromStringPref(prefs, mResources,
                R.string.pref_soundback_volume_key, R.string.pref_soundback_volume_default);

        setVolumeAdjustment(adjustment / 100.0f);
    }

    private void updateHapticFromPreference(SharedPreferences prefs) {
        final boolean enabled;

        if (mUseCompatKickBack) {
            enabled = SecureSettingsUtils.isAccessibilityServiceEnabled(
                    mContext, TalkBackUpdateHelper.KICKBACK_PACKAGE);
        } else {
            enabled = SharedPreferencesUtils.getBooleanPref(
                    prefs, mResources, R.string.pref_vibration_key, R.bool.pref_vibration_default);
        }

        setHapticEnabled(enabled);
    }

    private void updateAuditoryFromPreference(SharedPreferences prefs) {
        final boolean enabled;

        if (mUseCompatSoundBack) {
            enabled = SecureSettingsUtils.isAccessibilityServiceEnabled(
                    mContext, TalkBackUpdateHelper.SOUNDBACK_PACKAGE);
        } else {
            enabled = SharedPreferencesUtils.getBooleanPref(
                    prefs, mResources, R.string.pref_soundback_key, R.bool.pref_soundback_default);
        }

        setAuditoryEnabled(enabled);
    }

    /**
     * Applies all preferences in the mapping preferences store.
     */
    private void applyAllMapPreferences() {
        final String resPackage = mContext.getPackageName();
        final Map<String, ?> prefs = mMapPrefs.getAll();

        for (String entryName : prefs.keySet()) {
            final Object entry = prefs.get(entryName);
            if (!(entry instanceof String)) {
                continue;
            }

            final int id = mResources.getIdentifier(entryName, null, resPackage);
            if (id == 0) {
                continue;
            }

            // TODO: Should we remove the preference if it fails to apply?
            applyMapPreferenceFromEntry(id, (String) entry);
        }
    }

    /**
     * Attempts to load the preferred resource for the specified ID into the
     * appropriate mapped player.
     *
     * @param id The feedback identifier.
     * @param entry The resource as TYPE:VALUE.
     * @return {@code true} if a preference could be loaded for the identifier.
     */
    private boolean applyMapPreferenceFromEntry(int id, String entry) {
        if (entry == null) {
            return false;
        }

        final String[] splitValue = entry.split(":");
        if (splitValue.length != 2) {
            return false;
        }

        final String type = splitValue[0];
        final String value = splitValue[1];

        if (type.equals(TYPE_RESOURCE)) {
            final int resId = Integer.parseInt(value);
            return mSoundPool.load(id, resId);
        } else if (type.equals(TYPE_PATH)) {
            return mSoundPool.load(id, value);
        } else if (type.equals(TYPE_PATTERN)) {
            final int resId = Integer.parseInt(value);
            return mVibrator.load(id, resId);
        } else {
            return false;
        }
    }

    /**
     * Generates and plays a MIDI scale.
     *
     * @param program The MIDI program ID to use
     * @param velocity The MIDI velocity to use for each note
     * @param duration The duration in milliseconds of each note
     * @param startingPitch The MIDI pitch value on which the scale should begin
     * @param pitchesToPlay The number of pitches to play. 7 pitches (or 5
     *            pentatonic) is a complete scale. 8 (or 6 pentatonic) for a
     *            resolved scale.
     * @param scaleType The MIDI_SCALE_TYPE_* constant associated with the type
     *            of scale to play.
     *
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public boolean playMidiScale(int program, int velocity, int duration, int startingPitch,
            int pitchesToPlay, int scaleType) {
        if (!mAuditoryEnabled) {
            return false;
        }

        final int[] midiSequence = MidiUtils.generateMidiScale(
                program, velocity, duration, startingPitch, pitchesToPlay, scaleType);
        if (midiSequence == null) {
            return false;
        }

        final File file = MidiUtils.generateMidiFileFromArray(mContext, midiSequence);
        if (file == null) {
            return false;
        }

        final MediaPlayer scalePlayer = new MediaPlayer();
        scalePlayer.setOnPreparedListener(new OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                scalePlayer.start();
            }
        });

        scalePlayer.setOnCompletionListener(new OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                scalePlayer.release();
                file.delete();
            }
        });

        // Use the FD method of setting the MediaPlayer data source for
        // backwards compatibility.
        try {
            final FileInputStream in = new FileInputStream(file);

            try {
                scalePlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                scalePlayer.setDataSource(in.getFD());
                scalePlayer.prepareAsync();
            } finally {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener
            mMapPreferenceListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(
                        SharedPreferences sharedPreferences, String key) {
                    final int id = mResources.getIdentifier(key, null, mContext.getPackageName());
                    final String entryName = mResources.getResourceEntryName(id);
                    final String entry = mMapPrefs.getString(entryName, null);

                    applyMapPreferenceFromEntry(id, entry);
                }
            };

    private final OnSharedPreferenceChangeListener
            mPreferenceListener = new OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(
                        SharedPreferences sharedPreferences, String key) {
                    updatePreferences(sharedPreferences, key);
                }
            };
}
