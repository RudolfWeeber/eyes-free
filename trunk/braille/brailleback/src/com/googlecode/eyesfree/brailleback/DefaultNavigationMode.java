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
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.brailleback.rule.BrailleRule;
import com.googlecode.eyesfree.brailleback.rule.BrailleRuleRepository;
import com.googlecode.eyesfree.brailleback.utils.AccessibilityEventUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoRef;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * Navigation mode that is based on traversing the node tree using
 * accessibility focus.
 */
// TODO: Consolidate parts of this class with similar code in TalkBack.
class DefaultNavigationMode implements NavigationMode {
    private final DisplayManager mDisplayManager;
    private final AccessibilityService mAccessibilityService;
    private final NodeBrailler mNodeBrailler;
    private final FeedbackManager mFeedbackManager;
    private final FocusFinder mFocusFinder;
    private final BrailleRuleRepository mRuleRepository;

    private boolean mActive = false;
    private AccessibilityEvent mLastEvent;

    public DefaultNavigationMode(
        DisplayManager displayManager,
        AccessibilityService accessibilityService,
        FeedbackManager feedbackManager) {
        mDisplayManager = displayManager;
        mAccessibilityService = accessibilityService;
        mRuleRepository = new BrailleRuleRepository(mAccessibilityService);
        mNodeBrailler = new NodeBrailler(mAccessibilityService,
                mRuleRepository);
        mFeedbackManager = feedbackManager;
        mFocusFinder = new FocusFinder(mAccessibilityService);
    }

    @Override
    public boolean onPanLeftOverflow(DisplayManager.Content content) {
        return mFeedbackManager.emitOnFailure(
            onPanLeftOverflowInternal(content),
            FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
    }

    private boolean onPanLeftOverflowInternal(DisplayManager.Content content) {
        AccessibilityNodeInfoRef firstNode =
                AccessibilityNodeInfoRef.refreshed(
                    content.getFirstNode());
        // If the content doesn't have a first node, fall back on the
        // currently focused node.
        if (AccessibilityNodeInfoRef.isNull(firstNode)
                || !firstNode.get().isVisibleToUser()) {
            firstNode = AccessibilityNodeInfoRef.owned(getFocusedNode(true));
        }
        if (AccessibilityNodeInfoRef.isNull(firstNode)) {
            return false;
        }
        AccessibilityNodeInfoCompat target =
                mFocusFinder.linear(firstNode.get(),
                        FocusFinder.SEARCH_BACKWARD);
        try {
            return moveFocus(firstNode.get(), FocusFinder.SEARCH_BACKWARD);
        } finally {
            firstNode.recycle();
        }
    }

    @Override
    public boolean onPanRightOverflow(DisplayManager.Content content) {
        return mFeedbackManager.emitOnFailure(
            onPanRightOverflowInternal(content),
            FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
    }

    private boolean onPanRightOverflowInternal(
        DisplayManager.Content content) {
        AccessibilityNodeInfoRef target = findNodeForPanRight(content);
        if (AccessibilityNodeInfoRef.isNull(target)) {
            return false;
        }
        try {
            return target.get().performAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        } finally {
            target.recycle();
        }
    }

    /**
     * Find the first node to focus when panning right, taking into account
     * that some nodes have their children included on the braille display,
     * while some (such as containers) don't.
     */
    private AccessibilityNodeInfoRef findNodeForPanRight(
        DisplayManager.Content content) {
        AccessibilityNodeInfoRef current =
                AccessibilityNodeInfoRef.refreshed(content.getFirstNode());
        // Simplify the algorithm by finding the node to stop our traversal
        // at, which is null if the display is at the end of the tree
        // (in pre-order traversal order).
        AccessibilityNodeInfoRef sentinel =
                AccessibilityNodeInfoRef.refreshed(content.getLastNode());
        if (AccessibilityNodeInfoRef.isNull(current)
                || !current.get().isVisibleToUser()
                || AccessibilityNodeInfoRef.isNull(sentinel)) {
            return AccessibilityNodeInfoRef.owned(
                linearSearch(FocusFinder.SEARCH_FORWARD));
        }
        nextUpwardsOrClear(sentinel);
        while (!AccessibilityNodeInfoRef.isNull(current)
                && !current.get().equals(sentinel.get())) {
            BrailleRule rule = mRuleRepository.find(current.get());
            // If children of this node are not included on the display, look
            // for focusable descendants.
            if (!rule.includeChildren(current.get())) {
                AccessibilityNodeInfoCompat focusableDescendant =
                        findFirstFocusableDescendant(current.get());
                if (focusableDescendant != null) {
                    current.reset(focusableDescendant);
                    return current;
                }
                nextUpwardsOrClear(current);
            } else {
                if (!current.nextInOrder()) {
                    current.clear();
                }
            }
        }
        if (AccessibilityNodeInfoRef.isNull(current)) {
            if (!AccessibilityNodeInfoRef.isNull(sentinel)) {
                // We missed the sentinel, which would happen if there is some
                // node tree change under our feet.  Fall back on a normal
                // linear forward search so the user doesn't get stuck.
                current.reset(linearSearch(FocusFinder.SEARCH_FORWARD));
            } else {
                // Reached the end of the tree.
            }
        } else {
            // We reached the sentinel, now focus that node or one that we
            // find by linear forward search.
            if (!AccessibilityNodeInfoUtils.shouldFocusNode(
                mAccessibilityService, current.get())) {
                current.reset(mFocusFinder.linear(current.get(),
                                FocusFinder.SEARCH_FORWARD));
            }
        }
        return current;
    }

    /**
     * Moves {@code node} to the next sibling, or, if that doesn't succeed,
     * to the next sibling of the parent, recursively, until a next
     * node is found or the root is reached.  In the latter case, {@code node}
     * is cleared.
     */
    private void nextUpwardsOrClear(AccessibilityNodeInfoRef node) {
        if (node.nextSibling()) {
            return;
        }
        while (node.parent()) {
            if (node.nextSibling()) {
                return;
            }
        }
        node.clear();
    }

    // TODO: Protect against cycles.
    private AccessibilityNodeInfoCompat findFirstFocusableDescendant(
        AccessibilityNodeInfoCompat root) {
        if (root == null) {
            return null;
        }
        AccessibilityNodeInfoRef current =
                AccessibilityNodeInfoRef.unOwned(root);
        try {
            if (!current.firstChild()) {
                return null;
            }
            do {
                if (AccessibilityNodeInfoUtils.shouldFocusNode(
                        mAccessibilityService, current.get())) {
                    return current.release();
                }
                AccessibilityNodeInfoCompat n = findFirstFocusableDescendant(
                    current.get());
                if (n != null) {
                    return n;
                }
            } while (current.nextSibling());
        } finally {
            current.recycle();
        }
        return null;
    }

    private boolean linePrevious(DisplayManager.Content content) {
        return mFeedbackManager.emitOnFailure(
            linePreviousInternal(content),
            FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
    }

    private boolean linePreviousInternal(DisplayManager.Content content) {
        AccessibilityNodeInfoRef firstNode =
                AccessibilityNodeInfoRef.unOwned(
                    content.getFirstNode());
        // If the content doesn't have a first node, fall back on the
        // currently focused node.
        if (AccessibilityNodeInfoRef.isNull(firstNode)) {
            firstNode = AccessibilityNodeInfoRef.owned(getFocusedNode(true));
        }
        if (AccessibilityNodeInfoRef.isNull(firstNode)) {
            return false;
        }
        AccessibilityNodeInfoCompat target =
                mFocusFinder.linear(firstNode.get(),
                        FocusFinder.SEARCH_BACKWARD);
        if (target == null) {
            return false;
        }
        AccessibilityNodeInfoRef left = new AccessibilityNodeInfoRef();
        AccessibilityNodeInfoRef right = new AccessibilityNodeInfoRef();
        mNodeBrailler.findDisplayExtentFromNode(target, left, right);
        if (!AccessibilityNodeInfoUtils.shouldFocusNode(mAccessibilityService,
                        left.get())) {
            left.reset(mFocusFinder.linear(left.get(),
                            FocusFinder.SEARCH_FORWARD));
        }
        if (AccessibilityNodeInfoRef.isNull(left)) {
            left.reset(target);
            target = null;
        }
        try {
            return left.get().performAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(target);
            left.recycle();
            right.recycle();
        }
    }

    private boolean lineNext(DisplayManager.Content content) {
        return onPanRightOverflow(content);
    }

    @Override
    public boolean onMappedInputEvent(BrailleInputEvent event,
            DisplayManager.Content content) {
        switch (event.getCommand()) {
            case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
                return mFeedbackManager.emitOnFailure(
                    moveFocus(getFocusedNode(true),
                            FocusFinder.SEARCH_BACKWARD),
                    FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
            case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
                return mFeedbackManager.emitOnFailure(
                    moveFocus(getFocusedNode(true),
                            FocusFinder.SEARCH_FORWARD),
                    FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
            case BrailleInputEvent.CMD_NAV_LINE_PREVIOUS:
                return linePrevious(content);
            case BrailleInputEvent.CMD_NAV_LINE_NEXT:
                return lineNext(content);
            case BrailleInputEvent.CMD_ACTIVATE_CURRENT:
                // Activate the current node, but don't fall back on the
                // root if focus is cleared.
                return mFeedbackManager.emitOnFailure(
                    activateNode(getFocusedNode(false)),
                    FeedbackManager.TYPE_COMMAND_FAILED);
            case BrailleInputEvent.CMD_ROUTE:
                AccessibilityNodeInfoCompat node =
                        DisplaySpans.getAccessibilityNodeFromPosition(
                            event.getArgument(), content.getText());
                return mFeedbackManager.emitOnFailure(activateNode(node),
                        FeedbackManager.TYPE_COMMAND_FAILED);
            case BrailleInputEvent.CMD_SCROLL_FORWARD:
                return mFeedbackManager.emitOnFailure(
                    attemptScrollAction(
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD),
                    FeedbackManager.TYPE_COMMAND_FAILED);
            case BrailleInputEvent.CMD_SCROLL_BACKWARD:
                return mFeedbackManager.emitOnFailure(
                    attemptScrollAction(
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD),
                    FeedbackManager.TYPE_COMMAND_FAILED);
        }
        return false;
    }

    @Override
    public void onActivate() {
        mActive = true;
        if (mLastEvent != null) {
            onAccessibilityEvent(mLastEvent);
            setLastEvent(null);
        }
    }

    @Override
    public void onDeactivate() {
        mActive = false;
    }

    @Override
    public void onObserveAccessibilityEvent(AccessibilityEvent event) {
        if (isInteresting(event)) {
            setLastEvent(event);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isInteresting(event)) {
            return;
        }
        mDisplayManager.setContent(
            formatEventToBraille(event));
    }

    private void setLastEvent(AccessibilityEvent event) {
        if (mLastEvent != null) {
            mLastEvent.recycle();
        }
        if (event != null) {
            mLastEvent = AccessibilityEvent.obtain(event);
        } else {
            mLastEvent = null;
        }
    }

    private boolean isInteresting(AccessibilityEvent event)  {
        int t = event.getEventType();
        if ((t & (AccessibilityEvent.TYPE_VIEW_FOCUSED
                                | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED))
                != 0) {
            return true;
        }
        return false;

    }

    private boolean moveFocus(AccessibilityNodeInfoCompat from,
            int direction) {
        AccessibilityNodeInfoCompat next = null;
        next = mFocusFinder.linear(from, direction);
        try {
            if (next != null) {
                boolean ret = next.performAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                return ret;
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(next);
        }
        return false;
    }

    private AccessibilityNodeInfoCompat getFocusedNode(
            boolean fallbackOnRoot) {
        AccessibilityNodeInfo root =
                mAccessibilityService.getRootInActiveWindow();
        if (root != null) {
            try {
                AccessibilityNodeInfo focused =
                        root.findFocus(
                            AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
                if ((focused == null || !focused.isVisibleToUser()
                                && fallbackOnRoot)) {
                    focused = root;
                    root = null;
                }
                if (focused != null) {
                    return new AccessibilityNodeInfoCompat(focused);
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
        } else {
            LogUtils.log(this, Log.ERROR, "No current window root");
        }
        return null;
    }

    private boolean activateNode(AccessibilityNodeInfoCompat node) {
        if (node == null) {
            return false;
        }
        AccessibilityNodeInfoRef current =
                AccessibilityNodeInfoRef.unOwned(node);
        try {
            do {
                LogUtils.log(this, Log.VERBOSE,
                        "Considering to click: %s",
                        current.get().getInfo());
                int supportedActions = current.get().getActions();
                int action = 0;
                // For edit texts, the click action doesn't currently focus
                // the view, so we special case it here.
                // TODO: Revise when that changes.
                if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                        mAccessibilityService, current.get(),
                        EditText.class)) {
                    if ((supportedActions & AccessibilityNodeInfo.ACTION_FOCUS)
                            != 0) {
                        action = AccessibilityNodeInfo.ACTION_FOCUS;
                    } else {
                        // Put accessibility focus on the field.  If it is
                        // already focused and the IME is selected, that will
                        // activate the editing.
                        action = AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS;
                    }
                } else if ((supportedActions
                                & AccessibilityNodeInfo.ACTION_CLICK) != 0) {
                    action = AccessibilityNodeInfo.ACTION_CLICK;
                }
                if (action != 0 && current.get().performAction(action)) {
                    return true;
                } else {
                    LogUtils.log(this, Log.VERBOSE, "Action %d failed",
                            action);
                }
            } while (current.parent());
        } finally {
            current.recycle();
        }
        LogUtils.log(this, Log.VERBOSE, "Click action failed");
        return false;
    }

    /**
     * Formats some braille content from an {@link AccessibilityEvent}.
     *
     * @param event The event from which to format an utterance.
     * @return The formatted utterance.
     */
    private DisplayManager.Content formatEventToBraille(
        AccessibilityEvent event) {
        DisplayManager.Content content = mNodeBrailler.brailleNode(event);
        if (content != null) {
            return content;
        }

        // Fall back on putting the event text on the display.
        // TODO: This can interfere with what's on the display and should be
        // done in a more disciplined manner.
        LogUtils.log(this, Log.VERBOSE,
                "No node on event, falling back on event text");
        return new DisplayManager.Content(
            AccessibilityEventUtils.getEventText(event));
    }

    private AccessibilityNodeInfoCompat linearSearch(int direction) {
        AccessibilityNodeInfoCompat source = getFocusedNode(true);
        AccessibilityNodeInfoCompat result =
                mFocusFinder.linear(source, direction);
        AccessibilityNodeInfoUtils.recycleNodes(source);
        return result;
    }

    /**
     * Attempts to scroll using the specified action.
     */
    private boolean attemptScrollAction(int action) {
        AccessibilityNodeInfoCompat focusedNode = null;
        AccessibilityNodeInfoCompat scrollableNode = null;

        try {
            focusedNode = getFocusedNode(false);
            if (focusedNode == null) {
                return false;
            }

            scrollableNode =
                    AccessibilityNodeInfoUtils.getSelfOrMatchingPredecessor(
                        mAccessibilityService, focusedNode,
                        AccessibilityNodeInfoUtils.FILTER_SCROLLABLE);
            if (scrollableNode == null) {
                return false;
            }

            return scrollableNode.performAction(action);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(focusedNode
                    , scrollableNode);
        }
    }
}
