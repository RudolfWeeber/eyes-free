
package com.google.android.marvin.utils;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.MidiUtils;

import java.io.File;

/**
 * Wrapper for {@link SoundPool} that maps client-specified IDs to cached
 * sounds.
 */
public class MappedSoundPool {
    /** Default stream for audio feedback. */
    public static final int DEFAULT_STREAM_TYPE = AudioManager.STREAM_MUSIC;

    /** Default volume for sound playback relative to current stream volume. */
    public static final float DEFAULT_VOLUME = 1.0f;

    /** Default rate for sound playback. Use 1.0f for normal speed playback. */
    public static final float DEFAULT_RATE = 1.0f;

    /** Default pan adjustment for sound playback. Use 0.0f for center. */
    public static final float DEFAULT_PAN = 0.0f;

    /** Type string for array resources. */
    private static final String RES_TYPE_ARRAY = "array";

    /** Type string for raw resources. */
    private static final String RES_TYPE_RAW = "raw";

    /** Maximum number of concurrent audio streams. */
    private static final int MAX_STREAMS = 10;

    /** Map of assigned IDs to sound resource IDs. */
    private final SparseIntArray mSoundPoolMap = new SparseIntArray();

    /** Map of assigned IDs to stream types. */
    private final SparseIntArray mStreamTypeMap = new SparseIntArray();

    /** Map of assigned stream types to sound pools. */
    private final SparseArray<SoundPool> mSoundPoolStreams = new SparseArray<SoundPool>();

    /** The parent context. */
    private final Context mContext;

    /** The parent context's resources. */
    private final Resources mResources;

    /**
     * Creates a new mapped sound pool using the specified parent
     * {@code context}.
     *
     * @param context The parent context used to obtain resources.
     */
    public MappedSoundPool(Context context) {
        mContext = context;
        mResources = context.getResources();
    }

    /**
     * Attempts to loads the sound resource with the specified {@code resId} for
     * playback on the default stream (see {@link #DEFAULT_STREAM_TYPE}).
     *
     * @param id The identifier to associated with the sound.
     * @param resId The resource identifier of the sound to load.
     * @return Whether the sound was loaded successfully.
     */
    public boolean load(int id, int resId) {
        return load(id, resId, DEFAULT_STREAM_TYPE);
    }

    /**
     * Attempts to loads the sound resource with the specified {@code resId} for
     * playback on the specified {@code streamType}.
     *
     * @param id The identifier to associated with the sound.
     * @param resId The resource identifier of the sound to load.
     * @return Whether the sound was loaded successfully.
     */
    public boolean load(int id, int resId, int streamType) {
        if (resId == 0) {
            return false;
        }

        final SoundPool soundPool = getOrCreateSoundPool(streamType);
        final String resType = mResources.getResourceTypeName(resId);
        final int soundId;

        if (RES_TYPE_RAW.equals(resType)) {
            soundId = soundPool.load(mContext, resId, 1);
        } else if (RES_TYPE_ARRAY.equals(resType)) {
            final int[] notes = mResources.getIntArray(resId);
            soundId = loadMidiSoundFromArray(soundPool, notes);
        } else {
            LogUtils.log(this, Log.ERROR, "Unknown resource type for %d", resId);
            return false;
        }

        return assign(id, soundId, streamType);
    }

    /**
     * Attempts to loads the sound at the specified {@code path} for playback on
     * the default stream (see {@link #DEFAULT_STREAM_TYPE}).
     *
     * @param id The identifier to associated with the sound.
     * @param path The path of the sound file to load.
     * @return Whether the sound was loaded successfully.
     */
    public boolean load(int id, String path) {
        return load(id, path, DEFAULT_STREAM_TYPE);
    }

    /**
     * Attempts to loads the sound at the specified {@code path} for playback on
     * the specified {@code streamType}.
     *
     * @param id The identifier to associated with the sound.
     * @param path The path of the sound file to load.
     * @param streamType The stream type for playback.
     * @return Whether the sound was loaded successfully.
     */
    public boolean load(int id, String path, int streamType) {
        final SoundPool soundPool = getOrCreateSoundPool(streamType);
        final int soundId = soundPool.load(path, 1);
        return assign(id, soundId, streamType);
    }

    /**
     * Unloads the sound associated with the specified {@code id}.
     *
     * @param id An identifier associated with a loaded sound.
     * @return {@code true} if the sound was unloaded, or {@code false} if it
     *         could not be found.
     */
    public boolean unload(int id) {
        final int soundId = mSoundPoolMap.get(id);
        if (soundId == 0) {
            return false;
        }

        final int streamType = mStreamTypeMap.get(id);
        final SoundPool soundPool = mSoundPoolStreams.get(streamType);
        if (soundPool == null) {
            return false;
        }

        return soundPool.unload(soundId);
    }

    /**
     * Plays the previously loaded sound associated with the specified
     * {@code id}.
     *
     * @param id The identifier associated with the sound.
     * @param rate The playback rate modifier, range {0...2}.
     * @param volume The volume level modifier, range {0...1}.
     * @param pan The panning value, range {-1...1} where 0 is center.
     * @return Whether sound playback started successfully.
     */
    public boolean play(int id, float rate, float volume, float pan) {
        final int soundId = mSoundPoolMap.get(id);
        if (soundId == 0) {
            return false;
        }

        final int streamType = mStreamTypeMap.get(id);
        final SoundPool soundPool = mSoundPoolStreams.get(streamType);
        if (soundPool == null) {
            return false;
        }

        final float leftVolume = DEFAULT_VOLUME * Math.min(1.0f, (1.0f - pan)) * volume;
        final float rightVolume = DEFAULT_VOLUME * Math.min(1.0f, (1.0f + pan)) * volume;
        final float playRate = DEFAULT_RATE * rate;

        final int resultSoundId = soundPool.play(soundId, leftVolume, rightVolume, 0, 0, playRate);
        if (resultSoundId == 0) {
            LogUtils.log(this, Log.ERROR, "Failed to play sound id %s", id);
            return false;
        }

        return true;
    }

    /**
     * Stops all active sound playback.
     */
    public void interrupt() {
        // TODO: Stop all active streams.
    }

    /**
     * Releases all sound resources. After calling this method, no calls should
     * be made to this object.
     */
    public void shutdown() {
        for (int i = (mSoundPoolStreams.size() - 1); i >= 0; i--) {
            mSoundPoolStreams.valueAt(i).release();
        }

        mSoundPoolStreams.clear();
    }

    /**
     * Returns a {@link SoundPool} for the specified stream type, creating a new
     * pool if necessary.
     *
     * @param streamType The playback stream type.
     * @return A {@link SoundPool} for the specified stream type.
     */
    private SoundPool getOrCreateSoundPool(int streamType) {
        final SoundPool soundPool = mSoundPoolStreams.get(streamType);
        if (soundPool != null) {
            return soundPool;
        }

        final SoundPool newPool = new SoundPool(MAX_STREAMS, streamType, 0);
        mSoundPoolStreams.put(streamType, newPool);
        return newPool;
    }

    /**
     * Associates a loaded sound identifier on a particular stream with a
     * client-specified identifier. If the identifier was already assigned,
     * unloads the previously assigned sound.
     *
     * @param id The client-specified identifier.
     * @param soundId The sound identifier.
     * @param streamType The client-specified playback stream.
     * @return Whether the sound was assigned successfully.
     */
    private boolean assign(int id, int soundId, int streamType) {
        if (soundId <= 0) {
            LogUtils.log(this, Log.ERROR, "Failed to assign sound for %d", id);
            return false;
        }

        if (mSoundPoolMap.indexOfKey(id) >= 0) {
            unload(id);
        }

        mSoundPoolMap.put(id, soundId);
        mStreamTypeMap.put(id, streamType);
        return true;
    }

    /**
     * Generates a MIDI file from the specified {@code notes} array and loads it
     * into the specified {@code soundPool}.
     *
     * @param soundPool The sound pool to load the MIDI file into.
     * @param notes An array specifying a MIDI track.
     * @return The sound identifier for the loaded MIDI notes, or {@code -1} on
     *         error.
     */
    private int loadMidiSoundFromArray(SoundPool soundPool, int[] notes) {
        final File midiFile = MidiUtils.generateMidiFileFromArray(mContext, notes);
        if (midiFile == null) {
            return -1;
        }

        return soundPool.load(midiFile.getPath(), 1);
    }
}
