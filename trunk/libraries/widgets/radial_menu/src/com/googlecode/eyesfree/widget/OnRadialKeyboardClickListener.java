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

package com.googlecode.eyesfree.widget;

import android.text.TextUtils;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MenuItem;

/**
 * @author alanv@google.com (Alan Viverette)
 *
 */
public abstract class OnRadialKeyboardClickListener implements MenuItem.OnMenuItemClickListener {
    private static final KeyCharacterMap KEY_CHARACTER_MAP = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
    
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getGroupId() == R.id.group_keyboard) {
            final KeyEvent[] events = parseKeyCode(item.getItemId(), item.getTitle());
            
            if (events != null) {
                boolean handled = false;
                for (KeyEvent event : events) {
                    handled |= onKeyboardItemClick(item, event);
                }
                return handled;
            }
        }
        
        return false;
    }
    
    public abstract boolean onKeyboardItemClick(MenuItem item, KeyEvent event);
    
    private KeyEvent[] parseKeyCode(int itemId, CharSequence title) {
        if (itemId == R.id.key_delete) {
            return new KeyEvent[] {
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL),
                    new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)
            };
        } else if (itemId == R.id.key_space) {
            return parseKeyEventFromString(" ");
        } else {
            return parseKeyEventFromString(title);
        }
    }
    
    private KeyEvent[] parseKeyEventFromString(CharSequence title) {
        if (TextUtils.isEmpty(title)) {
            return new KeyEvent[0];
        }
        
        return KEY_CHARACTER_MAP.getEvents(new char[] {title.charAt(0)});
    }
}
