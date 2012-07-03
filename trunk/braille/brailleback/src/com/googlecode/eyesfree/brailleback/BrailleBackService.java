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

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.braille.translate.BrailleTranslator;
import com.googlecode.eyesfree.braille.translate.TranslatorManager;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * An accessibility service that provides feedback through a braille
 * display.
 */
public class BrailleBackService
        extends AccessibilityService
        implements Display.OnConnectionStateChangeListener,
        DisplayManager.OnMappedInputEventListener,
        DisplayManager.OnPanOverflowListener {

    /** Start the service, initializing a few components. */
    private static final int WHAT_START = 2;

    /** Shut down the service. */
    private static final int WHAT_SHUTDOWN = 3;

    // Braille dot bit pattern constants.
    public static final int DOT1 = 0x01;
    public static final int DOT2 = 0x02;
    public static final int DOT3 = 0x04;
    public static final int DOT4 = 0x08;
    public static final int DOT5 = 0x10;
    public static final int DOT6 = 0x20;
    public static final int DOT7 = 0x40;
    public static final int DOT8 = 0x80;

    private static BrailleBackService sInstance;

    /*package*/ FeedbackManager mFeedbackManager;

    /** Braille display manager. */
    /*package*/ DisplayManager mDisplayManager;

    private TranslatorManager mTranslatorManager;
    /** Used to translate text into braille. */
    /*package*/ BrailleTranslator mTranslator;

    /** Set if the infrastructure is initialized. */
    private boolean isInfrastructureInitialized;

    private ModeSwitcher mModeSwitcher;

    /**
     * Dot combination for switching navigation mode.
     * At this time, this is only used for debugging.
     */
    private static final int SWITCH_NAVIGATION_MODE_DOTS = DOT7 | DOT8;

    /** {@link Handler} for executing messages on the service main thread. */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case WHAT_START:
                    initializeDependencies();
                    return;
                case WHAT_SHUTDOWN:
                    shutdownDependencies();
                    return;
            }
        }
    };

    @Override
    public void onConnectionStateChanged(int state) {
        if (state == Display.STATE_NOT_CONNECTED) {
            mFeedbackManager.emitFeedback(
                FeedbackManager.TYPE_DISPLAY_DISCONNECTED);
        } else if (state == Display.STATE_CONNECTED) {
            mFeedbackManager.emitFeedback(
                FeedbackManager.TYPE_DISPLAY_CONNECTED);
        }
    }

    @Override
    public void onMappedInputEvent(BrailleInputEvent event,
            DisplayManager.Content content) {
        if (mModeSwitcher == null) {
            return;
        }
        // Global commands can't be overriden.
        if (handleGlobalCommands(event)) {
            return;
        }
        if (BuildConfig.DEBUG
                && event.getCommand() == BrailleInputEvent.CMD_BRAILLE_KEY
                && event.getArgument() == SWITCH_NAVIGATION_MODE_DOTS) {
            mModeSwitcher.switchMode();
            return;
        }
        if (!mModeSwitcher.onMappedInputEvent(event, content)) {
            mFeedbackManager.emitFeedback(FeedbackManager.TYPE_UNKNOWN_COMMAND);
        }
    }

    private boolean handleGlobalCommands(BrailleInputEvent event) {
        boolean success;
        switch (event.getCommand()) {
            case BrailleInputEvent.CMD_GLOBAL_HOME:
                success = performGlobalAction(GLOBAL_ACTION_HOME);
                break;
            case BrailleInputEvent.CMD_GLOBAL_BACK:
                success = performGlobalAction(GLOBAL_ACTION_BACK);
                break;
            case BrailleInputEvent.CMD_GLOBAL_RECENTS:
                success = performGlobalAction(GLOBAL_ACTION_RECENTS);
                break;
            case BrailleInputEvent.CMD_GLOBAL_NOTIFICATIONS:
                success = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
                break;
            case BrailleInputEvent.CMD_HELP:
                success = runHelp();
                break;
            default:
                return false;
        }
        if (!success) {
            mFeedbackManager.emitFeedback(FeedbackManager.TYPE_COMMAND_FAILED);
        }
        // Don't fall through even if the command failed, we own these
        // commands.
        return true;
    }

    @Override
    public void onPanLeftOverflow(DisplayManager.Content content) {
        if (mModeSwitcher != null
                && !mModeSwitcher.onPanLeftOverflow(content)) {
            mFeedbackManager.emitFeedback(
                FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
        }
    }

    @Override
    public void onPanRightOverflow(DisplayManager.Content content) {
        if (mModeSwitcher != null
                && !mModeSwitcher.onPanRightOverflow(content)) {
            mFeedbackManager.emitFeedback(
                FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
        }
    }

    @Override
    public void onServiceConnected() {
        sInstance = this;
        // TODO: Add setting like in talkback.
        if (BuildConfig.DEBUG) {
            LogUtils.setLogLevel(Log.VERBOSE);
        } else {
            LogUtils.setLogLevel(Log.WARN);
        }
        if (isInfrastructureInitialized) {
            return;
        }

        mHandler.sendEmptyMessage(WHAT_START);
        // We are in an initialized state now.
        isInfrastructureInitialized = true;
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
        if (mDisplayManager != null) {
            mDisplayManager.setContent(
                new DisplayManager.Content(getString(R.string.shutting_down)));
            mHandler.sendEmptyMessage(WHAT_SHUTDOWN);

            // We are not in an initialized state anymore.
            isInfrastructureInitialized = false;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        LogUtils.log(this, Log.VERBOSE, "Event: %s", event.toString());
        LogUtils.log(this, Log.VERBOSE, "Node: %s", event.getSource());
        // Since the IME is only temporarily overriding the current
        // navigation mode, it won't get this call from the mode switcher.
        // Invoke it here instead so it can keep track of focus.
        BrailleIME ime = BrailleIME.getActiveInstance();
        if (ime != null) {
            ime.onObserveAccessibilityEvent(event);
        }
        if (mModeSwitcher != null) {
            mModeSwitcher.onAccessibilityEvent(event);
        }
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt.
    }

    public void imeOpened(BrailleIME ime) {
        if (mModeSwitcher != null) {
            mModeSwitcher.overrideMode(ime);
        }
    }

    public void imeClosed() {
        if (mModeSwitcher != null) {
            mModeSwitcher.overrideMode(null);
        }
    }

    private void initializeDependencies() {
        mFeedbackManager = new FeedbackManager(this);
        mTranslatorManager = new TranslatorManager(
            this, new TranslatorManager.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if (status != TranslatorManager.SUCCESS) {
                            LogUtils.log(this, Log.ERROR,
                                    "Couldn't initialize braille translator");
                            return;
                        }
                        initializeDisplayManager();
                        initializeModeSwitcher();
                    }
                });
    }

    private void initializeDisplayManager() {
        // TODO: Allow a user to override this with a setting.
        mTranslator = mTranslatorManager.getTranslator(
            getString(R.string.brailletable_8dot_default));
        mDisplayManager = new DisplayManager(mTranslator,
                this /*context*/,
                this /*panOverflowListener*/,
                this /*connectionStateChangeListener*/,
                this /*inputEventListener*/);
        mDisplayManager.setContent(
            new DisplayManager.Content(getString(R.string.display_connected)));
    }

    private void initializeModeSwitcher() {
        mModeSwitcher = new ModeSwitcher(
            new DefaultNavigationMode(mDisplayManager, this, mFeedbackManager),
            new TreeDebugNavigationMode(
                mDisplayManager,
                mFeedbackManager,
                this));
    }

    private void shutdownDependencies() {
        if (mDisplayManager != null) {
            mDisplayManager.shutdown();
            mDisplayManager = null;
        }
        if (mTranslatorManager != null) {
            mTranslatorManager.destroy();
            mTranslatorManager = null;
        }
        // TODO: Shut down feedback manager and braille translator
        // when those classes have shutdown methods.
    }

    public boolean runHelp() {
        Intent intent = new Intent(this, KeyBindingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return true;
    }

    public static BrailleBackService getActiveInstance() {
        return sInstance;
    }
}
