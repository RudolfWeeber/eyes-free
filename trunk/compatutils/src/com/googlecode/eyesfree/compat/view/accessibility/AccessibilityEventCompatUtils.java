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

package com.googlecode.eyesfree.compat.view.accessibility;

import android.os.Parcel;
import android.view.accessibility.AccessibilityEvent;

/**
 * Provides backward-compatible access to method in {@link AccessibilityEvent}.
 */
public class AccessibilityEventCompatUtils {
    private AccessibilityEventCompatUtils() {
        // This class is non-instantiable.
    }

    /**
     * Returns a cached instance if such is available or a new one is created.
     * The returned instance is initialized from the given <code>event</code>.
     * 
     * @param event The other event.
     * @return An instance.
     */
    public static AccessibilityEvent obtain(AccessibilityEvent event) {
        final Parcel parcel = Parcel.obtain();

        // Write the event to the parcel and reset the data pointer.
        event.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        final AccessibilityEvent clone = AccessibilityEvent.CREATOR.createFromParcel(parcel);

        // Return the parcel to the global pool.
        parcel.recycle();

        return clone;
    }
}
