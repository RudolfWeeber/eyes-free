// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands;

import android.view.KeyEvent;

/**
 * A set of utility commands that result in some status being read out loud.
 * 
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public enum UtilityCommands implements Command {
    /**
     * Speak the current batter level.
     */
    BATTERY("Battery Level", KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_B),
    
    /**
     * Speak the current date and time.
     */
    TIME("Time and Date", KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_T),
    
    /**
     * Speak the current location.
     * TODO(clsimon): Implement the location command.
     */
//    LOCATION("Location", KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_L),
    
    /**
     * Speak the current connection status of each current connection, wifi, 3G, etc.
     */
    CONNECTIVITY("Connectivity", KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_O );
    
    private String displayName;
    private int defaultKeyCode;
    private int defaultModifier;
    private int keyCode;
    private int modifier;
    
    private UtilityCommands(String displayName, int defaultModifier, int defaultKeyCode) {
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
        return "com.google.android.marvin.commands.UTILITY_USER_EVENT_COMMAND";
    }
}