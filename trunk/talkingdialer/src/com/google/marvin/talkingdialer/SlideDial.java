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

import com.google.tts.TextToSpeechBeta;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;

/**
 * Enables the user to dial without looking at the phone.
 * 
 * The spot the user touches down is "5". What the user actually dials depends
 * on where they lift up relative to where they touched down; this is based on
 * the arrangement of numbers on a standard touchtone phone dialpad:
 * 
 * 1 2 3 4 5 6 7 8 9 * 0 #
 * 
 * Thus, sliding to the upperleft hand corner and lifting up will dial a "1".
 * 
 * A similar technique is used for dialing a contact. Stroking up will go to
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
  //   android.os.Debug.waitForDebugger();
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    tts = new TextToSpeechBeta(this, ttsInitListener);

  }

  private TextToSpeechBeta.OnInitListener ttsInitListener = new TextToSpeechBeta.OnInitListener() {
    public void onInit(int status, int version) {
      String pkgName = "com.google.marvin.talkingdialer";

      tts.addSpeech("You are about to dial", pkgName, R.raw.you_are_about_to_dial);
      tts.addSpeech("Dialing mode", pkgName, R.raw.dialing_mode);
      tts.addSpeech("deleted", pkgName, R.raw.deleted);
      tts.addSpeech("Nothing to delete", pkgName, R.raw.nothing_to_delete);
      tts.addSpeech("Phonebook", pkgName, R.raw.phonebook);
      tts.addSpeech("home", pkgName, R.raw.home);
      tts.addSpeech("cell", pkgName, R.raw.cell);
      tts.addSpeech("work", pkgName, R.raw.work);
      tts.addSpeech("[honk]", pkgName, R.raw.honk);
      tts.addSpeech("[tock]", pkgName, R.raw.tock_snd);


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
    contactsView = new ContactsView(this);
    setContentView(contactsView);
    tts.speak("Phonebook", 0, null);
  }

  public void switchToDialingView() {
    removeViews();
    mView = new SlideDialView(this);
    mView.parent = this;
    setContentView(mView);
    tts.speak("Dialing mode", 0, null);
  }
  
  public void removeViews(){
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
  public void onStop() {
    removeViews();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    tts.shutdown();
    super.onDestroy();
  }
}
