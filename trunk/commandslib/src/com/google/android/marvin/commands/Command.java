// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands;

/**
 * An interface that should be implemented by any user commands.  A user command is an event
 * sent to a {@link android.content.BroadcastReceiver} that requests some action be taken.  
 * 
 * Keyboards can choose to invoke the commands using the provided keyboard shortcut, consisting
 * of a modifier key and additional key.
 * 
 * To invoke the command, send a broadcast with the provided action, via {@code getAction()}, 
 * with an extra named {@code CommandConstants.EXTRA_EVENT_TYPE} whose contents are 
 * {@code getDisplayName()}.
 * 
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public interface Command {
    /**
     * @return The key code of the second key to be pressed to execute the command.
     */
    int getKeyCode();
    
    /**
     * Set the key code of the second key to be pressed to execute the command.
     */
    void setKeyCode(int keyCode);
    
    /**
     * @return The key code of the modifier key involved in the command execution.  Possible options
     * are search and menu.
     */
    int getModifier();
    
    /**
     * Set the key code of the modifier key involved in the command execution.
     */
    void setModifier(int keyCode);
    
    /**
     * @return  The name of the command that will be displayed to the user in settings, and
     * will be used as the contents of the extra when invoking the command.
     */
    String getDisplayName();
    
    /**
     * @return The action to direct the broadcast at to invoke the command.
     */
    String getAction();
}
