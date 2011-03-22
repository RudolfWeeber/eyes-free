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
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.Utterance;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;

/**
 * Formatter that returns an utterance to item position.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public final class ItemPositionFormatter implements Formatter {

    @Override
    public void format(AccessibilityEvent event, Context context, Utterance utterance,
            Object args) {
        int currentItemIndex = event.getCurrentItemIndex() + 1;
        int itemCount = event.getItemCount();
        String formattedText = context.getString(R.string.template_googletv_item_position,
                currentItemIndex, itemCount);
        utterance.getText().append(formattedText);
    }
}
