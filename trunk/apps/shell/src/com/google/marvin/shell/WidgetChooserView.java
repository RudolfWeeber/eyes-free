/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.marvin.shell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.AttributeSet;

/**
 * Allows the user to select the android widget they wish to start by moving
 * through the list of android widgets.
 *
 * @author sainsley@google.com (Sam Ainsley)
 */
public class WidgetChooserView extends ChooserView<AppWidgetProviderInfo> {

    private WidgetComparator comparator;

    public WidgetChooserView(Context context, AttributeSet attrs) {
        super(context, attrs);
        comparator = new WidgetComparator();
    }

    public void setWidgetList(ArrayList<AppWidgetProviderInfo> widgets) {
        items = widgets;
        Collections.sort(items, comparator);
    }

    @Override
    public boolean matchesSearch(int index) {
        String title = items.get(index).label.toLowerCase();
        return title.startsWith(currentString.toLowerCase());
    }

    @Override
    public void speakCurrentItem(boolean interrupt) {
        String name = items.get(currentIndex).label;
        if (interrupt) {
            parent.tts.speak(name, TextToSpeech.QUEUE_FLUSH, null);
        } else {
            parent.tts.speak(name, TextToSpeech.QUEUE_ADD, null);
        }
        invalidate();
    }

    @Override
    public void startActionHandler() {
        currentString = "";
        parent.onAndroidWidgetSelected(items.get(currentIndex), -1, false);
    }

    @Override
    public String getCurrentItemName() {
        return items.get(currentIndex).label;
    }

    public WidgetComparator getComparator() {
        return comparator;
    }

    private class WidgetComparator implements Comparator<AppWidgetProviderInfo> {

        @Override
        public int compare(AppWidgetProviderInfo lhs, AppWidgetProviderInfo rhs) {
            return lhs.label.compareTo(rhs.label);
        }

    }
}
