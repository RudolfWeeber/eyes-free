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
import android.os.Bundle;
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
import com.googlecode.eyesfree.utils.WebInterfaceUtils;

/**
 * Navigation mode that is based on traversing the node tree using
 * accessibility focus.
 */
// TODO: Consolidate parts of this class with similar code in TalkBack.
class DefaultNavigationMode implements NavigationMode {
    private static final int DIRECTION_BACKWARD =
        WebInterfaceUtils.DIRECTION_BACKWARD;
    private static final int DIRECTION_FORWARD =
        WebInterfaceUtils.DIRECTION_FORWARD;
    /**
     * Granularity used in web view navigation when the user presses the line
     * navigation keys or pan overflows.  Corresponds to chromevox group
     * granularity.
     */
    private static final int GRANULARITY_LINE =
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH;
    /**
     * Granularity used in web view navigation when the user presses the item
     * navigation keys.  Corresponds to chromevox object navigation.
     */
    private static final int GRANULARITY_ITEM =
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE;
    private final DisplayManager mDisplayManager;
    private final AccessibilityService mAccessibilityService;
    private final NodeBrailler mNodeBrailler;
    private final FeedbackManager mFeedbackManager;
    private final FocusFinder mFocusFinder;
    private final BrailleRuleRepository mRuleRepository;
    private final Bundle mArgumentBundle = new Bundle();

    private boolean mActive = false;
    private AccessibilityNodeInfoRef mLastFocusedNode =
            new AccessibilityNodeInfoRef();

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
        AccessibilityNodeInfoCompat currentNode = null;
        AccessibilityNodeInfoCompat firstNode = null;
        try {
            // If the currently focused node is a web view, we attempt
            // to delegate navigation to the web view first.
            currentNode = getFocusedNode(true);
            if (currentNode != null
                    && WebInterfaceUtils.hasWebContent(currentNode)
                    && WebInterfaceUtils.performNavigationAtGranularityAction(
                            currentNode, DIRECTION_BACKWARD, GRANULARITY_LINE)) {
                return true;
            }
            firstNode = AccessibilityNodeInfoUtils.refreshNode(
                    content.getFirstNode());
            // If the content doesn't have a first node, fall back on the
            // currently focused node.
            if (firstNode == null || !firstNode.isVisibleToUser()) {
                AccessibilityNodeInfoUtils.recycleNodes(firstNode);
                firstNode = currentNode;
                currentNode = null;
            }
            if (firstNode == null) {
                return false;
            }
            AccessibilityNodeInfoCompat target =
                mFocusFinder.linear(firstNode, FocusFinder.SEARCH_BACKWARD);
            return moveFocus(firstNode, DIRECTION_BACKWARD);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(currentNode, firstNode);
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
        AccessibilityNodeInfoCompat currentNode = getFocusedNode(true);
        try {
            if (currentNode != null
                    && WebInterfaceUtils.hasWebContent(currentNode)
                    && WebInterfaceUtils.performNavigationAtGranularityAction(
                            currentNode, DIRECTION_FORWARD, GRANULARITY_LINE)) {
                return true;
            }
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(currentNode);
        }
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
        if (current == null) {
            current = new AccessibilityNodeInfoRef();
        }
        // Simplify the algorithm by finding the node to stop our traversal
        // at, which is null if the display is at the end of the tree
        // (in pre-order traversal order).
        AccessibilityNodeInfoRef sentinel =
                AccessibilityNodeInfoRef.refreshed(content.getLastNode());
        try {
            if (AccessibilityNodeInfoRef.isNull(current)
                    || !current.get().isVisibleToUser()
                    || AccessibilityNodeInfoRef.isNull(sentinel)) {
                current.reset(linearSearch(FocusFinder.SEARCH_FORWARD));
                return current;
            }
            nextUpwardsOrClear(sentinel);
            while (!AccessibilityNodeInfoRef.isNull(current)
                    && !current.get().equals(sentinel.get())) {
                BrailleRule rule = mRuleRepository.find(current.get());
                // If children of this node are not included on the display,
                // look for focusable descendants.
                if (!rule.includeChildren(current.get(),
                                mAccessibilityService)) {
                    AccessibilityNodeInfoCompat focusableDescendant =
                        FocusFinder.findFirstFocusableDescendant(
                                current.get(), mAccessibilityService);
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
                    // We missed the sentinel, which would happen if there is
                    // some node tree change under our feet.  Fall back on a
                    // normal linear forward search so the user doesn't get
                    // stuck.
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
        } finally {
            if (sentinel != null) {
                sentinel.recycle();
            }
        }
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

    private boolean linePrevious(DisplayManager.Content content) {
        return mFeedbackManager.emitOnFailure(
            linePreviousInternal(content),
            FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
    }

    /**
     * Moves accessibility focus to the first focusable node of the previous
     * 'line'.
     */
    private boolean linePreviousInternal(DisplayManager.Content content) {
        AccessibilityNodeInfoRef left = new AccessibilityNodeInfoRef();
        AccessibilityNodeInfoRef right = new AccessibilityNodeInfoRef();
        AccessibilityNodeInfoCompat target = null;
        AccessibilityNodeInfoCompat currentNode = getFocusedNode(true);
        try {
            if (currentNode != null
                    && WebInterfaceUtils.hasWebContent(currentNode)
                    && WebInterfaceUtils.performNavigationAtGranularityAction(
                            currentNode, DIRECTION_BACKWARD, GRANULARITY_LINE)) {
                return true;
            }
            AccessibilityNodeInfoRef firstNode =
                AccessibilityNodeInfoRef.unOwned(
                        content.getFirstNode());
            // If the content doesn't have a first node, fall back on the
            // currently focused node.
            if (AccessibilityNodeInfoRef.isNull(firstNode)) {
                firstNode = AccessibilityNodeInfoRef.owned(currentNode);
                currentNode = null;
            }
            if (AccessibilityNodeInfoRef.isNull(firstNode)) {
                return false;
            }
            // Move backwards one step from the first node that is currently
            // displayed.
            target = mFocusFinder.linear(firstNode.get(),
                    FocusFinder.SEARCH_BACKWARD);
            firstNode.recycle();
            if (target == null) {
                return false;
            }
            // Find what would be covered by the display if target
            // would have accessibility focus.
            mNodeBrailler.findDisplayExtentFromNode(target, left, right);
            // Find the first focusable nodes moving forward from left,
            // i fleft is not focusable itself.
            if (!AccessibilityNodeInfoUtils.shouldFocusNode(
                            mAccessibilityService,
                            left.get())) {
                left.reset(mFocusFinder.linear(left.get(),
                                FocusFinder.SEARCH_FORWARD));
            }
            // If we didn't find a focusable node at the beginning of the
            // line we are trying to move to, just move to target as
            // a fallback.
            if (AccessibilityNodeInfoRef.isNull(left)) {
                left.reset(target);
                target = null;
            }
            return left.get().performAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(target, currentNode);
            left.recycle();
            right.recycle();
        }
    }

    private boolean lineNext(DisplayManager.Content content) {
        return onPanRightOverflow(content);
    }

    private boolean itemPrevious() {
        return mFeedbackManager.emitOnFailure(
                navigateItem(DIRECTION_BACKWARD),
            FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
    }

    private boolean navigateItem(int direction) {
        AccessibilityNodeInfoCompat currentNode = getFocusedNode(true);
        try {
            if (currentNode != null
                    && WebInterfaceUtils.hasWebContent(currentNode)
                    && WebInterfaceUtils.performNavigationAtGranularityAction(
                            currentNode, direction, GRANULARITY_ITEM)) {
                return true;
            }
            return moveFocus(currentNode, direction);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(currentNode);
        }
    }

    private boolean itemNext() {
        return mFeedbackManager.emitOnFailure(
                navigateItem(DIRECTION_FORWARD),
            FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
    }

    @Override
    public boolean onMappedInputEvent(BrailleInputEvent event,
            DisplayManager.Content content) {
        switch (event.getCommand()) {
            case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
                return itemPrevious();
            case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
                return itemNext();
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
        mLastFocusedNode.clear();
        brailleFocusedNode();
    }

    @Override
    public void onDeactivate() {
        mActive = false;
    }

    @Override
    public void onObserveAccessibilityEvent(AccessibilityEvent event) {
        // Nothing to do.
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
                brailleNodeFromEvent(event);
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                brailleFocusedNode();
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                if (!brailleFocusedNode()) {
                    // Since focus is typically not set in a newly opened
                    // window, so braille the window as-if the first focusable
                    // node had focus.  We don't update the focus because that
                    // will make other services (e.g. talkback) reflect this
                    // change, which is not desired.
                    brailleFirstFocusableNode();
                }
                break;
        }
    }

    @Override
    public void onInvalidateAccessibilityNode(
            AccessibilityNodeInfoCompat node) {
        brailleFocusedNode();
    }

    private boolean moveFocus(AccessibilityNodeInfoCompat from,
            int direction) {
        int searchDirection = (direction == DIRECTION_BACKWARD)
            ? FocusFinder.SEARCH_BACKWARD
            : FocusFinder.SEARCH_FORWARD;
        AccessibilityNodeInfoCompat next = null;
        next = mFocusFinder.linear(from, searchDirection);
        try {
            if (next != null) {
               return next.performAction(
                       AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
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
        AccessibilityNodeInfo focused = null;
        try {
            AccessibilityNodeInfo ret = null;
            if (root != null) {
                focused = root.findFocus(
                    AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
                if (focused != null && focused.isVisibleToUser()) {
                    ret = focused;
                    focused = null;
                } else if (fallbackOnRoot) {
                    ret = root;
                    root = null;
                }
            } else {
                LogUtils.log(this, Log.ERROR, "No current window root");
            }
            if (ret != null) {
                return new AccessibilityNodeInfoCompat(ret);
            }
        } finally {
            if (root != null) {
                root.recycle();
            }
            if (focused != null) {
                focused.recycle();
            }
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
        AccessibilityNodeInfoCompat eventNode = getNodeFromEvent(event);
        if (eventNode != null) {
            DisplayManager.Content ret = mNodeBrailler.brailleNode(
                eventNode);
            ret.setPanStrategy(DisplayManager.Content.PAN_CURSOR);
            mLastFocusedNode.reset(eventNode);
            return ret;
        }

        // Fall back on putting the event text on the display.
        // TODO: This can interfere with what's on the display and should be
        // done in a more disciplined manner.
        LogUtils.log(this, Log.VERBOSE,
                "No node on event, falling back on event text");
        mLastFocusedNode.clear();
        return new DisplayManager.Content(
            AccessibilityEventUtils.getEventText(event));
    }

    private void brailleNodeFromEvent(AccessibilityEvent event) {
        mDisplayManager.setContent(
            formatEventToBraille(event));
    }

    private boolean brailleFocusedNode() {
        AccessibilityNodeInfoCompat focused = getFocusedNode(false);
        if (focused != null) {
            DisplayManager.Content content = mNodeBrailler.brailleNode(
                focused);
            if (focused.equals(mLastFocusedNode.get())
                    && (content.getPanStrategy()
                            == DisplayManager.Content.PAN_RESET)) {
                content.setPanStrategy(DisplayManager.Content.PAN_KEEP);
            }
            mDisplayManager.setContent(content);
            mLastFocusedNode.reset(focused);
            return true;
        }
        return false;
    }

    private void brailleFirstFocusableNode() {
        AccessibilityNodeInfo unwrappedRoot =
                mAccessibilityService.getRootInActiveWindow();
        if (unwrappedRoot != null) {
            AccessibilityNodeInfoCompat root =
                    new AccessibilityNodeInfoCompat(unwrappedRoot);
            AccessibilityNodeInfoCompat toBraille = null;
            if (AccessibilityNodeInfoUtils.shouldFocusNode(
                            mAccessibilityService, root)) {
                toBraille = root;
                root = null;
            } else {
                toBraille = mFocusFinder.linear(root,
                        FocusFinder.SEARCH_FORWARD);
                if (toBraille == null) {
                    // Fall back on root as a last resort.
                    toBraille = root;
                    root = null;
                }
            }
            DisplayManager.Content content = mNodeBrailler.brailleNode(
                toBraille);
            if (AccessibilityNodeInfoRef.isNull(mLastFocusedNode)
                    && (content.getPanStrategy()
                            == DisplayManager.Content.PAN_RESET)) {
                content.setPanStrategy(DisplayManager.Content.PAN_KEEP);
            }
            mLastFocusedNode.clear();
            mDisplayManager.setContent(content);
            AccessibilityNodeInfoUtils.recycleNodes(root, toBraille);
        }
    }

    private AccessibilityNodeInfoCompat getNodeFromEvent(
            AccessibilityEvent event) {
        AccessibilityNodeInfo node = event.getSource();
        if (node != null) {
            return new AccessibilityNodeInfoCompat(node);
        } else {
            return null;
        }
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
