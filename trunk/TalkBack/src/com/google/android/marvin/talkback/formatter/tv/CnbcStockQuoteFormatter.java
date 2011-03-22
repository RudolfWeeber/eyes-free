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
import com.google.android.marvin.talkback.TalkBackService;
import com.google.android.marvin.talkback.Utterance;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;

/**
 * Formatter that returns an utterance to announce a stock quote in
 * the CNBC application.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public final class CnbcStockQuoteFormatter implements Formatter {

    @Override
    public void format(AccessibilityEvent event, Context context, Utterance utterance,
            Object args) {
        CharSequence abbreviation = event.getText().get(0);
        if (abbreviation.charAt(0) == '.') {
            abbreviation = abbreviation.subSequence(1, abbreviation.length());
            abbreviation = TalkBackService.getInstance().cleanUpString(abbreviation.toString());
        }
        CharSequence description = event.getText().get(2);
        CharSequence lastValue = event.getText().get(3);
        CharSequence absoluteChange = event.getText().get(4);
        CharSequence relativeChange = event.getText().get(5);
        String formattedText = context.getString(R.string.template_googletv_stock_quote,
                abbreviation, description, lastValue, absoluteChange, relativeChange);
        utterance.getText().append(formattedText);
    }
}
