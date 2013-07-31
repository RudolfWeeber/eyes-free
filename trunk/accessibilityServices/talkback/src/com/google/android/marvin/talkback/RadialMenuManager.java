package com.google.android.marvin.talkback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.WindowManager;

import com.google.android.marvin.talkback.SpeechController.UtteranceCompleteRunnable;
import com.google.android.marvin.talkback.tutorial.AccessibilityTutorialActivity;
import com.googlecode.eyesfree.utils.FeedbackController;
import com.googlecode.eyesfree.widget.RadialMenu;
import com.googlecode.eyesfree.widget.RadialMenuItem;
import com.googlecode.eyesfree.widget.RadialMenuItem.OnMenuItemSelectionListener;
import com.googlecode.eyesfree.widget.RadialMenuOverlay;
import com.googlecode.eyesfree.widget.RadialMenuView;
import com.googlecode.eyesfree.widget.SimpleOverlay;
import com.googlecode.eyesfree.widget.SimpleOverlay.SimpleOverlayListener;

class RadialMenuManager extends BroadcastReceiver{
    /** Delay in milliseconds before speaking the radial menu usage hint. */
    /*package*/ static final int DELAY_RADIAL_MENU_HINT = 2000;

    /** The MIDI program ID to use for sounds played when radial menus open */
    private static final int MIDI_PROGRAM_ID = 115;

    /** The MIDI note velocity value to use for sounds played when radial menus open */
    private static final int MIDI_VELOCITY = 105;

    /**
     * The duration in milliseconds during which MIDI sounds may be played when
     * radial menus open
     */
    private static final int MIDI_MIN_DURATION_WINDOW = 100;
    private static final int MIDI_LOG_DURATION_WINDOW = 50;

    /** The MIDI pitch ID on which to start scales played when radial menus open */
    private static final int MIDI_STARTING_PITCH = 54;

    /** Cached radial menus. */
    private final SparseArray<RadialMenuOverlay> mCachedRadialMenus =
            new SparseArray<RadialMenuOverlay>();

    private final TalkBackService mService;
    private final SpeechController mSpeechController;
    private final MappedFeedbackController mFeedbackController;

    /** Client that responds to menu item selection and click. */
    private RadialMenuClient mClient;

    /** IntentFilter for closing the menu when the screen is turned off */
    private final IntentFilter mScreenOffFilter;

    /** How many radial menus are showing. */
    private int mIsRadialMenuShowing;

    /** Whether we have queued hint speech and it has not completed yet. */
    private boolean mHintSpeechPending;

    public RadialMenuManager(TalkBackService context) {
        mService = context;
        mSpeechController = context.getSpeechController();
        mFeedbackController = MappedFeedbackController.getInstance();
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
     * @return {@code true} if the menu could be shown.
     */
    public boolean showRadialMenu(int menuId) {
        if (AccessibilityTutorialActivity.isTutorialActive()) {
            return false;
        }

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

            final RadialMenuView view = overlay.getView();
            view.setSubMenuMode(RadialMenuView.SubMenuMode.LIFT_TO_ACTIVATE);

            if (mClient != null) {
                mClient.onCreateRadialMenu(menuId, menu);
            }

            mCachedRadialMenus.put(menuId, overlay);
        }

        if ((mClient != null) && !mClient.onPrepareRadialMenu(menuId, overlay.getMenu())) {
            mFeedbackController.playAuditory(R.id.sounds_complete);
            return false;
        }

        overlay.showWithDot();
        return true;
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
     * Plays a C harmonic minor scale roughly representative of the size of the
     * specified menu.
     *
     * @param menu
     */
    private void playScaleForMenu(Menu menu) {
        final int size = menu.size();
        if (size <= 0) {
            return;
        }

        // Relative size starts at 2 (includes cancel) and increases linearly
        // for up to 4 menu items, after which it takes four items to add 1 to
        // the relative size.
        final int relativeSize = 1 + ((size <= 4) ? size : (3 + (size / 4)));
        final int noteDuration = ((MIDI_MIN_DURATION_WINDOW
                + (int) (MIDI_LOG_DURATION_WINDOW * Math.log(relativeSize))) / relativeSize);

        mFeedbackController.playMidiScale(MIDI_PROGRAM_ID, MIDI_VELOCITY, noteDuration,
                MIDI_STARTING_PITCH, relativeSize,
                FeedbackController.MIDI_SCALE_TYPE_HARMONIC_MINOR);
    }

    /**
     * Handles selecting menu items.
     */
    private final OnMenuItemSelectionListener mOnSelection = new OnMenuItemSelectionListener() {
        @Override
        public boolean onMenuItemSelection(RadialMenuItem menuItem) {
            mHandler.removeCallbacks(mRadialMenuHint);

            mFeedbackController.playHaptic(R.id.patterns_actionable);
            mFeedbackController.playAuditory(R.id.sounds_actionable);

            final boolean handled = (mClient != null) && mClient.onMenuItemHovered(menuItem);

            if (!handled) {
                final CharSequence text;
                if (menuItem == null) {
                    text = mService.getString(android.R.string.cancel);
                } else if (menuItem.hasSubMenu()) {
                    text = mService.getString(R.string.template_menu, menuItem.getTitle());
                } else {
                    text = menuItem.getTitle();
                }

                mSpeechController.speak(text, SpeechController.QUEUE_MODE_INTERRUPT,
                        FeedbackItem.FLAG_NO_HISTORY, null);
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

            mFeedbackController.playHaptic(R.id.patterns_clicked);
            mFeedbackController.playAuditory(R.id.sounds_clicked);

            final boolean handled = (mClient != null) && mClient.onMenuItemClicked(menuItem);

            if (!handled && (menuItem == null)) {
                mService.interruptAllFeedback();
            }

            if ((menuItem != null) && menuItem.hasSubMenu()) {
                playScaleForMenu(menuItem.getSubMenu());
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

            // TODO(caseyburkhardt): Find an alternative or just speak the number of items.
            // Play a note in a C major scale for each item in the menu.
            playScaleForMenu(menu);

            mIsRadialMenuShowing++;
        }

        @Override
        public void onHide(SimpleOverlay overlay) {
            mHandler.removeCallbacks(mRadialMenuHint);

            if (mHintSpeechPending) {
                mSpeechController.interrupt();
            }

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

            mHintSpeechPending = true;
            mSpeechController.speak(hintText, null, null, SpeechController.QUEUE_MODE_QUEUE,
                    FeedbackItem.FLAG_NO_HISTORY, null, null, mHintSpeechCompleted);
        }
    };

    /**
     * Runnable that confirms the hint speech has completed.
     */
    private final UtteranceCompleteRunnable mHintSpeechCompleted = new UtteranceCompleteRunnable() {
        @Override
        public void run(int status) {
            mHintSpeechPending = false;
        }
    };

    private final Handler mHandler = new Handler();

    public interface RadialMenuClient {
        public void onCreateRadialMenu(int menuId, RadialMenu menu);
        public boolean onPrepareRadialMenu(int menuId, RadialMenu menu);
        public boolean onMenuItemHovered(MenuItem menuItem);
        public boolean onMenuItemClicked(MenuItem menuItem);
    }
}
