package com.google.android.marvin.talkback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.SparseArray;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.WindowManager;

import com.googlecode.eyesfree.widget.RadialMenu;
import com.googlecode.eyesfree.widget.RadialMenuItem;
import com.googlecode.eyesfree.widget.RadialMenuItem.OnMenuItemSelectionListener;
import com.googlecode.eyesfree.widget.RadialMenuOverlay;
import com.googlecode.eyesfree.widget.SimpleOverlay;
import com.googlecode.eyesfree.widget.SimpleOverlay.SimpleOverlayListener;

class RadialMenuManager extends BroadcastReceiver{
    /** Delay in milliseconds before speaking the radial menu usage hint. */
    private static final int DELAY_RADIAL_MENU_HINT = 2000;

    /** Cached radial menus. */
    private final SparseArray<RadialMenuOverlay> mCachedRadialMenus =
            new SparseArray<RadialMenuOverlay>();

    private final TalkBackService mService;
    private final SpeechController mSpeechController;
    private final PreferenceFeedbackController mFeedbackController;

    /** Client that responds to menu item selection and click. */
    private RadialMenuClient mClient;

    /** IntentFilter for closing the menu when the screen is turned off */
    private final IntentFilter mScreenOffFilter;

    /** How many radial menus are showing. */
    private int mIsRadialMenuShowing;

    public RadialMenuManager(TalkBackService context) {
        mService = context;
        mSpeechController = context.getSpeechController();
        mFeedbackController = context.getFeedbackController();
        mScreenOffFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    }

    /**
     * Sets the radial menu client, which is responsible for populating menus,
     * responding to click actions, and can optionally handle feedback from
     * selection.
     *
     * @param client The client to set.
     */
    public void setClient(RadialMenuClient client) {
        mClient = client;
    }

    /**
     * Shows the specified menu resource as a radial menu.
     *
     * @param menuId The identifier of the menu to display.
     */
    public void showRadialMenu(int menuId) {
        RadialMenuOverlay overlay = mCachedRadialMenus.get(menuId);

        if (overlay == null) {
            overlay = new RadialMenuOverlay(mService, false);
            overlay.setListener(mOverlayListener);

            final WindowManager.LayoutParams params = overlay.getParams();
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            overlay.setParams(params);

            final RadialMenu menu = overlay.getMenu();
            menu.setDefaultSelectionListener(mOnSelection);
            menu.setDefaultListener(mOnClick);

            if (mClient != null) {
                mClient.onCreateRadialMenu(menuId, menu);
            }

            mCachedRadialMenus.put(menuId, overlay);
        }

        if (mClient != null) {
            mClient.onPrepareRadialMenu(menuId, overlay.getMenu());
        }

        overlay.showWithDot();
    }

    public boolean isRadialMenuShowing() {
        return (mIsRadialMenuShowing > 0);
    }

    public IntentFilter getFilter() {
        return mScreenOffFilter;
    }

    public void dismissAll() {
        for (int i = 0; i < mCachedRadialMenus.size(); ++i) {
            final RadialMenuOverlay menu = mCachedRadialMenus.valueAt(i);

            if (menu.isVisible()) {
                menu.dismiss();
            }
        }
    }

    public void clearCache() {
        mCachedRadialMenus.clear();
    }

    /**
     * Handles selecting menu items.
     */
    private final OnMenuItemSelectionListener mOnSelection = new OnMenuItemSelectionListener() {
        @Override
        public boolean onMenuItemSelection(RadialMenuItem menuItem) {
            mHandler.removeCallbacks(mRadialMenuHint);

            mFeedbackController.playVibration(R.array.view_actionable_pattern);
            mFeedbackController.playSound(R.raw.view_hover_enter_actionable);

            boolean handled = (mClient != null) && mClient.onMenuItemHovered(menuItem);

            if (!handled) {
                final CharSequence text;
                if (menuItem == null) {
                    text = mService.getString(android.R.string.cancel);
                } else {
                    text = menuItem.getTitle();
                }

                mSpeechController.cleanUpAndSpeak(text, SpeechController.QUEUE_MODE_INTERRUPT,
                        SpeechController.FLAG_NO_HISTORY, null);
            }

            return true;
        }
    };

    /**
     * Handles clicking on menu items.
     */
    private final OnMenuItemClickListener mOnClick = new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            mHandler.removeCallbacks(mRadialMenuHint);

            mFeedbackController.playVibration(R.array.view_clicked_pattern);
            mFeedbackController.playSound(R.raw.view_clicked);

            boolean handled = (mClient != null) && mClient.onMenuItemClicked(menuItem);

            if (!handled && (menuItem == null)) {
                mService.interruptAllFeedback();
            }

            return true;
        }
    };

    /**
     * Handles feedback from showing and hiding radial menus.
     */
    private final SimpleOverlayListener mOverlayListener = new SimpleOverlayListener() {
        @Override
        public void onShow(SimpleOverlay overlay) {
            final RadialMenu menu = ((RadialMenuOverlay) overlay).getMenu();

            mHandler.postDelayed(mRadialMenuHint, DELAY_RADIAL_MENU_HINT);

            // Play a repeated tone once for each item in the menu.
            // TODO(caseyburkhardt): Reconsider this for large (>8 items) menus.
            mFeedbackController.playRepeatedSound(R.raw.view_scrolled_tone, menu.size());

            mIsRadialMenuShowing++;
        }

        @Override
        public void onHide(SimpleOverlay overlay) {
            mHandler.removeCallbacks(mRadialMenuHint);

            mIsRadialMenuShowing--;
        }
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_SCREEN_OFF == intent.getAction()) {
            dismissAll();
        }
    }

    /**
     * Runnable that speaks a usage hint for the radial menu.
     */
    private final Runnable mRadialMenuHint = new Runnable() {
        @Override
        public void run() {
            final String hintText = mService.getString(R.string.hint_radial_menu);
            mSpeechController.cleanUpAndSpeak(hintText, SpeechController.QUEUE_MODE_QUEUE,
                    SpeechController.FLAG_NO_HISTORY, null);
        }
    };

    private final Handler mHandler = new Handler();

    public interface RadialMenuClient {
        public void onCreateRadialMenu(int menuId, RadialMenu menu);
        public void onPrepareRadialMenu(int menuId, RadialMenu menu);
        public boolean onMenuItemHovered(MenuItem menuItem);
        public boolean onMenuItemClicked(MenuItem menuItem);
    }
}
