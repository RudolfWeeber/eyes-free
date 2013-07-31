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
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.util.Log;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.android.marvin.talkback.TalkBackService.AccessibilityEventListener;
import com.google.android.marvin.talkback.tutorial.AccessibilityTutorialActivity;
import com.googlecode.eyesfree.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.NodeFocusFinder;
import com.googlecode.eyesfree.utils.WeakReferenceHandler;
import com.googlecode.eyesfree.utils.WebInterfaceUtils;

/**
 * Places focus in response to various {@link AccessibilityEvent} types,
 * including hover events, list scrolling, and placing input focus. Also handles
 * single-tap activation in response to touch interaction events.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
class ProcessorFocusAndSingleTap implements AccessibilityEventListener {
    /** This processor requires JellyBean (API 16). */
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN;

    /** Single-tap requires JellyBean (API 17). */
    public static final int MIN_API_LEVEL_SINGLE_TAP = Build.VERSION_CODES.JELLY_BEAN_MR1;

    /** Whether refocusing is enabled. Requires API 17. */
    private static final boolean SUPPORTS_INTERACTION_EVENTS =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1);

    /** The timeout after which an event is no longer considered a tap. */
    public static final long TAP_TIMEOUT = ViewConfiguration.getJumpTapTimeout();

    /** The period after a scroll event when focus following is disabled. */
    private static final long TIMEOUT_VIEW_SCROLLED = TalkBackService.DELAY_AUTO_AFTER_STATE;

    /** The period after a window event when focus following is disabled. */
    private static final long TIMEOUT_WINDOW_STATE_CHANGED = TalkBackService.DELAY_AUTO_AFTER_STATE;

    private final TalkBackService mService;
    private final SpeechController mSpeechController;
    private final CursorController mCursorController;
    private final AccessibilityManager mAccessibilityManager;

    private int mLastScrollAction = 0;
    private int mLastScrollFromIndex = -1;
    private int mLastScrollToIndex = -1;

    /**
     * Whether single-tap activation is enabled, always {@code false} on
     * versions prior to Jelly Bean MR1.
     */
    private boolean mSingleTapEnabled;

    /** The first focused item touched during the current touch interaction. */
    private AccessibilityNodeInfoCompat mFirstFocusedItem;

    /** The source of the most recently handled VIEW_SCROLLED event. */
    private AccessibilityNodeInfoCompat mLastScrollSource;

    /** The number of items focused during the current touch interaction. */
    private int mFocusedItems;

    /** Whether the current interaction may result in refocusing. */
    private boolean mMaybeRefocus;

    /** Whether the current interaction may result in a single tap. */
    private boolean mMaybeSingleTap;

    /** The time stamp of the last view scrolled event. */
    private long mLastViewScrolledEvent;

    /** The time stamp of the last window state changed event. */
    private long mLastWindowStateChangedEvent;

    public ProcessorFocusAndSingleTap(TalkBackService service) {
        mService = service;
        mSpeechController = service.getSpeechController();
        mCursorController = service.getCursorController();
        mAccessibilityManager = (AccessibilityManager) service.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!mAccessibilityManager.isTouchExplorationEnabled()) {
            // Don't manage focus when touch exploration is disabled.
            return;
        }

        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                // Prevent conflicts between lift-to-type and single tap. This
                // is only necessary when a CLICKED event occurs during a touch
                // interaction sequence (e.g. before an INTERACTION_END event),
                // but it isn't harmful to call more often.
                cancelSingleTap();
                break;
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                setFocusFromViewFocused(event, record);
                break;
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
                setFocusFromViewSelected(event, record);
                break;
            case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
                setFocusFromViewHoverEnter(record);
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                handleWindowStateChange(event);
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                handleWindowContentChanged(record);
                break;
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                handleViewScrolled(event, record);
                break;
            case AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START:
                // This event type only exists on API 17+ (JB MR1).
                handleTouchInteractionStart(event);
                break;
            case AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_END:
                // This event type only exists on API 17+ (JB MR1).
                handleTouchInteractionEnd(event);
                break;
        }
    }

    /**
     * Sets whether single-tap activation is enabled. If it is, the follow focus
     * processor needs to avoid re-focusing items that are already focused.
     *
     * @param enabled Whether single-tap activation is enabled.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void setSingleTapEnabled(boolean enabled) {
        mSingleTapEnabled = enabled;
    }

    private void handleWindowStateChange(AccessibilityEvent event) {
        mLastWindowStateChangedEvent = event.getEventTime();

        // Invalidate scrolling information.
        if (mLastScrollSource != null) {
            mLastScrollSource.recycle();
            mLastScrollSource = null;
        }
        mLastScrollAction = 0;
        mLastScrollFromIndex = -1;
        mLastScrollToIndex = -1;

        // Since we may get WINDOW_STATE_CHANGE events from the keyboard even
        // though the active window is still another app, only clear focus if
        // the event's window ID matches the cursor's window ID.
        final AccessibilityNodeInfoCompat cursor = mCursorController.getCursor();
        if ((cursor != null) && (cursor.getWindowId() == event.getWindowId())) {
            mCursorController.clearCursor();
        }

        AccessibilityNodeInfoUtils.recycleNodes(cursor);
    }

    private void handleWindowContentChanged(AccessibilityRecordCompat record) {
        final AccessibilityNodeInfoCompat source = record.getSource();
        if (source == null) {
            return;
        }

        source.recycle();

        mHandler.followContentChangedDelayed(record);
    }

    private void handleViewScrolled(AccessibilityEvent event, AccessibilityRecordCompat record) {
        mLastViewScrolledEvent = event.getEventTime();

        final AccessibilityNodeInfoCompat source = record.getSource();
        if (source == null) {
            LogUtils.log(this, Log.ERROR, "Drop scroll with no source node");
            return;
        }

        // Only move focus if we've already seen the source.
        if (source.equals(mLastScrollSource)) {
            final boolean isMovingForward =
                    (mLastScrollAction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    || (event.getFromIndex() > mLastScrollFromIndex)
                    || (event.getToIndex() > mLastScrollToIndex);
            final boolean wasScrollAction = (mLastScrollAction != 0);
            mHandler.followScrollDelayed(source, isMovingForward, wasScrollAction);

            // Performing a scroll action results in smooth scrolling, which may
            // send multiple events spaced at least 100ms apart.
            mHandler.clearScrollActionDelayed();
        } else {
            setScrollActionImmediately(0);
        }

        if (mLastScrollSource != null) {
            mLastScrollSource.recycle();
        }

        mLastScrollSource = source;
        mLastScrollFromIndex = record.getFromIndex();
        mLastScrollToIndex = record.getToIndex();
    }

    private void setScrollActionImmediately(int action) {
        mHandler.cancelClearScrollAction();
        mLastScrollAction = action;
    }

    /**
     * Attempts to place focus on the {@code event}'s selected item.
     */
    private boolean setFocusFromViewSelected(
            AccessibilityEvent event, AccessibilityRecordCompat record) {
        AccessibilityNodeInfoCompat source = null;
        AccessibilityNodeInfoCompat child = null;

        try {
            source = record.getSource();
            if (source == null) {
                return false;
            }

            final int index = (record.getCurrentItemIndex() - record.getFromIndex());
            if ((index < 0) || (index >= source.getChildCount())) {
                return false;
            }

            child = source.getChild(index);
            if (child == null) {
                return false;
            }

            if (!AccessibilityNodeInfoUtils.isTopLevelScrollItem(mService, child)) {
                return false;
            }

            return tryFocusing(child);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(source, child);
        }
    }

    /**
     * Attempts to place focus on the {@code event}'s source node.
     */
    private boolean setFocusFromViewFocused(
            AccessibilityEvent event, AccessibilityRecordCompat record) {
        AccessibilityNodeInfoCompat source = null;
        AccessibilityNodeInfoCompat existing = null;
        AccessibilityNodeInfoCompat child = null;

        try {
            source = record.getSource();
            if (source == null) {
                return false;
            }

            // Under certain conditions, we may need to ignore this event.
            if (shouldDropFocusEvent(event, source)) {
                return false;
            }

            // Try focusing the source node.
            if (tryFocusing(source)) {
                return true;
            }

            // If we fail and the source node already contains focus, abort.
            existing = source.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (existing != null) {
                return false;
            }

            // If we fail to focus a node, perhaps because it is a focusable
            // but non-speaking container, we should still attempt to place
            // focus on a speaking child within the container.
            child = AccessibilityNodeInfoUtils.searchFromBfs(
                    mService, source, AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
            if (child == null) {
                return false;
            }

            return tryFocusing(child);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(source, existing, child);
        }
    }

    private boolean shouldDropFocusEvent(
            AccessibilityEvent event, AccessibilityNodeInfoCompat node) {
        final long eventTime = event.getEventTime();

        return ((eventTime - mLastViewScrolledEvent) < TIMEOUT_VIEW_SCROLLED) ||
                ((eventTime - mLastWindowStateChangedEvent) < TIMEOUT_WINDOW_STATE_CHANGED);
    }

    /**
     * Attempts to place focus within a new window.
     */
    private boolean ensureFocusConsistency(boolean shouldPlaceFocus) {
        AccessibilityNodeInfoCompat root = null;
        AccessibilityNodeInfoCompat focused = null;
        AccessibilityNodeInfoCompat inputFocused = null;
        AccessibilityNodeInfoCompat firstFocus = null;

        try {
            root = AccessibilityServiceCompatUtils.getRootInActiveWindow(mService);
            if (root == null) {
                return false;
            }

            // First, see if we've already placed accessibility focus.
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (focused != null) {
                if (AccessibilityNodeInfoUtils.shouldFocusNode(mService, focused)) {
                    return true;
                }

                LogUtils.log(Log.VERBOSE, "Clearing focus from invalid node");
                focused.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            }

            // If not, should we attempt to place focus?
            if (!shouldPlaceFocus) {
                return false;
            }

            // Next, see if the system has placed input focus.
            inputFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (tryFocusing(inputFocused)) {
                return true;
            }

            // Finally, just try to focus the first focusable item.
            firstFocus = AccessibilityNodeInfoUtils.searchFromInOrderTraversal(mService, root,
                    AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS, NodeFocusFinder.SEARCH_FORWARD);
            if (tryFocusing(firstFocus)) {
                return true;
            }

            LogUtils.log(Log.ERROR, "Failed to place focus from new window");

            return false;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(root, focused, inputFocused, firstFocus);
        }
    }

    /**
     * Handles the beginning of a new touch interaction event.
     *
     * @param event The source event.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void handleTouchInteractionStart(AccessibilityEvent event) {
        if (mFirstFocusedItem != null) {
            mFirstFocusedItem.recycle();
            mFirstFocusedItem = null;
        }

        if (mSpeechController.isSpeaking()) {
            mMaybeRefocus = false;

            final AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
            // Don't silence speech on first touch if the tutorial is active
            // or if a WebView is active. This works around an issue where
            // the IME is unintentionally dismissed by WebView's
            // performAction implementation.
            if (!AccessibilityTutorialActivity.isTutorialActive()
                    && !AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                            mService, currentNode, android.webkit.WebView.class)) {
                mService.interruptAllFeedback();
            }
            AccessibilityNodeInfoUtils.recycleNodes(currentNode);
        } else {
            mMaybeRefocus = true;
        }

        mMaybeSingleTap = true;
        mFocusedItems = 0;
    }

    /**
     * Handles the end of an ongoing touch interaction event.
     *
     * @param event The source event.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void handleTouchInteractionEnd(AccessibilityEvent event) {
        if (mFirstFocusedItem == null) {
            return;
        }

        if (mSingleTapEnabled && mMaybeSingleTap) {
            mHandler.cancelRefocusTimeout(false);
            performClick(mFirstFocusedItem);
        }

        mFirstFocusedItem.recycle();
        mFirstFocusedItem = null;
    }

    /**
     * Attempts to place focus on an accessibility-focusable node, starting from
     * the {@code event}'s source node.
     */
    private boolean setFocusFromViewHoverEnter(AccessibilityRecordCompat event) {
        AccessibilityNodeInfoCompat touched = null;
        AccessibilityNodeInfoCompat focusable = null;

        try {
            touched = event.getSource();
            if (touched == null) {
                return false;
            }

            focusable = AccessibilityNodeInfoUtils.findFocusFromHover(mService, touched);
            if (focusable == null) {
                return false;
            }

            if (SUPPORTS_INTERACTION_EVENTS && (mFirstFocusedItem == null) && (mFocusedItems == 0)
                    && focusable.isAccessibilityFocused()) {
                mFirstFocusedItem = AccessibilityNodeInfoCompat.obtain(focusable);

                if (mSingleTapEnabled) {
                    mHandler.refocusAfterTimeout(focusable);
                    return false;
                }

                return attemptRefocusNode(focusable);
            }

            if (!tryFocusing(focusable)) {
                return false;
            }

            // If something received focus, single tap cannot occur.
            if (mSingleTapEnabled) {
                cancelSingleTap();
            }

            mFocusedItems++;

            return true;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(touched, focusable);
        }
    }

    /**
     * Ensures that a single-tap will not occur when the current touch
     * interaction ends.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void cancelSingleTap() {
        mMaybeSingleTap = false;
    }

    private boolean attemptRefocusNode(AccessibilityNodeInfoCompat node) {
        if (!mMaybeRefocus || mSpeechController.isSpeaking()) {
            return false;
        }

        // Never refocus web content, it will just read the title again.
        if (WebInterfaceUtils.hasWebContent(node)) {
            return false;
        }

        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)) {
            return false;
        }

        return tryFocusing(node);
    }

    private boolean followContentChangedEvent(AccessibilityRecordCompat event) {
        return ensureFocusConsistency(false /* shouldPlaceFocus */);
    }

    private boolean followScrollEvent(
            AccessibilityNodeInfoCompat source, boolean isMovingForward, boolean wasScrollAction) {
        AccessibilityNodeInfoCompat root = null;
        AccessibilityNodeInfoCompat focused = null;

        try {
            // First, see if we've already placed accessibility focus.
            root = AccessibilityServiceCompatUtils.getRootInActiveWindow(mService);
            if (root == null) {
                return false;
            }

            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (focused != null) {
                // If a node already has focus, ensure it should still have
                // focus. Return immediately if it's correctly focused and this
                // event is not the result a scroll action OR we successfully
                // refocus the node.
                if (AccessibilityNodeInfoUtils.shouldFocusNode(mService, focused)
                        && (!wasScrollAction || mCursorController.refocus())) {
                    return true;
                }

                LogUtils.log(this, Log.DEBUG, "Clear focus from %s", focused);
                focused.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            }

            // Try focusing the appropriate child node.
            if (tryFocusingChild(source, isMovingForward)) {
                return true;
            }

            // Finally, try focusing the scrollable node itself.
            if (tryFocusing(source)) {
                return true;
            }

            return false;
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(root, focused);
        }
    }

    /**
     * If {@code wasMovingForward} is true, moves to the first focusable child.
     * Otherwise, moves to the last focusable child.
     */
    private boolean tryFocusingChild(
            AccessibilityNodeInfoCompat parent, boolean wasMovingForward) {
        final int direction = wasMovingForward ? NodeFocusFinder.SEARCH_FORWARD
                : NodeFocusFinder.SEARCH_BACKWARD;

        AccessibilityNodeInfoCompat child = null;

        try {
            child = findChildFromNode(parent, direction);
            if (child == null) {
                return false;
            }

            return tryFocusing(child);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(child);
        }
    }

    /**
     * Returns the first focusable child found while traversing the child of the
     * specified node in a specific direction. Only traverses direct children.
     *
     * @param root The node to search within.
     * @param direction The direction to search, one of
     *            {@link NodeFocusFinder#SEARCH_BACKWARD} or
     *            {@link NodeFocusFinder#SEARCH_FORWARD}.
     * @return The first focusable child encountered in the specified direction.
     */
    private AccessibilityNodeInfoCompat findChildFromNode(
            AccessibilityNodeInfoCompat root, int direction) {
        final int childCount = root.getChildCount();
        if (childCount == 0) {
            return null;
        }

        final int increment;
        final int startIndex;

        if (direction == NodeFocusFinder.SEARCH_FORWARD) {
            increment = 1;
            startIndex = 0;
        } else {
            increment = -1;
            startIndex = (childCount - 1);
        }

        for (int childIndex = startIndex; (childIndex >= 0) && (childIndex < childCount);
                childIndex += increment) {
            final AccessibilityNodeInfoCompat child = root.getChild(childIndex);
            if (child == null) {
                continue;
            }

            if (AccessibilityNodeInfoUtils.shouldFocusNode(mService, child)) {
                return child;
            }

            child.recycle();
        }

        return null;
    }

    private boolean tryFocusing(AccessibilityNodeInfoCompat source) {
        if (source == null) {
            return false;
        }

        if (!AccessibilityNodeInfoUtils.shouldFocusNode(mService, source)) {
            return false;
        }

        if (!source.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS)) {
            return false;
        }

        mHandler.interruptFollowDelayed();
        return true;
    }

    private void performClick(AccessibilityNodeInfoCompat node) {
        // Performing a click on an EditText does not show the IME, so we need
        // to place input focus on it. If the IME was already connected and is
        // hidden, there is nothing we can do.
        if (AccessibilityNodeInfoUtils.nodeMatchesAnyClassByType(
                mService, node, android.widget.EditText.class)) {
            node.performAction(AccessibilityNodeInfoCompat.ACTION_FOCUS);
            return;
        }

        // If a user quickly touch explores in web content (event stream <
        // TAP_TIMEOUT), we'll send an unintentional ACTION_CLICK. Switch
        // off clicking on web content for now.
        if (WebInterfaceUtils.hasWebContent(node)) {
            return;
        }

        node.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
    }

    /**
     * Listens to accessibility actions performed by the parent service.
     *
     * @param action The action performed.
     */
    public void onActionPerformed(int action) {
        switch (action) {
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:
                setScrollActionImmediately(action);
                break;
        }
    }

    private final FollowFocusHandler mHandler = new FollowFocusHandler(this);

    private static class FollowFocusHandler
            extends WeakReferenceHandler<ProcessorFocusAndSingleTap> {
        private static final int FOCUS_AFTER_SCROLL = 1;
        private static final int FOCUS_AFTER_CONTENT_CHANGED = 2;
        private static final int REFOCUS_AFTER_TIMEOUT = 3;
        private static final int CLEAR_SCROLL_ACTION = 4;

        /** Delay after a scroll event before checking focus. */
        private static final long FOCUS_AFTER_SCROLL_DELAY = 250;
        private static final long FOCUS_AFTER_CONTENT_CHANGED_DELAY = 500;

        /** Delay after a scroll event to clear the cached scroll action. */
        private static final long CLEAR_SCROLL_ACTION_DELAY = 200;

        private AccessibilityRecordCompat mCachedContentRecord;
        private AccessibilityNodeInfoCompat mCachedScrollNode;
        private AccessibilityNodeInfoCompat mCachedFocusedNode;

        public FollowFocusHandler(ProcessorFocusAndSingleTap parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, ProcessorFocusAndSingleTap parent) {
            switch (msg.what) {
                case FOCUS_AFTER_SCROLL:
                    final boolean isMovingForward = (msg.arg1 == 1);
                    final boolean wasScrollAction = (msg.arg2 == 1);

                    parent.followScrollEvent(mCachedScrollNode, isMovingForward, wasScrollAction);

                    if (mCachedScrollNode != null) {
                        mCachedScrollNode.recycle();
                        mCachedScrollNode = null;
                    }
                    break;
                case FOCUS_AFTER_CONTENT_CHANGED:
                    parent.followContentChangedEvent(mCachedContentRecord);

                    if (mCachedContentRecord != null) {
                        mCachedContentRecord.recycle();
                        mCachedContentRecord = null;
                    }
                    break;
                case REFOCUS_AFTER_TIMEOUT:
                    parent.cancelSingleTap();
                    cancelRefocusTimeout(true);
                    break;
                case CLEAR_SCROLL_ACTION:
                    parent.setScrollActionImmediately(0);
                    break;
            }
        }

        /**
         * Ensure that focus is placed after content change actions, but use a delay to
         * avoid consuming too many resources.
         *
         * @param record The scroll event.
         */
        public void followContentChangedDelayed(AccessibilityRecordCompat record) {
            removeMessages(FOCUS_AFTER_CONTENT_CHANGED);

            if (mCachedContentRecord != null) {
                mCachedContentRecord.recycle();
                mCachedContentRecord = null;
            }

            mCachedContentRecord = AccessibilityRecordCompat.obtain(record);

            final Message msg = obtainMessage(FOCUS_AFTER_CONTENT_CHANGED);
            sendMessageDelayed(msg, FOCUS_AFTER_CONTENT_CHANGED_DELAY);
        }

        /**
         * Ensure that focus is placed after scroll actions, but use a delay to
         * avoid consuming too many resources.
         */
        public void followScrollDelayed(AccessibilityNodeInfoCompat source, boolean isMovingForward,
                boolean wasScrollAction) {
            removeMessages(FOCUS_AFTER_SCROLL);

            if (mCachedScrollNode != null) {
                mCachedScrollNode.recycle();
                mCachedScrollNode = null;
            }

            mCachedScrollNode = AccessibilityNodeInfoCompat.obtain(source);

            final int wrapIsMovingForward = isMovingForward ? 1 : 0;
            final int wrapWasScrollAction = wasScrollAction ? 1 : 0;

            final Message msg = obtainMessage(
                    FOCUS_AFTER_SCROLL, wrapIsMovingForward, wrapWasScrollAction);
            sendMessageDelayed(msg, FOCUS_AFTER_SCROLL_DELAY);
        }

        /**
         * Attempts to refocus the specified node after a timeout period, unless
         * {@link #cancelRefocusTimeout} is called first.
         *
         * @param source The node to refocus after a timeout.
         */
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        public void refocusAfterTimeout(AccessibilityNodeInfoCompat source) {
            removeMessages(REFOCUS_AFTER_TIMEOUT);

            if (mCachedFocusedNode != null) {
                mCachedFocusedNode.recycle();
                mCachedFocusedNode = null;
            }

            mCachedFocusedNode = AccessibilityNodeInfoCompat.obtain(source);

            final Message msg = obtainMessage(REFOCUS_AFTER_TIMEOUT);
            sendMessageDelayed(msg, TAP_TIMEOUT);
        }

        /**
         * Clears the cached scroll action after a short delay.
         */
        public void clearScrollActionDelayed() {
            removeMessages(CLEAR_SCROLL_ACTION);
            sendEmptyMessageDelayed(CLEAR_SCROLL_ACTION, CLEAR_SCROLL_ACTION_DELAY);
        }

        /**
         * Cancels a refocus timeout initiated by {@link #refocusAfterTimeout}
         * and optionally refocuses the target node immediately.
         *
         * @param shouldRefocus Whether to refocus the target node immediately.
         */
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        public void cancelRefocusTimeout(boolean shouldRefocus) {
            removeMessages(REFOCUS_AFTER_TIMEOUT);

            final ProcessorFocusAndSingleTap parent = getParent();
            if (parent == null) {
                return;
            }

            if (shouldRefocus && (mCachedFocusedNode != null)) {
                parent.attemptRefocusNode(mCachedFocusedNode);
            }

            if (mCachedFocusedNode != null) {
                mCachedFocusedNode.recycle();
                mCachedFocusedNode = null;
            }
        }

        /**
         * Interrupt any pending follow-focus messages.
         */
        public void interruptFollowDelayed() {
            removeMessages(FOCUS_AFTER_CONTENT_CHANGED);
            removeMessages(FOCUS_AFTER_SCROLL);
        }

        /**
         * Cancel any pending clear scroll action messages.
         */
        public void cancelClearScrollAction() {
            removeMessages(CLEAR_SCROLL_ACTION);
        }
    }
}
