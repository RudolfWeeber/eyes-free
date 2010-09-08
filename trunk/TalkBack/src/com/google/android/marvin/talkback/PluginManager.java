/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.google.android.marvin.talkback.TalkBackService.InfrastructureStateListener;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Handler.Callback;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class is responsible for managing TalkBack plug-ins.
 * <p>
 *   TalkBack plug-in mechanism:
 * </p>
 * <ul>
 *   <li>
 *       A plug-in is a standard Android APK file.
 *   </li>
 *   <li>Each plug-in consists of one or more speech strategy files, zero or more
 *       custom formatters/filters, and a place holder service.
 *   </li>
 *   <li>Each plug-in is targeted for one or more packages which means that it
 *       defines rules for speaking events originating from these packages.
 *   </li>
 *   <li>If more than one plug-in targets the same package the last loaded one
 *       wins i.e. overrides previously loaded ones.
 *   </li>
 *   <li>The plug-in mechanism watches for (re)installed plug-ins and loads them
 *       appropriately.
 *   </li>
 *   <li>
 *     <strong>
 *       For a plug-in to define custom filters and formatters it must have the
 *       com.google.android.marvin.talkback.PERMISSION_PLUGIN_DEFINES_CLASSES
 *       permission which is signature protected. In other words, only packages
 *       signed with the same key with TalkBack will be able to define custom
 *       filters and formatters.
 *     </strong>
 *   </li>
 * </ul>
 * <p>
 *   Android.apk:
 * </p>
 * <ul>
 *   <li>
 *     The place holder service should be declared as such in the manifest and
 *     it should also have intent filer for action
 *     <code>"com.google.android.marvin.talkback.Plugin"</code>.
 *   </li>
 *   <li>
 *     The place holder service declaration should contain two meta data
 *     declarations. The declaration <code>packages</code> is a colon
 *     separated list of package names and denotes the packages this
 *     plug-in is targeted for and therefore will receive only events
 *     from these packages. The declaration <code>speechstrategies</code>
 *     is a colon separated list of XML file names which define the
 *     speech strategies for the handled packages. <strong> Note that
 *     the first speech strategy should contain the rules for the first
 *     package and so on. Excessive package names or speech strategies
 *     are ignored.
 *   </li>
 * </ul>
 * <p>
 *   Speech strategies:
 * </p>
 * <ul>
 *   <li>
 *     Each speech strategy XML file contains rules for handling a single
 *     package. The speech rules in this files can refer to both resources
 *     and Java classes which implement a filter or formatter interfaces.
 *   </li>
 *   <li>
 *     Speech strategies <strong>should be placed in the res/raw folder</strong>.
 *   </li>
 *   <li>
 *     The plug-ins' speech rules override the default TalkBack rules.
 *   </li>
 *   <li>
 *     Speech rules are examined linearly and the first matching one is
 *     used, therefore their ordering in the speech strategy file matters.
 *   </li>
 * <p>
 *   Custom filters/formatters:
 * </p>
 * <ul>
 *   <li>
 *     Are implementations of <code>com.google.android.marvin.talkback.Filter</code>
 *     and <code>com.google.android.marvin.talkback.Formatter</code> and used when
 *     the expressiveness of the XML is insufficient for proper event filtering or
 *     utterance formatting.
 *   </li>
 *   <li>
 *   </li>
 * </ul>
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public class PluginManager implements Handler.Callback, InfrastructureStateListener,
        OnSharedPreferenceChangeListener {

    /**
     * Tag used for logging.
     */
    private static final String LOG_TAG = "PluginManager";

    /**
     * Mutex lock to ensure atomic operation while caching plug-ins.
     */
    private static final Object mLock = new Object();

    /**
     * This action classifies a service as a TalkBack plug-in.
     */
    private static final String ACTION_TALKBACK_PLUGIN = "com.google.android.marvin.talkback.Plugin";

    /**
     * Meta-data key to obtain packages handled by a given plug-in.
     */
    private static final String KEY_METADATA_PACKAGES = "packages";

    /**
     * Meta-data key to obtain speech strategies provided by a given plug-in.
     */
    private static final String KEY_METADATA_SPEECHSTRATEGIES = "speechstrategies";

    /**
     * Intent for fetching TalkBack plug-ins.
     */
    private static final Intent sPluginIntent = new Intent(ACTION_TALKBACK_PLUGIN);

    /**
     * Message to perform asynchronous plug-in loading.
     */
    private static final int WHAT_DO_LOAD_PLUGIN_FROM_HANDLER = 0x00000001;

    /**
     * Mapping from a plug-in package target packages for removing obsolete speech rules.
     */
    private final HashMap<String, PluginInfo> mPluginCache = new HashMap<String, PluginInfo>();

    /**
     * Pre-compiles a pattern for splitting colon separated strings.
     */
    private final Pattern mColonSplitPattern = Pattern.compile(":");

    /**
     * The {@link Context} this manager operates in.
     */
    private final Context mContext;

    /**
     * Handle to the processor for managing speech rules.
     */
    private final SpeechRuleProcessor mSpeechRuleProcessor;

    /**
     * Handle to the package manager for plug-in loading.
     */
    private final PackageManager mPackageManager;

    /**
     * Handle to the shared preferences.
     */
    private final SharedPreferences mPreferences;

    /**
     * Monitor to handle add/remove/change of packages.
     */
    private final BasePackageMonitor mPackageMonitor;

    /**
     * Worker for asynchronous plug-in loading.
     */
    private final Worker mWorker;

    /**
     * Creates a new instance.
     */
    PluginManager(Context context, SpeechRuleProcessor processor) {
        mContext = context;
        mSpeechRuleProcessor = processor;
        mPackageManager = context.getPackageManager();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mPackageMonitor = new BasePackageMonitor() {
            @Override
            protected void onPackageAdded(String packageName) {
                handlePackageAddedOrChanged(packageName);
            }

            @Override
            protected void onPackageChanged(String packageName) {
                handlePackageAddedOrChanged(packageName);
            }

            @Override
            protected void onPackageRemoved(String packageName) {
                handlePackageRemoved(packageName);
            }    
        };
        mWorker = new Worker(this);
    }

    /**
     * @return The list of plug-ins fetched via the given <code>packageManager</code>.
     */
    public static List<ServiceInfo> getPlugins(PackageManager packageManager) {
        List<ResolveInfo> resolveInfos = packageManager.queryIntentServices(sPluginIntent,
                PackageManager.GET_META_DATA);
        ArrayList<ServiceInfo> plugins = new ArrayList<ServiceInfo>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            plugins.add(resolveInfo.serviceInfo);
        }
        return plugins;
    }

    public boolean handleMessage(Message message) {
        if (message.what == WHAT_DO_LOAD_PLUGIN_FROM_HANDLER) {
            doLoadPluginFromHandler((PluginInfo) message.obj);
            return true;
        }
        return false;
    }

    public void onInfrastructureStateChange(boolean isInitialized) {
        if (isInitialized) {
            buildPluginCache();
            mPreferences.registerOnSharedPreferenceChangeListener(this);
            mPackageMonitor.register(mContext);
            mWorker.start();
            loadPlugins();
        } else {
            clearPluginCache();
            mPreferences.unregisterOnSharedPreferenceChangeListener(this);
            mPackageMonitor.unregister();
            mWorker.stop();
            unloadPlugins();
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Object value = null;
        synchronized (mLock) {
            value = mPluginCache.get(key);
        }
        if (value != null && value instanceof PluginInfo) {
            PluginInfo pluginInfo = (PluginInfo) value;
            pluginInfo.enabled = sharedPreferences.getBoolean(key, false);
            if (pluginInfo.enabled) {
                loadPlugin(pluginInfo);
            } else {
                unloadPlugin(pluginInfo);
            }
        }
    }

    /**
     * Builds the plug-in cache.
     */
    private void buildPluginCache() {
        synchronized (mLock) {
            List<ServiceInfo> plugins = getPlugins(mPackageManager);
            for (ServiceInfo plugin : plugins) {
                if (!isValidPlugin(plugin)) {
                    continue;
                }
                String key = new ComponentName(plugin.packageName, plugin.name).flattenToString();
                PluginInfo pluginInfo = createPluginInfo(plugin);
                mPluginCache.put(key, pluginInfo);
            }   
        }
    }

    /**
     * Clears the plug-in cache.
     */
    private void clearPluginCache() {
        synchronized (mLock) {
            mPluginCache.clear();    
        }
    }

    /**
     * @return If a <code>plug-in</code> is valid which is it has the same
     *         shared user Id (this requires that the plug-in package is
     *         signed with the TalkBack key).
     */
    private boolean isValidPlugin(ServiceInfo plugin) {
        return (android.os.Process.myUid() == plugin.applicationInfo.uid);
    }

    /**
     * Loads the plug-ins.
     */
    private void loadPlugins() {
        synchronized (mLock) {
            for (PluginInfo pluginInfo : mPluginCache.values()) {
                if (pluginInfo.enabled) {
                    loadPlugin(pluginInfo);
                }
            }
        }
    }

    /**
     * Unloads the plug-ins.
     */
    private void unloadPlugins() {
        for (PluginInfo pluginInfo : mPluginCache.values()) {
            if (pluginInfo.enabled) {
                unloadPlugin(pluginInfo);
            }
        }
    }

    /**
     * @return A plug-in info instance given the <code>plug-in</code>.
     */
    private PluginInfo createPluginInfo(ServiceInfo plugin) {
        String targePackagesValue = plugin.metaData.getString(KEY_METADATA_PACKAGES);
        String speechStrategiesValue = plugin.metaData.getString(KEY_METADATA_SPEECHSTRATEGIES);
        String key = new ComponentName(plugin.packageName, plugin.name).flattenToString();

        PluginInfo info = new PluginInfo();
        info.enabled = mPreferences.getBoolean(key, false);
        info.serviceInfo = plugin;
        info.publicSourceDir = plugin.applicationInfo.publicSourceDir;
        info.speechStrategies = mColonSplitPattern.split(speechStrategiesValue);
        info.targetPackages = mColonSplitPattern.split(targePackagesValue);

        return info;
    }

    /**
     * Loads a given <code>pluginInfo</code>.
     */
    private void loadPlugin(PluginInfo pluginInfo) {
        String key = new ComponentName(pluginInfo.serviceInfo.packageName,
                pluginInfo.serviceInfo.name).flattenToString();
        if (!mPreferences.getBoolean(key, false)) {
            return;
        }
        mWorker.getHandler().obtainMessage(WHAT_DO_LOAD_PLUGIN_FROM_HANDLER, pluginInfo)
                .sendToTarget();
    }

    /**
     * Unloads a given <code>pluginInfo</code>.
     */ 
    private void unloadPlugin(PluginInfo pluginInfo) {
        String[] targetPackages = pluginInfo.targetPackages;
        for (int i = 0, count = targetPackages.length; i < count; i++) {
            mSpeechRuleProcessor.removeSpeechRulesForPackage(targetPackages[i]);
        }        
    }

    /**
     * Loads a <code>plugin</code> and should be called from our handler
     * to achieve asynchronous loading.
     */
    private void doLoadPluginFromHandler(PluginInfo pluginInfo) {
        String[] targetPackages = pluginInfo.targetPackages;
        String[] speechStrategies = pluginInfo.speechStrategies;
        String pluginPackage = pluginInfo.serviceInfo.packageName;

        Context context = createPackageContext(pluginPackage);
        if (context == null) {
            return;
        }

        if (targetPackages.length == 0) {
            Log.w(LOG_TAG, "Plug-in does not define \"packages\" meta-data mapping: "
                    + pluginPackage + ". Ignoring!");
            return;
        }
        if (speechStrategies.length == 0) {
            Log.w(LOG_TAG, "Plug-in does not define \"speechstrategies\" meta-data mapping: "
                    + pluginPackage + ". Ignoring!");
            return;
        }
        if (targetPackages.length < speechStrategies.length) {
            Log.w(LOG_TAG, "#packages < #speechstrategies => Loading only speech strategies"
                    + " for the declared packages and ignoring the rest speech strategies.");
        } else if (targetPackages.length > speechStrategies.length) {
            Log.w(LOG_TAG, "#packages > #speechstrategies => Loading only packages for"
                    + " the declared speech strategies and ignoring the rest packages.");
        }

        Resources resources = context.getResources();
        for (int i = 0, count = targetPackages.length; i < count; i++) {
            // check if more packages than speech strategies
            if (i >= speechStrategies.length) {
                return;
            }
            String speechStrategyResourceName = speechStrategies[i].replace(".xml", "");
            int resourceId = resources.getIdentifier(context.getPackageName() + ":raw/"
                    + speechStrategyResourceName, null, null);
            mSpeechRuleProcessor.addSpeechStrategy(context, pluginInfo.publicSourceDir,
                    targetPackages[i], resourceId);
        }
    }

    /**
     * @return {@link Context} instance for the given <code>packageName</code>.
     */
    private Context createPackageContext(String packageName) {
        try {
            int flags = (Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            return mContext.getApplicationContext().createPackageContext(packageName, flags);
        } catch (NameNotFoundException nnfe) {
            Log.e(LOG_TAG, "Error creating package context.", nnfe);
        }
        return null;
    }

    /**
     * Worker for asynchronous plug-in loading.
     */
    class Worker implements Runnable {        

        /**
         * Lock to ensure {@link Handler} instance.
         */
        final Object mWorkerLock = new Object();

        /**
         * {@link Handler} for asynchronous message processing.
         */
        Handler mHandler;

        Callback mCallback;

        /**
         * Creates a new worker whose handler's messages are handled
         * by the provided <code>callback</code>.
         */
        Worker(Handler.Callback callbalk) {
            mCallback = callbalk;
        }

        @Override
        public void run() {
            synchronized (mWorkerLock) {
                Looper.prepare();
                mHandler = new Handler(mCallback);
                mWorkerLock.notify();
            }
            Looper.loop();
        }

        /**
         * @return This worker's {@link Handler}.
         */
        public Handler getHandler() {
            return mHandler;
        }

        /**
         * Starts the worker.
         */
        public void start() {
            Thread thread = new Thread(this);
            thread.start();
            synchronized (mWorkerLock) {
                while (mHandler == null) {
                    try {
                        mWorkerLock.wait();
                    } catch (InterruptedException ie) {
                        /* ignore */
                    }
                }
            }
        }

        /**
         * Stops the worker.
         */
        public void stop() {
            mHandler.getLooper().quit();
        }
    }

    /**
     * Helper class to hold together plug-in info.
     */
    class PluginInfo {
        boolean enabled;

        ServiceInfo serviceInfo;

        String publicSourceDir;

        String[] targetPackages;

        String[] speechStrategies;
    }

    /**
     * Handles the removal of a <code>packageName</code>.
     */
    private void handlePackageRemoved(String packageName) {
        synchronized (mLock) {
            Iterator<PluginInfo> iterator = mPluginCache.values()
                    .iterator();
            while (iterator.hasNext()) {
                PluginInfo pluginInfo = iterator.next();
                if (!pluginInfo.serviceInfo.packageName.equals(packageName)) {
                    continue;    
                }
                iterator.remove();
                unloadPlugin(pluginInfo);
            }
        }
    }

    /**
     * Handles the addition or change of a <code>packageName</code>.
     */
    private void handlePackageAddedOrChanged(String packageName) {
        ServiceInfo plugin = getPluginForPackage(packageName);
        if (plugin != null) {
            if (!isValidPlugin(plugin)) {
                return;
            }
            String key = new ComponentName(plugin.packageName, plugin.name)
                .flattenToString();
            PluginInfo pluginInfo = createPluginInfo(plugin);
            synchronized (mLock) {
                mPluginCache.put(key, pluginInfo);
                if (pluginInfo.enabled) {
                    loadPlugin(pluginInfo);
                }
            }
        }
    }

    /**
     * @return The plug-in for a <code>pacakgeName</code> or null if no such.
     */
    private ServiceInfo getPluginForPackage(String packageName) {
        List<ResolveInfo> resolveInfos = mPackageManager.queryIntentServices(sPluginIntent,
                PackageManager.GET_META_DATA);
        for (ResolveInfo resolveInfo : resolveInfos) {
            ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            if (serviceInfo.packageName.equals(packageName)) {
                return serviceInfo;
            }
        }
        return null;
    }
}
