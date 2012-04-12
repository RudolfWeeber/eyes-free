/*
 * Copyright 2011 Google Inc.
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.googlecode.eyesfree.utils.LogUtils;

/**
 * Processes shortcuts.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class TalkBackShortcutHandler extends Activity {
    /**
     * Broadcast action for performing shortcuts. The service listens for this
     * broadcast.
     */
    public static final String BROADCAST_SHORTCUT =
            "com.google.android.marvin.talkback.BROADCAST_SHORTCUT";

    /**
     * Activity action for performing shortcuts. This system uses this action to
     * launch the shortcut handler, which sends a broadcast intent with the
     * {@link #BROADCAST_SHORTCUT} action.
     */
    public static final String ACTION_SHORTCUT = "com.google.android.marvin.talkback.SHORTCUT";

    /** Intent extra for specifying a shortcut to perform. */
    public static final String EXTRA_SHORTCUT_NAME = "shortcutName";

    /** Enumeration for available shortcuts. */
    public enum TalkBackShortcut {
        REPEAT_LAST_UTTERANCE(R.string.shortcut_repeat_last_utterance),
        SPELL_LAST_UTTERANCE(R.string.shortcut_spell_last_utterance);

        /** The resource identifier for this shortcut's description. */
        public final int resId;

        private TalkBackShortcut(int resId) {
            this.resId = resId;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        forwardShortcut();
    }

    /**
     * Extracts a shortcut from the activity's intent and forwards the shortcut
     * to TalkBack.
     */
    private void forwardShortcut() {
        final String shortcutName = getIntent().getStringExtra(EXTRA_SHORTCUT_NAME);
        if (shortcutName == null) {
            return;
        }

        final TalkBackShortcut shortcut = TalkBackShortcut.valueOf(shortcutName);
        if (shortcut == null) {
            return;
        }

        LogUtils.log(
                TalkBackShortcutHandler.class, Log.DEBUG, "Received shortcut %s", shortcutName);

        final Intent broadcastIntent = new Intent(this, TalkBackService.class);
        broadcastIntent.setAction(BROADCAST_SHORTCUT);
        broadcastIntent.putExtra(EXTRA_SHORTCUT_NAME, shortcutName);

        sendBroadcast(broadcastIntent);

        finish();
    }
}
