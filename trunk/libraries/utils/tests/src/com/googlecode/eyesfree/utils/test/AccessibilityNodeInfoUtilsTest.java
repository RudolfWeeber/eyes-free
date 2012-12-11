/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.googlecode.eyesfree.utils.test;

import android.annotation.TargetApi;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.View;

import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;

/**
 * Tests for {@link AccessibilityNodeInfoUtils} that rely on activity
 * instrumentation.
 * <p>
 * Running the tests in a full-blown activity is necessary to obtain correct and
 * functional {@link AccessibilityNodeInfoCompat}s from {@link View}s.
 */
@TargetApi(14)
public class AccessibilityNodeInfoUtilsTest extends LayoutInstrumentationTestCase {
    public void testShouldFocusNode() {
        setContentViewWithLayout(R.layout.non_speaking_container);

        assertShouldFocusNode("Container is not focusable", R.id.container, false);
        assertShouldFocusNode("Text is focusable", R.id.textView, true);
        assertShouldFocusNode("Button is focusable", R.id.button, true);
    }

    private void assertShouldFocusNode(String message, int id, boolean expectedValue) {
        final AccessibilityNodeInfoCompat node = getNodeForView(id);

        assertNotNull("Obtain node", node);
        assertEquals(message, expectedValue,
                AccessibilityNodeInfoUtils.shouldFocusNode(getActivity(), node));

        AccessibilityNodeInfoUtils.recycleNodes(node);
    }

    public void testFindFocusFromHover() {
        setContentViewWithLayout(R.layout.non_speaking_container);

        assertFocusFromHover("Container does place focus", R.id.container, -1);
        assertFocusFromHover("Text receives focus", R.id.textView, R.id.textView);
        assertFocusFromHover("Button receives focus", R.id.button, R.id.button);
    }

    private void assertFocusFromHover(String message, int hoveredId, int expectedId) {
        final AccessibilityNodeInfoCompat hoveredNode = getNodeForView(hoveredId);

        assertNotNull("Obtain hovered node", hoveredNode);

        final AccessibilityNodeInfoCompat expectedNode = getNodeForView(expectedId);
        final AccessibilityNodeInfoCompat actualNode =
                AccessibilityNodeInfoUtils.findFocusFromHover(getActivity(), hoveredNode);

        // The expected and actual nodes may intentionally be null.
        assertEquals(message, expectedNode, actualNode);

        AccessibilityNodeInfoUtils.recycleNodes(hoveredNode, expectedNode, actualNode);
    }
}
