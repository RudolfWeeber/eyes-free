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

package com.google.android.marvin.talkback.formatter.tv;

import com.google.android.marvin.talkback.Formatter;
import com.google.android.marvin.talkback.Utterance;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

/**
 * Formatter that returns an utterance to announce a button in the Launcher
 * application.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public final class LauncherButtonFormatter implements Formatter {

    @Override
    public void format(AccessibilityEvent event, Context context, Utterance utterance,
            Object args) {
        CharSequence contentDescription = event.getContentDescription();
        if (contentDescription != null) {
            utterance.getText().append(contentDescription);
            return;
        }
        List<CharSequence> eventText = event.getText();
        if (eventText.isEmpty()) {
            return;
        }
        CharSequence text = eventText.get(0);
        if (text != null) {
            utterance.getText().append(text);
        }
    }
}
