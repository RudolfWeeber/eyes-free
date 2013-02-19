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

package com.googlecode.eyesfree.utils;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * Utility class for sending commands to ChromeVox.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
public class WebInterfaceUtils {
    /**
     * If injection of accessibility enhancing JavaScript screen-reader is
     * enabled.
     * <p>
     * This property represents a boolean value encoded as an integer (1 is
     * true, 0 is false).
     */
    private static final String ACCESSIBILITY_SCRIPT_INJECTION = "accessibility_script_injection";

    /**
     * Direction constant for forward movement within a page.
     */
    public static final int DIRECTION_FORWARD = 1;

    /**
     * Direction constant for backward movement within a page.
     */
    public static final int DIRECTION_BACKWARD = -1;

    /**
     * Action argument to use with {@link #performNavigationAtGranularityAction(AccessibilityNodeInfoCompat,
     * int, int)} to instruct ChromeVox to read the currently focused element
     * within the node. within the page.
     */
    public static final int ACTION_READ_CURRENT_HTML_ELEMENT = -1;

    /**
     * Action argument to use with {@link #performNavigationAtGranularityAction(AccessibilityNodeInfoCompat,
     * int, int)} to instruct ChromeVox to read the title of the page within
     * the node.
     */
    public static final int ACTION_READ_PAGE_TITLE_ELEMENT = -2;

    /**
     * Action argument to use with {@link #performNavigationAtGranularityAction(AccessibilityNodeInfoCompat,
     * int, int)} to instruct ChromeVox to stop all speech and automatic
     * actions.
     */
    public static final int ACTION_STOP_SPEECH = -3;

    /**
     * HTML element argument to use with {@link #performNavigationToHtmlElementAction(AccessibilityNodeInfoCompat,
     * int, String)} to instruct ChromeVox to move to the next or previous page
     * section.
     */
    public static final String HTML_ELEMENT_MOVE_BY_SECTION = "SECTION";

    /**
     * HTML element argument to use with {@link #performNavigationToHtmlElementAction(AccessibilityNodeInfoCompat,
     * int, String)} to instruct ChromeVox to move to the next or previous list.
     */
    public static final String HTML_ELEMENT_MOVE_BY_LIST = "LIST";

    /**
     * HTML element argument to use with {@link #performNavigationToHtmlElementAction(AccessibilityNodeInfoCompat,
     * int, String)} to instruct ChromeVox to move to the next or previous
     * control.
     */
    public static final String HTML_ELEMENT_MOVE_BY_CONTROL = "CONTROL";

    /**
     * Sends an instruction to ChromeVox to read the specified HTML element in
     * the given direction within a node.
     *
     * WARNING: Calling this method with a source node of
     * {@link android.webkit.WebView} has the side effect of closing the IME
     * if currently displayed.
     *
     * @param node The node containing web content with ChromeVox to which the
     *            message should be sent
     * @param direction {@link #DIRECTION_FORWARD} or
     *            {@link #DIRECTION_BACKWARD}
     * @param htmlElement The HTML tag to send
     * @return {@code true} if the action was performed, {@code false}
     *         otherwise.
     */
    public static boolean performNavigationToHtmlElementAction(
            AccessibilityNodeInfoCompat node, int direction, String htmlElement) {
        final int action = (direction == DIRECTION_FORWARD)
                ? AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT
                : AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT;
        final Bundle args = new Bundle();
        args.putString(
                AccessibilityNodeInfoCompat.ACTION_ARGUMENT_HTML_ELEMENT_STRING, htmlElement);
        return node.performAction(action, args);
    }

    /**
     * Sends an instruction to ChromeVox to move within a page at a specified
     * granularity in a given direction.
     *
     * WARNING: Calling this method with a source node of
     * {@link android.webkit.WebView} has the side effect of closing the IME
     * if currently displayed.
     *
     * @param node The node containing web content with ChromeVox to which the
     *            message should be sent
     * @param direction {@link #DIRECTION_FORWARD} or
     *            {@link #DIRECTION_BACKWARD}
     * @param granularity The granularity with which to move or a special case argument.
     * @return {@code true} if the action was performed, {@code false} otherwise.
     */
    public static boolean performNavigationAtGranularityAction(
            AccessibilityNodeInfoCompat node, int direction, int granularity) {
        final int action = (direction == DIRECTION_FORWARD)
                ? AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
                : AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
        final Bundle args = new Bundle();
        args.putInt(
                AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, granularity);
        return node.performAction(action, args);
    }

    /**
     * Sends instruction to ChromeVox to perform one of the special actions
     * defined by the ACTION constants in this class. WARNING: Calling this
     * method with a source node of {@link android.webkit.WebView} has the side
     * effect of closing the IME if currently displayed.
     *
     * WARNING: Calling this method with a source node of
     * {@link android.webkit.WebView} has the side effect of closing the IME
     * if currently displayed.
     *
     * @param node The node containing web content with ChromeVox to which the
     *            message should be sent
     * @param action The ACTION constant in this class match the special action
     *            that ChromeVox should perform.
     * @return {@code true} if the action was performed, {@code false} otherwise.
     */
    public static boolean performSpecialAction(AccessibilityNodeInfoCompat node, int action) {
        /*
         * We use performNavigationAtGranularity to communicate with ChromeVox
         * for these actions because it is side-effect-free. If we use
         * performNavigationToHtmlElementAction and ChromeVox isn't injected,
         * we'll actually move selection within the fallback implementation.
         */
        return performNavigationAtGranularityAction(node, DIRECTION_FORWARD, action);
    }

    /**
     * Determines whether or not the given node contains web content.
     *
     * @param node The node to evaluate
     * @return {@code true} if the node contains web content, {@code false} otherwise
     */
    public static boolean hasWebContent(AccessibilityNodeInfoCompat node) {
        return AccessibilityNodeInfoUtils.supportsAnyAction(node,
                AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT,
                AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT);
    }

    /**
     * @return {@code true} if the user has explicitly enabled injection of
     *         accessibility scripts into web content.
     */
    public static boolean isScriptInjectionEnabled(Context context) {
        final int injectionSetting = Settings.Secure.getInt(
                context.getContentResolver(), ACCESSIBILITY_SCRIPT_INJECTION, 0);
        return (injectionSetting == 1);
    }
}
