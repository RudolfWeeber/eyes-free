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

package com.google.android.marvin.aime.usercommands;

import com.google.android.marvin.commands.Command;
import com.google.android.marvin.commands.CommandConstants;
import com.google.android.marvin.commands.CommandsManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.KeyEvent;

import java.util.LinkedList;
import java.util.List;

/**
 * @author clsimon@google.com (Cheryl Simon)
 * @author alanv@google.com (Alan Viverette)
 */
public class UserCommandHandler {
    private static final String TAG = "UserCommandHandler";

    private final Context mContext;
    private final CommandsManager mManager;

    /** Tracks the {@link KeyEvent}s for which we received a keyDown event. */
    private final LinkedList<KeyEvent> mCurrentKeyDownEvents;

    /** Contains the list of available keyboard shortcuts and actions. */
    private final LinkedList<Command> mAvailableCommands;

    /** Receives broadcast updates. */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context receiveContext, Intent intent) {
            String action = intent.getAction();

            if (CommandsManager.COMMAND_UPDATE_ACTION.equals(action)) {
                updateAvailableCommands();
            }
        }
    };

    /**
     * If greater than 0, means a command was handled in this key cycle, and we
     * need to ignore the next {@code mCommandHandled} key up events.
     */
    private int mCommandHandled = 0;

    /**
     * Set to {@code} true when the Menu key is down.
     */
    private boolean mModifierKeyDown = false;

    /**
     * Constructs a new user command handler using the specified parent context.
     *
     * @param context The parent context.
     */
    public UserCommandHandler(Context context) {
        mContext = context;
        mManager = new CommandsManager();
        mCurrentKeyDownEvents = new LinkedList<KeyEvent>();
        mAvailableCommands = new LinkedList<Command>();

        IntentFilter intentFilter = new IntentFilter(CommandsManager.COMMAND_UPDATE_ACTION);
        mContext.registerReceiver(mReceiver, intentFilter);

        updateAvailableCommands();
    }

    /**
     * Releases resources associated with this user command handler. You should
     * call this in in your activity's onDestroy() method.
     */
    public void release() {
        mContext.unregisterReceiver(mReceiver);
    }

    /**
     * Returns whether a key code is a valid modifier for user commands.
     *
     * @param keyCode The key code.
     * @return {@code true} if the key code is a valid modifier
     */
    private boolean isModifierKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_MENU:
                return true;
            default:
                return false;
        }
    }

    /**
     * Attempts to handle a key down event. Returns {@code true} if the event is
     * captured.
     *
     * @param ev The key event to handle.
     * @return {@code true} if the key is captured.
     */
    public boolean onKeyDown(KeyEvent ev) {
        if (isModifierKey(ev.getKeyCode())) {
            mModifierKeyDown = true;
        } else if (!mModifierKeyDown) {
            return false;
        }

        mCurrentKeyDownEvents.add(ev);

        // TODO Figure out a way to do this that doesn't involve looping through
        // evey single command every single time a key is pressed.
        for (Command command : mAvailableCommands) {
            if (matchesKeyEvents(command, mCurrentKeyDownEvents)) {
                Intent intent = new Intent(command.getAction());
                intent.putExtra(CommandConstants.EXTRA_COMMAND_NAME, command.getDisplayName());
                mContext.sendBroadcast(intent);
                mCommandHandled = mCurrentKeyDownEvents.size();
                return true;
            }
        }

        return false;
    }

    /**
     * Attempts to handle a key up event. Returns <code>true</code> if the event
     * is captured.
     *
     * @param ev The key event to handle.
     * @return {@code true} if the key is captured.
     */
    public boolean onKeyUp(KeyEvent ev) {
        if (isModifierKey(ev.getKeyCode())) {
            mModifierKeyDown = false;
        }

        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        mCurrentKeyDownEvents.clear();

        // If we handled a command, we want to ignore the key ups of all of the
        // events so we don't show a menu or search for something.
        if (mCommandHandled > 0) {
            mCommandHandled--;
            return true;
        }

        return false;
    }

    /**
     * Updates the list of available commands.
     */
    private void updateAvailableCommands() {
        Log.i(TAG, "Updating available commands...");

        mAvailableCommands.clear();
        mAvailableCommands.addAll(mManager.getAvailableCommands(mContext));
    }

    /**
     * @return {@code true} if the command matches the provided {@code event}.
     */
    private boolean matchesKeyEvents(Command command, List<KeyEvent> events) {
        if (events.size() < 2) {
            return false;
        }
        // look for 2 events, assume first event is modifier, last event is
        // second key.
        KeyEvent modifierEvent = events.get(0);
        if (modifierEvent.getKeyCode() != command.getModifier()) {
            return false;
        }
        KeyEvent keyEvent = events.get(events.size() - 1);
        if (keyEvent.getKeyCode() != command.getKeyCode()) {
            return false;
        }
        return true;
    }

}
