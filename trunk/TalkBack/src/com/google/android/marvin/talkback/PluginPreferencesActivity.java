/*
 * Copyright 2010 Google Inc.
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

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;

import java.util.List;

/**
 * This class is preference activity that manages TalkBack plug-ins.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 *
 */
public class PluginPreferencesActivity extends PreferenceActivity
        implements OnPreferenceChangeListener {

    /**
     * Meta-data key to obtain if the plug-in defines classes.
     */
    private static final String KEY_METADATA_DEFINESCLASSES = "definesclasses";

    /**
     * Monitor to handle add/remove/change of packages.
     */
    private BasePackageMonitor mPackageMonitor;

    /**
     * Cached list of the installed plug-ins;
     */
    private List<ServiceInfo> mPlugins;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPlugins = PluginManager.getPlugins(getPackageManager());
        mPackageMonitor = new BasePackageMonitor() {
            @Override
            protected void onPackageAdded(String packageName) {
                mPlugins = PluginManager.getPlugins(getPackageManager());
                addEnabledPluginsCheckboxPreferences();
            }

            @Override
            protected void onPackageChanged(String packageName) {
                mPlugins = PluginManager.getPlugins(getPackageManager());
                addEnabledPluginsCheckboxPreferences();
            }

            @Override
            protected void onPackageRemoved(String packageName) {
                mPlugins = PluginManager.getPlugins(getPackageManager());
                addEnabledPluginsCheckboxPreferences();
            }
        };

        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(this));
        addEnabledPluginsCheckboxPreferences();
        registerForContextMenu(getListView());
        mPackageMonitor.register(this);
    }

    @Override
    protected void onDestroy() {
        mPackageMonitor.unregister();
        super.onDestroy();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.manage_plugin_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.uninstall_plugin:
                int index = info.position;
                CheckBoxPreference preferene = (CheckBoxPreference) getPreferenceScreen()
                        .getPreference(index);
                uninstallPlugin(preferene.getKey());
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * Sends the user to the uninstall screen for the given <code>packageName</code>.
     */
    private void uninstallPlugin(String pluginName) {
        ComponentName componentName = ComponentName.unflattenFromString(pluginName);
        Uri packageURI = Uri.parse("package:" + componentName.getPackageName());
        Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
        startActivity(uninstallIntent);
    }

    /**
     * Adds check-box preferences to this screen for each installed plug-in 
     * and whose checked state corresponds to the plug-in enabled state.
     */
    private void addEnabledPluginsCheckboxPreferences() {
        List<ServiceInfo> installedPlugins = mPlugins;

        getPreferenceScreen().removeAll();
        for (ServiceInfo installedPlugin : installedPlugins) {
            String key = new ComponentName(installedPlugin.packageName,
                    installedPlugin.name).flattenToString();
            CheckBoxPreference preference = new CheckBoxPreference(this);
            preference.setKey(key);
            preference.setTitle(installedPlugin.loadLabel(getPackageManager()));
            preference.setOnPreferenceChangeListener(this);
            getPreferenceScreen().addPreference(preference);
        }
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (newValue == Boolean.TRUE && preference instanceof CheckBoxPreference) {
            CheckBoxPreference checkBoxPreference = (CheckBoxPreference) preference;
            ComponentName pluginComponentName = ComponentName.unflattenFromString(preference
                    .getKey());
            for (ServiceInfo plugin : mPlugins) {
                if (plugin.packageName.equals(pluginComponentName.getPackageName())
                        && plugin.name.equals(pluginComponentName.getClassName())
                        && plugin.metaData.getBoolean(KEY_METADATA_DEFINESCLASSES, false)) {
                    showTalkBackPluginDefinesClassesDialog(plugin, checkBoxPreference);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Shows dialog to obtain informed consent for enabling a class defining
     * <code>plugin</code>.
     */
    private void showTalkBackPluginDefinesClassesDialog(ServiceInfo plugin,
            CheckBoxPreference preference) {
        final CheckBoxPreference finalPreference = preference;
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_warning_talkback_plugin_security))
            .setMessage(getString(R.string.message_warning_talkback_plugin_security,
                    plugin.applicationInfo.loadLabel(getPackageManager())))
            .setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            finalPreference.setChecked(true);
                        }
            })
            .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finalPreference.setChecked(false);
                }
            })
            .create()
            .show();
    }
}
