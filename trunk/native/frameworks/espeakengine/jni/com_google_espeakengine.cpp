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

using namespace android;

typedef unsigned int uint32;
typedef unsigned short uint16;

typedef void (synthDoneCB_t)(short *, int);  //Move this to a .h file


// Callback to the TTS API
//
// TODO: make this work for multiple callbacks
synthDoneCB_t* ttsSynthDoneCBPointer;


/* Functions internal to the eSpeak engine wrapper */

// Language will be specified according to the Android conventions for 
// localization as documented here: 
// http://developer.android.com/guide/topics/resources/resources-i18n.html
//
// language will be a string of the form "xx" or "xx-rYY", where xx is a 
// two letter ISO 639-1 language code in lowercase and rYY is a two letter ISO 
// 3166-1-alpha-2 language code in uppercase preceded by a lowercase "r".
// Note that the "-rYY" portion may be omitted if the region is unimportant.
//
// TODO: write an actual conversion function here
static void setLanguage(const char* language)
{   
    espeak_VOICE voice;
    memset( &voice, 0, sizeof(espeak_VOICE)); // Zero out the voice first
    voice.languages = language;
    voice.variant = 0;

    // Convert from xx-rYY format to the eSpeak format of xx-yy if needed
    if (strlen(language) > 2){
      char espeakLangStr[6];
      sprintf(espeakLangStr, "en-us");
      voice.languages = espeakLangStr;
    }

    espeak_ERROR err = espeak_SetVoiceByProperties(&voice);
}

static void setSpeechRate(int speechRate)
{
    espeak_ERROR err = espeak_SetParameter(espeakRATE, speechRate, 0);
}


/* Functions exposed to the TTS API */

/* Callback from espeak.  Should call back to the TTS API */
static int eSpeakCallback(short *wav, int numsamples,
				      espeak_EVENT *events) {    
    LOGI("eSpeak callback received!");
    ttsSynthDoneCBPointer(wav, numsamples);
    LOGI("eSpeak callback processed!");
    return 0;  // continue synthesis (1 is to abort)
}


// Initializes the TTS engine and returns whether initialization succeeded
extern "C" bool init()
{
    // TODO Make sure that the speech data is loaded in 
    // the directory /sdcard/espeak-data before calling this.
    int sampleRate = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS,
                                       4096, "/sdcard", 0);

    if (sampleRate <= 0) {
        LOGI("eSpeak initialization failed!");
        return false;
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

    return true;
}


// Synthesizes the text. When synthesis completes, the engine should use a callback to notify the TTS API.
extern "C" void synth(const char *text, synthDoneCB_t synthDoneCBPtr)
{
    ttsSynthDoneCBPointer = synthDoneCBPtr;
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
                       0);

    err = espeak_Synchronize();
}

// Synthesizes IPA text
extern "C" void synthIPA(const char *text, synthDoneCB_t synthDoneCBPtr)
{
        LOGI("Synth IPA not supported.");
}


// Interrupts synthesis
//
// TODO: check if there is any stop synth call that should be made here
extern "C" void stop()
{

}


// Sets the property with the specified value
//
// TODO: add pitch property here
extern "C" void set(const char *property, const char *value)
{
  if (strcmp(property, "language") == 0){
        setLanguage(value);
  } else if (strcmp(property, "rate") == 0){
        setSpeechRate(atoi(value));
  } else {
        LOGI("Unknown property!");
  }
}


// Shutsdown the TTS engine
extern "C" void shutdown()
{
    espeak_Terminate();
}
