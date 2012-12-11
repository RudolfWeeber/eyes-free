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

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.TestingUtils;

/**
 * Abstract class used for unit tests that require a fully-functional node
 * hierarchy.
 * <p>
 * Do not run tests on the UI thread. Call {@link #setContentViewWithLayout} off
 * the UI thread before attempting to access the view hierarchy.
 */
public abstract class LayoutInstrumentationTestCase
        extends ActivityInstrumentationTestCase2<TestActivity> {
    private final Object mLayoutLock = new Object();

    private boolean mAwaitingLayout;

    public LayoutInstrumentationTestCase() {
        super(TestActivity.class);
    }

    @Override
    protected void setUp() {
        LogUtils.setLogLevel(Log.VERBOSE);

        // Force the device's screen on and dismiss the key guard.
        getActivity().runOnUiThread(new ShowActivityRunnable(getActivity()));
    }

    /**
     * Calls {@link android.app.Activity#setContentView} with the specified
     * layout resource and waits for a layout pass.
     * <p>
     * An initial layout pass is required for
     * {@link AccessibilityNodeInfo#isVisibleToUser} to return the correct
     * value.
     *
     * @param layoutResID Resource ID to be passed to
     *            {@link android.app.Activity#setContentView}.
     */
    protected void setContentViewWithLayout(final int layoutResID) {
        final View decorView = getActivity().getWindow().getDecorView();
        final View.OnLayoutChangeListener listener = new View.OnLayoutChangeListener() {
                @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                synchronized (mLayoutLock) {
                    mAwaitingLayout = false;
                    mLayoutLock.notifyAll();
                }
            }
        };

        decorView.addOnLayoutChangeListener(listener);

        // Set the content view on the UI thread and await layout.
        try {
            runTestOnUiThread(new Runnable() {
                    @Override
                public void run() {
                    synchronized (mLayoutLock) {
                        getActivity().setContentView(layoutResID);
                        mAwaitingLayout = true;
                    }
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }

        // Wait (up 4 seconds) until the layout pass has happened.
        synchronized (mLayoutLock) {
            try {
                if (mAwaitingLayout) {
                    mLayoutLock.wait(4000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        decorView.removeOnLayoutChangeListener(listener);

        assertFalse("Layout pass occurred", mAwaitingLayout);
    }

    /**
     * Returns the {@link AccessibilityNodeInfoCompat} for a specific view, or
     * {@code null} if the view is invalid or an error occurred while obtaining
     * the info.
     *
     * @param id The view id, or {@code -1} to return null.
     * @return The view's node info, or {@code null}.
     */
    protected AccessibilityNodeInfoCompat getNodeForView(int id) {
        if (id < 0) {
            return null;
        }

        final View view = getActivity().findViewById(id);
        if (view == null) {
            return null;
        }

        final AccessibilityNodeInfo node = view.createAccessibilityNodeInfo();
        if (node == null) {
            return null;
        }

        final AccessibilityNodeInfoCompat compatNode = new AccessibilityNodeInfoCompat(node);

        // Force the node to seal so we can use it.
        TestingUtils.AccessibilityNodeInfo_setSealed(compatNode, true);

        return compatNode;
    }
}
