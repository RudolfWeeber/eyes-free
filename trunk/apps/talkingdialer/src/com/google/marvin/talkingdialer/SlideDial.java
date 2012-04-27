/*
 * Copyright (C) 2008 Google Inc.
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

package com.google.marvin.talkingdialer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;

import com.google.marvin.talkingdialer.RunButton.OnHoverListener;
import com.google.marvin.talkingdialer.ScrollSidebarView.OnScrollDetectedListener;

/**
 * Enables the user to dial without looking at the phone. The spot the user
 * touches down is "5". What the user actually dials depends on where they lift
 * up relative to where they touched down; this is based on the arrangement of
 * numbers on a standard touchtone phone dialpad: 1 2 3 4 5 6 7 8 9 * 0 # Thus,
 * sliding to the upperleft hand corner and lifting up will dial a "1". A
 * similar technique is used for dialing a contact. Stroking up will go to
 * previous contact; stroking down will go to the next contact.
 *
 * @author clchen@google.com (Charles L. Chen)
 */
public class SlideDial extends Activity {
    public static final String EXTRA_NUMBER = "number";

    private static final int DIALING_VIEW = 0;
    private static final int CONTACTS_VIEW = 1;

    private FrameLayout mFrameLayout;
    private SlideDialView mDialerView;
    private ContactsView mContactsView;
    private SharedPreferences mPrefs;

    private int mCurrentView = -1;

    // TODO: Make this private, add getter method.
    public TextToSpeech tts;

    // TODO: Make this private, add getter method.
    public boolean contactsPickerMode = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mFrameLayout = (FrameLayout) findViewById(R.id.frameLayout);
        final RunButton callButton = (RunButton) findViewById(R.id.callButton);
        callButton.setOnClickListener(new OnClickListener() {
                @Override
            public void onClick(View v) {
                if (mContactsView != null) {
                    mContactsView.dialActionHandler();
                } else if (mDialerView != null) {
                    mDialerView.callCurrentNumber();
                }
            }
        });
        callButton.setOnHoverListener(new OnHoverListener() {
                @Override
            public void onHoverEnter() {
                if (mContactsView != null) {
                    mContactsView.dialActionHandler();
                } else if (mDialerView != null) {
                    mDialerView.callCurrentNumber();
                }
            }

                @Override
            public void onHoverExit() {
            }
        });
    }

    private final OnScrollDetectedListener scrollListener = new OnScrollDetectedListener() {
            @Override
        public void onScrollDetected(int direction) {
            if (direction > 0) {
                mContactsView.nextContact();
            } else {
                mContactsView.prevContact();
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        final String action = getIntent().getAction();
        contactsPickerMode = (action != null) && action.equals(Intent.ACTION_PICK);
        if (tts == null) {
            tts = new TextToSpeech(this, ttsInitListener);
        } else {
            initView();
        }
    }

    /**
     * Load a main view, depending on preferences and whether this activity has
     * been launched as a contact chooser.
     */
    private void initView() {
        if (contactsPickerMode) {
            mCurrentView = CONTACTS_VIEW;
            switchToContactsView();
        } else {
            mPrefs = getPreferences(MODE_PRIVATE);
            mCurrentView = mPrefs.getInt(getString(R.string.view_mode_preference), DIALING_VIEW);
            if (mCurrentView == DIALING_VIEW) {
                switchToDialingView();
            } else {
                switchToContactsView();
            }
        }
    }

    private final OnInitListener ttsInitListener = new OnInitListener() {
            @Override
        public void onInit(int status) {
            final String pkgName = SlideDial.class.getPackage().getName();
            tts.addEarcon(getString(R.string.earcon_tock), pkgName, R.raw.tock_snd);
            initView();
        }
    };

    public void returnResults(String dialedNumber) {
        dialedNumber = dialedNumber.replaceAll("[^0-9*#,;]", "");
        final Intent dummyIntent = new Intent();
        dummyIntent.putExtra(EXTRA_NUMBER, dialedNumber);
        setResult(RESULT_OK, dummyIntent);
        finish();
    }

    public void returnResults(String dialedNumber, String contactName) {
        dialedNumber = dialedNumber.replaceAll("[^0-9*#,;]", "");
        final Intent dummyIntent = new Intent();
        dummyIntent.putExtra("number", dialedNumber);
        dummyIntent.putExtra("label", contactName);
        setResult(RESULT_OK, dummyIntent);
        finish();
    }

    public void switchToContactsView() {
        removeViews();
        final ScrollSidebarView lScroll = (ScrollSidebarView) findViewById(R.id.lScroll);
        lScroll.setVisibility(View.VISIBLE);
        lScroll.setOnScrollDetectedListener(scrollListener);
        final ScrollSidebarView rScroll = (ScrollSidebarView) findViewById(R.id.rScroll);
        rScroll.setVisibility(View.VISIBLE);
        rScroll.setOnScrollDetectedListener(scrollListener);
        if (mContactsView == null) {
            mContactsView = new ContactsView(this);
        }
        if (mFrameLayout.getChildCount() > 1) {
            mFrameLayout.removeViewAt(0);
        }
        mFrameLayout.addView(mContactsView, 0);
        // setContentView(contactsView);
        mCurrentView = CONTACTS_VIEW;
        tts.speak(getString(R.string.phonebook), 0, null);
    }

    public void switchToDialingView() {
        removeViews();
        final ScrollSidebarView lScroll = (ScrollSidebarView) findViewById(R.id.lScroll);
        lScroll.setVisibility(View.INVISIBLE);
        final ScrollSidebarView rScroll = (ScrollSidebarView) findViewById(R.id.rScroll);
        rScroll.setVisibility(View.INVISIBLE);

        if (mDialerView == null) {
            mDialerView = new SlideDialView(this);
        }
        // setContentView(mView);
        if (mFrameLayout.getChildCount() > 1) {
            mFrameLayout.removeViewAt(0);
        }
        mFrameLayout.addView(mDialerView, 0);
        mCurrentView = DIALING_VIEW;
        tts.speak(getString(R.string.dialing_mode), 0, null);
    }

    public void removeViews() {
        if (mContactsView != null) {
            mContactsView.shutdown();
            mContactsView.setVisibility(View.GONE);
            mContactsView = null;
        }
        if (mDialerView != null) {
            mDialerView.shutdown();
            mDialerView.setVisibility(View.GONE);
            mDialerView = null;
        }
    }

    public void quit() {
        setResult(RESULT_CANCELED, null);
        finish();
    }

    @Override
    protected void onPause() {
        if ((mPrefs != null) && (mCurrentView != -1)) {
            final Editor editor = mPrefs.edit();
            editor.putInt(getString(R.string.view_mode_preference), mCurrentView);
            editor.commit();
        }
        removeViews();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        tts.shutdown();
        super.onDestroy();
    }
}
