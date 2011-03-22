/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.google.android.marvin.aime;

import com.google.android.marvin.aime.usercommands.UserCommandHandler;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.Locale;

/**
 * Accessible InputMethodService. Provides handle to
 * {@link AccessibleInputConnection}, for powerful navigation capabilities. It
 * also fires accessibility events. Extend this class to make your IME
 * accessible. It overrides default behaviour of trackball and dpad for
 * improving accessibility.<br>
 * <br>
 * Call {@link #setGranularity(int)} and {@link #setAction(int)}, everytime
 * current granularity or action of IME changes, to reflect change in trackball
 * and dpad behavior.<br>
 * Default <code>granularity</code> is {@link TextNavigation#GRANULARITY_CHAR}
 * and default <code>action</code> is {@link TextNavigation#ACTION_MOVE} for
 * trackball and dpad motion.
 *
 * @author hiteshk@google.com (Hitesh Khandelwal)
 * @author alanv@google.com (Alan Viverette)
 */
public abstract class AccessibleInputMethodService extends InputMethodService {
    /** List of characters ignored by word iterator. */
    private final char[] ignoredCharForWords = { ' ' };

    /** String to speak when granularity changes. */
    private String mGranularitySet;

    /** Strings used to describe granularity changes. */
    private String[] mGranularityTypes;

    /** String to speak when action changes. */
    private String mActionSet;

    /** Strings used to describe action changes. */
    private String[] mActionTypes;

    /** Current granularity (unit type). */
    private int mGranularity = TextNavigation.GRANULARITY_CHAR;

    /** Current action set. */
    private int mAction = TextNavigation.ACTION_MOVE;

    /** Handle to AccessibleInputConnection. */
    private AccessibleInputConnection mAIC = null;

    /** Handle to base InputConnection. */
    private InputConnection mIC = null;

    /** Stored key down event. */
    private KeyEvent mPreviousDownEvent;

    /** Whether accessibility is enabled. */
    private boolean mAccessibilityEnabled;

    private UserCommandHandler mUserCommandHandler;
    private AccessibilityManager mAccessibilityManager;

    @Override
    public void onCreate() {
        super.onCreate();

        final Resources res = getResources();
        mGranularityTypes = res.getStringArray(R.array.granularity_types);
        mGranularitySet = res.getString(R.string.set_granularity);
        mActionTypes = res.getStringArray(R.array.action_types);
        mActionSet = res.getString(R.string.set_action);

        mPreviousDownEvent = null;

        mUserCommandHandler = new UserCommandHandler(this);
        mAccessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);

        // Register content observer to receive accessibility status changes.
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false,
                mAccessibilityObserver);

        updateAccessibilityState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mUserCommandHandler.release();
    }

    /**
     * Returns an {@link AccessibleInputConnection} bound to the current
     * {@link InputConnection}.
     *
     * @return an instance of AccessibleInputConnection
     */
    @Override
    public AccessibleInputConnection getCurrentInputConnection() {
        InputConnection currentIC = super.getCurrentInputConnection();

        if (currentIC == null) {
            mAIC = null;
            return null;
        }

        if (mAIC == null || (mIC != null && mIC != currentIC)) {
            mIC = currentIC;
            mAIC = new AccessibleInputConnection(this, mIC, true, ignoredCharForWords);
        }

        return mAIC;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        // Interrupt ongoing accessibility events when we start a new view.
        if (mAccessibilityManager.isEnabled()) {
            mAccessibilityManager.interrupt();
        }
    }

    /**
     * Overrides default trackball behavior:
     * <ul>
     * <li>Up/down: Increases/decreases text navigation granularity</li>
     * <li>Left/right: Moves to previous/next unit of text</li>
     * </ul>
     * <br>
     * Moving the trackball far to the left or right results in moving by
     * multiple units.
     * <p>
     * If one of the following conditions is met, default behavior is preserved:
     * <ul>
     * <li>No input connection available</li>
     * <li>Input view is hidden</li>
     * <li>Not currently editing text</li>
     * <li>Cannot move in the specified direction</li>
     * </ul>
     * </p>
     */
    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        AccessibleInputConnection aic = getCurrentInputConnection();
        if (aic == null || !isInputViewShown()) {
            return super.onTrackballEvent(event);
        }

        float x = event.getX();
        float absX = Math.abs(event.getX());
        float y = event.getY();
        float absY = Math.abs(event.getY());

        if (absY > 2 * absX && absY >= 0.75) {
            // Up and down switch granularities, but this is less common so it's
            // less sensitive.

            if (y < 0) {
                adjustGranularity(1);
            } else {
                adjustGranularity(-1);
            }
        } else {
            // If they moved the trackball really far, move by more than one but
            // only announce for the last move.

            int count = Math.max(1, (int) Math.floor(absX + 0.25));
            boolean isNext = (x > 0);
            boolean isShiftPressed = (event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0;

            moveUnit(count, isNext, isShiftPressed);
        }

        return true;
    }

    /**
     * Overrides default directional pad behavior:
     * <ul>
     * <li>Up/down: Increases/decreases text navigation granularity</li>
     * <li>Left/right: Moves to previous/next unit of text</li>
     * </ul>
     * <p>
     * If one of the following conditions is met, default behavior is preserved:
     * <ul>
     * <li>No input connection available</li>
     * <li>Input view is hidden</li>
     * <li>Not currently editing text</li>
     * <li>Cannot move in the specified direction</li>
     * </ul>
     * </p>
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mUserCommandHandler.onKeyUp(event)) {
            return true;
        }

        AccessibleInputConnection aic = getCurrentInputConnection();
        if (aic == null || !aic.hasExtractedText()) {
            return super.onKeyUp(keyCode, event);
        }

        KeyEvent downEvent = mPreviousDownEvent;
        mPreviousDownEvent = null;

        boolean captureEvent = false;

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                captureEvent = previousUnit(1, event.isShiftPressed());
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                captureEvent = nextUnit(1, event.isShiftPressed());
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (event.isAltPressed()) {
                    adjustGranularity(1);
                    captureEvent = true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.isAltPressed()) {
                    adjustGranularity(-1);
                    captureEvent = true;
                }
                break;
        }

        if (captureEvent) {
            return true;
        }

        // If we didn't capture the event, attempt to send the previous down
        // event and then preserve default behavior.
        if (downEvent != null && !super.onKeyDown(downEvent.getKeyCode(), downEvent)) {
            aic.sendKeyEvent(downEvent);
        }

        return super.onKeyUp(keyCode, event);
    }

    /**
     * Captures and stores directional pad events. If onKeyUp() preserves
     * default behavior, the original down event will be released.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mUserCommandHandler.onKeyDown(event)) {
            return true;
        }

        AccessibleInputConnection aic = getCurrentInputConnection();
        if (aic == null || !aic.hasExtractedText()) {
            return super.onKeyDown(keyCode, event);
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mPreviousDownEvent = event;
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    /**
     * Moves forward <code>count</code> units using the current granularity and
     * action. Returns <code>true</code> if successful. Moving can fail if the
     * carat is already at the end of the text or if there is no available input
     * connection.
     *
     * @param count The number of units to move.
     * @param isShiftPressed <code>true</code> if the shift key is pressed.
     * @return <code>true</code> if successful.
     * @see AccessibleInputMethodService#setGranularity(int)
     * @see AccessibleInputMethodService#setAction(int)
     */
    protected boolean nextUnit(int count, boolean isShiftPressed) {
        return moveUnit(count, true, isShiftPressed);
    }

    /**
     * Moves backward <code>count</code> units using the current granularity and
     * action. Returns <code>true</code> if successful. Moving can fail if the
     * carat is already at the beginning of the text or if there is no available
     * input connection.
     *
     * @param count The number of units to move.
     * @param isShiftPressed <code>true</code> if the shift key is pressed.
     * @return <code>true</code> if successful.
     * @see AccessibleInputMethodService#setGranularity(int)
     * @see AccessibleInputMethodService#setAction(int)
     */
    protected boolean previousUnit(int count, boolean isShiftPressed) {
        return moveUnit(count, false, isShiftPressed);
    }

    /**
     * Moves <code>count</code> units in the specified direction using the
     * current granularity and action.
     *
     * @param count The number of units to move.
     * @param forward <code>true</code> to move <code>count</code> units
     *            forward, <code>false</code> to move backward.
     * @param isShiftPressed <code>true</code> if the shift key is pressed.
     * @return <code>true</code> if successful or <code>false</code> if no input
     *         connection was available or the movement failed
     */
    private boolean moveUnit(int count, boolean forward, boolean isShiftPressed) {
        // If the input connection is null or count is 0, no-op.
        AccessibleInputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null || !inputConnection.hasExtractedText()) {
            return false;
        } else if (count == 0) {
            return true;
        }

        // If the shift key is held down, force ACTION_EXTEND mode.
        int action = (isShiftPressed ? TextNavigation.ACTION_EXTEND : mAction);

        // Disable sending accessibility events while we send multiple events,
        // then announce only the last event.
        boolean savedSendAccessibilityEvents = inputConnection.isSendAccessibilityEvents();
        inputConnection.setSendAccessibilityEvents(false);
        for (int i = 0; i < count - 1; i++) {
            if (forward) {
                inputConnection.next(mGranularity, action);
            } else {
                inputConnection.previous(mGranularity, action);
            }
        }
        inputConnection.setSendAccessibilityEvents(savedSendAccessibilityEvents);

        // Obtain the new position from the final event. If the position is
        // null, we failed to move and should return false.
        Position newPosition = null;
        if (forward) {
            newPosition = inputConnection.next(mGranularity, action);
        } else {
            newPosition = inputConnection.previous(mGranularity, action);
        }
        return (newPosition != null);
    }

    /**
     * Adjusts granularity up or down. Returns <code>true</code> if granularity
     * is set to a different value. Wraps around if granularity is already at
     * the minimum or maximum setting.
     *
     * @param direction The direction in which to change granularity.
     * @return <code>true</code> if granularity is set to a different value.
     * @see TextNavigation#NUM_GRANULARITY_TYPES
     */
    protected boolean adjustGranularity(int direction) {
        int oldGranularity = getGranularity();
        int granularity = oldGranularity + direction;

        if (granularity < 0) {
            granularity += TextNavigation.NUM_GRANULARITY_TYPES;
        } else if (granularity >= TextNavigation.NUM_GRANULARITY_TYPES) {
            granularity -= TextNavigation.NUM_GRANULARITY_TYPES;
        }

        setGranularity(granularity);

        return (oldGranularity != granularity);
    }

    /**
     * Sets granularity (unit type) for text navigation.
     *
     * @param granularity Value could be
     *            {@link TextNavigation#GRANULARITY_CHAR},
     *            {@link TextNavigation#GRANULARITY_WORD},
     *            {@link TextNavigation#GRANULARITY_SENTENCE},
     *            {@link TextNavigation#GRANULARITY_PARAGRAPH} or
     *            {@link TextNavigation#GRANULARITY_ENTIRE_TEXT}
     * @return <code>true</code> if granularity changed
     */
    public boolean setGranularity(int granularity) {
        AccessibleInputConnection.checkValidGranularity(granularity);
        String speak = String.format(mGranularitySet, mGranularityTypes[granularity]);
        getCurrentInputConnection().trySendAccessiblityEvent(speak);

        if (mGranularity == granularity) {
            return false;
        }

        mGranularity = granularity;
        onGranularityChanged(granularity);

        return true;
    }

    /**
     * Returns the current granularity.
     *
     * @return the current granularity
     * @see AccessibleInputMethodService#setGranularity(int)
     */
    public int getGranularity() {
        return mGranularity;
    }

    /**
     * Callback for change in granularity. Override this method to update any
     * internal state or GUI of IME.
     *
     * @param granularity The type of granularity.
     * @see TextNavigation#GRANULARITY_CHAR
     * @see TextNavigation#GRANULARITY_WORD
     * @see TextNavigation#GRANULARITY_SENTENCE
     * @see TextNavigation#GRANULARITY_PARAGRAPH
     * @see TextNavigation#GRANULARITY_ENTIRE_TEXT
     */
    public void onGranularityChanged(int granularity) {
    }

    /**
     * Sets action to be performed on the current selection.
     *
     * @param action Value could be either {@link TextNavigation#ACTION_MOVE} or
     *            {@link TextNavigation#ACTION_EXTEND}.
     */
    public boolean setAction(int action) {
        AccessibleInputConnection.checkValidAction(action);

        if (mAction == action) {
            return false;
        }

        mAction = action;
        String speak = String.format(mActionSet, mActionTypes[action]);
        getCurrentInputConnection().trySendAccessiblityEvent(speak);
        onActionChanged(action);
        return true;
    }

    /**
     * Returns the current action.
     *
     * @return the current action
     * @see AccessibleInputMethodService#setAction(int)
     */
    public int getAction() {
        return mAction;
    }

    /**
     * Callback for change in action. Override this method to update any
     * internal state or GUI of IME.
     *
     * @param action The type of action.
     * @see TextNavigation#ACTION_MOVE
     * @see TextNavigation#ACTION_EXTEND
     */
    public void onActionChanged(int action) {
    }

    /**
     * Returns whether accessibility is enabled.
     *
     * @return whether accessibility is enabled
     */
    protected boolean isAccessibilityEnabled() {
        return mAccessibilityEnabled;
    }

    /**
     * Updates the current accessibility enabled state.
     */
    private void updateAccessibilityState() {
        mAccessibilityEnabled = (Settings.Secure.getInt(
                getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1);

        // Reset text navigation when accessibility is disabled.
        if (!mAccessibilityEnabled) {
            mAction = TextNavigation.ACTION_MOVE;
            mGranularity = TextNavigation.GRANULARITY_CHAR;
        }
    }

    /**
     * Callback for change in accessibility enabled state.
     *
     * @param accessibilityEnabled
     */
    protected void onAccessibilityChanged(boolean accessibilityEnabled) {
    }

    /**
     * This handler is used by the {@link ContentObserver} below.
     */
    private final Handler mHandler = new Handler();

    /**
     * This observer listens for changes in the accessibility enabled state.
     */
    private final ContentObserver mAccessibilityObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            if (selfChange) {
                return;
            }

            updateAccessibilityState();

            // Force a configuration change.
            Configuration newConfig = new Configuration();
            newConfig.setToDefaults();
            newConfig.locale = Locale.getDefault();
            Settings.System.getConfiguration(getContentResolver(), newConfig);
            onConfigurationChanged(newConfig);

            onAccessibilityChanged(mAccessibilityEnabled);
        }
    };
}
