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

package com.google.tts;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.HashMap;

/*
 * Checks if the voice data for the SVOX Pico Engine is present on the
 * sd card.
 */
public class CheckVoiceData extends Activity {

    // The following constants are the same path constants as the ones defined
    // in external/svox/pico/tts/com_svox_picottsengine.cpp
    private final static String ESPEAK_DATA_PATH = Environment.getExternalStorageDirectory()
            + "/espeak-data/";

    private final static String[] baseDataFiles = {
            "af_dict", "config", "cs_dict", "cy_dict", "de_dict", "el_dict", "en_dict", "eo_dict",
            "es_dict", "fi_dict", "fr_dict", "grc_dict", "hbs_dict", "hi_dict", "hu_dict",
            "id_dict", "is_dict", "it_dict", "jbo_dict", "ku_dict", "la_dict", "mk_dict",
            "nl_dict", "no_dict", "phondata", "phonindex", "phontab", "pl_dict", "pt_dict",
            "ro_dict", "ru_dict", "sk_dict", "sv_dict", "sw_dict", "ta_dict", "tr_dict", "vi_dict",
            "zh_dict", "zhy_dict", "mbrola/dummyfile", "mbrola_ph/af1_phtrans",
            "mbrola_ph/ca1_phtrans", "mbrola_ph/cr1_phtrans", "mbrola_ph/cs_phtrans",
            "mbrola_ph/de2_phtrans", "mbrola_ph/de4_phtrans", "mbrola_ph/de6_phtrans",
            "mbrola_ph/en1_phtrans", "mbrola_ph/es_phtrans", "mbrola_ph/es4_phtrans",
            "mbrola_ph/fr1_phtrans", "mbrola_ph/gr2_phtrans", "mbrola_ph/grc-de6_phtrans",
            "mbrola_ph/hu1_phtrans", "mbrola_ph/id1_phtrans", "mbrola_ph/in1_phtrans",
            "mbrola_ph/it3_phtrans", "mbrola_ph/la1_phtrans", "mbrola_ph/nl_phtrans",
            "mbrola_ph/pl1_phtrans", "mbrola_ph/pt_phtrans", "mbrola_ph/ptbr_phtrans",
            "mbrola_ph/ptbr4_phtrans", "mbrola_ph/ro1_phtrans", "mbrola_ph/sv_phtrans",
            "mbrola_ph/sv2_phtrans", "mbrola_ph/us_phtrans", "mbrola_ph/us3_phtrans",
            "soundicons/dummyfile", "voices/af", "voices/bs", "voices/cs", "voices/cy",
            "voices/de", "voices/default", "voices/el", "voices/eo", "voices/es", "voices/es-la",
            "voices/fi", "voices/fr", "voices/fr-be", "voices/grc", "voices/hi", "voices/hr",
            "voices/hu", "voices/id", "voices/is", "voices/it", "voices/jbo", "voices/ku",
            "voices/la", "voices/mk", "voices/nl", "voices/no", "voices/pl", "voices/pt",
            "voices/pt-pt", "voices/ro", "voices/ru", "voices/sk", "voices/sr", "voices/sv",
            "voices/sw", "voices/ta", "voices/tr", "voices/vi", "voices/zh", "voices/zhy",
            "voices/!v/croak", "voices/!v/f1", "voices/!v/f2", "voices/!v/f3", "voices/!v/f4",
            "voices/!v/m1", "voices/!v/m2", "voices/!v/m3", "voices/!v/m4", "voices/!v/m5",
            "voices/!v/m6", "voices/!v/whisper", "voices/en/en", "voices/en/en-n",
            "voices/en/en-r", "voices/en/en-rp", "voices/en/en-sc", "voices/en/en-wi",
            "voices/en/en-wm", "voices/mb/mb-af1", "voices/mb/mb-af1-en", "voices/mb/mb-br1",
            "voices/mb/mb-br3", "voices/mb/mb-br4", "voices/mb/mb-cr1", "voices/mb/mb-cz2",
            "voices/mb/mb-de2", "voices/mb/mb-de4", "voices/mb/mb-de4-en", "voices/mb/mb-de5",
            "voices/mb/mb-de5-en", "voices/mb/mb-de6", "voices/mb/mb-de6-grc", "voices/mb/mb-de7",
            "voices/mb/mb-en1", "voices/mb/mb-es1", "voices/mb/mb-es2", "voices/mb/mb-fr1",
            "voices/mb/mb-fr1-en", "voices/mb/mb-fr4", "voices/mb/mb-fr4-en", "voices/mb/mb-gr2",
            "voices/mb/mb-gr2-en", "voices/mb/mb-hu1", "voices/mb/mb-hu1-en", "voices/mb/mb-id1",
            "voices/mb/mb-it3", "voices/mb/mb-it4", "voices/mb/mb-la1", "voices/mb/mb-nl2",
            "voices/mb/mb-nl2-en", "voices/mb/mb-pl1", "voices/mb/mb-pl1-en", "voices/mb/mb-ro1",
            "voices/mb/mb-ro1-en", "voices/mb/mb-sw1", "voices/mb/mb-sw1-en", "voices/mb/mb-sw2",
            "voices/mb/mb-sw2-en", "voices/mb/mb-us1", "voices/mb/mb-us2", "voices/mb/mb-us3"
    };

    private final static String[] supportedLanguages = {
            "afr", "bos", "zho", "hrv", "ces", "nld", "eng", "eng-USA", "eng-GBR",
            "epo", "fin", "fra", "deu", "ell", "hin", "hun", "isl", "ind", "ita", "kur", "lat",
            "mkd", "nor", "pol", "por", "ron", "rus", "srp", "slk", "spa", "spa-MEX", "swa", "swe",
            "tam", "tur", "vie", "cym"
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int result = TextToSpeechBeta.Engine.CHECK_VOICE_DATA_PASS;
        Intent returnData = new Intent();
        returnData.putExtra(TextToSpeechBeta.Engine.EXTRA_VOICE_DATA_ROOT_DIRECTORY, ESPEAK_DATA_PATH);
        returnData.putExtra(TextToSpeechBeta.Engine.EXTRA_VOICE_DATA_FILES, baseDataFiles);
        returnData.putExtra(TextToSpeechBeta.Engine.EXTRA_VOICE_DATA_FILES_INFO, baseDataFiles);

        ArrayList<String> available = new ArrayList<String>();
        ArrayList<String> unavailable = new ArrayList<String>();

        // TODO (clchen): Check each language INDIVIDUALLY
        boolean passedAllChecks = true;

        for (int i = 0; i < baseDataFiles.length; i++) {
            if (!fileExists(baseDataFiles[i])) {
                passedAllChecks = false;
                break;
            }
        }

        if (passedAllChecks) {
            for (int i = 0; i < supportedLanguages.length; i++) {
                available.add(supportedLanguages[i]);
            }
        } else {
            for (int i = 0; i < supportedLanguages.length; i++) {
                unavailable.add(supportedLanguages[i]);
            }
        }

        returnData.putStringArrayListExtra("availableVoices", available);
        returnData.putStringArrayListExtra("unavailableVoices", unavailable);
        setResult(result, returnData);
        finish();
    }

    private boolean fileExists(String filename) {
        File tempFile = new File(ESPEAK_DATA_PATH + filename);
        File tempFileSys = new File(ESPEAK_DATA_PATH + filename);
        if ((!tempFile.exists()) && (!tempFileSys.exists())) {
            return false;
        }
        return true;
    }

}
