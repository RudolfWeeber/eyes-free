/*
 * Copyright (C) 2008 Google Inc.
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

#include <stdio.h>
#include <unistd.h>

#define LOG_TAG "eSpeak Engine"

#include <utils/Log.h>
#include <android_runtime/AndroidRuntime.h>
#include <speak_lib.h>
#include <tts/TtsEngine.h>


namespace android {

// Callback to the TTS API
synthDoneCB_t* ttsSynthDoneCBPointer;

char* currentLanguage = "en-rUS";
char* currentRate = "140";


/* Functions internal to the eSpeak engine wrapper */
static void setSpeechRate(int speechRate)
{
    espeak_ERROR err = espeak_SetParameter(espeakRATE, speechRate, 0);
}


/* Functions exposed to the TTS API */

/* Callback from espeak.  Should call back to the TTS API */
static int eSpeakCallback(short *wav, int numsamples,
				      espeak_EVENT *events) {    
    LOGI("eSpeak callback received!");
    size_t bufferSize = numsamples * sizeof(short);
    ttsSynthDoneCBPointer(events->user_data, 22050, AudioSystem::PCM_16_BIT, 1, (int8_t *)wav, bufferSize);
    LOGI("eSpeak callback processed!");
    return 0;  // continue synthesis (1 is to abort)
}


// Initializes the TTS engine and returns whether initialization succeeded
tts_result TtsEngine::init(synthDoneCB_t* synthDoneCBPtr)
{
    // TODO Make sure that the speech data is loaded in 
    // the directory /sdcard/espeak-data before calling this.
    int sampleRate = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS,
                                       4096, "/sdcard", 0);

    if (sampleRate <= 0) {
        LOGI("eSpeak initialization failed!");
        return TTS_FAILURE;
    }
    espeak_SetSynthCallback(eSpeakCallback);

    int speechRate = 140;
    espeak_ERROR err = espeak_SetParameter(espeakRATE, speechRate, 0);

    espeak_VOICE voice;
    memset( &voice, 0, sizeof(espeak_VOICE)); // Zero out the voice first
    const char *langNativeString = "en-us";   //Default to US English
    voice.languages = langNativeString;
    voice.variant = 0;
    err = espeak_SetVoiceByProperties(&voice);

    ttsSynthDoneCBPointer = synthDoneCBPtr;
    return TTS_SUCCESS;
}


// Synthesizes the text. When synthesis completes, the engine should use a callback to notify the TTS API.
tts_result TtsEngine::synthesizeText(const char *text, void *userdata)
{
    espeak_SetSynthCallback(eSpeakCallback);

    unsigned int unique_identifier;
    espeak_ERROR err;

    err = espeak_Synth(text,
                       strlen(text),
                       0,  // position
                       POS_CHARACTER,
                       0,  // end position (0 means no end position)
                       espeakCHARS_UTF8,
                       &unique_identifier,
                       userdata);

    err = espeak_Synchronize();
    return TTS_SUCCESS;
}

// Synthesizes IPA text
tts_result TtsEngine::synthesizeIpa(const char *text, void *userdata)
{
    LOGI("Synth IPA not supported.");
    return TTS_FEATURE_UNSUPPORTED;
}


// Interrupts synthesis
tts_result TtsEngine::stop()
{
    espeak_Cancel();
    return TTS_SUCCESS;
}

tts_result TtsEngine::loadLanguage(const char* language, const size_t size)
{   
    LOGI("loadLanguage not supported.");
    return TTS_FEATURE_UNSUPPORTED;
}

// Language will be specified according to the Android conventions for 
// localization as documented here: 
// http://developer.android.com/guide/topics/resources/resources-i18n.html
//
// language will be a string of the form "xx" or "xx-rYY", where xx is a 
// two letter ISO 639-1 language code in lowercase and rYY is a two letter ISO 
// 3166-1-alpha-2 language code in uppercase preceded by a lowercase "r".
// Note that the "-rYY" portion may be omitted if the region is unimportant.
//
tts_result TtsEngine::setLanguage(const char* language, const size_t size)
{   
    espeak_VOICE voice;
    memset(&voice, 0, sizeof(espeak_VOICE)); // Zero out the voice first
    voice.variant = 0;
    char espeakLangStr[6];
    if ((strlen(language) != 2) && (strlen(language) != 6)){
        LOGI("Error: Invalid language. Language must be in either xx or xx-rYY format.");
        return TTS_VALUE_INVALID;
    }
    if (strcmp(language, "en-rUS") == 0){
        strcpy(espeakLangStr, "en-us");
    } else if (strcmp(language, "en-rGB") == 0){
        strcpy(espeakLangStr, "en-uk");
    } else if (strcmp(language, "es-rMX") == 0){
        strcpy(espeakLangStr, "es-la");
    } else if (strcmp(language, "zh-rHK") == 0){
        strcpy(espeakLangStr, "zh");
        voice.variant = 5;
    } else {
        espeakLangStr[0] = language[0];
        espeakLangStr[1] = language[1];
        espeakLangStr[2] = 0;
        // Bail out and do nothing if the language is not supported by eSpeak
        if ((strcmp(language, "af") != 0) && 
            (strcmp(language, "bs") != 0) && 
            (strcmp(language, "zh") != 0) && 
            (strcmp(language, "hr") != 0) && 
            (strcmp(language, "cz") != 0) && 
            (strcmp(language, "nl") != 0) && 
            (strcmp(language, "en") != 0) && 
            (strcmp(language, "eo") != 0) && 
            (strcmp(language, "fi") != 0) && 
            (strcmp(language, "fr") != 0) && 
            (strcmp(language, "de") != 0) && 
            (strcmp(language, "el") != 0) && 
            (strcmp(language, "hi") != 0) && 
            (strcmp(language, "hu") != 0) && 
            (strcmp(language, "is") != 0) && 
            (strcmp(language, "id") != 0) && 
            (strcmp(language, "it") != 0) && 
            (strcmp(language, "ku") != 0) && 
            (strcmp(language, "la") != 0) && 
            (strcmp(language, "mk") != 0) && 
            (strcmp(language, "no") != 0) && 
            (strcmp(language, "pl") != 0) && 
            (strcmp(language, "pt") != 0) && 
            (strcmp(language, "ro") != 0) && 
            (strcmp(language, "ru") != 0) && 
            (strcmp(language, "sr") != 0) && 
            (strcmp(language, "sk") != 0) && 
            (strcmp(language, "es") != 0) && 
            (strcmp(language, "sw") != 0) && 
            (strcmp(language, "sv") != 0) && 
            (strcmp(language, "ta") != 0) && 
            (strcmp(language, "tr") != 0) && 
            (strcmp(language, "vi") != 0) && 
            (strcmp(language, "cy") != 0) ){
            LOGI("Error: Unsupported language.");
            return TTS_PROPERTY_UNSUPPORTED;
        }
        // Use American English as the default English
        if (strcmp(language, "en") == 0) {
            strcpy(espeakLangStr, "en-us");
        }
    }
    voice.languages = espeakLangStr;
    espeak_ERROR err = espeak_SetVoiceByProperties(&voice);
    currentLanguage = new char [strlen(language)];
    strcpy(currentLanguage, language);
    return TTS_SUCCESS;
}

tts_result TtsEngine::getLanguage(char *value, size_t *iosize)
{
    strcpy(value, currentLanguage);
    return TTS_SUCCESS;
}

// Sets the property with the specified value
//
// TODO: add pitch property here
tts_result TtsEngine::setProperty(const char *property, const char *value, const size_t size)
{
    if (strcmp(property, "rate") == 0){
        setSpeechRate(atoi(value));
        currentRate = new char [size];
        strcpy(currentRate, value);
    } else {
        LOGI("Unknown property!");
        return TTS_PROPERTY_UNSUPPORTED;
    }
    return TTS_SUCCESS;
}


// Sets the property with the specified value
//
// TODO: add pitch property here
tts_result TtsEngine::getProperty(const char *property, char *value, size_t *iosize)
{
    if (strcmp(property, "language") == 0){
        strcpy(value, currentLanguage);
    } else if (strcmp(property, "rate") == 0){
        strcpy(value, currentRate);
    } else {
        LOGI("Unknown property!");
        return TTS_PROPERTY_UNSUPPORTED;
    }
    return TTS_SUCCESS;
}


// Shutsdown the TTS engine
tts_result TtsEngine::shutdown()
{
    espeak_Terminate();
    return TTS_SUCCESS;
}


TtsEngine* getTtsEngine()
{
    return new TtsEngine();
}

}; // namespace android