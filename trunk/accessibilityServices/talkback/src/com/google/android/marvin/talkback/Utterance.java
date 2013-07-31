/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.marvin.talkback;

import android.os.Bundle;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * This class represents an utterance composed of text to be spoken and meta
 * data about how this text to be spoken. The utterances are cached in a pool of
 * instances to be reused as an optimization to reduce new object instantiation.
 *
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public class Utterance {

    /** Key for obtaining the queuing meta-data property. */
    public static final String KEY_METADATA_QUEUING = "queuing";

    /** Key for obtaining the earcon rate meta-data property. */
    public static final String KEY_METADATA_EARCON_RATE = "earcon_rate";

    /** Key for obtaining the earcon volume meta-data property. */
    public static final String KEY_METADATA_EARCON_VOLUME = "earcon_volume";

    /**
     * Key for obtaining the speech parameters meta-data property. Must contain
     * a {@link Bundle}.
     */
    public static final String KEY_METADATA_SPEECH_PARAMS = "speech_params";

    /** Key for obtaining the speech flags meta-data property. */
    public static final String KEY_METADATA_SPEECH_FLAGS = "speech_flags";

    /** The maximum size of the pool with cached utterances. */
    private static final int MAX_POOL_SIZE = 3;

    /** Lock for accessing the utterance pool. */
    private static final Object sPoolLock = new Object();

    /** Pool of cached utterances. */
    private static Utterance sPool;

    /** The current size of the utterance pool. */
    private static int sPoolSize;

    /** Lock for modifying the {@link #mReferenceCount}. */
    private final Object mReferenceLock = new Object();

    /** Meta-data of how the utterance should be spoken. */
    private final Bundle mMetadata = new Bundle();

    /** The list of text to speak. */
    private final List<CharSequence> mSpokenFeedback = new LinkedList<CharSequence>();

    /** The list of auditory feedback identifiers to play. */
    private final Set<Integer> mAuditoryFeedback = new HashSet<Integer>();

    /** The list of haptic feedback identifiers to play. */
    private final Set<Integer> mHapticFeedback = new HashSet<Integer>();

    /** The next cached utterance. */
    private Utterance mNext;

    /** Denotes if an utterance is currently in the cache pool. */
    private boolean mIsInPool;

    /** The number of references held to the data in this utterance. */
    private int mReferenceCount;

    /**
     * Creates a new instance.
     */
    private Utterance() {
        // This class is not publicly instantiable.
    }

    /**
     * Returns a shallow clone of an utterance.
     * <p>
     * It is safe to call {@link #recycle} on the original {@link Utterance} and
     * continue using the returned value.
     *
     * @param other An utterance.
     * @return A shallow clone of the utterance.
     */
    public static Utterance clone(Utterance other) {
        if (other == null) {
            return null;
        }

        // Increase the reference count to prevent recycling.
        synchronized (other.mReferenceLock) {
            other.mReferenceCount++;
        }

        return other;
    }

    /**
     * Returns a cached instance if such is available or a new one is
     * instantiated.
     *
     * @return An instance.
     */
    public static Utterance obtain() {
        return obtain("");
    }

    /**
     * Returns a cached instance if such is available or a new one is
     * instantiated and sets its <code>text</code>.
     *
     * @param text The text of the returned utterance.
     * @return An instance.
     */
    public static Utterance obtain(String text) {
        synchronized (sPoolLock) {
            if (sPool != null) {
                Utterance utterance = sPool;
                sPool = sPool.mNext;
                sPoolSize--;
                utterance.mNext = null;
                utterance.mIsInPool = false;
                return utterance;
            }
            return new Utterance();
        }
    }

    /**
     * Adds spoken feedback to this utterance.
     *
     * @param text The text to speak.
     */
    public void addSpoken(CharSequence text) {
        // TODO: Consider adding additional parameters (flags, pitch, etc.) and
        // allowing each element of spoken feedback to have different
        // properties. Will require extensive SpeechController modifications.
        mSpokenFeedback.add(text);
    }

    /**
     * Adds a spoken feedback flag to this utterance's metadata.
     *
     * @param flag The flag to add. One of:
     *            <ul>
     *            <li>{@link FeedbackItem#FLAG_DURING_RECO}
     *            <li>{@link FeedbackItem#FLAG_NO_HISTORY}
     *            <li>{@link FeedbackItem#FLAG_ADVANCE_CONTINUOUS_READING}
     *            </ul>
     */
    public void addSpokenFlag(int flag) {
        final int flags = mMetadata.getInt(KEY_METADATA_SPEECH_FLAGS, 0);
        mMetadata.putInt(KEY_METADATA_SPEECH_FLAGS, flags | flag);
    }

    /**
     * Adds auditory feedback to this utterance.
     *
     * @param id The value associated with the auditory feedback to play.
     */
    public void addAuditory(int id) {
        mAuditoryFeedback.add(id);
    }

    /**
     * Adds auditory feedback to this utterance.
     *
     * @param ids A collection of identifiers associated with the auditory
     *            feedback to play.
     */
    public void addAllAuditory(Collection<? extends Integer> ids) {
        mAuditoryFeedback.addAll(ids);
    }

    /**
     * Adds haptic feedback to this utterance.
     *
     * @param id The value associated with the haptic feedback to play.
     */
    public void addHaptic(int id) {
        mHapticFeedback.add(id);
    }

    /**
     * Adds haptic feedback to this utterance.
     *
     * @param ids A collection of identifiers associated with the haptic
     *            feedback to play.
     */
    public void addAllHaptic(Collection<? extends Integer> ids) {
        mHapticFeedback.addAll(ids);
    }

    /**
     * Gets the meta-data of this utterance.
     *
     * @return The utterance meta-data.
     */
    public Bundle getMetadata() {
        return mMetadata;
    }

    /**
     * @return An unmodifiable list of spoken text attached to this utterance.
     */
    public List<CharSequence> getSpoken() {
        return Collections.unmodifiableList(mSpokenFeedback);
    }

    /**
     * @return An unmodifiable set of auditory feedback identifiers attached to
     *         this utterance.
     */
    public Set<Integer> getAuditory() {
        return Collections.unmodifiableSet(mAuditoryFeedback);
    }

    /**
     * @return An unmodifiable set of haptic feedback identifiers attached to
     *         this utterance.
     */
    public Set<Integer> getHaptic() {
        return Collections.unmodifiableSet(mHapticFeedback);
    }

    /**
     * Return an instance back to be reused.
     * <p>
     * <b>Note: You must not touch the object after calling this function.</b>
     */
    public void recycle() {
        synchronized (mReferenceLock) {
            mReferenceCount--;

            if (mReferenceCount > 0) {
                return;
            }
        }

        if (mIsInPool) {
            return;
        }

        clear();

        synchronized (sPoolLock) {
            if (sPoolSize <= MAX_POOL_SIZE) {
                mNext = sPool;
                sPool = this;
                mIsInPool = true;
                sPoolSize++;
            }
        }
    }

    /**
     * Clears the state of this instance.
     */
    private void clear() {
        mMetadata.clear();
        mSpokenFeedback.clear();
        mAuditoryFeedback.clear();
        mHapticFeedback.clear();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Text:{");
        builder.append(mSpokenFeedback);
        builder.append("}, Metadata:");
        builder.append(mMetadata);
        return builder.toString();
    }
}
