/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.inputmethod.latin.tutorial;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;

/**
 * This class extends the {@link Button} widget by modifying the
 * {@link Button#dispatchPopulateAccessibilityEvent(AccessibilityEvent)} method
 * and giving preference to the widget's content description. See
 * {@link #setContentDescription(CharSequence)} for more on content
 * descriptions.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class ContentButton extends Button {
    public ContentButton(Context context) {
        super(context);
    }

    public ContentButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ContentButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (!isShown()) {
            return false;
        }

        CharSequence content = getContentDescription();

        // HACKHACK: If the content description has been set to an empty
        // string, this will prevent TalkBack from speaking anything. We
        // have to set the event type to _FOCUSED because _CLICKED events
        // always speak "clicked".
        if (content != null && TextUtils.isEmpty(content)) {
            event.setEventType(AccessibilityEvent.TYPE_VIEW_FOCUSED);
            event.setClassName(View.class.getName());
            event.setPackageName(View.class.getPackage().getName());
            return true;
        }

        return super.dispatchPopulateAccessibilityEvent(event);
    }
}
