// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.marvin.talkback.commands;

import com.google.android.marvin.commands.Command;

import android.view.KeyEvent;

/**
 * A set of user commands that will be executed by TalkBack.  Only commands that require 
 * information private to TalkBack should be implemented here.
 * 
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public enum TalkBackCommands implements Command {
    /**
     * Repeat the last spoken utterance.
     */
    REPEAT_LAST("Repeat Last Utterance", KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_R),
    
    /**
     * Spell the last spoken utterance.
     */
    SPELL_LAST("Spell Last Utterance", KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_S);
    
    private String displayName;
    private int defaultKeyCode;
    private int defaultModifier;
    private int keyCode;
    private int modifier;
    
    private TalkBackCommands(String displayName, int defaultModifier, int defaultKeyCode) {
        this.displayName = displayName;
        this.defaultKeyCode = defaultKeyCode;
        this.keyCode = defaultKeyCode;
        this.defaultModifier = defaultModifier;
        this.modifier = defaultModifier;
    }
    
    public void resetCommand() {
        keyCode = defaultKeyCode;
        modifier = defaultModifier;
    }

    /**
     * @return the keyCode
     */
    public int getKeyCode() {
        return keyCode;
    }

    /**
     * @param keyCode the keyCode to set
     */
    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    /**
     * @return the modifier
     */
    public int getModifier() {
        return modifier;
    }

    /**
     * @param modifier the modifier to set
     */
    public void setModifier(int modifier) {
        this.modifier = modifier;
    }

    /**
     * @return the displayName
     */
    public String getDisplayName() {
        return displayName;
    }
    
    public String getAction() {
        return "com.google.android.marvin.commands.TALKBACK_USER_EVENT_COMMAND";
    }
}
