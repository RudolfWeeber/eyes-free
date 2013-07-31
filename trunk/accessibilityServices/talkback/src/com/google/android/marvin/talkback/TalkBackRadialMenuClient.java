/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.marvin.talkback.BreakoutMenuUtils.JogDial;
import com.google.android.marvin.talkback.RadialMenuManager.RadialMenuClient;
import com.google.android.marvin.talkback.menurules.NodeMenuRuleProcessor;
import com.googlecode.eyesfree.widget.RadialMenu;
import com.googlecode.eyesfree.widget.RadialMenu.OnMenuVisibilityChangedListener;
import com.googlecode.eyesfree.widget.RadialMenuItem;
import com.googlecode.eyesfree.widget.RadialMenuItem.OnMenuItemSelectionListener;
import com.googlecode.eyesfree.widget.RadialSubMenu;

/**
 * TalkBack-specific implementation of RadialMenuClient.
 */
public class TalkBackRadialMenuClient implements RadialMenuClient {
    /** The parent service. */
    private final TalkBackService mService;

    /** Menu inflater, used for constructing menus on-demand. */
    private final MenuInflater mMenuInflater;

    /** Menu rule processor, used to generate local context menus. */
    private final NodeMenuRuleProcessor mMenuRuleProcessor;

    public TalkBackRadialMenuClient(TalkBackService service) {
        mService = service;
        mMenuInflater = new MenuInflater(mService);

        if (Build.VERSION.SDK_INT >= NodeMenuRuleProcessor.MIN_API_LEVEL) {
            mMenuRuleProcessor = new NodeMenuRuleProcessor(mService);
        } else {
            mMenuRuleProcessor = null;
        }
    }

    @Override
    public void onCreateRadialMenu(int menuId, RadialMenu menu) {
        final int menuRes;

        if (menuId == R.menu.global_context_menu) {
            onCreateGlobalContextMenu(menu);
        }
    }

    @Override
    public boolean onPrepareRadialMenu(int menuId, RadialMenu menu) {
        if (menuId == R.menu.local_context_menu) {
            return onPrepareLocalContextMenu(menu);
        }

        return true;
    }

    /**
     * Handles clicking on a radial menu item.
     *
     * @param menuItem The radial menu item that was clicked.
     */
    @Override
    public boolean onMenuItemClicked(MenuItem menuItem) {
        if (menuItem == null) {
            // Let the manager handle cancellations.
            return false;
        }

        final int itemId = menuItem.getItemId();
        if (itemId == R.id.read_from_top) {
            mService.getFullScreenReadController().startReading(true);
        } else if (itemId == R.id.read_from_current) {
            mService.getFullScreenReadController().startReading(false);
        } else if (itemId == R.id.repeat_last_utterance) {
            mService.getSpeechController().repeatLastUtterance();
        } else if (itemId == R.id.spell_last_utterance) {
            mService.getSpeechController().spellLastUtterance();
        } else if (itemId == R.id.pause_feedback) {
            mService.requestSuspendTalkBack();
        } else if (itemId == R.id.talkback_settings) {
            final Intent settingsIntent = new Intent(mService, TalkBackPreferencesActivity.class);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mService.startActivity(settingsIntent);
        } else {
            // The menu item was not recognized.
            return false;
        }

        return true;
    }

    @Override
    public boolean onMenuItemHovered(MenuItem menuItem) {
        // Let the manager handle spoken feedback from hovering.
        return false;
    }

    private void onCreateGlobalContextMenu(RadialMenu menu) {
        mMenuInflater.inflate(R.menu.global_context_menu, menu);

        if (Build.VERSION.SDK_INT < FullScreenReadController.MIN_API_LEVEL) {
            menu.removeItem(R.id.read_from_top);
            menu.removeItem(R.id.read_from_current);
        }

        // Remove summary menu if not supported, otherwise populate.
        if (Build.VERSION.SDK_INT < QuickNavigationJogDial.MIN_API_LEVEL) {
            menu.removeItem(R.id.summary);
        } else {
            onCreateSummaryMenuItem(menu.findItem(R.id.summary));
        }

        // Only show "Repeat last utterance" on useful platforms.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            menu.removeItem(R.id.repeat_last_utterance);
        }
    }

    private void onCreateSummaryMenuItem(RadialMenuItem summaryItem) {
        final RadialSubMenu summary = summaryItem.getSubMenu();
        final QuickNavigationJogDial quickNav = new QuickNavigationJogDial(mService);

        // TODO: This doesn't seem like a very clean OOP implementation.
        quickNav.populateMenu(summary);

        summary.setDefaultSelectionListener(quickNav);
        summary.setOnMenuVisibilityChangedListener(quickNav);
    }

    private boolean onPrepareLocalContextMenu(RadialMenu menu) {
        final AccessibilityNodeInfoCompat currentNode = mService.getCursorController().getCursor();
        if (mMenuRuleProcessor == null || currentNode == null) {
            return false;
        }

        final boolean result = mMenuRuleProcessor.prepareMenuForNode(menu, currentNode);
        currentNode.recycle();
        return result;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static class QuickNavigationJogDial extends JogDial
            implements OnMenuItemSelectionListener, OnMenuVisibilityChangedListener {
        public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN;

        private static final int SEGMENT_COUNT=  16;
        private static final boolean ALLOW_WRAP = false;
        private static final boolean ALLOW_SCROLL = true;

        private final Handler mHandler = new Handler();

        private final Context mContext;
        private final SpeechController mSpeechController;
        private final CursorController mCursorController;
        private final MappedFeedbackController mFeedbackController;

        public QuickNavigationJogDial(TalkBackService service) {
            super(SEGMENT_COUNT);

            mContext = service;
            mSpeechController = service.getSpeechController();
            mCursorController = service.getCursorController();
            mFeedbackController = MappedFeedbackController.getInstance();
        }

        @Override
        public void onFirstTouch() {
            if (!mCursorController.refocus()) {
                mCursorController.next(ALLOW_WRAP, ALLOW_SCROLL);
            }
        }

        @Override
        public void onPrevious() {
            if (!mCursorController.previous(ALLOW_WRAP, ALLOW_SCROLL)) {
                mFeedbackController.playAuditory(R.id.sounds_complete);
            }
        }

        @Override
        public void onNext() {
            if (!mCursorController.next(ALLOW_WRAP, ALLOW_SCROLL)) {
                mFeedbackController.playAuditory(R.id.sounds_complete);
            }
        }

        @Override
        public boolean onMenuItemSelection(RadialMenuItem item) {
            mHandler.removeCallbacks(mHintRunnable);

            if (item == null) {
                // Let the manager handle cancellations.
                return false;
            }

            // Don't provide feedback for individual segments.
            return true;
        }

        @Override
        public void onMenuShown(RadialMenu menu) {
            mHandler.postDelayed(mHintRunnable, RadialMenuManager.DELAY_RADIAL_MENU_HINT);
        }

        @Override
        public void onMenuDismissed(RadialMenu menu) {
            mHandler.removeCallbacks(mHintRunnable);
            mCursorController.refocus();
        }

        private final Runnable mHintRunnable = new Runnable() {
            @Override
            public void run() {
                final String hintText = mContext.getString(R.string.hint_summary_jog_dial);
                mSpeechController.speak(hintText, SpeechController.QUEUE_MODE_QUEUE,
                        FeedbackItem.FLAG_NO_HISTORY, null);
            }
        };
    }
}
