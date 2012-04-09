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

package com.google.android.marvin.talkback;

import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;

/**
 * This interface defines the contract for writing filters. A filter either
 * accepts or rejects an {@link AccessibilityEvent}.
 * 
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public interface Filter {

    /**
     * Check if the filter accepts a given <code>event</code>.
     * 
     * @param event The event.
     * @param context The context to be used for loading resources etc.
     * @param args Additional arguments for the filter.
     * @return True if the event is accepted, false otherwise.
     */
    boolean accept(AccessibilityEvent event, Context context, Bundle args);
}
