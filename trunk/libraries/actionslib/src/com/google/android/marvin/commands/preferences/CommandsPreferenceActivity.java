// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands.preferences;

import com.google.android.marvin.actionslib.R;
import com.google.android.marvin.commands.Command;
import com.google.android.marvin.commands.CommandsManager;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.Editable;
import android.text.method.BaseKeyListener;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;

import java.util.List;

/**
 * The preferences page for configuring keyboard shortcuts for the commands.
 * 
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public class CommandsPreferenceActivity extends PreferenceActivity {
    
    private KeyCharacterMap keyCharacterMap;
    private List<Command> commands;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.BUILT_IN_KEYBOARD);
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(this);
        screen.setTitle("Keyboard Shortcuts");
                
        setPreferenceScreen(screen);

        // get the possible preferences using the CommandsManager
        final CommandsManager commandsManager = new CommandsManager();
        commands = commandsManager.getAvailableCommands(this);
      
        createPreferenceScreens(commandsManager, screen);
    }
    
    private void createPreferenceScreens(
            final CommandsManager commandsManager, final PreferenceScreen parent) {

        final String menuString = getString(R.string.menu_key);
        final String searchString = getString(R.string.search_key);
        String[] modifierKeys = { menuString, searchString };
        
        // for each command, add a screen with two sub preferences, one for the modifier and one
        // for the key.
        for (final Command command : commands) {
            String modifierKeyName = getKeyName(command.getModifier());
            String letterKeyName = getKeyName(command.getKeyCode());
            
            final PreferenceScreen shortcutScreen = 
                getPreferenceManager().createPreferenceScreen(this);
           
            shortcutScreen.setTitle(command.getDisplayName());
            shortcutScreen.setSummary(getShortcutSummary(command));

            // Create a list preference for the modifier that allows Menu or Search to be selected.
            final ListPreference list = new ListPreference(this);
            list.setEntries(modifierKeys);
            list.setEntryValues(modifierKeys);
            list.setTitle(R.string.modifier_key);
            list.setSummary(modifierKeyName);
            list.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // when the value changes, update the database with the new value.
                    int newModifierKey = getKeyCode((String)newValue);
                    command.setModifier(newModifierKey);
                    commandsManager.updateCommandShortcut(
                                CommandsPreferenceActivity.this, command);

                    // update the summaries with the new value
                    list.setSummary((String)newValue);

                    // Note: for some reason you can't update the parent summary from here, instead
                    // recreate the screens to force an update.
                    parent.removeAll();
                    createPreferenceScreens(commandsManager, parent);
                    return true;
                }
            });
            
            shortcutScreen.addPreference(list);
            
            // add an edit text preference for the letter key.
            final EditTextPreference editTextPreference = new EditTextPreference(this);
            editTextPreference.setTitle(R.string.letter_key);
            editTextPreference.setSummary(letterKeyName);
            
            editTextPreference.getEditText().setKeyListener(new BaseKeyListener() {
                @Override
                public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || 
                            keyCode == KeyEvent.KEYCODE_ALT_RIGHT ||
                            keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                            keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
                            keyCode == KeyEvent.KEYCODE_SEARCH || 
                            keyCode == KeyEvent.KEYCODE_MENU || 
                            keyCode == KeyEvent.KEYCODE_ENTER|| 
                            keyCode == KeyEvent.KEYCODE_DEL) {
                        return true;
                    }
                    return false;
                }
                @Override
                public boolean onKeyUp(View view, Editable text, int keyCode, KeyEvent event) {
                    int unicode = event.getUnicodeChar();
                    if (unicode != 0 && unicode != 0xEF02) {
                        String character = new String(new int[] {unicode}, 0 , 1);
                        text.clear();
                        text.append(character);
                        return true;
                    }
                    return false;
                }                
                @Override
                public int getInputType() {
                    return 0;
                }
            });
            
            editTextPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // when the value changes, save the new value to the database
                    int newLetterKey = getKeyCode((String)newValue);
                    
                    command.setKeyCode(newLetterKey);
                    commandsManager.updateCommandShortcut(
                        CommandsPreferenceActivity.this, command);
                    
                    // update the summaries with the new value
                    editTextPreference.setSummary((String)newValue);
                    
                    // Note: for some reason you can't update the parent summary from here, instead
                    // recreate the screens to force an update.
                    parent.removeAll();
                    createPreferenceScreens(commandsManager, parent);
                    return true;
                }
            });
            
            shortcutScreen.addPreference(editTextPreference);
            parent.addPreference(shortcutScreen);
        }
    }
    
    /**
     * The summary for the shorcut page is a combination of the modifier and letter.
     */
    protected String getShortcutSummary(Command command) {
        String modifierKeyName = getKeyName(command.getModifier());
        String letterKeyName = getKeyName(command.getKeyCode());
        return modifierKeyName + " + " + letterKeyName;
    }
    
    /**
     * Get a string value to display for the key. 
     */
    protected String getKeyName(int key) {
        switch(key) {
        case KeyEvent.KEYCODE_SEARCH:
            return getString(R.string.search_key);
        case KeyEvent.KEYCODE_MENU:
            return getString(R.string.menu_key);
        default:
            int unicodeChar = new KeyEvent(KeyEvent.ACTION_DOWN, key).getUnicodeChar();
            return new String(new int[] { unicodeChar }, 0, 1);
        }
    }
    
    /**
     * Turn the entered string value back into a integer key code.
     */
    protected int getKeyCode(String name) {
        if (name.equals(getString(R.string.menu_key))) {
            return KeyEvent.KEYCODE_MENU;
        }
        if (name.equals(getString(R.string.search_key))) {
            return KeyEvent.KEYCODE_SEARCH;
        }

        KeyEvent[] events = keyCharacterMap.getEvents(name.toCharArray());
        if (events.length > 0) {
            return events[0].getKeyCode();
        }
        return 0;
    }
}
