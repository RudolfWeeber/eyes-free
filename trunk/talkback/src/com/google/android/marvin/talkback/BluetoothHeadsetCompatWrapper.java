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

package com.google.android.marvin.talkback;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;

import java.lang.reflect.Method;

class BluetoothHeadsetCompatWrapper {
    private static final Class<?> CLASS_BluetoothHeadset = BluetoothHeadset.class;
    private static final Method METHOD_startScoUsingVirtualVoiceCall = CompatUtils.getMethod(
            CLASS_BluetoothHeadset, "startScoUsingVirtualVoiceCall", BluetoothDevice.class);
    private static final Method METHOD_stopScoUsingVirtualVoiceCall = CompatUtils.getMethod(
            CLASS_BluetoothHeadset, "stopScoUsingVirtualVoiceCall", BluetoothDevice.class);

    private final BluetoothHeadset mHeadset;

    public BluetoothHeadsetCompatWrapper(BluetoothHeadset headset) {
        mHeadset = headset;
    }

    public boolean startScoUsingVirtualVoiceCall(BluetoothDevice device) {
        return (Boolean) CompatUtils.invoke(mHeadset, false, METHOD_startScoUsingVirtualVoiceCall,
                device);
    }

    public boolean stopScoUsingVirtualVoiceCall(BluetoothDevice device) {
        return (Boolean) CompatUtils.invoke(mHeadset, false, METHOD_stopScoUsingVirtualVoiceCall,
                device);
    }
}
