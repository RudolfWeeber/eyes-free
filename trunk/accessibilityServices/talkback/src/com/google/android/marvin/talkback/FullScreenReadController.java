/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.marvin.talkback;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.SpeechController.UtteranceCompleteRunnable;
import com.google.android.marvin.talkback.TalkBackService.AccessibilityEventListener;
import com.googlecode.eyesfree.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.googlecode.eyesfree.utils.AccessibilityEventUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.NodeFocusFinder;
import com.googlecode.eyesfree.utils.WebInterfaceUtils;

// TODO(caseyburkhardt): Handle changing window content.  Look at hierarchy cache invalidation.
/**
 * Component used to control reading of the entire display.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class FullScreenReadController implements AccessibilityEventListener {
    /** The minimum API level supported by the cursor controller. */
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN;

    /** Tag used for log output and wake lock */
    public static final String TAG = FullScreenReadController.class.getSimpleName();

    /** The possible states of the controller. */
    public enum AutomaticReadingState {
        STOPPED, READING_FROM_BEGINNING, READING_FROM_NEXT, ENTERING_WEB_CONTENT
    }

    /** Event types that should interrupt continuous reading, if active. */
    private static final int MASK_EVENT_TYPES_INTERRUPT_CONTINUOUS =
            AccessibilityEvent.TYPE_VIEW_CLICKED |
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED |
            AccessibilityEvent.TYPE_VIEW_SELECTED |
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED |
            AccessibilityEventCompat.TYPE_ANNOUNCEMENT |
            AccessibilityEventCompat.TYPE_GESTURE_DETECTION_START |
            AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_START |
            AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START |
            AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER |
            AccessibilityEventCompat.TYPE_VIEW_TEXT_SELECTION_CHANGED;

    /**
     * The current state of the controller. Should only be updated through
     * {@link FullScreenReadController#setReadingState(AutomaticReadingState)}
     */
    private AutomaticReadingState mCurrentState;

    /** The parent service */
    private TalkBackService mService;

    /** TalkBack's TTS wrapper */
    private SpeechController mSpeechController;

    /** Controller for linearly navigating the view hierarchy tree */
    private CursorController mCursorController;

    /** Controller for providing user feedback */
    private MappedFeedbackController mFeedbackController;

    /** Wake lock for keeping the device unlocked while reading */
    private WakeLock mWakeLock;

    @SuppressWarnings("deprecation")
    public FullScreenReadController(TalkBackService service) {
        mService = service;
        mSpeechController = service.getSpeechController();
        mCursorController = service.getCursorController();
        mFeedbackController = MappedFeedbackController.getInstance();

        final PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);

        setReadingState(AutomaticReadingState.STOPPED);
    }

    /**
     * Releases all resources held by this controller and save any persistent
     * preferences.
     */
    public void shutdown() {
        interrupt();
    }

    /**
     * Attempts to start reading, optionally starting from the beginning of the
     * screen.
     */
    public boolean startReading(boolean fromBeginning) {
        if (isActive()) {
            return false;
        }

        // TODO(caseyburkhardt): Attempt to unify these methods.
        final boolean result;
        if (fromBeginning) {
            result = startReadingFromBeginning();
        } else {
            result = startReadingFromNextNode();
        }

        return result;
    }

    /**
     * Starts linearly reading from the node with accessibility focus.
     */
    private boolean startReadingFromNextNode() {
        final AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
        if (currentNode == null) {
            return false;
        }

        setReadingState(AutomaticReadingState.READING_FROM_NEXT);

        mCursorController.setGranularity(CursorGranularity.DEFAULT, false /* fromUser */);

        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }

        // Avoid reading the elements in web content twice by calling directly
        // into ChromeVox rather than advancing CursorController first.
        if (WebInterfaceUtils.hasWebContent(currentNode)) {
            moveIntoWebContent();
        } else {
            moveForward();
        }

        currentNode.recycle();
        return true;
    }

    /**
     * Starts linearly reading from the top of the view hierarchy.
     */
    private boolean startReadingFromBeginning() {
        AccessibilityNodeInfoCompat rootNode = null;
        AccessibilityNodeInfoCompat currentNode = null;

        try {
            rootNode = AccessibilityServiceCompatUtils.getRootInActiveWindow(mService);
            if (rootNode == null) {
                return false;
            }

            currentNode = AccessibilityNodeInfoUtils.searchFromInOrderTraversal(mService, rootNode,
                    AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS, NodeFocusFinder.SEARCH_FORWARD);
            if (currentNode == null) {
                return false;
            }

            setReadingState(AutomaticReadingState.READING_FROM_BEGINNING);

            mCursorController.setGranularity(CursorGranularity.DEFAULT, false /* fromUser */);

            if (!mWakeLock.isHeld()) {
                mWakeLock.acquire();
            }

            mCursorController.clearCursor();
            mCursorController.setCursor(currentNode); // Will automatically move forward.

            if (WebInterfaceUtils.hasWebContent(currentNode)) {
                // This works only because a focused WebView has a rule to generate
                // some default text and passes the isEmpty check above.
                moveIntoWebContent();
            }

            return true;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(rootNode, currentNode);
        }
    }

    /**
     * Speaks the currently focused node or the currently selected HTML element.
     */
    public boolean speakCurrentNode() {
        AccessibilityNodeInfoCompat currentNode = null;

        try {
            currentNode = mCursorController.getCursor();
            if (currentNode == null) {
                return false;
            }

            if (WebInterfaceUtils.hasWebContent(currentNode)) {
                return WebInterfaceUtils.performSpecialAction(
                        currentNode, WebInterfaceUtils.ACTION_READ_CURRENT_HTML_ELEMENT);
            }

            return mCursorController.refocus();
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(currentNode);
        }
    }

    /**
     * Stops speech output and view traversal at the current position.
     */
    public void interrupt() {
        setReadingState(AutomaticReadingState.STOPPED);

        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    private void moveForward() {
        if (!mCursorController.next(false /* shouldWrap */, true /* shouldScroll */)) {
            mFeedbackController.playAuditory(R.id.sounds_complete, 1.3f, 1, 0);
            interrupt();
        }

        if (currentNodeHasWebContent()) {
            moveIntoWebContent();
        }
    }

    private void moveIntoWebContent() {
        final AccessibilityNodeInfoCompat webNode = mCursorController.getCursor();
        if (webNode == null) {
            // Reset state.
            interrupt();
            return;
        }

        if (mCurrentState == AutomaticReadingState.READING_FROM_BEGINNING) {
            // Reset ChromeVox's active indicator to the start to the page.
            WebInterfaceUtils.performNavigationAtGranularityAction(webNode,
                    WebInterfaceUtils.DIRECTION_BACKWARD,
                    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);
        }

        WebInterfaceUtils.performNavigationAtGranularityAction(webNode,
                WebInterfaceUtils.DIRECTION_FORWARD,
                AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);

        setReadingState(AutomaticReadingState.ENTERING_WEB_CONTENT);

        webNode.recycle();
    }

    public void setReadingState(AutomaticReadingState newState) {
        LogUtils.log(TAG, Log.VERBOSE, "Continuous reading switching to mode: %s", newState.name());

        mCurrentState = newState;
        mSpeechController.setShouldInjectAutoReadingCallbacks(isActive(), mNodeSpokenRunnable);
    }

    public AutomaticReadingState getReadingState() {
        return mCurrentState;
    }

    /**
     * Returns whether full-screen reading is currently active. Equivalent to
     * calling {@code getReadingState() != AutomaticReadingState#STOPPED}.
     *
     * @return Whether full-screen reading is currently active.
     */
    public boolean isActive() {
        return (getReadingState() != AutomaticReadingState.STOPPED);
    }

    private boolean currentNodeHasWebContent() {
        final AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
        if (currentNode == null) {
            return false;
        }

        final boolean isWebContent = WebInterfaceUtils.hasWebContent(currentNode);
        currentNode.recycle();
        return isWebContent;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isActive()) {
            return;
        }

        // Only interrupt full screen reading on events that can't be generated
        // by automated cursor movement or from delayed user interaction.
        if (AccessibilityEventUtils.eventMatchesAnyType(
                event, MASK_EVENT_TYPES_INTERRUPT_CONTINUOUS)) {
            interrupt();
        }
    }

    /** Runnable executed when a node has finished being spoken */
    private final UtteranceCompleteRunnable mNodeSpokenRunnable = new UtteranceCompleteRunnable() {
        @Override
        public void run(int status) {
            if ((mCurrentState != AutomaticReadingState.STOPPED)
                    && (mCurrentState != AutomaticReadingState.ENTERING_WEB_CONTENT)) {
                moveForward();
            }
        }
    };
}
