// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands;

/**
 * A default implementation of {@link Command}.
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public class CommandImpl implements Command {
    private final String commandName;
    private int modifierKey;
    private int keyCode;
    private final String action;
    
    public CommandImpl(String commandName, int modifierKey, int keyCode, String action) {
        this.commandName = commandName;
        this.modifierKey = modifierKey;
        this.keyCode = keyCode;
        this.action = action;
    }
    
    @Override
    public String toString() {
        return commandName + " " + modifierKey + " " + keyCode + " " + action;
    }

    @Override
    public String getAction() {
        return action;
    }
    
    @Override
    public String getDisplayName() {
        return commandName;
    }
    
    @Override
    public int getKeyCode() {
        return keyCode;
    }
    
    @Override
    public int getModifier() {
        return modifierKey;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((action == null) ? 0 : action.hashCode());
        result = prime * result + ((commandName == null) ? 0 : commandName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CommandImpl other = (CommandImpl) obj;
        if (action == null) {
            if (other.action != null)
                return false;
        } else if (!action.equals(other.action))
            return false;
        if (commandName == null) {
            if (other.commandName != null)
                return false;
        } else if (!commandName.equals(other.commandName))
            return false;
        return true;
    }

    @Override
    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    @Override
    public void setModifier(int modifierKey) {
        this.modifierKey = modifierKey;
    }
}
