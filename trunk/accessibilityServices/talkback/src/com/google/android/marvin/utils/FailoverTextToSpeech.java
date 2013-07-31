package com.google.android.marvin.utils;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.provider.Settings.Secure;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.marvin.talkback.SpeechController;
import com.googlecode.eyesfree.compat.provider.SettingsCompatUtils.SecureCompatUtils;
import com.googlecode.eyesfree.compat.speech.tts.TextToSpeechCompatUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.WeakReferenceHandler;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Set;

/**
 * Wrapper for {@link TextToSpeech} that handles fail-over when a specific
 * engine does not work.
 * <p>
 * Does <strong>NOT</strong> implement queuing! Every call to {@link #speak}
 * flushes the global speech queue.
 * <p>
 * This wrapper handles the following:
 * <ul>
 * <li>Fail-over from a failing TTS to a working one
 * <li>Splitting utterances into &lt;4k character chunks
 * <li>Switching to the system TTS when media is unmounted
 * <li>Utterance-specific pitch and rate changes
 * <li>Pitch and rate changes relative to the user preference
 * </ul>
 */
@SuppressWarnings("deprecation")
public class FailoverTextToSpeech {
    /** The package name for the Google TTS engine. */
    private static final String PACKAGE_GOOGLE_TTS = "com.google.android.tts";

    /** Number of times a TTS engine can fail before switching. */
    private static final int MAX_TTS_FAILURES = 3;

    /**
     * Utterances must be no longer than MAX_UTTERANCE_LENGTH for the TTS to be
     * able to handle them properly.
     * <p>
     * See {@code android.speech.tts.TextToSpeechService#MAX_SPEECH_ITEM_CHAR_LENGTH}.
     */
    private static final int MAX_UTTERANCE_LENGTH = 3999;

    /** Constant to flush speech globally. This is a private API. */
    private static final int SPEECH_FLUSH_ALL = 2;

    /**
     * {@link BroadcastReceiver} for determining changes in the media state used
     * for switching the TTS engine.
     */
    private final MediaMountStateMonitor mMediaStateMonitor = new MediaMountStateMonitor();

    /** A list of installed TTS engines. */
    private final LinkedList<String> mInstalledTtsEngines = new LinkedList<String>();

    private final Context mContext;
    private final ContentResolver mResolver;

    /** The TTS engine. */
    private TextToSpeech mTts;

    /** The engine loaded into the current TTS. */
    private String mTtsEngine;

    /** The number of time the current TTS has failed consecutively. */
    private int mTtsFailures;

    /** The package name of the preferred TTS engine. */
    private String mDefaultTtsEngine;

    /** The package name of the system TTS engine. */
    private String mSystemTtsEngine;

    /** A temporary TTS used for switching engines. */
    private TextToSpeech mTempTts;

    /** The engine loading into the temporary TTS. */
    private String mTempTtsEngine;

    /** The rate adjustment specified in {@link android.provider.Settings}. */
    private float mDefaultRate;

    /** The pitch adjustment specified in {@link android.provider.Settings}. */
    private float mDefaultPitch;

    /** The most recent rate sent to {@link TextToSpeech#setSpeechRate}. */
    private float mCurrentRate = 1.0f;

    /** The most recent pitch sent to {@link TextToSpeech#setSpeechRate}. */
    private float mCurrentPitch = 1.0f;

    private FailoverTtsListener mListener;

    public FailoverTextToSpeech(Context context) {
        mContext = context;
        mContext.registerReceiver(mMediaStateMonitor, mMediaStateMonitor.getFilter());

        final Uri defaultSynth = Secure.getUriFor(Secure.TTS_DEFAULT_SYNTH);
        final Uri defaultPitch = Secure.getUriFor(Secure.TTS_DEFAULT_PITCH);
        final Uri defaultRate = Secure.getUriFor(Secure.TTS_DEFAULT_RATE);

        mResolver = context.getContentResolver();
        mResolver.registerContentObserver(defaultSynth, false, mSynthObserver);
        mResolver.registerContentObserver(defaultPitch, false, mPitchObserver);
        mResolver.registerContentObserver(defaultRate, false, mRateObserver);

        if (USE_GOOGLE_TTS_WORKAROUNDS) {
            registerGoogleTtsFixCallbacks();
        }

        updateDefaultPitch();
        updateDefaultRate();

        // Updating the default engine reloads the list of installed engines and
        // the system engine. This also loads the default engine.
        updateDefaultEngine();
    }

    /**
     * Sets the listener for changes in speaking state.
     *
     * @param listener The listener to set.
     */
    public void setListener(FailoverTtsListener listener) {
        mListener = listener;
    }

    /**
     * Whether the text-to-speech engine is ready to speak.
     *
     * @return {@code true} if calling {@link #speak} is expected to succeed.
     */
    public boolean isReady() {
        return (mTts != null);
    }

    /**
     * Returns the label for the current text-to-speech engine.
     *
     * @return The localized name of the current engine.
     */
    public CharSequence getEngineLabel() {
        return TextToSpeechUtils.getLabelForEngine(mContext, mTtsEngine);
    }

    /**
     * Speak the specified text.
     *
     * @param text The text to speak.
     * @param pitch The pitch adjustment, in the range [0 ... 1].
     * @param rate The rate adjustment, in the range [0 ... 1].
     * @param params The parameters to pass to the text-to-speech engine.
     */
    public void speak(String text, float pitch, float rate, HashMap<String, String> params) {
        // Handle empty text immediately.
        if (TextUtils.isEmpty(text)) {
            mHandler.onUtteranceCompleted(params.get(Engine.KEY_PARAM_UTTERANCE_ID));
            return;
        }

        int result;

        Exception failureException = null;
        try {
            result = trySpeak(text, pitch, rate, params);
        } catch (Exception e) {
            failureException = e;
            result = TextToSpeech.ERROR;
        }

        if (result == TextToSpeech.ERROR) {
            attemptTtsFailover(mTtsEngine);
        }

        if ((result != TextToSpeech.SUCCESS)
                && params.containsKey(Engine.KEY_PARAM_UTTERANCE_ID)) {
            if (failureException != null) {
                LogUtils.log(this, Log.WARN, "Failed to speak \"%s\" due to an exception", text);
                failureException.printStackTrace();
            } else {
                LogUtils.log(this, Log.WARN, "Failed to speak \"%s\"", text);
            }

            mHandler.onUtteranceCompleted(params.get(Engine.KEY_PARAM_UTTERANCE_ID));
        }
    }

    /**
     * Stops speech from all applications. No utterance callbacks will be sent.
     */
    public void stopAll() {
        try {
            mTts.speak("", SPEECH_FLUSH_ALL, null);
        } catch (Exception e) {
            // Don't care, we're not speaking.
        }
    }

    /**
     * Unregisters receivers, observers, and shuts down the text-to-speech
     * engine. No calls should be made to this object after calling this method.
     */
    public void shutdown() {
        mContext.unregisterReceiver(mMediaStateMonitor);

        if (USE_GOOGLE_TTS_WORKAROUNDS) {
            unregisterGoogleTtsFixCallbacks();
        }

        mResolver.unregisterContentObserver(mSynthObserver);
        mResolver.unregisterContentObserver(mPitchObserver);
        mResolver.unregisterContentObserver(mRateObserver);

        TextToSpeechUtils.attemptTtsShutdown(mTts);
        mTts = null;

        TextToSpeechUtils.attemptTtsShutdown(mTempTts);
        mTempTts = null;
    }

    /**
     * Attempts to speak the specified text.
     *
     * @param text
     * @param pitch
     * @param rate
     * @param params
     * @return The result of speaking the specified text.
     */
    @SuppressWarnings("unused")
    private int trySpeak(String text, float pitch, float rate, HashMap<String, String> params) {
        if (mTts == null) {
            return TextToSpeech.ERROR;
        }

        final float effectivePitch = (pitch * mDefaultPitch);
        final float effectiveRate = (rate * mDefaultRate);

        // Set the pitch and rate only if necessary, since that is slow.
        if ((mCurrentPitch != effectivePitch) || (mCurrentRate != effectiveRate)) {
            mTts.stop();
            mTts.setPitch(effectivePitch);
            mTts.setSpeechRate(effectiveRate);

            mCurrentPitch = effectivePitch;
            mCurrentRate = effectiveRate;
        }

        // Split long utterances to avoid killing TTS. TTS will die if
        // the incoming string is greater than 3999 characters.
        final LinkedList<String> speakableUtterances = new LinkedList<String>();
        if (text.length() > MAX_UTTERANCE_LENGTH) {
            splitUtteranceIntoSpeakableStrings(text, speakableUtterances);
            text = speakableUtterances.removeFirst();
        }

        final String utteranceId = params.get(Engine.KEY_PARAM_UTTERANCE_ID);
        final int result = mTts.speak(text, SPEECH_FLUSH_ALL, params);
        LogUtils.log(this, Log.DEBUG, "Speak call for \"%s\" returned %d", utteranceId, result);

        if (result == TextToSpeech.SUCCESS) {
            // If we were able to speak, queue any remaining fragments.
            for (String speakableUtterance : speakableUtterances) {
                LogUtils.log(this, Log.DEBUG, "Queue overflow speech: \"%s\"", speakableUtterance);
                mTts.speak(speakableUtterance, TextToSpeech.QUEUE_ADD, params);
            }
        } else if (USE_GOOGLE_TTS_WORKAROUNDS) {
            // Otherwise, maybe there's something wrong with the locale.
            ensureSupportedLocale();
        }

        return result;
    }

    /**
     * Try to switch the TTS engine.
     *
     * @param engine The package name of the desired TTS engine
     */
    private void setTtsEngine(String engine, boolean resetFailures) {
        if (resetFailures) {
            mTtsFailures = 0;
        }

        // Always try to stop the current engine before switching.
        TextToSpeechUtils.attemptTtsShutdown(mTts);

        if (mTempTts != null) {
            LogUtils.log(SpeechController.class, Log.ERROR,
                    "Can't start TTS engine %s while still loading previous engine", engine);
            return;
        }

        LogUtils.log(SpeechController.class, Log.INFO, "Switching to TTS engine: %s", engine);

        // If this is an old version of Android, we need to use a deprecated
        // method to switch the engine.
        if ((mTts != null) && (Build.VERSION.SDK_INT < 14) && (Build.VERSION.SDK_INT > 8)) {
            setEngineByPackageName_GB_HC(engine);
            return;
        }

        mTempTtsEngine = engine;
        mTempTts = TextToSpeechCompatUtils.newTextToSpeech(mContext, mTtsChangeListener, engine);
    }

    /**
     * Attempts to set the engine by package name. Supported in GB and HC.
     *
     * @param engine The name of the engine to use.
     */
    private void setEngineByPackageName_GB_HC(String engine) {
        mTempTtsEngine = engine;
        mTempTts = mTts;

        final int status = TextToSpeechCompatUtils.setEngineByPackageName(mTts, mTempTtsEngine);
        mTtsChangeListener.onInit(status);
    }

    /**
     * Assumes the current engine has failed and attempts to start the next
     * available engine.
     *
     * @param failedEngine The package name of the engine to switch from.
     */
    private void attemptTtsFailover(String failedEngine) {
        LogUtils.log(
                SpeechController.class, Log.ERROR, "Attempting TTS failover from %s", failedEngine);

        mTtsFailures++;

        // If there is only one installed engine, or if the current engine
        // hasn't failed enough times, just restart the current engine.
        if ((mInstalledTtsEngines.size() <= 1) || (mTtsFailures < MAX_TTS_FAILURES)) {
            setTtsEngine(failedEngine, false);
            return;
        }

        // Move the engine to the back of the list.
        if (failedEngine != null) {
            mInstalledTtsEngines.remove(failedEngine);
            mInstalledTtsEngines.addLast(failedEngine);
        }

        // Try to use the first available TTS engine.
        final String nextEngine = mInstalledTtsEngines.getFirst();

        setTtsEngine(nextEngine, true);
    }

    /**
     * Handles TTS engine initialization.
     *
     * @param status The status returned by the TTS engine.
     */
    @SuppressWarnings("deprecation")
    private void handleTtsInitialized(int status) {
        if (mTempTts == null) {
            LogUtils.log(this, Log.ERROR, "Attempted to initialize TTS more than once!");
            return;
        }

        final TextToSpeech tempTts = mTempTts;
        final String tempTtsEngine = mTempTtsEngine;

        mTempTts = null;
        mTempTtsEngine = null;

        if (status != TextToSpeech.SUCCESS) {
            attemptTtsFailover(tempTtsEngine);
            return;
        }

        final boolean isSwitchingEngines = (mTts != null);

        if (isSwitchingEngines) {
            TextToSpeechUtils.attemptTtsShutdown(mTts);
        }

        mTts = tempTts;
        mTts.setOnUtteranceCompletedListener(mTtsListener);

        if (tempTtsEngine == null) {
            mTtsEngine = TextToSpeechCompatUtils.getCurrentEngine(mTts);
        } else {
            mTtsEngine = tempTtsEngine;
        }

        if (USE_GOOGLE_TTS_WORKAROUNDS) {
            updateDefaultLocale();
        }

        LogUtils.log(SpeechController.class, Log.INFO, "Switched to TTS engine: %s", tempTtsEngine);

        if (mListener != null) {
            mListener.onTtsInitialized(isSwitchingEngines);
        }
    }

    /**
     * Method that's called by TTS whenever an utterance is completed. Do common
     * tasks and execute any UtteranceCompleteActions associate with this
     * utterance index (or an earlier index, in case one was accidentally
     * dropped).
     *
     * @param utteranceId The utteranceId from the onUtteranceCompleted callback
     *            - we expect this to consist of UTTERANCE_ID_PREFIX followed by
     *            the utterance index.
     * @param success {@code true} if the utterance was spoken successfully.
     */
    private void handleUtteranceCompleted(String utteranceId, boolean success) {
        if (success) {
            mTtsFailures = 0;
        }

        if (mListener != null) {
            mListener.onUtteranceCompleted(utteranceId, success);
        }
    }

    /**
     * Handles media state changes.
     *
     * @param action The current media state.
     */
    private void handleMediaStateChanged(String action) {
        if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
            if (!TextUtils.equals(mSystemTtsEngine, mTtsEngine)) {
                // Temporarily switch to the system TTS engine.
                LogUtils.log(this, Log.VERBOSE, "Saw media unmount");
                setTtsEngine(mSystemTtsEngine, true);
            }
        }

        if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            if (!TextUtils.equals(mDefaultTtsEngine, mTtsEngine)) {
                // Try to switch back to the default engine.
                LogUtils.log(this, Log.VERBOSE, "Saw media mount");
                setTtsEngine(mDefaultTtsEngine, true);
            }
        }
    }

    private void updateDefaultEngine() {
        final ContentResolver resolver = mContext.getContentResolver();

        // Always refresh the list of available engines, since the user may have
        // installed a new TTS and then switched to it.
        mInstalledTtsEngines.clear();
        mSystemTtsEngine = TextToSpeechUtils.reloadInstalledTtsEngines(
                mContext, mInstalledTtsEngines);

        // This may be null if the user hasn't specified an engine.
        mDefaultTtsEngine = Secure.getString(resolver, Secure.TTS_DEFAULT_SYNTH);

        // Always switch engines when the system default changes.
        setTtsEngine(mDefaultTtsEngine, true);
    }

    /**
     * Loads the default pitch adjustment from {@link Secure#TTS_DEFAULT_PITCH}.
     * This will take effect during the next call to {@link #trySpeak}.
     */
    private void updateDefaultPitch() {
        mDefaultPitch = (Secure.getInt(mResolver, Secure.TTS_DEFAULT_PITCH, 100) / 100.0f);
    }

    /**
     * Loads the default rate adjustment from {@link Secure#TTS_DEFAULT_RATE}.
     * This will take effect during the next call to {@link #trySpeak}.
     */
    private void updateDefaultRate() {
        mDefaultRate = (Secure.getInt(mResolver, Secure.TTS_DEFAULT_RATE, 100) / 100.0f);
    }

    /** Whether we may need to work around Google TTS language issues. */
    private static final boolean USE_GOOGLE_TTS_WORKAROUNDS =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1);

    /**
     * Preferred locale for fallback language.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private static final Locale PREFERRED_FALLBACK_LOCALE = Locale.US;

    /**
     * The system's default locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private Locale mSystemLocale = Locale.getDefault();

    /**
     * The current engine's default locale. This will be {@code null} if the
     * user never specified a preference.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private Locale mDefaultLocale = null;

    /**
     * Whether we're using a fallback locale because the TTS attempted to use an
     * unsupported locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private boolean mUsingFallbackLocale;

    /**
     * Whether we've ever explicitly set the locale using
     * {@link TextToSpeech#setLanguage}. If so, we'll need to work around a TTS
     * bug and manually update the TTS locale every time the user changes
     * locale-related settings.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private boolean mHasSetLocale;

    /**
     * Helper method that ensures the text-to-speech engine works even when the
     * user is using the Google TTS and has the system set to a non-embedded
     * language.
     * <p>
     * This method should be called on API >= 15 whenever the TTS engine is
     * loaded, the system locale changes, or the default TTS locale changes.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void ensureSupportedLocale() {
        if (needsFallbackLocale()) {
            attemptSetFallbackLanguage();
        } else if (mUsingFallbackLocale || mHasSetLocale) {
            // We might need to restore the system locale. Or, if we've ever
            // explicitly set the locale, we'll need to work around a bug where
            // there's no way to tell the TTS engine to use whatever it thinks
            // the default language should be.
            attemptRestorePreferredLocale();
        }
    }

    /**
     * Returns whether we need to attempt to use a fallback language.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private boolean needsFallbackLocale() {
        // If the user isn't using Google TTS, or if they set a preferred
        // locale, we do not need to check locale support.
        if (!PACKAGE_GOOGLE_TTS.equals(mTtsEngine) || (mDefaultLocale != null)) {
            return false;
        }

        // Otherwise, the TTS engine will attempt to use the system locale which
        // may not be supported. If the locale is embedded or advertised as
        // available, we're fine.
        final Set<String> features = mTts.getFeatures(mSystemLocale);
        if (features.contains(Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS)
                || isAvailableStatus(mTts.isLanguageAvailable(mSystemLocale))) {
            return false;
        }

        return true;
    }

    /**
     * Attempts to obtain and set a fallback TTS locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void attemptSetFallbackLanguage() {
        final Locale fallbackLocale = getBestAvailableLocale();
        if (fallbackLocale == null) {
            LogUtils.log(this, Log.ERROR, "Failed to find fallback locale");
            return;
        }

        final int status = mTts.setLanguage(fallbackLocale);
        if (!isAvailableStatus(status)) {
            LogUtils.log(this, Log.ERROR, "Failed to set fallback locale to %s", fallbackLocale);
            return;
        }

        LogUtils.log(this, Log.VERBOSE, "Set fallback locale to %s", fallbackLocale);

        mUsingFallbackLocale = true;
        mHasSetLocale = true;
    }

    /**
     * Attempts to obtain a supported TTS locale with preference given to
     * {@link #PREFERRED_FALLBACK_LOCALE}. The resulting locale may not be
     * optimal for the user, but it will likely be enough to understand what's
     * on the screen.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private Locale getBestAvailableLocale() {
        // Always attempt to use the preferred locale first.
        if (mTts.isLanguageAvailable(PREFERRED_FALLBACK_LOCALE) >= 0) {
            return PREFERRED_FALLBACK_LOCALE;
        }

        // Since there's no way to query available languages from an engine,
        // we'll need to check every locale supported by the device.
        Locale bestLocale = null;
        int bestScore = -1;

        final Locale[] locales = Locale.getAvailableLocales();
        for (Locale locale : locales) {
            final int status = mTts.isLanguageAvailable(locale);
            if (!isAvailableStatus(status)) {
                continue;
            }

            final int score = compareLocales(mSystemLocale, locale);
            if (score > bestScore) {
                bestLocale = locale;
                bestScore = score;
            }
        }

        return bestLocale;
    }

    /**
     * Attempts to restore the user's preferred TTS locale, if set. Otherwise
     * attempts to restore the system locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void attemptRestorePreferredLocale() {
        final Locale preferredLocale = (mDefaultLocale != null ? mDefaultLocale : mSystemLocale);
        final int status = mTts.setLanguage(preferredLocale);
        if (!isAvailableStatus(status)) {
            LogUtils.log(this, Log.ERROR, "Failed to restore TTS locale to %s", preferredLocale);
            return;
        }

        LogUtils.log(this, Log.INFO, "Restored TTS locale to %s", preferredLocale);

        mUsingFallbackLocale = false;
        mHasSetLocale = true;
    }

    /**
     * Handles updating the default locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void updateDefaultLocale() {
        final String defaultLocale = TextToSpeechUtils.getDefaultLocaleForEngine(
                mResolver, mTtsEngine);
        mDefaultLocale = ((defaultLocale != null) ? new Locale(defaultLocale) : null);

        // The default locale changed, which may mean we can restore the user's
        // preferred locale.
        ensureSupportedLocale();
    }

    /**
     * Handles updating the system locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void onConfigurationChanged(Configuration newConfig) {
        final Locale newLocale = newConfig.locale;
        if (newLocale.equals(mSystemLocale)) {
            return;
        }

        mSystemLocale = newLocale;

        // The system locale changed, which may mean we need to override the
        // current TTS locale.
        ensureSupportedLocale();
    }

    /**
     * Registers the configuration change callback.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void registerGoogleTtsFixCallbacks() {
        final Uri defaultLocaleUri = Secure.getUriFor(SecureCompatUtils.TTS_DEFAULT_LOCALE);
        mResolver.registerContentObserver(defaultLocaleUri, false, mLocaleObserver);
        mContext.registerComponentCallbacks(mComponentCallbacks);
    }

    /**
     * Unregisters the configuration change callback.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void unregisterGoogleTtsFixCallbacks() {
        mResolver.unregisterContentObserver(mLocaleObserver);
        mContext.unregisterComponentCallbacks(mComponentCallbacks);
    }

    /**
     * Compares a locale against a primary locale. Returns higher values for
     * closer matches. A return value of 3 indicates that the locale is an exact
     * match for the primary locale's language, country, and variant.
     *
     * @param primary The primary locale for comparison.
     * @param other The other locale to compare against the primary locale.
     * @return A value indicating how well the other locale matches the primary
     *         locale. Higher is better.
     */
    private static int compareLocales(Locale primary, Locale other) {
        final String lang = primary.getLanguage();
        if ((lang == null) || !lang.equals(other.getLanguage())) {
            return 0;
        }

        final String country = primary.getCountry();
        if ((country == null) || !country.equals(other.getCountry())) {
            return 1;
        }

        final String variant = primary.getVariant();
        if ((variant == null) || !variant.equals(other.getVariant())) {
            return 2;
        }

        return 3;
    }

    /**
     * Returns {@code true} if the specified status indicates that the language
     * is available.
     *
     * @param status A language availability code, as returned from
     *            {@link TextToSpeech#isLanguageAvailable}.
     * @return {@code true} if the status indicates that the language is
     *         available.
     */
    private static boolean isAvailableStatus(int status) {
        return (status == TextToSpeech.LANG_AVAILABLE)
                || (status == TextToSpeech.LANG_COUNTRY_AVAILABLE)
                || (status == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE);
    }

    /**
     * Splits long utterances up into shorter utterances. This is needed because
     * the Android TTS does not support Strings that are longer than 3999
     * characters.
     *
     * @param utterance The original utterance.
     * @param results A {@link LinkedList} to populate with speakable utterances.
     */
    private static void splitUtteranceIntoSpeakableStrings(
            CharSequence utterance, LinkedList<String> results) {
        final int end = utterance.length();
        int start = 0;

        while (start < end) {
            final int fragmentEnd = start + MAX_UTTERANCE_LENGTH;
            int splitLocation = TextUtils.lastIndexOf(utterance, ' ', start, fragmentEnd);
            if (splitLocation < 0) {
                splitLocation = Math.min(fragmentEnd, end);
            }
            results.add(TextUtils.substring(utterance, start, splitLocation));
            start = (splitLocation + 1);
        }
    }

    private final FailoverTextToSpeech.SpeechHandler mHandler = new SpeechHandler(this);

    /**
     * Handles changes to the default TTS engine.
     */
    private final ContentObserver mSynthObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateDefaultEngine();
        }
    };

    private final ContentObserver mPitchObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateDefaultPitch();
        }
    };

    private final ContentObserver mRateObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateDefaultRate();
        }
    };

    /**
     * Callbacks used to observe changes to the TTS locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private final ContentObserver mLocaleObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateDefaultLocale();
        }
    };

    /** Hands utterance completed processing to the main thread. */
    private final OnUtteranceCompletedListener mTtsListener = new OnUtteranceCompletedListener() {
        @Override
        public void onUtteranceCompleted(String utteranceId) {
            LogUtils.log(this, Log.DEBUG, "Received completion for \"%s\"", utteranceId);

            mHandler.onUtteranceCompleted(utteranceId);
        }
    };

    /**
     * When changing TTS engines, switches the active TTS engine when the new
     * engine is initialized.
     */
    private final OnInitListener mTtsChangeListener = new OnInitListener() {
        @Override
        public void onInit(int status) {
            mHandler.onTtsInitialized(status);
        }
    };

    /**
     * Callbacks used to observe configuration changes.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private final ComponentCallbacks mComponentCallbacks = new ComponentCallbacks() {
        @Override
        public void onLowMemory() {
            // Do nothing.
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            FailoverTextToSpeech.this.onConfigurationChanged(newConfig);
        }
    };

    /**
     * {@link BroadcastReceiver} for detecting media mount and unmount.
     */
    private class MediaMountStateMonitor extends BroadcastReceiver {
        private final IntentFilter mMediaIntentFilter;

        public MediaMountStateMonitor() {
            mMediaIntentFilter = new IntentFilter();
            mMediaIntentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            mMediaIntentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            mMediaIntentFilter.addDataScheme("file");
        }

        public IntentFilter getFilter() {
            return mMediaIntentFilter;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            mHandler.onMediaStateChanged(action);
        }
    }

    /** Handler used to return to the main thread from the TTS thread. */
    private static class SpeechHandler extends WeakReferenceHandler<FailoverTextToSpeech> {
        /** Hand-off engine initialized. */
        private static final int MSG_INITIALIZED = 1;

        /** Hand-off utterance completed. */
        private static final int MSG_UTTERANCE_COMPLETED = 2;

        /** Hand-off media state changes. */
        private static final int MSG_MEDIA_STATE_CHANGED = 3;

        public SpeechHandler(FailoverTextToSpeech parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, FailoverTextToSpeech parent) {
            switch (msg.what) {
                case MSG_INITIALIZED:
                    parent.handleTtsInitialized(msg.arg1);
                    break;
                case MSG_UTTERANCE_COMPLETED:
                    parent.handleUtteranceCompleted((String) msg.obj, true);
                    break;
                case MSG_MEDIA_STATE_CHANGED:
                    parent.handleMediaStateChanged((String) msg.obj);
            }
        }

        public void onTtsInitialized(int status) {
            obtainMessage(MSG_INITIALIZED, status, 0).sendToTarget();
        }

        public void onUtteranceCompleted(String utteranceId) {
            obtainMessage(MSG_UTTERANCE_COMPLETED, utteranceId).sendToTarget();
        }

        public void onMediaStateChanged(String action) {
            obtainMessage(MSG_MEDIA_STATE_CHANGED, action).sendToTarget();
        }
    }

    public interface FailoverTtsListener {
        public void onTtsInitialized(boolean wasSwitchingEngines);
        public void onUtteranceCompleted(String utteranceId, boolean success);
    }
}