/*
 * Copyright  (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.inputmethod;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.KeyEvent;

import com.google.android.marvin.aime.AccessibleInputConnection;
import com.google.android.marvin.aime.AccessibleInputMethodService;
import com.googlecode.eyesfree.inputmethod.latin.LatinIMESettings;
import com.googlecode.eyesfree.linearnavigation.ILinearNavigationConnection;

public abstract class LinearNavigationInputMethodService extends AccessibleInputMethodService {
    private static final String ACTION_CONTROLLER =
            "com.googlecode.eyesfree.linearnavigation.ControllerService";

    private ILinearNavigationConnection mLinearNavigationConnection;

    private boolean mLinearNavigationEnabled = false;

    @Override
    public void onDestroy() {
        super.onDestroy();

        disconnectService();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final AccessibleInputConnection connection = getCurrentInputConnection();
        final boolean isEditingText = ((connection != null) && connection.hasExtractedText());

        if (!mLinearNavigationEnabled || isEditingText) {
            return super.onKeyUp(keyCode, event);
        }

        boolean handled = false;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                handled = next();
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                handled = previous();
                break;
        }

        if (handled) {
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    private boolean next() {
        if (mLinearNavigationConnection == null) {
            connectService();
            return false;
        }

        try {
            mLinearNavigationConnection.next();
            return true;
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean previous() {
        if (mLinearNavigationConnection == null) {
            connectService();
            return false;
        }

        try {
            mLinearNavigationConnection.previous();
            return true;
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!mLinearNavigationEnabled) {
            return super.onKeyUp(keyCode, event);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    private void connectService() {
        final Intent service = new Intent(ACTION_CONTROLLER);

        bindService(service, mServiceConnection, Service.BIND_AUTO_CREATE);
    }

    private void disconnectService() {
        if (mLinearNavigationConnection == null) {
            return;
        }

        mLinearNavigationConnection = null;

        unbindService(mServiceConnection);
    }

    protected void setLinearNavigationEnabled(boolean enabled) {
        // First check whether the service is installed and enabled.
        final String enabledServices =
                Settings.Secure.getString(getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        final boolean serviceEnabled =
                (enabledServices != null)
                        && enabledServices.contains(LatinIMESettings.LINEAR_NAVIGATION_PACKAGE);

        if (!serviceEnabled) {
            enabled = false;
        }
        
        final boolean changed = (enabled != mLinearNavigationEnabled);

        mLinearNavigationEnabled = enabled;

        if (!changed) {
            return;
        }

        if (enabled) {
            connectService();
        } else {
            disconnectService();
        }
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mLinearNavigationConnection = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mLinearNavigationConnection = ILinearNavigationConnection.Stub.asInterface(service);
        }
    };
}
