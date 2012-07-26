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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.google.marvin.talkingdialer.ContactsManager.Contact;
import com.google.marvin.talkingdialer.ContactsManager.ContactData;

/**
 * Talking dialer for eyes-free dialing Enables the user to dial without looking
 * at the phone. The spot the user touches down is "5". What the user actually
 * dials depends on where they lift up relative to where they touched down; this
 * is based on the arrangement of numbers on a standard touchtone phone dialpad:
 * 1 2 3 4 5 6 7 8 9 * 0 # Thus, sliding to the upperleft hand corner and
 * lifting up will dial a "1". A similar technique is used for dialing a
 * contact. Stroking up will go to previous contact; stroking down will go to
 * the next contact.
 *
 * @author clchen@google.com (Charles L. Chen)
 */
public class TalkingDialer extends Activity {

    private PackageManager pm;

    private AccessibilityManager accessibilityManager;

    private static Method AccessibilityManager_isTouchExplorationEnabled;

    public static final String EXTRA_NUMBER = "number";
    public static final String EXTRA_EMAIL = "email";

    private static final int DIALING_VIEW = 0;
    private static final int CONTACTS_VIEW = 1;

    // Available modes
    protected static final int SELECT_PHONE = 0;
    protected static final int SELECT_EMAIL = 1;
    protected static final int DIAL = 2;
    protected int mIntentMode = DIAL;

    private SlideDialView mDialerView;
    private ContactsView mContactsView;
    private SharedPreferences mPrefs;

    private int mCurrentView = -1;

    // TODO: Make this private, add getter method.
    public TextToSpeech tts;
    boolean isVideoSupported = false;

    protected ContactsManager contactManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
    }

    @Override
    public void onResume() {
        super.onResume();

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        final String action = getIntent().getAction();

        if (Intent.ACTION_PICK.equals(action)) {
            if (ContactsContract.CommonDataKinds.Email.CONTENT_URI.toString()
                    .equals(getIntent().getStringExtra("ContactsMode"))) {
                mIntentMode = SELECT_EMAIL;
            } else {
                mIntentMode = SELECT_PHONE;
            }
        } else {
            mIntentMode = DIAL;
        }
        isVideoSupported = false;
        if (mIntentMode == DIAL) {
            if (Build.VERSION.SDK_INT > 10) {
                PackageManager pm = getPackageManager();
                Intent i = new Intent();
                i.setClassName("com.google.android.talk",
                        "com.google.android.talk.videochat.VideoChatActivity");
                i.setAction("initiate");
                if (!pm.queryIntentActivities(i, 0).isEmpty()) {
                    isVideoSupported = true;
                }
            }
        }
        contactManager = new ContactsManager(getBaseContext(), mIntentMode, isVideoSupported);

        if (tts == null) {
            tts = new TextToSpeech(this, ttsInitListener);
        } else {
            initView();
        }
    }

    static {
        initCompatibility();
    }

    private static void initCompatibility() {
        try {
            AccessibilityManager_isTouchExplorationEnabled = AccessibilityManager.class
                    .getMethod("isTouchExplorationEnabled");
            /* success, this is a newer device */
        } catch (NoSuchMethodException nsme) {
            /* failure, must be older device */
        }
    }

    protected boolean isTouchExplorationEnabled() {
        try {
            if (AccessibilityManager_isTouchExplorationEnabled != null) {
                Object retobj = AccessibilityManager_isTouchExplorationEnabled
                        .invoke(accessibilityManager);
                return (Boolean) retobj;
            }
        } catch (IllegalAccessException ie) {
            System.err.println("unexpected " + ie);
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Load a main view, depending on preferences and whether this activity has
     * been launched as a contact chooser.
     */
    private void initView() {

        if (mIntentMode == SELECT_PHONE) {
            switchToContactsView(false);
        } else if (mIntentMode == SELECT_EMAIL) {
            switchToContactsView(true);
        } else {
            mPrefs = getPreferences(MODE_PRIVATE);
            mCurrentView = mPrefs.getInt(
                    getString(R.string.view_mode_preference), DIALING_VIEW);
            if (mCurrentView == DIALING_VIEW) {
                switchToDialingView();
            } else {
                switchToContactsView(false);
            }
        }
    }

    private final OnInitListener ttsInitListener = new OnInitListener() {
            @Override
        public void onInit(int status) {

            accessibilityManager = (AccessibilityManager) getBaseContext()
                    .getSystemService(Context.ACCESSIBILITY_SERVICE);
            final String pkgName = TalkingDialer.class.getPackage().getName();
            tts.addEarcon(getString(R.string.earcon_tock), pkgName,
                    R.raw.tock_snd);
            initView();
        }
    };

    private void grabAccessibilityFocus(View v) {
        v.requestFocus();
        v.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        v.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
    }

    public void switchToContactsView(boolean inVideoMode) {
        removeViews();
        if (contactManager.hasContacts()) {
            if (mContactsView == null) {
                mContactsView = new ContactsView(this, inVideoMode);
            }
            setContentView(mContactsView);
            mCurrentView = CONTACTS_VIEW;
            tts.speak(getString(R.string.phonebook), 0, null);
            grabAccessibilityFocus(mContactsView);
        } else {
            tts.speak(getString(R.string.no_contacts_found), 0, null);
            switchToDialingView();
        }
    }

    public void switchToDialingView() {
        Log.i("SlideDial", "Switch to dialing view");
        removeViews();

        if (mDialerView == null) {
            mDialerView = new SlideDialView(this);
        }
        setContentView(mDialerView);

        mCurrentView = DIALING_VIEW;
        tts.speak(getString(R.string.dialing_mode), 1, null);
        grabAccessibilityFocus(mDialerView);
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

    @Override
    protected void onPause() {
        if ((mPrefs != null) && (mCurrentView != -1)) {
            final Editor editor = mPrefs.edit();
            editor.putInt(getString(R.string.view_mode_preference),
                    mCurrentView);
            editor.commit();
        }
        removeViews();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.i("SlideDial", "DESTROY");
        tts.shutdown();
        super.onDestroy();
    }

    public void returnResults(Contact currentContact, ContactData currentData) {
        returnResults(currentData.data, currentContact.name,
                !currentData.isNumber);
    }

    public void returnResults(String dialedNumber, String contactName,
            boolean videoCall) {

        // Number selection
        if (getIntent().getAction().equals(Intent.ACTION_PICK)) {
            final Intent dummyIntent = new Intent();
            if (videoCall) {
                dummyIntent.putExtra("email", dialedNumber);
            } else {
                dialedNumber = dialedNumber.replaceAll("[^0-9*#,;]", "");
                dummyIntent.putExtra("number", dialedNumber);
            }
            dummyIntent.putExtra("label", contactName);
            setResult(RESULT_OK, dummyIntent);
            finish();
            return;
        }

        // Launching calls
        if (videoCall) {
            // Launch Video Call
            final Intent i = new Intent();
            i.setClassName("com.google.android.talk",
                    "com.google.android.talk.videochat.VideoChatActivity");
            i.setAction("initiate");
            if (!pm.queryIntentActivities(i, 0).isEmpty()) {
                final String address = dialedNumber;
                if (Build.VERSION.SDK_INT < 10) {
                    final Intent intent = new Intent();
                    intent.setClassName("com.google.android.talk",
                            "com.google.android.talk.videochat.VideoChatActivity");
                    intent.setAction("initiate");
                    String uriString = String
                            .format("content://com.google.android.providers.talk/messagesByAcctAndContact/1/%s",
                                    Uri.encode(address));
                    intent.setData(Uri.parse(uriString));
                    startActivity(intent);
                } else {
                    Intent intent = new Intent();
                    intent.setClassName("com.google.android.talk",
                            "com.google.android.talk.videochat.VideoChatActivity");
                    intent.setAction("initiate");
                    intent.putExtra("accountId", (long) 1);
                    intent.putExtra("from", address);
                    startActivity(intent);
                }
            }
        } else {
            dialedNumber = dialedNumber.replaceAll("[^0-9*#,;]", "");
            final Uri phoneNumberUri = Uri.parse("tel:"
                    + Uri.encode(dialedNumber));
            // Launch Phone Call
            final Intent intent = new Intent(Intent.ACTION_CALL, phoneNumberUri);
            startActivity(intent);
            finish();
        }
    }
}
