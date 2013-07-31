
package com.google.android.marvin.utils;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.os.Vibrator;
import android.util.Log;
import android.util.SparseArray;

import com.googlecode.eyesfree.utils.LogUtils;

public class MappedVibrator {
    /** Map of client-specified IDs to vibration pattern arrays. */
    private final SparseArray<long[]> mPatternMap = new SparseArray<long[]>();

    /** Parent resources, used to load pattern array resources. */
    private final Resources mResources;

    /** The actual vibrator used to provide feedback. */
    private final Vibrator mVibrator;

    /**
     * Creates a {@link Vibrator} that maps client-specified IDs to resources.
     *
     * @param context The parent context.
     */
    public MappedVibrator(Context context) {
        mResources = context.getResources();
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * Attempts to loads the pattern resource with the specified {@code resId}
     * for vibration playback.
     *
     * @param id The identifier to associated with the pattern.
     * @param resId The resource identifier of the pattern to load.
     * @return Whether the pattern was loaded successfully.
     */
    public boolean load(int id, int resId) {
        if (resId == 0) {
            return false;
        }

        final int[] pattern;

        try {
            pattern = mResources.getIntArray(resId);
        } catch (NotFoundException e) {
            LogUtils.log(this, Log.ERROR, "Failed to load resource %d for pattern %d", id, resId);
            return false;
        }

        final long[] longPattern = new long[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            longPattern[i] = pattern[i];
        }

        mPatternMap.put(id, longPattern);
        return true;
    }

    /**
     * Plays the vibration pattern associated with the specified identifier.
     *
     * @param id The identifier to associated with the pattern.
     * @param repeatIndex The index into the pattern at which to start the
     *            repeat, or -1 to disable repeating.
     * @return Whether vibration was started successfully with the specified
     *         pattern.
     */
    public boolean play(int id, int repeatIndex) {
        final long[] pattern = mPatternMap.get(id);
        if (pattern == null) {
            LogUtils.log(this, Log.WARN, "Missing vibration for id %d", id);
            return false;
        }

        mVibrator.vibrate(pattern, repeatIndex);
        return true;
    }

    /**
     * Stops any ongoing vibration.
     */
    public void interrupt() {
        mVibrator.cancel();
    }

    /**
     * Releases resources associated with this vibrator. After calling this
     * method, no calls should be made to this object.
     */
    public void shutdown() {
        mVibrator.cancel();
    }
}
