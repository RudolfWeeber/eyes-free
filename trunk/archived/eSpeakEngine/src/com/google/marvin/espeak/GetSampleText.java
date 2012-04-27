/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.google.marvin.espeak;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import android.util.Log;

/*
 * Returns the sample text string for the language requested
 */
public class GetSampleText extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int result = TextToSpeech.LANG_AVAILABLE;
        Intent returnData = new Intent();

        Intent i = getIntent();
        String language = i.getExtras().getString("language");
        String country = i.getExtras().getString("country");
        String variant = i.getExtras().getString("variant");

        if (language.equals("afr")) {
            returnData.putExtra("sampleText", getString(R.string.afr));
        } else if (language.equals("bos")) {
            returnData.putExtra("sampleText", getString(R.string.bos));
        } else if (language.equals("zho") || language.equals("cmn") || language.equals("yue")) {
            returnData.putExtra("sampleText", getString(R.string.zho));
        } else if (language.equals("hrv")) {
            returnData.putExtra("sampleText", getString(R.string.hrv));
        } else if (language.equals("ces")) {
            returnData.putExtra("sampleText", getString(R.string.ces));
        } else if (language.equals("nld")) {
            returnData.putExtra("sampleText", getString(R.string.nld));
        } else if (language.equals("eng")) {
            returnData.putExtra("sampleText", getString(R.string.eng));
        } else if (language.equals("epo")) {
            returnData.putExtra("sampleText", getString(R.string.epo));
        } else if (language.equals("fin")) {
            returnData.putExtra("sampleText", getString(R.string.fin));
        } else if (language.equals("fra")) {
            returnData.putExtra("sampleText", getString(R.string.fra));
        } else if (language.equals("deu")) {
            returnData.putExtra("sampleText", getString(R.string.deu));
        } else if (language.equals("ell")) {
            returnData.putExtra("sampleText", getString(R.string.ell));
        } else if (language.equals("hin")) {
            returnData.putExtra("sampleText", getString(R.string.hin));
        } else if (language.equals("hun")) {
            returnData.putExtra("sampleText", getString(R.string.hun));
        } else if (language.equals("isl")) {
            returnData.putExtra("sampleText", getString(R.string.isl));
        } else if (language.equals("ind")) {
            returnData.putExtra("sampleText", getString(R.string.ind));
        } else if (language.equals("ita")) {
            returnData.putExtra("sampleText", getString(R.string.ita));
        } else if (language.equals("kur")) {
            returnData.putExtra("sampleText", getString(R.string.kur));
        } else if (language.equals("lat")) {
            returnData.putExtra("sampleText", getString(R.string.lat));
        } else if (language.equals("mkd")) {
            returnData.putExtra("sampleText", getString(R.string.mkd));
        } else if (language.equals("nor")) {
            returnData.putExtra("sampleText", getString(R.string.nor));
        } else if (language.equals("pol")) {
            returnData.putExtra("sampleText", getString(R.string.pol));
        } else if (language.equals("por")) {
            returnData.putExtra("sampleText", getString(R.string.por));
        } else if (language.equals("ron")) {
            returnData.putExtra("sampleText", getString(R.string.ron));
        } else if (language.equals("rus")) {
            returnData.putExtra("sampleText", getString(R.string.rus));
        } else if (language.equals("srp")) {
            returnData.putExtra("sampleText", getString(R.string.srp));
        } else if (language.equals("slk")) {
            returnData.putExtra("sampleText", getString(R.string.slk));
        } else if (language.equals("spa")) {
            returnData.putExtra("sampleText", getString(R.string.spa));
        } else if (language.equals("swa")) {
            returnData.putExtra("sampleText", getString(R.string.swa));
        } else if (language.equals("swe")) {
            returnData.putExtra("sampleText", getString(R.string.swe));
        } else if (language.equals("tam")) {
            returnData.putExtra("sampleText", getString(R.string.tam));
        } else if (language.equals("tur")) {
            returnData.putExtra("sampleText", getString(R.string.tur));
        } else if (language.equals("vie")) {
            returnData.putExtra("sampleText", getString(R.string.vie));
        } else if (language.equals("cym")) {
            returnData.putExtra("sampleText", getString(R.string.cym));
        } else {
            result = TextToSpeech.LANG_NOT_SUPPORTED;
            returnData.putExtra("sampleText", "");
        }

        setResult(result, returnData);
        finish();
    }
}
