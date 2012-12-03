/*
 * Copyright (C) 2012 Google Inc.
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

import android.content.Intent;
import android.os.Message;

import com.googlecode.eyesfree.utils.WeakReferenceHandler;

/**
 * Transfers handling of broadcasts to the handler thread.
 */
public abstract class BroadcastHandler<T> extends WeakReferenceHandler<T> {
    private static final int MSG_BROADCAST_RECEIVED = 1;

    public BroadcastHandler(T parent) {
        super(parent);
    }

    @Override
    protected void handleMessage(Message msg, T parent) {
        switch (msg.what) {
            case MSG_BROADCAST_RECEIVED:
                handleOnReceive((Intent) msg.obj, parent);
                break;
        }
    }

    public void onReceive(Intent intent) {
        obtainMessage(MSG_BROADCAST_RECEIVED, intent).sendToTarget();
    }

    /**
     * Handles broadcasts on the handler thread.
     *
     * @param intent The received Broadcast intent.
     */
    public abstract void handleOnReceive(Intent intent, T parent);
}