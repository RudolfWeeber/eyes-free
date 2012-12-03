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
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.android.marvin.talkback.CursorGranularityManager.CursorGranularity;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.NodeFocusFinder;
import com.googlecode.eyesfree.utils.WebInterfaceUtils;

import java.util.HashSet;

// TODO(caseyburkhardt): Handle changing window content.  Look at hierarchy cache invalidation.
/**
 * Component used to control reading of the entire display.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
@TargetApi(16)
public class FullScreenReadController {

    /**
     * The possible states of the controller.
     *
     * @author caseyburkhardt@google.com (Casey Burkhardt)
     */
    public enum AutomaticReadingState {
        STOPPED, READING_FROM_BEGINNING, READING_FROM_NEXT, ENTERING_WEB_CONTENT
    }

    /** The minimum API level supported by the cursor controller. */
    public static final int MIN_API_LEVEL = 16;

    /** Tag used for log output and wake lock */
    public static final String TAG = "FullScreenReadController";

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
    private PreferenceFeedbackController mFeedbackController;

    /** Wake lock for keeping the device unlocked while reading */
    private WakeLock mWakeLock;

    /** Runnable executed when a node has finished being spoken */
    private Runnable mNodeSpokenRunnable;

    @SuppressWarnings("deprecation")
    public FullScreenReadController(TalkBackService service) {
        mService = service;
        mSpeechController = service.getSpeechController();
        mCursorController = service.getCursorController();
        mFeedbackController = service.getFeedbackController();
        final PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);
        setReadingState(AutomaticReadingState.STOPPED);
        mNodeSpokenRunnable = new Runnable() {
            @Override
            public void run() {
                if (mCurrentState != AutomaticReadingState.STOPPED
                        && mCurrentState != AutomaticReadingState.ENTERING_WEB_CONTENT) {
                    moveForward();
                }
            }
        };
    }

    /**
     * Releases all resources held by this controller and save any persistent
     * preferences.
     */
    public void shutdown() {
        interrupt();
    }

    /**
     * Starts linearly reading from the node with accessibility focus.
     */
    public boolean startReadingFromNextNode() {
        AccessibilityNodeInfoCompat startNode = mCursorController.getCursor();
        if (startNode == null) {
            return false;
        }
        setReadingState(AutomaticReadingState.READING_FROM_NEXT);
        mCursorController.setGranularity(CursorGranularity.DEFAULT);
        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
        moveForward();
        return true;
    }

    /**
     * Speaks the currently focused node or the currently selected HTML element.
     */
    public boolean speakCurrentNode() {
        final AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
        if (currentNode != null) {
            if (currentNodeHasWebContent()) {
                return WebInterfaceUtils.performSpecialAction(
                        currentNode, WebInterfaceUtils.ACTION_READ_CURRENT_HTML_ELEMENT);
            } else {
                mCursorController.clearCursor();
                return mCursorController.setCursor(currentNode);
            }
        }
        return false;
    }

    /**
     * Starts linearly reading from the top of the view hierarchy.
     */
    public boolean startReadingFromBeginning() {
        final AccessibilityNodeInfo rootNode = mService.getRootInActiveWindow();
        if (rootNode == null) {
            return false;
        }

        final AccessibilityNodeInfoCompat compatRoot = new AccessibilityNodeInfoCompat(rootNode);
        AccessibilityNodeInfoCompat currentNode = NodeFocusFinder.focusSearch(
                compatRoot, NodeFocusFinder.SEARCH_FORWARD);

        final HashSet<AccessibilityNodeInfoCompat> seenNodes =
                new HashSet<AccessibilityNodeInfoCompat>();

        // TODO(caseyburkhardt): Refactor to use CursorController to find the
        // starting node by clearing focus and advancing.
        while (currentNode != null) {
            if (seenNodes.contains(currentNode)) {
                return false;
            }
            if (AccessibilityNodeInfoUtils.shouldFocusNode(mService, currentNode)) {
                break;
            }
            seenNodes.add(currentNode);
            currentNode = NodeFocusFinder.focusSearch(currentNode, NodeFocusFinder.SEARCH_FORWARD);
        }

        // Recycle all the seen nodes.
        AccessibilityNodeInfoUtils.recycleNodes(seenNodes);
        AccessibilityNodeInfoUtils.recycleNodes(compatRoot);

        if (currentNode == null) {
            return false;
        }

        setReadingState(AutomaticReadingState.READING_FROM_BEGINNING);
        mCursorController.setGranularity(CursorGranularity.DEFAULT);
        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
        mCursorController.clearCursor();
        mCursorController.setCursor(currentNode); // Will automatically move forward.
       if (currentNodeHasWebContent()) {
            // This works only because a focused WebView has a rule to generate
            // some default text and passes the isEmpty check above.
            moveIntoWebContent();
        }

        return true;
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
        if (!mCursorController.next(false)) {
            mFeedbackController.playSound(R.raw.complete, 1.3f, 1f);
            interrupt();
        }

        if (currentNodeHasWebContent()) {
            moveIntoWebContent();
        }
    }

    private void moveIntoWebContent() {
        final AccessibilityNodeInfoCompat webNode = mCursorController.getCursor();
        if (webNode != null) {
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
        } else {
            // Reset state.
            interrupt();
        }
    }

    public void setReadingState(AutomaticReadingState newState) {
        LogUtils.log(TAG, Log.VERBOSE, "Continuous reading switching to mode: %s", newState.name());
        mCurrentState = newState;
        mSpeechController.setShouldInjectAutoReadingCallbacks(
                (newState != AutomaticReadingState.STOPPED) ? true : false, mNodeSpokenRunnable);
    }

    public AutomaticReadingState getReadingState() {
        return mCurrentState;
    }

    private boolean currentNodeHasWebContent() {
        final AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
        final boolean isWebContent = WebInterfaceUtils.hasWebContent(currentNode);
        currentNode.recycle();
        return isWebContent;
    }
}
