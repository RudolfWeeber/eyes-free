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

import com.google.tts.TTSEarcon;
import com.google.tts.TextToSpeechBeta;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

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

    private SlideDialView mView;

    private ContactsView contactsView;

    public TextToSpeechBeta tts;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (tts == null) {
            tts = new TextToSpeechBeta(this, ttsInitListener);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    private TextToSpeechBeta.OnInitListener ttsInitListener = new TextToSpeechBeta.OnInitListener() {
        public void onInit(int status, int version) {
            String pkgName = SlideDial.class.getPackage().getName();
            tts.addEarcon(TTSEarcon.TOCK.toString(), pkgName, R.raw.tock_snd);
            switchToDialingView();
        }
    };

    public void returnResults(String dialedNumber) {
        dialedNumber = dialedNumber.replaceAll("[^0-9*#,;]", "");
        Intent dummyIntent = new Intent();
        dummyIntent.putExtra("number", dialedNumber);
        setResult(RESULT_OK, dummyIntent);
        finish();
    }

    public void switchToContactsView() {
        removeViews();
        if (contactsView == null) {
            contactsView = new ContactsView(this);
        }
        setContentView(contactsView);
        tts.speak(getString(R.string.phonebook), 0, null);
    }

    public void switchToDialingView() {
        removeViews();
        if (mView == null) {
            mView = new SlideDialView(this);
        }
        mView.parent = this;
        setContentView(mView);
        tts.speak(getString(R.string.dialing_mode), 0, null);
    }

    public void removeViews() {
        if (contactsView != null) {
            contactsView.shutdown();
            contactsView.setVisibility(View.GONE);
            contactsView = null;
        }
        if (mView != null) {
            mView.shutdown();
            mView.setVisibility(View.GONE);
            mView = null;
        }
    }
    
    public void quit() {
        setResult(RESULT_CANCELED, null);
        finish();
    }
    
    @Override
    protected void onDestroy() {
        removeViews();
        tts.shutdown();
        super.onDestroy();
    }
}
