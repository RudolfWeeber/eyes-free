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

package com.googlecode.eyesfree.brailleback;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.googlecode.eyesfree.braille.display.BrailleDisplayProperties;
import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.BrailleKeyBinding;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.utils.LogUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Shows key bindings for the currently connected Braille display.
 */
public class KeyBindingsActivity extends Activity
        implements Display.OnConnectionStateChangeListener {
    Display mDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.key_bindings);

    }

    @Override
    protected void onStart() {
        mDisplay = new Display(this, this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        if (mDisplay != null) {
            mDisplay.shutdown();
            mDisplay = null;
        }
        super.onStop();
    }

    @Override
    public void onConnectionStateChanged(int state) {
        if (state == Display.STATE_CONNECTED) {
            populateListView();
        }
    }

    private void populateListView() {
        BrailleDisplayProperties props = mDisplay.getDisplayProperties();
        if (props == null) {
            return;
        }
        BrailleKeyBinding[] bindings = props.getKeyBindings();
        ArrayList<BrailleKeyBinding> sortedBindings =
                new ArrayList<BrailleKeyBinding>(Arrays.asList(bindings));
        Collections.sort(sortedBindings, COMPARE_BINDINGS);

        ArrayList<KeyBinding> result = new ArrayList<KeyBinding>();

        String[] supportedCommands =
                getResources().getStringArray(R.array.help_supportedCommands);
        String[] descriptions =
                getResources().getStringArray(R.array.help_commandDescriptions);
        BrailleKeyBinding dummyBinding = new BrailleKeyBinding();
        for (int i = 0; i < supportedCommands.length; ++i) {
            String name = supportedCommands[i];
            int command = BrailleInputEvent.stringToCommand(name);
            if (command == BrailleInputEvent.CMD_NONE) {
                LogUtils.log(this, Log.WARN,
                        "Unknown command %s in resource", name);
                continue;
            }
            dummyBinding.setCommand(command);
            int index = Collections.binarySearch(sortedBindings,
                    dummyBinding, COMPARE_BINDINGS_BY_COMMAND);
            if (index < 0) {
                // No bindings for this command for this display, that's
                // normal.
                continue;
            }
            while (index > 0
                    && sortedBindings.get(index - 1).getCommand() == command) {
                index -= 1;
            }
            addBindingsForCommand(sortedBindings, index, descriptions[i],
                    result);
        }

        KeyBindingsAdapter adapter = new KeyBindingsAdapter(
            KeyBindingsActivity.this, android.R.layout.simple_list_item_2,
            android.R.id.text1);
        adapter.addAll(result);

        ListView list = (ListView) findViewById(R.id.list);
        list.setAdapter(adapter);
    }

    private void addBindingsForCommand(
            List<BrailleKeyBinding> bindings, int startIndex,
            String commandDescription,
            List<KeyBinding> result) {
        String delimiter = getString(R.string.help_keyBindingDelimiter);
        int command = bindings.get(startIndex).getCommand();
        int index = startIndex;
        BrailleKeyBinding prevBinding = null;
        do {
            BrailleKeyBinding binding = bindings.get(index);
            // Skip over duplicates in the keymap.
            if (prevBinding == null
                || !Arrays.equals(
                        prevBinding.getKeyNames(),
                        binding.getKeyNames())) {
                result.add(new KeyBinding(
                    commandDescription,
                    TextUtils.join(delimiter, binding.getKeyNames())));
            }
            prevBinding = binding;
            index += 1;
        } while (index < bindings.size()
                && bindings.get(index).getCommand() == command);
    }

    private static class KeyBindingsAdapter extends ArrayAdapter<KeyBinding> {
        public KeyBindingsAdapter(Context context, int layout, int textViewResourceId) {
            super(context, layout, textViewResourceId);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View view = super.getView(position, convertView, parent);
            final KeyBinding item = getItem(position);

            ((TextView) view.findViewById(android.R.id.text2)).setText(item.binding);

            return view;
        }
    }

    private static class KeyBinding {
        private final String label;
        private final String binding;

        public KeyBinding(String label, String binding) {
            this.label = label;
            this.binding = binding;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Compares key bindings by command number, then by number of keys in the
     * binding, and finally by key names for consistency.  Used for sorting.
     */
    private static final Comparator COMPARE_BINDINGS =
            new Comparator<BrailleKeyBinding>() {
            @Override
            public int compare(BrailleKeyBinding lhs, BrailleKeyBinding rhs) {
                int command1 = lhs.getCommand();
                int command2 = rhs.getCommand();
                if (command1 != command2) {
                    return command1 - command2;
                }
                String[] names1 = lhs.getKeyNames();
                String[] names2 = rhs.getKeyNames();
                if (names1.length != names2.length) {
                    return names1.length - names2.length;
                }
                for (int i = 0; i < names1.length; ++i) {
                    String key1 = names1[i];
                    String key2 = names2[i];
                    int res = key1.compareTo(key2);
                    if (res != 0) {
                        return res;
                    }
                }
                return 0;
            }
    };

    /**
     * Compares key bindings by command number.  Used for search.
     */
    private static final Comparator COMPARE_BINDINGS_BY_COMMAND =
            new Comparator<BrailleKeyBinding>() {
            @Override
            public int compare(BrailleKeyBinding lhs, BrailleKeyBinding rhs) {
                return lhs.getCommand() - rhs.getCommand();
            }
    };
}
