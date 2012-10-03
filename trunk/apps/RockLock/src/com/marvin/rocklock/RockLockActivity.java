/*
 * Copyright (C) 2010 Google Inc.
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

package com.marvin.rocklock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.marvin.rocklock.MusicGestureOverlay.Gesture;
import com.marvin.rocklock.MusicGestureOverlay.GestureListener;
import com.marvin.rocklock.navigation.SongPicker;

/**
 * The main Rock Lock application that runs as an alternate lock screen which
 * enables the user to use stroke gestures to play music. If there is no lock
 * pattern, Rock Lock will replace the lock screen entirely; dismissing Rock
 * Lock will unlock the phone. If there is a lock pattern, Rock Lock will put up
 * the default pattern locked screen when the user dismisses Rock Lock.
 *
 * @author sainsley@google.com (Sam Ainsley)
 * @author clchen@google.com (Charles L. Chen)
 */
public class RockLockActivity extends FragmentActivity implements
        OnSharedPreferenceChangeListener {

    public static final String TICK_EARCON = "[TICK]";
    public static final String TOCK_EARCON = "[TOCK]";
    private static final long[] VIBE_PATTERN = {
    0, 10, 70, 80 };

    protected static final String PREF_RESUME_TIME = "TAG_TIME";
    public static final String EXTRA_STARTED_BY_SERVICE = "STARTED_BY_SERVICE";
    private static final int POKE_TIMEOUT = 5000;

    private boolean mWasStartedByService = false;
    private boolean mPoked = false;

    private RockLockActivity mSelf;
    protected RockLockMusicPlayer mPlayer;

    protected TextToSpeech mTts;
    protected String mCurrentUtteranceId;
    private Vibrator mVibe;

    private AudioManager mAudioManager;
    private NotificationManager mNotificationManager;
    private SharedPreferences mPrefs;

    private boolean mPausedForCall;
    private RockLockPhoneListener mPhoneListener;

    // Gesture logic

    private boolean mIsSeeking = false;
    private boolean mPreviewGesture = false;
    private boolean mBrowsingPref;
    private MusicGestureOverlay mGestureOverlay;
    private ImageView mIconDisplay;
    private TextView mCurrentTrack;
    private TextView mCurrentInfo;
    private TextView mUpcomingText;
    private TextView mModeText;

    // Bookmarks

    private static final String BOOKMARKS_FILE = "bookmarks.data";
    private ArrayList<Bookmark> mBookmarks;

    // Catch media button events so that controls from plugged in headsets and
    // BlueTooth headsets will work.
    //
    // Note that this only works if there are NO other apps that are trying to
    // consume the media button events and aborting the broadcasts; otherwise,
    // whether it works or not is a function of the order in which the
    // broadcasts are sent.
    private BroadcastReceiver mMediaButtonReceiver = new BroadcastReceiver() {
            @Override
        public void onReceive(Context ctx, Intent data) {
            this.abortBroadcast();
            KeyEvent event = data.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            int keyCode = event.getKeyCode();
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if ((keyCode == KeyEvent.KEYCODE_HEADSETHOOK)
                        || (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
                    mPlayer.togglePlayPause();
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                    mPlayer.nextTrack(false);
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                    mPlayer.prevTrack(false);
                }
            }
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        mSelf = this;

        sendBroadcast(new Intent(
                Intent.ACTION_MEDIA_MOUNTED,
                Uri.parse("file://" + Environment.getExternalStorageDirectory())));

        mPlayer = new RockLockMusicPlayer(this);

        super.onCreate(savedInstanceState);

        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(R.layout.main);
        mIconDisplay = (ImageView) findViewById(R.id.gestureIcon);
        mCurrentTrack = (TextView) findViewById(R.id.current_track);
        mCurrentInfo = (TextView) findViewById(R.id.current_info);
        mUpcomingText = (TextView) findViewById(R.id.upcoming);
        mModeText = (TextView) findViewById(R.id.mode_text);

        mGestureOverlay = (MusicGestureOverlay) findViewById(R.id.gestureLayer);
        mGestureOverlay.setGestureListener(new PlayerGestureListener(), true);
        updateDisplayText(null, null, false);

        mTts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {

                @Override
            public void onInit(int status) {
                mTts.setOnUtteranceCompletedListener(new OnUtteranceCompletedListener() {
                        @Override
                    public void onUtteranceCompleted(String utteranceId) {
                        if (mCurrentUtteranceId != null
                                && mCurrentUtteranceId.equals(utteranceId)) {
                            mPlayer.restoreMusic();
                        }
                    }
                });
            }
        });
        mTts.addEarcon(TOCK_EARCON, RockLockActivity.class.getPackage()
                .getName(), R.raw.tock_snd);
        mTts.addEarcon(TICK_EARCON, RockLockActivity.class.getPackage()
                .getName(), R.raw.tick_snd);

        mVibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mWasStartedByService = getIntent().getBooleanExtra(
                EXTRA_STARTED_BY_SERVICE, false);

        mPhoneListener = new RockLockPhoneListener();
        final TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
        mPausedForCall = false;

        // Start the service in case it is not already running
        startService(new Intent(this, ScreenOnHandlerService.class));

        // Get notification manager
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        loadBookmarks();

        // Browsing preference
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mBrowsingPref = mPrefs
                .getBoolean(getString(R.string.browse_mode), true);
    }

    /**
     * Inflates the options menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public void onStop() {
        super.onStop();
        // Put resume notification in notification bar
        if (mPlayer.isPlaying()) {
            int id = 1;
            CharSequence contentTitle = getString(R.string.app_name);
            CharSequence contentText = mPlayer.getNavigator()
                    .getCurrentSongInfo(true);
            // Set up pending intent
            Intent notificationIntent = new Intent(this, RockLockActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);
            // Show notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    mSelf).setContentTitle(contentTitle)
                    .setContentText(contentText).setSmallIcon(R.drawable.icon)
                    .setOngoing(true).setContentIntent(contentIntent);
            Notification noti = builder.getNotification();
            mNotificationManager.notify(id, noti);
        }
    }

    @Override
    public void onDestroy() {
        // Save last play time
        Editor edit = mPrefs.edit();
        edit.putInt(PREF_RESUME_TIME, mPlayer.getCurrentSongTime());
        edit.commit();
        // Save bookmarks
        saveBookmarks();
        // Shutdown services
        mPoked = true;
        mTts.stop();
        mTts.shutdown();
        mPlayer.stop();
        mPlayer.getNavigator().saveMode();
        // Remove notifications
        mNotificationManager.cancelAll();
        if (!mPausedForCall) {
            mSelf.unRegisterPhoneListener();
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        super.onPause();
        mPlayer.getNavigator().save();
        unregisterReceiver(mMediaButtonReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Poke application
        mPoked = false;
        new Thread(new PokeWatcher()).start();
        // Restore music
        mPlayer.getNavigator().restore();
        // Register headphone receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_BUTTON);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mMediaButtonReceiver, filter);
    }

    /**
     * Handles the player menu items
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i;
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.search:
                launchSearch();
                return true;
            case R.id.toggle_shuffle:
                mPlayer.getNavigator().toggleShuffle();
                updateDisplayText(null, null, false);
                return true;
            case R.id.playlists:
                launchPlaylists();
                return true;
            case R.id.bookmarks:
                launchBookmarks();
                return true;
            case R.id.settings:
                i = new Intent(mSelf, PrefsActivity.class);
                mSelf.startActivity(i);
                return true;
            case R.id.help:
                i = new Intent();
                i.setAction(Intent.ACTION_VIEW);
                i.setData(Uri.parse("https://eyes-free.googlecode.com/svn/trunk/apps/RockLock/rocklock_tutorial.html"));
                mSelf.startActivity(i);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Play default vibration
     */
    protected void playVibration() {
        playVibration(VIBE_PATTERN);
    }

    /**
     * Play custom vibration
     *
     * @param pattern
     */
    protected void playVibration(long[] pattern) {
        mVibe.vibrate(pattern, -1);
    }

    /**
     * On back: pop fragment stack or dismiss lock screen from this point
     */
    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            dismissSlideUnlockScreen();
        }
        updateDisplayText(null, null, false);
        updateIconDisplay();
    }

    /**
     * Pick up back button and volume up and down
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                mPlayer.togglePlayPause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                mPlayer.nextTrack(false);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                mPlayer.prevTrack(false);
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND);
                return true;
            case KeyEvent.KEYCODE_BACK:
                onBackPressed();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    /**
     * Updates the text displayed on the gesture overlay for sited users
     *
     * @param status the current status text to display
     * @param info the current info text to display
     */
    protected void updateDisplayText(String status, String info,
            boolean isBrowsing) {
        if ((status == null) || (info == null)) {
            if (!isBrowsing) {
                updateUpcoming(getString(R.string.next) + ": "
                        + mPlayer.getNavigator().peekNextTrack());
            } else {
                updateUpcoming(getString(R.string.tap_to_play) + ": "
                        + mPlayer.getNavigator().getCurrentSong());
            }

            mCurrentTrack.setText(mPlayer.getPlaying());
            mCurrentInfo.setText(mPlayer.getPlayingInfo());
            mModeText.setText(
                    mPlayer.getNavigator().getModeString() + " " + getString(R.string.mode));
            return;
        }
        mCurrentTrack.setText(status);
        mCurrentInfo.setText(info);
        mModeText.setText("");
    }

    protected void updateIconDisplay() {
        if (mPlayer.isPlaying()) {
            mIconDisplay.setImageDrawable(getResources().getDrawable(
                    R.drawable.play));
        } else {
            mIconDisplay.setImageDrawable(getResources().getDrawable(
                    R.drawable.pause));
        }
    }

    protected void clearIconDisplay() {
        mIconDisplay.setImageDrawable(null);
    }

    protected void updateUpcoming(String text) {
        if (text == null) {
            text = getString(R.string.next) + ": "
                    + mPlayer.getNavigator().peekNextTrack();
        }
        mUpcomingText.setText(text);
    }

    /**
     * Handles an update request from the MediaPlayer's OnCompletionListener
     */
    protected void handleUpdateRequest() {
        runOnUiThread(new Runnable() {
                @Override
            public void run() {
                updateDisplayText(null, null, false);
                updateUpcoming(null);
            }

        });
    }

    /**
     * Add a bookmark for the current song / time
     */
    private void addBookmark() {
        if (mPlayer.isReady() && mPlayer.getPlayingId() != null) {
            String id = mPlayer.getPlayingId();
            String file = mPlayer.getPlayingFile();
            int time = mPlayer.getCurrentSongTime();
            mBookmarks.add(new Bookmark(id, file, time));
            speakAndDuck(getString(R.string.bookmarked), true);
        }
    }

    /**
     * Save bookmarks to file
     */
    private void saveBookmarks() {
        // Save bookmarks
        FileOutputStream fos;
        try {
            fos = openFileOutput(BOOKMARKS_FILE, Context.MODE_PRIVATE);

            StringBuilder bookmarkString = new StringBuilder();
            for (Bookmark bookmark : mBookmarks) {
                if (bookmark.isValid()) {
                    bookmarkString.append(bookmark.toString());
                    bookmarkString.append("\n");
                }
            }
            fos.write(bookmarkString.toString().getBytes());
            fos.close();
        } catch (IOException e) {
            Log.e("RockLockActivity", e.toString());
        }
    }

    /**
     * Load bookmarks from file
     */
    private void loadBookmarks() {
        mBookmarks = new ArrayList<Bookmark>();
        // Read bookmarks
        FileInputStream fis;
        try {
            fis = openFileInput(BOOKMARKS_FILE);
            InputStreamReader streamReader = new InputStreamReader(fis);
            BufferedReader bufferedReader = new BufferedReader(streamReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Bookmark newBookmark = new Bookmark(line);
                if (newBookmark.isValid()) {
                    mBookmarks.add(new Bookmark(line));
                }
            }
        } catch (IOException e) {
            Log.e("RockLockActivity", e.toString());
        }
    }

    /**
     * Gets the bookmarks for this activity
     *
     * @return
     */
    protected ArrayList<Bookmark> getBookmarks() {
        return mBookmarks;
    }

    /**
     * Shows the bookmarks fragment
     */
    private void launchBookmarks() {
        Bundle args = new Bundle();
        BookmarkFragment bookmarkManager = new BookmarkFragment();
        bookmarkManager.setArguments(args);

        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction();

        // Replace whatever is in the fragment_container view with this
        // fragment, and add the transaction to the back stack so the user can
        // navigate back
        transaction.replace(R.id.contentFrame, bookmarkManager);
        // Only allow one instance of menu fragments
        if (getSupportFragmentManager().getBackStackEntryCount() != 0) {
            onBackPressed();
        }
        transaction.addToBackStack(null);

        // Commit the transaction
        transaction.commit();

        speakAndDuck(getString(R.string.bookmarks), true);
    }

    /**
     * Shows the search fragment
     */
    private void launchSearch() {

        Bundle args = new Bundle();
        args.putInt(getString(R.string.browser_mode), mPlayer.getNavigator()
                .getNavMode());
        SearchFragment browser = new SearchFragment();
        browser.setArguments(args);

        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction();

        // Replace whatever is in the fragment_container view with this
        // fragment, and add the transaction to the back stack so the user can
        // navigate back
        transaction.replace(R.id.contentFrame, browser);
        // Only allow one instance of menu fragments
        if (getSupportFragmentManager().getBackStackEntryCount() != 0) {
            onBackPressed();
        }
        transaction.addToBackStack(null);
        // Commit the transaction
        transaction.commit();

        if (mPlayer.isPlaying()) {
            mPlayer.togglePlayPause();
        }
    }

    /**
     * Shows the playlist screen
     */
    private void launchPlaylists() {

        Bundle args = new Bundle();
        PlaylistFragment playlistManager = new PlaylistFragment();
        playlistManager.setArguments(args);

        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction();

        // Replace whatever is in the fragment_container view with this
        // fragment, and add the transaction to the back stack so the user can
        // navigate back
        transaction.replace(R.id.contentFrame, playlistManager);
        // Only allow one instance of menu fragments
        if (getSupportFragmentManager().getBackStackEntryCount() != 0) {
            onBackPressed();
        }
        transaction.addToBackStack(null);

        // Commit the transaction
        transaction.commit();

        speakAndDuck(getString(R.string.playlists), true);
    }

    public void speakAndDuck(String utterance, boolean interrupt) {
        mPlayer.speakAndDuck(utterance, interrupt);
    }

    //
    // LOCK LOGIC
    //
    private class PokeWatcher implements Runnable {
        @Override
        public void run() {
            try {
                Thread.sleep(POKE_TIMEOUT);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (mWasStartedByService && !mPoked && (mPlayer != null)
                    && !mPlayer.isPlaying()) {
                finish();
            }
        }
    }

    private void dismissSlideUnlockScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        // finish() must be called in another thread or else the addFlags
        // call in the previous line will not take effect.
        new Thread(new Runnable() {
                @Override
            public void run() {
                finish();
            }
        }).start();
    }

    protected void poke() {
        mPoked = true;
    }

    /**
     * Handles gestures for player
     */
    private class PlayerGestureListener implements GestureListener {

        @Override
        public void onGestureChange(int gCur, int gPrev) {
            playVibration();
            // TODO(sainsley) : add Gesture.CIRCLE to overlay class
            // Handle wrap-around from Gesture.LEFT to Gesture.UPLEFT and vice
            // versa, but we still want to treat fresh UPLEFT gestures as linear
            boolean circular = Math.abs(gCur - gPrev) <= 1
                    && gCur != Gesture.UPLEFT;
            if (gCur == Gesture.LEFT && gPrev == Gesture.UPLEFT) {
                circular = true;
            } else if (gCur == Gesture.UPLEFT && gPrev == Gesture.LEFT) {
                circular = true;
            }
            // Are we seeking?
            if (circular) {
                // Handle circular gesture: seek
                mIsSeeking = true;
                mTts.stop();
                Resources res = getResources();
                int dir;
                if (gCur < gPrev) {
                    dir = SongPicker.DIRECTION_BACKWARD;
                    mIconDisplay.setImageDrawable(res
                            .getDrawable(R.drawable.rewind));
                } else {
                    dir = SongPicker.DIRECTION_FORWARD;
                    mIconDisplay.setImageDrawable(res
                            .getDrawable(R.drawable.fastforward));
                }
                mPlayer.seek(dir);
            } else if (!mIsSeeking) {
                updateIconDisplay();
                playVibration();
                switch (gCur) {
                    case Gesture.UPLEFT:
                        speakAndDuck(getString(R.string.add_to_playlist), true);
                        break;
                    case Gesture.DOWNLEFT:
                        mPlayer.getNavigator().peekMode(
                                SongPicker.DIRECTION_BACKWARD, true);
                        break;
                    case Gesture.LEFT:
                        mPlayer.peekPrevTrack();
                        break;
                    case Gesture.UPRIGHT:
                        mPlayer.getNavigator().peekMode(
                                SongPicker.DIRECTION_FORWARD, true);
                        break;
                    case Gesture.DOWNRIGHT:
                        speakAndDuck(getString(R.string.add_bookmark), true);
                        break;
                    case Gesture.RIGHT:
                        mPlayer.peekNextTrack();
                        break;
                    case Gesture.UP:
                        mPlayer.getNavigator().peekPrevGroup();
                        break;
                    case Gesture.DOWN:
                        mPlayer.getNavigator().peekNextGroup();
                        break;
                    default:
                        break;
                }
            }
        }

        @Override
        public void onGestureFinish(int g) {
            playVibration();
            // Don't do anything if we were seeking
            if (mIsSeeking) {
                updateIconDisplay();
                mIsSeeking = false;
                return;
            }
            // Don't do anything if we just previewed a gesture
            if (mPreviewGesture) {
                mPreviewGesture = false;
                return;
            }
            // Handle intentional linear gesture
            switch (g) {
                case Gesture.UP:
                    mPlayer.prevGroup(!mBrowsingPref);
                    break;
                case Gesture.UPRIGHT:
                    mPlayer.getNavigator().jumpMode(SongPicker.DIRECTION_FORWARD,
                            true);
                    break;
                case Gesture.CENTER:
                    mPlayer.togglePlayPause();
                    break;
                case Gesture.RIGHT:
                    mPlayer.nextTrack(!mBrowsingPref);
                    break;
                case Gesture.DOWNRIGHT:
                    addBookmark();
                    break;
                case Gesture.DOWN:
                    mPlayer.nextGroup(!mBrowsingPref);
                    break;
                case Gesture.DOWNLEFT:
                    mPlayer.getNavigator().jumpMode(SongPicker.DIRECTION_BACKWARD,
                            true);
                    break;
                case Gesture.LEFT:
                    mPlayer.prevTrack(!mBrowsingPref);
                    break;
                case Gesture.UPLEFT:
                    // Note: We need to resolve the ambiguity between what we
                    // are playing and what we are browsing when it comes to
                    // adding things to playlists on the fly
                    // Right now we just add whatever we have browsed to.
                    String id = mPlayer.getNavigator().getCurrentSongId();
                    if (id != null) {
                        PlaylistUtils.showPlaylistDialog(mSelf, id);
                    }
                    break;
                default:
                    break;
            }
            updateDisplayText(null, null, !mBrowsingPref);
            updateIconDisplay();
        }

        @Override
        public void onGestureStart(int g) {
            playVibration();
            poke();
            mIsSeeking = false;
            mPreviewGesture = false;
        }

        @Override
        public void onGestureHold(int g) {
            playVibration();
            // Announce preview
            if (g == Gesture.CENTER) {
                mPlayer.getNavigator().speakCurrentTrack(false);
                mPlayer.getNavigator().peekMode(0, false);
                mPlayer.getNavigator().speakPlayMode(false);
                mPreviewGesture = true;
            }
        }

        @Override
        public void onGesture2Change(int gCur, int gPrev) {
            playVibration();
            switch (gCur) {
                case Gesture.UPLEFT:
                case Gesture.DOWNLEFT:
                case Gesture.LEFT:
                    mPlayer.peekPrevTrack();
                    break;
                case Gesture.UPRIGHT:
                case Gesture.DOWNRIGHT:
                case Gesture.RIGHT:
                    mPlayer.peekNextTrack();
                    break;
                case Gesture.UP:
                    mPlayer.getNavigator().peekPrevGroup();
                    break;
                case Gesture.DOWN:
                    mPlayer.getNavigator().peekNextGroup();
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onGesture2Finish(int g) {
            playVibration();
            mIsSeeking = false;
            // Do nothing if we were previewing a gesture
            if (mPreviewGesture) {
                mPreviewGesture = false;
                return;
            }
            switch (g) {
            // TODO(sainsley) : change to quadrants for two-finger gestures
                case Gesture.UPLEFT:
                case Gesture.DOWNLEFT:
                case Gesture.LEFT:
                    mPlayer.prevTrack(mBrowsingPref);
                    break;
                case Gesture.UPRIGHT:
                case Gesture.DOWNRIGHT:
                case Gesture.RIGHT:
                    mPlayer.nextTrack(mBrowsingPref);
                    break;
                case Gesture.UP:
                    mPlayer.prevGroup(mBrowsingPref);
                    break;
                case Gesture.DOWN:
                    mPlayer.nextGroup(mBrowsingPref);
                    break;
                default:
                    break;
            }
            updateDisplayText(null, null, mBrowsingPref);

        }

        @Override
        public void onGestureHold2(int g) {
            // do nothing
        }

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key.equals(getString(R.string.verbose_mode))) {
            mPlayer.toggleVerbose();
        }
        // TODO(sainsley): handle two-finger browsing vs. single fingler
        // TODO(sainsley): handle lock preference changed
    }

    private class RockLockPhoneListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            // If the phone is not idle, immediately quit and let the
            // default lock screen handle it.
            if (TelephonyManager.CALL_STATE_RINGING == state) {
                finish();
                mPausedForCall = true;
                return;
            } else if (TelephonyManager.CALL_STATE_IDLE == state
                    && mPausedForCall) {
                Intent i = new Intent(mSelf, RockLockActivity.class);
                mSelf.unRegisterPhoneListener();
                mSelf.startActivity(i);
            }
        }
    }

    /**
     * Unregisters phone listeners
     */
    private void unRegisterPhoneListener() {
        final TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);
    }
}
