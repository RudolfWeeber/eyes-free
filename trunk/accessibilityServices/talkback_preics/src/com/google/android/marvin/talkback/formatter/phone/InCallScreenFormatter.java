/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.google.android.marvin.talkback.formatter.phone;

import com.google.android.marvin.talkback.Formatter;
import com.google.android.marvin.talkback.Utterance;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

/**
 * Formatter that returns an utterance to announce the incoming call screen.
 *
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public final class InCallScreenFormatter implements Formatter {

    // Indices of the text elements for a in-call-screen event
    private static final int INDEX_UPPER_TITLE = 1;

    private static final int INDEX_PHOTO = 2;

    private static final int INDEX_NAME = 4;

    private static final int INDEX_LABEL = 6;

    private static final int INDEX_SOCIAL_STATUS = 7;

    private static final String SPACE = " ";

    @Override
    public void format(AccessibilityEvent event, Context context, Utterance utterance,
            Object args) {
        List<CharSequence> eventText = event.getText();
        StringBuilder utteranceText = utterance.getText();

        // guard against old version of the phone application
        if (eventText.size() == 1) {
            utteranceText.append(eventText.get(0));
            return;
        }

        CharSequence title = eventText.get(INDEX_UPPER_TITLE);
        if (title != null) {
            utteranceText.append(title);
            utteranceText.append(SPACE);
        }

        CharSequence name = eventText.get(INDEX_NAME);
        if (name == null) {
            return;
        }

        utteranceText.append(name);
        utteranceText.append(SPACE);

        if (!isPhoneNumber(name.toString())) {
            CharSequence label = eventText.get(INDEX_LABEL);
            if (label != null) {
                utteranceText.append(label);
                utteranceText.append(SPACE);
            }
            CharSequence photo = eventText.get(INDEX_PHOTO);
            if (photo != null) {
                utteranceText.append(photo);
                utteranceText.append(SPACE);
            }
            CharSequence socialStatus = eventText.get(INDEX_SOCIAL_STATUS);
            if (socialStatus != null) {
                utteranceText.append(socialStatus);
                utteranceText.append(SPACE);
            }
        }
    }

    /**
     * Returns if a <code>value</code> is a phone number.
     */
    private boolean isPhoneNumber(String value) {
        String valueNoDeshes = value.replaceAll("-", "");
        try {
            Long.parseLong(valueNoDeshes);
            return true;
        } catch (IllegalArgumentException iae) {
            return false;
        }
    }
}
