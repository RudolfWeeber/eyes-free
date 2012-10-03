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

package com.marvin.rocklock;

import java.util.LinkedHashMap;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.marvin.rocklock.MusicGestureOverlay.Gesture;
import com.marvin.rocklock.MusicGestureOverlay.GestureListener;
import com.marvin.rocklock.navigation.SongNavigator;
import com.marvin.rocklock.navigation.SongPicker;

/**
 * A UI Fragment that supports browsing music content that always speaks. You
 * can browse music groups with up/down swipes and select specific tracks within
 * those groups via the circle gesture. You can switch between navigation modes
 * by swiping left/right and play the current song with a single tap. Supports
 * navigation via 5 different song pickers: Albums, Artists, Genres, Playlists,
 * and Tracks. Also supports search mode in which case all single-fingered
 * gestures are dedicated to directional typing and all browse gestures require
 * two fingers. Also doubles as a means to select songs when building a playlist
 *
 * @author sainsley@google.com (Sam Ainsley)
 */
public class SearchFragment extends Fragment {

    // Search logic
    /*
     * These are the 4 alphabet groups for the 8-directional eyes-free keyboard
     */
    private static final int GROUP_AE = 0;
    private static final int GROUP_IM = 1;
    private static final int GROUP_QU = 2;
    private static final int GROUP_Y = 4;
    private static final int GROUP_NONE = 5;
    private static final int GROUP_GONE = -1;

    private boolean mIsScrolling;
    private int mCurrentWheel;
    private String mCurrentString = "";

    // Navigation logic
    private SongNavigator mNavigator;
    private LinkedHashMap<Integer, String> mResults;
    private Object[] mResultIds;
    private int mCurrentResult;

    // UI elements
    private RockLockActivity mParent;
    private MusicGestureOverlay mGestureOverlay;

    /**
     * Inflate view and instantiate gesture listener
     *
     * @param inflater the LayoutInflater from the parent class
     * @param container the ViewGroup that will contain this view
     * @param savedInstanceState the parent's saved instance bundle
     * @return the created View
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.main, container, false);

        mGestureOverlay = (MusicGestureOverlay) view
                .findViewById(R.id.gestureLayer);
        mGestureOverlay.setGestureListener(new BrowserGestureListener(), true);
        mGestureOverlay.toggleKeyboard();
        mParent.clearIconDisplay();

        return view;
    }

    /**
     * Instantiate pickers on attach
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        mParent = (RockLockActivity) activity;
        mNavigator = new SongNavigator(activity);

        int mode = getArguments().getInt(getString(R.string.browser_mode));
        mNavigator.setPickerMode(mode);
    }

    /**
     * Save music pickers on pause
     */
    @Override
    public void onPause() {
        super.onPause();
        mNavigator.save();
    }

    /**
     * Restores music pickers on resume and sets current picker is it hasn't
     * already been set.
     */
    @Override
    public void onResume() {
        super.onResume();
        mNavigator.restore();
        mCurrentString = "";
        mParent.speakAndDuck(getString(R.string.search), true);
    }

    private boolean findMoreResults(int dir) {
        LinkedHashMap<Integer, String> oldResults = mResults;
        mResults = mNavigator.searchModes(dir, mCurrentString);
        boolean hasResults = mResults != null;
        boolean restored = false;
        if (!hasResults) {
            mCurrentString = mCurrentString.substring(0, mCurrentString.length() - 1);
            if (oldResults != null) {
                mResults = oldResults;
                restored = true;
            }
        }
        if (hasResults || restored) {
            mResultIds = mResults.keySet().toArray();
            mCurrentResult = 0;
            if (restored) {
                mParent.speakAndDuck(mResults.get(mResultIds[mCurrentResult]), false);
            }
        }
        updateText(hasResults || restored);
        return hasResults;
    }

    private void updateText(boolean hasResults) {
        if (hasResults) {
            mParent.updateDisplayText(getString(R.string.searching)
                    + mCurrentString, mResults.get(mResultIds[mCurrentResult]), false);
            mParent.updateUpcoming(mNavigator.getModeString());
        } else {
            mParent.updateDisplayText(getString(R.string.searching)
                    + mCurrentString,
                    mParent.getString(R.string.no_results), false);
            mParent.updateUpcoming("");
        }
    }

    //
    // Search Methods
    //

    /**
     * Gets current character group wheel
     *
     * @param value the current gesture value
     * @return the current wheel
     */
    // TODO(sainsley): perhaps this should be moved into the gesture class as
    // as static method?
    private int getWheel(int value) {
        switch (value) {
            case Gesture.UP:
            case Gesture.DOWN:
                return GROUP_IM;
            case Gesture.UPLEFT:
            case Gesture.DOWNRIGHT:
                return GROUP_AE;
            case Gesture.UPRIGHT:
            case Gesture.DOWNLEFT:
                return GROUP_QU;
            case Gesture.LEFT:
            case Gesture.RIGHT:
                return GROUP_Y;
            default:
                return GROUP_NONE;
        }
    }

    /**
     * Gets current character given current wheel and gesture value
     *
     * @param wheel
     * @param value
     * @return current character
     */
    private String getCharacter(int wheel, int value) {
        switch (wheel) {
            case GROUP_AE:
                switch (value) {
                    case Gesture.UPLEFT:
                        return "A";
                    case Gesture.UP:
                        return "B";
                    case Gesture.UPRIGHT:
                        return "C";
                    case Gesture.RIGHT:
                        return "D";
                    case Gesture.CENTER:
                        return "";
                    case Gesture.DOWNRIGHT:
                        return "E";
                    case Gesture.DOWN:
                        return "F";
                    case Gesture.DOWNLEFT:
                        return "G";
                    case Gesture.LEFT:
                        return "H";
                    default:
                        return "";
                }
            case GROUP_IM:
                switch (value) {
                    case Gesture.UPLEFT:
                        return "P";
                    case Gesture.UP:
                        return "I";
                    case Gesture.UPRIGHT:
                        return "J";
                    case Gesture.RIGHT:
                        return "K";
                    case Gesture.CENTER:
                        return "";
                    case Gesture.DOWNRIGHT:
                        return "L";
                    case Gesture.DOWN:
                        return "M";
                    case Gesture.DOWNLEFT:
                        return "N";
                    case Gesture.LEFT:
                        return "O";
                    default:
                        return "";
                }
            case GROUP_QU:
                switch (value) {
                    case Gesture.UPLEFT:
                        return "W";
                    case Gesture.UP:
                        return "X";
                    case Gesture.UPRIGHT:
                        return "Q";
                    case Gesture.RIGHT:
                        return "R";
                    case Gesture.CENTER:
                        return "";
                    case Gesture.DOWNRIGHT:
                        return "S";
                    case Gesture.DOWN:
                        return "T";
                    case Gesture.DOWNLEFT:
                        return "U";
                    case Gesture.LEFT:
                        return "V";
                    default:
                        return "";
                }
            case GROUP_Y:
                switch (value) {
                    case Gesture.UPLEFT:
                        return ",";
                    case Gesture.UP:
                        return "!";
                    case Gesture.UPRIGHT:
                        return " ";
                    case Gesture.RIGHT:
                        return "Y";
                    case Gesture.CENTER:
                        return "";
                    case Gesture.DOWNRIGHT:
                        return "Z";
                    case Gesture.DOWN:
                        return "?";
                    case Gesture.DOWNLEFT:
                        return ".";
                    case Gesture.LEFT:
                        return "<-";
                    default:
                        return "";
                }
            default:
                return "TAP";
        }
    }

    /**
     * Gesture listener for browser class
     */
    private class BrowserGestureListener implements GestureListener {

        @Override
        public void onGestureChange(int gCur, int gPrev) {
            mIsScrolling = false;
            mParent.playVibration();
            String currentCharacter;
            // There is a wheel that is active
            if (gCur != Gesture.CENTER) {
                if (mCurrentWheel == GROUP_NONE) {
                    mCurrentWheel = getWheel(gCur);
                }
                currentCharacter = getCharacter(mCurrentWheel, gCur);
            } else {
                currentCharacter = "";
            }

            mGestureOverlay.updateKeyboard(mCurrentWheel, currentCharacter);
            mGestureOverlay.invalidate();

            if (currentCharacter.equals("")) {
                mParent.mTts.playEarcon(
                        RockLockActivity.TOCK_EARCON, TextToSpeech.QUEUE_FLUSH, null);
            } else {
                if (currentCharacter.equals(".")) {
                    mParent.mTts.speak(mParent.getString(R.string.period), 0,
                            null);
                } else if (currentCharacter.equals("!")) {
                    mParent.mTts.speak(
                            mParent.getString(R.string.exclamation_point), 0,
                            null);
                } else if (currentCharacter.equals("?")) {
                    mParent.mTts.speak(
                            mParent.getString(R.string.question_mark), 0, null);
                } else if (currentCharacter.equals(",")) {
                    mParent.mTts.speak(mParent.getString(R.string.comma), 0,
                            null);
                } else if (currentCharacter.equals("<-")) {
                    mParent.mTts.speak(mParent.getString(R.string.backspace),
                            0, null);
                } else {
                    mParent.mTts.speak(currentCharacter, 0, null);
                }
            }
        }

        @Override
        public void onGestureFinish(int g) {
            mGestureOverlay.updateKeyboard(GROUP_GONE, "");
            if (mIsScrolling) {
                mIsScrolling = false;
                return;
            }
            mParent.playVibration();
            // Searching and there is a wheel that is active
            String currentCharacter = "";
            currentCharacter = getCharacter(mCurrentWheel, g);
            // Launch search result if tap
            // Otherwise append character to search string
            if (currentCharacter.equals("TAP")) {
                if (mResults != null) {
                    mNavigator.moveToResult((Integer) mResultIds[mCurrentResult]);
                    mParent.mPlayer.moveTo(
                            mNavigator.getNavMode(), mNavigator.getGroupIdx(),
                            mNavigator.getSongIdx());
                }
                mParent.onBackPressed();
                return;
            } else if (currentCharacter.equals("<-")) {
                if (mCurrentString.length() > 0) {
                    mCurrentString = mCurrentString.substring(0,
                            mCurrentString.length() - 1);
                }
            } else {
                mCurrentString += currentCharacter.toLowerCase();
            }
            findMoreResults(SongPicker.DIRECTION_FORWARD);
            return;
        }

        @Override
        public void onGestureStart(int g) {
            mIsScrolling = false;
            mCurrentWheel = GROUP_NONE;
            mParent.playVibration();
            mParent.poke();
            mGestureOverlay.updateKeyboard(mCurrentWheel, "");
        }

        @Override
        public void onGesture2Change(int gCur, int gPrev) {
            mParent.playVibration();
            mGestureOverlay.updateKeyboard(GROUP_NONE, "");
            // TODO(sainsley) : add peeks here (if necessary). This is a little
            // tricky since results are filtered
        }

        @Override
        public void onGesture2Finish(int g) {
            if (mResults == null) {
                return;
            }
            mGestureOverlay.updateKeyboard(GROUP_GONE, "");
            mIsScrolling = false;
            mParent.playVibration();
            // Browse results
            switch (g) {
                case Gesture.LEFT:
                    // TODO(sainsley): when switching between results modes, if
                    // the user has not changed the query, we shouldn't have to
                    // requery within the current picker.
                    // I.E. store current search in SongPicker and only requery
                    // if that search has changed.
                    mNavigator.jumpMode(SongPicker.DIRECTION_BACKWARD, false);
                    if (!findMoreResults(SongPicker.DIRECTION_BACKWARD)) {
                        return;
                    }
                    break;
                case Gesture.RIGHT:
                    mNavigator.jumpMode(SongPicker.DIRECTION_FORWARD, false);
                    if (!findMoreResults(SongPicker.DIRECTION_FORWARD)) {
                        return;
                    }
                    break;
                case Gesture.UP:
                    if (--mCurrentResult < 0) {
                        mCurrentResult = mResultIds.length - 1;
                    }
                    mParent.speakAndDuck(mResults.get(mResultIds[mCurrentResult]), true);
                    break;
                case Gesture.DOWN:
                    if (++mCurrentResult == mResultIds.length) {
                        mCurrentResult = 0;
                    }
                    mParent.speakAndDuck(mResults.get(mResultIds[mCurrentResult]), true);
                    break;
                default:
                    break;
            }
            updateText(true);
        }

        @Override
        public void onGestureHold(int g) {
            // No need for preview
        }

        @Override
        public void onGestureHold2(int g) {
            // No need for preview
        }
    }
}
