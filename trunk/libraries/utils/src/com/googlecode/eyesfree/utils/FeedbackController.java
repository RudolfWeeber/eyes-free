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

package com.googlecode.eyesfree.utils;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.leff.midi.MidiFile;
import com.leff.midi.MidiTrack;
import com.leff.midi.event.Controller;
import com.leff.midi.event.ProgramChange;
import com.leff.midi.event.meta.Tempo;
import com.leff.midi.event.meta.TimeSignature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Provides auditory and haptic feedback.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class FeedbackController {
    /** Default stream for audio feedback. */
    private static final int DEFAULT_STREAM = AudioManager.STREAM_MUSIC;

    /** Default volume for sound playback relative to current stream volume. */
    private static final float DEFAULT_VOLUME = 1.0f;

    /** Default rate for sound playback. Use 1.0f for normal speed playback. */
    private static final float DEFAULT_RATE = 1.0f;

    /** Number of channels to use in SoundPool for auditory icon feedback. */
    private static final int NUMBER_OF_CHANNELS = 10;

    /** Default beats-per-minute for MIDI tracks. You can dance to 95. */
    private static final int MIDI_DEFAULT_BPM = 95;

    /** Default channel for MIDI tracks. This should be 0. */
    private static final int MIDI_DEFAULT_CHANNEL = 0;

    /** Controller type for the most significant 7 bits of volume. */
    private static final int MIDI_CONTROLLER_VOLUME_MSB = 0x07;

    /** Default volume for MIDI tracks. Maximum value is 0x7F. */
    private static final int MIDI_DEFAULT_VOLUME = 0x7F;

    /** Default tempo track for MIDI compositions. Uses 4/4 signature. */
    private static final MidiTrack MIDI_DEFAULT_TEMPO_TRACK = new MidiTrack();

    static {
        final TimeSignature timeSignature = new TimeSignature();
        final Tempo tempo = new Tempo();
        timeSignature.setTimeSignature(
                4, 4, TimeSignature.DEFAULT_METER, TimeSignature.DEFAULT_DIVISION);
        tempo.setBpm(MIDI_DEFAULT_BPM);

        MIDI_DEFAULT_TEMPO_TRACK.insertEvent(timeSignature);
        MIDI_DEFAULT_TEMPO_TRACK.insertEvent(tempo);
    }

    /** Default delay time between repeated sounds. */
    private static final int DEFAULT_REPETITION_DELAY = 150;

    /** Map of resource IDs to vibration pattern arrays. */
    private final SparseArray<long[]> mResourceIdToVibrationPatternMap = new SparseArray<long[]>();

    /** Map of resource IDs to loaded sound stream IDs. */
    private final SparseIntArray mResourceIdToSoundMap = new SparseIntArray();

    /** Unloaded resources to play post-load */
    private final ArrayList<Integer> mPostLoadPlayables = new ArrayList<Integer>();

    /** Parent context. Required for mapping resource IDs to resources. */
    private final Context mContext;

    /** Parent resources. Used to distinguish raw and MIDI resources. */
    private final Resources mResources;

    /** Vibration service used to play vibration patterns. */
    private final Vibrator mVibrator;

    /** Sound pool used to play auditory icons. */
    private final SoundPool mSoundPool;

    /** Handler used for delaying feedback */
    private final Handler mHandler;

    /** Whether haptic feedback is enabled. */
    private boolean mHapticEnabled = true;

    /** Whether auditory feedback is enabled. */
    private boolean mAuditoryEnabled = true;

    /** Current volume (range 0..1). */
    private float mVolume = DEFAULT_VOLUME;

    /**
     * Used with {@link #playMidiScale(int, int, int, int, int, int)} to
     * generate a major scale
     */
    public static final int MIDI_SCALE_TYPE_MAJOR = 1;

    /**
     * Used with {@link #playMidiScale(int, int, int, int, int, int)} to
     * generate a natural minor scale
     */
    public static final int MIDI_SCALE_TYPE_NATURAL_MINOR = 2;

    /**
     * Used with {@link #playMidiScale(int, int, int, int, int, int)} to
     * generate a harmonic minor scale
     */
    public static final int MIDI_SCALE_TYPE_HARMONIC_MINOR = 3;

    /**
     * Used with {@link #playMidiScale(int, int, int, int, int, int)} to
     * generate a melodic minor scale
     */
    public static final int MIDI_SCALE_TYPE_MELODIC_MINOR = 4;

    /**
     * Used with {@link #playMidiScale(int, int, int, int, int, int)} to
     * generate a pentatonic major scale
     */
    public static final int MIDI_SCALE_TYPE_PENTATONIC = 5;

    /**
     * Constructs and initializes a new feedback controller.
     */
    public FeedbackController(Context context) {
        mContext = context;
        mResources = context.getResources();
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mSoundPool = new SoundPool(NUMBER_OF_CHANNELS, DEFAULT_STREAM, 1);
        mSoundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (status == 0) {
                    synchronized (mPostLoadPlayables) {
                        if (mPostLoadPlayables.contains(sampleId)) {
                            soundPool.play(
                                    sampleId, DEFAULT_VOLUME, DEFAULT_VOLUME, 1, 0, DEFAULT_RATE);
                            mPostLoadPlayables.remove(Integer.valueOf(sampleId));
                        }
                    }
                }
            }
        });
        mHandler = new Handler();

        mResourceIdToSoundMap.clear();
        mResourceIdToVibrationPatternMap.clear();
    }

    /**
     * @return The parent context.
     */
    protected final Context getContext() {
        return mContext;
    }

    /**
     * Sets the current volume for auditory feedback.
     *
     * @param volume Volume value (range 0..100).
     */
    public void setVolume(int volume) {
        mVolume = (Math.min(100, Math.max(0, volume)) / 100.0f);
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
     * Stops all active feedback.
     */
    public void interrupt() {
        mVibrator.cancel();
    }

    /**
     * Releases resources associated with this feedback controller. It is good
     * practice to call this method when you're done using the controller.
     */
    public void shutdown() {
        mVibrator.cancel();
        mSoundPool.release();
    }

    /**
     * Asynchronously make a sound available for later use if audio feedback is
     * enabled. Sounds should be loaded using this function whenever audio
     * feedback is enabled.
     *
     * @param resId Resource ID of the sound to be loaded.
     * @return The sound pool identifier for the resource.
     */
    public int preloadSound(int resId) {
        final int soundPoolId;

        final String resType = mResources.getResourceTypeName(resId);
        if ("raw".equals(resType)) {
            soundPoolId = mSoundPool.load(mContext, resId, 1);
        } else if ("array".equals(resType)) {
            final int[] notes = mResources.getIntArray(resId);
            soundPoolId = loadMidiSoundFromArray(notes, false);
        } else {
            LogUtils.log(this, Log.ERROR, "Failed to load sound: Unknown resource type");
            return -1;
        }

        if (soundPoolId < 0) {
            LogUtils.log(this, Log.ERROR, "Failed to load sound: Invalid sound pool ID");
            return -1;
        }

        mResourceIdToSoundMap.put(resId, soundPoolId);
        return soundPoolId;
    }

    /**
     * Convenience method for playing different sounds based on a boolean
     * result.
     *
     * @param result The conditional that controls which sound resource is
     *            played.
     * @param trueResId The resource to play if the result is {@code true}.
     * @param falseResId The resource to play if the result is {@code false}.
     * @see #playSound(int)
     */
    public void playSoundConditional(boolean result, int trueResId, int falseResId) {
        final int resId = (result ? trueResId : falseResId);

        if (resId > 0) {
            playSound(resId);
        }
    }

    /**
     * Plays the sound file specified by the given resource identifier at the
     * default rate.
     *
     * @param resId The sound file's resource identifier.
     * @return {@code true} if successful
     */
    public boolean playSound(int resId) {
        return playSound(resId, DEFAULT_RATE, DEFAULT_VOLUME);
    }

    /**
     * Plays the sound file specified by the given resource identifier at the
     * specified rate.
     *
     * @param resId The sound file's resource identifier.
     * @param rate The rate at which to play back the sound, where 1.0 is normal
     *            speed and 0.5 is half-speed.
     * @param volume The volume at which to play the sound, where 1.0 is normal
     *            volume and 0.5 is half-volume.
     * @return {@code true} if successful
     */
    public boolean playSound(int resId, float rate, float volume) {
        if (!mAuditoryEnabled) {
            return false;
        }

        final int soundId = mResourceIdToSoundMap.get(resId);

        if (soundId == 0) {
            // Make the sound available for the next time it is needed
            // to preserve backwards compatibility with callers who don't
            // preload the sounds.
            loadAndPlaySound(resId);

            // Since we need to play the sound immediately after it loads, we
            // should just return true.
            return true;
        }

        final float relativeVolume = mVolume * volume;
        final int stream = mSoundPool.play(soundId, relativeVolume, relativeVolume, 1, 0, rate);

        return (stream != 0);
    }

    /**
     * Plays the sound file specified by the given resource identifier repeatedly.
     *
     * @param resId The resource of the sound file to play
     * @param repetitions The number of times to repeat the sound file
     * @return {@code true} if successful
     */
    public boolean playRepeatedSound(int resId, int repetitions) {
        return playRepeatedSound(
                resId, DEFAULT_RATE, DEFAULT_VOLUME, repetitions, DEFAULT_REPETITION_DELAY);
    }

    /**
     * Plays the sound file specified by the given resource identifier
     * repeatedly.
     *
     * @param resId The resource of the sound file to play
     * @param rate The rate at which to play the sound file
     * @param volume The volume at which to play the sound, where 1.0 is normal
     *            volume and 0.5 is half-volume.
     * @param repetitions The number of times to repeat the sound file
     * @param delay The amount of time between calls to start playback of the
     *            sound in miliseconds. Should be the sound's playback time plus
     *            some delay.
     * @return {@code true} if successful
     */
    public boolean playRepeatedSound(
            int resId, float rate, float volume, int repetitions, long delay) {
        if (!mAuditoryEnabled) {
            return false;
        }

        mHandler.post(new RepeatedSoundRunnable(resId, rate, volume, repetitions, delay));
        return true;
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
        if (!mAuditoryEnabled || pitchesToPlay <= 0 || duration <= 0) {
            return false;
        }

        if (scaleType == MIDI_SCALE_TYPE_PENTATONIC) {
            // Pentatonic are 5-note scales that drop the 4th and 7th notes in
            // each scale. To play the correct number of pitches, we must add
            // the number of notes to be dropped to the original major scale.
            int completeScales = pitchesToPlay / 5;
            int notesInPartialScale = pitchesToPlay % 5;

            pitchesToPlay += (completeScales * 2) + ((notesInPartialScale > 3) ? 1 : 0);
        }

        final ArrayList<Integer> notes = new ArrayList<Integer>();

        // Generate as much of a major scale is needed.
        int nextPitch = startingPitch;
        for (int i = 1; i <= pitchesToPlay; ++i) {
            notes.add(nextPitch);

            // Calculate the next pitch based on scale position.
            final int noteInScale = (i % 7);
            switch (noteInScale) {
                case 1:
                case 2:
                case 4:
                case 5:
                case 6:
                    nextPitch += 2;
                    break;
                case 0:
                case 3:
                    nextPitch += 1;
                    break;
            }
        }

        if (scaleType == MIDI_SCALE_TYPE_NATURAL_MINOR
                || scaleType == MIDI_SCALE_TYPE_HARMONIC_MINOR
                || scaleType == MIDI_SCALE_TYPE_MELODIC_MINOR) {
            for (int i = 0; i < notes.size(); ++i) {
                final int noteInScale = (i + 1) % 7;

                // Lower the 3rd, 6th, and 7th of each scale by a half step
                // based on the type of minor scale.
                switch (noteInScale) {
                    case 3:
                        notes.add(i, (notes.remove(i) - 1));
                        break;
                    case 6:
                        if (scaleType == MIDI_SCALE_TYPE_NATURAL_MINOR
                                || scaleType == MIDI_SCALE_TYPE_HARMONIC_MINOR) {
                            notes.add(i, (notes.remove(i) - 1));
                        }
                        break;
                    case 0:
                        if (scaleType == MIDI_SCALE_TYPE_NATURAL_MINOR) {
                            notes.add(i, (notes.remove(i) - 1));
                        }
                        break;
                }
            }
        } else if (scaleType == MIDI_SCALE_TYPE_PENTATONIC) {
            ArrayList<Integer> indiciesToRemove = new ArrayList<Integer>();
            for (int i = 0; i < notes.size(); ++i) {
                final int noteInScale = (i + 1) % 7;

                // Petatonic scales are derived by removing the 4th and 7th from each scale.
                switch (noteInScale) {
                    case 4:
                    case 0:
                        indiciesToRemove.add(i);
                }
            }

            for (int i = indiciesToRemove.size(); i > 0; --i) {
                notes.remove((int) indiciesToRemove.get(i - 1));
            }
        }

        // Generate the MIDI sequence array from the derived notes
        int[] midiSequence = new int[(notes.size() * 3) + 1];
        midiSequence[0] = program;
        for (int i = 1; i < midiSequence.length; i += 3) {
            midiSequence[i] = notes.remove(0);
            midiSequence[i + 1] = velocity;
            midiSequence[i + 2] = duration;
        }

        // TODO(caseyburkhardt): See if these can be cached reasonably.
        final int soundPoolId = loadMidiSoundFromArray(midiSequence, true);
        if (soundPoolId < 0) {
            return false;
        }

        return true;
    }

    /**
     * Plays the vibration pattern specified by the given resource identifier.
     *
     * @param resId The vibration pattern's resource identifier.
     * @return {@code true} if successful
     */
    public boolean playVibration(int resId) {
        if (!mHapticEnabled || (mVibrator == null)) {
            return false;
        }

        long[] pattern = getVibrationPattern(resId);
        mVibrator.vibrate(pattern, -1);
        return true;
    }

    /**
     * Plays the vibration pattern specified by the given resource identifier
     * and repeats it indefinitely from the specified index.
     *
     * @see #cancelVibration()
     * @param resId The vibration pattern's resource identifier.
     * @param repeatIndex The index at which to loop vibration in the pattern.
     * @return {@code true} if successful
     */
    public boolean playRepeatedVibration(int resId, int repeatIndex) {
        if (!mHapticEnabled || (mVibrator == null)) {
            return false;
        }

        long[] pattern = getVibrationPattern(resId);

        if (repeatIndex < 0 || repeatIndex >= pattern.length) {
            throw new ArrayIndexOutOfBoundsException(repeatIndex);
        }
        mVibrator.vibrate(pattern, repeatIndex);
        return true;
    }

    /**
     * Cancels vibration feedback if in progress.
     */
    public void cancelVibration() {
        if (mVibrator != null) {
            mVibrator.cancel();
        }
    }

    /**
     * Retrieves the vibration pattern associated with the array resource.
     *
     * @param resId The array resource id of the pattern to retrieve.
     * @return an array of {@code long} values from the pattern.
     */
    private long[] getVibrationPattern(int resId) {
        final long[] cachedPattern = mResourceIdToVibrationPatternMap.get(resId);
        if (cachedPattern != null) {
            return cachedPattern;
        }

        final int[] intPattern = mResources.getIntArray(resId);
        if (intPattern == null) {
            return new long[0];
        }

        final long[] pattern = new long[intPattern.length];
        for (int i = 0; i < pattern.length; i++) {
            pattern[i] = intPattern[i];
        }

        mResourceIdToVibrationPatternMap.put(resId, pattern);

        return pattern;
    }

    /**
     * Loads the sound specified by the raw resource id and plays it. If
     *
     * @param resId The resource id of the sound to play.
     */
    private void loadAndPlaySound(int resId) {
        if (mResourceIdToSoundMap.indexOfKey(resId) >= 0) {
            // The sound is already loaded.
            return;
        }

        final int soundPoolId = preloadSound(resId);
        mPostLoadPlayables.add(soundPoolId);
    }

    public void playMidiSoundFromPool(int soundID) {
        mSoundPool.play(soundID, DEFAULT_VOLUME, DEFAULT_VOLUME, 1, 0, DEFAULT_RATE);
    }

    public int loadMidiSoundFromArray(int[] notes, boolean playOnLoad) {
        final MidiTrack track = readMidiTrackFromArray(notes);
        if (track == null) {
            return -1;
        }

        final File midiFile = writeMidiTrackToTempFile(track);
        if (midiFile == null) {
            return -1;
        }

        final int soundId = mSoundPool.load(midiFile.getPath(), 1);

        if (playOnLoad) {
            mPostLoadPlayables.add(soundId);
        }

        return soundId;
    }

    /**
     * Reads a MIDI track from an array of notes. The array format must be:
     * <ul>
     * <li>Program ID,
     * <li>Note pitch, velocity, duration,
     * <li>(additional notes)
     * </ul>
     *
     * @param notes The array to read as a MIDI track.
     * @return A MIDI track.
     */
    private MidiTrack readMidiTrackFromArray(int[] notes) {
        final MidiTrack noteTrack = new MidiTrack();
        int tick = 0;

        final int program = notes[0];
        if ((program < 0) || (program > 127)) {
            throw new IllegalArgumentException("MIDI track program must be in the range [0,127]");
        }

        noteTrack.insertEvent(new Controller(
                0, 0, MIDI_DEFAULT_CHANNEL, MIDI_CONTROLLER_VOLUME_MSB, MIDI_DEFAULT_VOLUME));
        noteTrack.insertEvent(new ProgramChange(0, 0, program));

        if ((notes.length % 3) != 1) {
            throw new IllegalArgumentException(
                    "MIDI note array must contain a single integer followed by triplets");
        }

        for (int i = 1; i < (notes.length - 2); i += 3) {
            final int pitch = notes[i];
            if ((pitch < 21) || (pitch > 108)) {
                throw new IllegalArgumentException("MIDI note pitch must be in the range [21,108]");
            }

            final int velocity = notes[i + 1];
            if ((velocity < 0) || (velocity > 127)) {
                throw new IllegalArgumentException(
                        "MIDI note velocity must be in the range [0,127]");
            }

            final int duration = notes[i + 2];

            noteTrack.insertNote(MIDI_DEFAULT_CHANNEL, pitch, velocity, tick, duration);

            tick += duration;
        }

        return noteTrack;
    }

    private File writeMidiTrackToTempFile(MidiTrack noteTrack) {
        // Always add the default tempo track first.
        final ArrayList<MidiTrack> tracks = new ArrayList<MidiTrack>();
        tracks.add(MIDI_DEFAULT_TEMPO_TRACK);
        tracks.add(noteTrack);

        // Attempt to write the track to a file and return it.
        try {
            final MidiFile midi = new MidiFile(MidiFile.DEFAULT_RESOLUTION, tracks);
            final File output = File.createTempFile("talkback", "mid");
            midi.writeToFile(output);
            return output;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Class used for repeated playing of sound resources.
     */
    private class RepeatedSoundRunnable implements Runnable {
        private final int mResId;

        private final float mPlaybackRate;

        private final float mPlaybackVolume;

        private final int mRepetitions;

        private final long mDelay;

        private int mTimesPlayed;

        public RepeatedSoundRunnable(
                int resId, float rate, float volume, int repetitions, long delay) {
            mResId = resId;
            mPlaybackRate = rate;
            mPlaybackVolume = volume;
            mRepetitions = repetitions;
            mDelay = delay;

            mTimesPlayed = 0;
        }

        @Override
        public void run() {
            if (mTimesPlayed < mRepetitions) {
                playSound(mResId, mPlaybackRate, mPlaybackVolume);
                mTimesPlayed++;
                mHandler.postDelayed(this, mDelay);
            }
        }
    }
}
