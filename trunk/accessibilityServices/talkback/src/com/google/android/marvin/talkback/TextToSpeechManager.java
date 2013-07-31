
package com.google.android.marvin.talkback;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.speech.tts.TextToSpeech.Engine;
import android.support.v4.content.LocalBroadcastManager;

import com.googlecode.eyesfree.utils.TtsEngineUtils;
import com.googlecode.eyesfree.utils.TtsEngineUtils.TtsEngineInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Manages the list of available TTS engines and their supported languages.
 * <p>
 * Only supported on Ice Cream Sandwich MR1 and above (API 15+).
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public class TextToSpeechManager {
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;

    /** The list of available engines and their supported languages. */
    private final Map<TtsEngineInfo, List<Locale>>
            mAvailableEngines = new HashMap<TtsEngineInfo, List<Locale>>();

    /** The list of callbacks for discovery events. */
    private final List<TtsDiscoveryListener> mListeners = new LinkedList<TtsDiscoveryListener>();

    /** The parent context. */
    private final Context mContext;

    /** The local broadcast manager, used to discover TTS engines. */
    private final LocalBroadcastManager mBroadcastManager;

    /**
     * Constructs a new text-to-speech engine discovery manager.
     *
     * @param context The parent context.
     */
    public TextToSpeechManager(Context context) {
        mContext = context;
        mBroadcastManager = LocalBroadcastManager.getInstance(context);

        final IntentFilter discoveryIntentFilter = new IntentFilter();
        discoveryIntentFilter.addAction(TtsDiscoveryProxyActivity.BROADCAST_TTS_DISCOVERY_STARTED);
        discoveryIntentFilter.addAction(TtsDiscoveryProxyActivity.BROADCAST_TTS_DISCOVERY_FINISHED);
        discoveryIntentFilter.addAction(TtsDiscoveryProxyActivity.BROADCAST_TTS_DISCOVERED);
        mBroadcastManager.registerReceiver(mReceiver, discoveryIntentFilter);

        final IntentFilter ttsIntentFilter = new IntentFilter();
        ttsIntentFilter.addAction(Engine.ACTION_TTS_DATA_INSTALLED);
        mContext.registerReceiver(mReceiver, ttsIntentFilter);

        final IntentFilter packageIntentFilter = new IntentFilter();
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageIntentFilter.addDataScheme("package_name");
        mContext.registerReceiver(mReceiver, packageIntentFilter);
    }

    /**
     * Adds a discovery event callback. The listener will be called when engines
     * are discovered or updated.
     *
     * @param listener The callback listener to add.
     */
    public void addListener(TtsDiscoveryListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    /**
     * Removes a discovery event callback. This is a no-op if the callback does
     * not exist in the list of callbacks.
     *
     * @param listener The callback listener to remove.
     */
    public void removeListener(TtsDiscoveryListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    /**
     * Shuts down the manager and releases associated resources.
     */
    public void shutdown() {
        mBroadcastManager.unregisterReceiver(mReceiver);
        mContext.unregisterReceiver(mReceiver);

        synchronized (mListeners) {
            mListeners.clear();
        }
    }

    /**
     * Starts asynchronous discovery of available engines and supported
     * languages.
     */
    public void startDiscovery() {
        final Intent intent = new Intent(mContext, TtsDiscoveryProxyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // Start the discovery proxy as an activity in a new task.
        mContext.startActivity(intent);
    }

    /**
     * @return An unmodifiable set of available engines.
     */
    public Set<TtsEngineInfo> getEngines() {
        return Collections.unmodifiableSet(mAvailableEngines.keySet());
    }

    /**
     * Returns an unmodifiable list of languages supported by the specified
     * engine.
     *
     * @param engine The engine from which to obtain a list of supported
     *            languages.
     * @return An unmodifiable list of languages supported by the specified
     *         engine.
     */
    public List<Locale> getLanguagesForEngine(TtsEngineInfo engine) {
        return mAvailableEngines.get(engine);
    }

    /**
     * If the specified package contains a TTS engine, starts asynchronous
     * discovery of supported languages for that package.
     *
     * @param appPackage The application package to check.
     */
    private void startDiscoveryForPackage(String appPackage) {
        final TtsEngineInfo engine = TtsEngineUtils.getEngineInfo(mContext, appPackage);
        if (engine == null) {
            return;
        }

        final Intent intent = new Intent(mContext, TtsDiscoveryProxyActivity.class);
        intent.putExtra(TtsDiscoveryProxyActivity.EXTRA_ENGINE_NAME, appPackage);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Start the discovery proxy as an activity in a new task.
        mContext.startActivity(intent);
    }

    /**
     * If the specified package contains a TTS engine, removes it from the list
     * of available engines. If applicable, notifies listeners using
     * {@link TtsDiscoveryListener#onTtsRemoval}.
     *
     * @param appPackage The application package to check.
     */
    private void removeEngineForPackage(String appPackage) {
        final TtsEngineInfo engineInfo = TtsEngineUtils.getEngineInfo(mContext, appPackage);
        if (engineInfo == null) {
            return;
        }

        mAvailableEngines.remove(engineInfo);

        synchronized (mListeners) {
            for (TtsDiscoveryListener listener : mListeners) {
                listener.onTtsRemoval(engineInfo);
            }
        }
    }

    /**
     * Handles discovery of a new engine or updates to an existing engine. If
     * applicable, notifies listeners using
     * {@link TtsDiscoveryListener#onTtsDiscovery}.
     *
     * @param engine The engine name.
     * @param availableLanguages The list of available languages.
     */
    private void handleDiscoveryResult(String engine, List<String> availableLanguages) {
        if (engine == null) {
            return;
        }

        final TtsEngineInfo engineInfo = TtsEngineUtils.getEngineInfo(mContext, engine);
        if (engineInfo == null) {
            return;
        }

        final List<Locale> availableLocales =
                TtsEngineUtils.parseAvailableLanguages(availableLanguages);
        mAvailableEngines.put(engineInfo, availableLocales);

        synchronized (mListeners) {
            for (TtsDiscoveryListener listener : mListeners) {
                listener.onTtsDiscovery(engineInfo, availableLanguages);
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (TtsDiscoveryProxyActivity.BROADCAST_TTS_DISCOVERED.equals(action)) {
                final String engine = intent.getStringExtra(
                        TtsDiscoveryProxyActivity.EXTRA_ENGINE_NAME);
                final ArrayList<String> availableLanguages = intent.getStringArrayListExtra(
                        TtsDiscoveryProxyActivity.EXTRA_AVAILABLE_LANGUAGES);
                handleDiscoveryResult(engine, availableLanguages);
            } else if (Engine.ACTION_TTS_DATA_INSTALLED.equals(action)) {
                startDiscovery();
            } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)
                    || Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                final Uri uri = intent.getData();
                final String appPackage = uri.getEncodedSchemeSpecificPart();
                startDiscoveryForPackage(appPackage);
            } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                final Uri uri = intent.getData();
                final String appPackage = uri.getEncodedSchemeSpecificPart();
                removeEngineForPackage(appPackage);
            }
        }
    };

    /**
     * Listener used to handle TTS discovery events.
     */
    public interface TtsDiscoveryListener {
        /**
         * Called when a TTS engine is discovered or updated.
         *
         * @param engine The engine information.
         * @param availableLanguages The list of available languages.
         */
        public void onTtsDiscovery(TtsEngineInfo engine, List<String> availableLanguages);

        /**
         * Called when a TTS engine is removed.
         *
         * @param engine The engine information.
         */
        public void onTtsRemoval(TtsEngineInfo engine);
    }
}
