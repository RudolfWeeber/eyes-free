package com.google.android.marvin.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;

import com.googlecode.eyesfree.compat.provider.SettingsCompatUtils.SecureCompatUtils;
import com.googlecode.eyesfree.compat.speech.tts.TextToSpeechCompatUtils.EngineCompatUtils;

import java.util.List;

class TextToSpeechUtils {
    private static final String LOCALE_DELIMITER = "-";

    /**
     * Reloads the list of installed TTS engines.
     *
     * @param context The parent context.
     * @param results The list to populate with installed TTS engines.
     * @return The package for the system default TTS.
     */
    public static String reloadInstalledTtsEngines(Context context, List<String> results) {
        final PackageManager pm = context.getPackageManager();

        // ICS and pre-ICS have different ways of enumerating installed TTS
        // services.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return reloadInstalledTtsEngines_ICS(pm, results);
        } else {
            return reloadInstalledTtsEngines_HC(pm, results);
        }
    }

    /**
     * Reloads the list of installed engines for devices running ICS or
     * above.
     *
     * @param pm The package manager.
     * @param results The list to populate with installed TTS engines.
     * @return The package for the system default TTS.
     */
    private static String reloadInstalledTtsEngines_ICS(
            PackageManager pm, List<String> results) {
        final Intent intent = new Intent(EngineCompatUtils.INTENT_ACTION_TTS_SERVICE);
        final List<ResolveInfo> resolveInfos = pm.queryIntentServices(
                intent, PackageManager.GET_SERVICES);

        String systemTtsEngine = null;

        for (ResolveInfo resolveInfo : resolveInfos) {
            final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            final ApplicationInfo appInfo = serviceInfo.applicationInfo;
            final String packageName = serviceInfo.packageName;
            final boolean isSystemApp = ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);

            results.add(serviceInfo.packageName);

            if (isSystemApp) {
                systemTtsEngine = packageName;
            }
        }

        return systemTtsEngine;
    }

    /**
     * Reloads the list of installed engines for devices running API 13 or
     * below.
     *
     * @param pm The package manager.
     * @param results The list to populate with installed TTS engines.
     * @return The package for the system default TTS.
     */
    private static String reloadInstalledTtsEngines_HC(
            PackageManager pm, List<String> results) {
        final Intent intent = new Intent("android.intent.action.START_TTS_ENGINE");
        final List<ResolveInfo> resolveInfos = pm.queryIntentActivities(
                intent, PackageManager.GET_ACTIVITIES);

        String systemTtsEngine = null;

        for (ResolveInfo resolveInfo : resolveInfos) {
            final ActivityInfo activityInfo = resolveInfo.activityInfo;
            final ApplicationInfo appInfo = activityInfo.applicationInfo;
            final String packageName = activityInfo.packageName;
            final boolean isSystemApp = ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);

            results.add(packageName);

            if (isSystemApp) {
                systemTtsEngine = packageName;
            }
        }

        return systemTtsEngine;
    }

    /**
     * Attempts to shutdown the specified TTS engine, ignoring any errors.
     *
     * @param tts The TTS engine to shutdown.
     */
    static void attemptTtsShutdown(TextToSpeech tts) {
        try {
            tts.shutdown();
        } catch (Exception e) {
            // Don't care, we're shutting down.
        }
    }

    /**
     * Returns the localized name of the TTS engine with the specified package
     * name.
     *
     * @param context The parent context.
     * @param enginePackage The package name of the TTS engine.
     * @return The localized name of the TTS engine.
     */
    static CharSequence getLabelForEngine(Context context, String enginePackage) {
        if (enginePackage == null) {
            return null;
        }

        final PackageManager pm = context.getPackageManager();
        final Intent intent = new Intent(EngineCompatUtils.INTENT_ACTION_TTS_SERVICE);
        intent.setPackage(enginePackage);

        final List<ResolveInfo> resolveInfos =
                pm.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY);

        if ((resolveInfos == null) || resolveInfos.isEmpty()) {
            return null;
        }

        final ResolveInfo resolveInfo = resolveInfos.get(0);
        final ServiceInfo serviceInfo = resolveInfo.serviceInfo;

        if (serviceInfo == null) {
            return null;
        }

        return serviceInfo.loadLabel(pm);
    }

    static String getDefaultLocaleForEngine(ContentResolver cr, String engineName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return getDefaultLocale_HC(cr);
        } else {
            return getDefaultLocale_ICS(cr, engineName);
        }
    }

    private static String getDefaultLocale_ICS(ContentResolver cr, String engineName) {
        final String defaultLocales = Secure.getString(cr, SecureCompatUtils.TTS_DEFAULT_LOCALE);
        return parseEnginePrefFromList(defaultLocales, engineName);
    }

    /**
     * Parses a comma separated list of engine locale preferences. The list is of the
     * form {@code "engine_name_1:locale_1,engine_name_2:locale2"} and so on and
     * so forth. Returns null if the list is empty, malformed or if there is no engine
     * specific preference in the list.
     */
    private static String parseEnginePrefFromList(String prefValue, String engineName) {
        if (TextUtils.isEmpty(prefValue)) {
            return null;
        }

        String[] prefValues = prefValue.split(",");

        for (String value : prefValues) {
            final int delimiter = value.indexOf(':');
            if (delimiter > 0) {
                if (engineName.equals(value.substring(0, delimiter))) {
                    return value.substring(delimiter + 1);
                }
            }
        }

        return null;
    }

    /**
     * @return the old style locale string constructed from
     *         {@link Settings.Secure#TTS_DEFAULT_LANG},
     *         {@link Settings.Secure#TTS_DEFAULT_COUNTRY} and
     *         {@link Settings.Secure#TTS_DEFAULT_VARIANT}. If no such locale is
     *         set, then return {@code null}.
     */
    @SuppressWarnings({ "javadoc", "deprecation" })
    private static String getDefaultLocale_HC(ContentResolver cr) {
        final String lang = Settings.Secure.getString(cr, Settings.Secure.TTS_DEFAULT_LANG);
        if (TextUtils.isEmpty(lang)) {
            return null;
        }

        final StringBuilder locale = new StringBuilder(lang);
        final String country = Settings.Secure.getString(cr, Settings.Secure.TTS_DEFAULT_COUNTRY);
        if (TextUtils.isEmpty(country)) {
            return locale.toString();
        }

        locale.append(LOCALE_DELIMITER);
        locale.append(country);

        final String variant = Settings.Secure.getString(cr, Settings.Secure.TTS_DEFAULT_VARIANT);
        if (TextUtils.isEmpty(variant)) {
            return locale.toString();
        }

        locale.append(LOCALE_DELIMITER);
        locale.append(variant);

        return locale.toString();
    }
}