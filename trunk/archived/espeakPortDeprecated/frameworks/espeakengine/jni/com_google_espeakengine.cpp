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

const char * supportedLangIso3[] = {
"afr",
"bos",
"yue",
"cmn",
"zho",
"hrv",
"ces",
"nld",
"eng",
"epo",
"fin",
"fra",
"deu",
"ell",
"hin",
"hun",
"isl",
"ind",
"ita",
"kur",
"lat",
"mkd",
"nor",
"pol",
"por",
"ron",
"rus",
"srp",
"slk",
"spa",
"swa",
"swe",
"tam",
"tur",
"vie",
"cym"
 };


const char * supportedLang[] = { 
"af",
"bs",
"zh-rHK",
"zh",
"zh",
"hr",
"cz",
"nl",
"en",
"eo",
"fi",
"fr",
"de",
"el",
"hi",
"hu",
"is",
"id",
"it",
"ku",
"la",
"mk",
"no",
"pl",
"pt",
"ro",
"ru",
"sr",
"sk",
"es",
"sw",
"sv",
"ta",
"tu",
"vi",
"cy"
 };

int languageCount = 36;

// Callback to the TTS API
synthDoneCB_t* ttsSynthDoneCBPointer;

char* currentLanguage = "en-rUS";
char* currentRate = "140";

char currentLang[10];
char currentCountry[10];
char currentVariant[10];


/* Functions internal to the eSpeak engine wrapper */
static void setSpeechRate(int speechRate)
{
    espeak_ERROR err = espeak_SetParameter(espeakRATE, speechRate, 0);
}


/* Functions exposed to the TTS API */

/* Callback from espeak.  Should call back to the TTS API */
static int eSpeakCallback(short *wav, int numsamples,
				      espeak_EVENT *events) {
    int8_t * castedWav = (int8_t *)wav;
    size_t bufferSize = 0;
    if (numsamples < 1){
      size_t silenceBufferSize = 2;
      int8_t *silence = new int8_t[silenceBufferSize]; // TODO: This will be a small memory leak, but do it this way for now because passing in an empty buffer can cause a crash.
      silence[0] = 0;
      silence[1] = 0;
      ttsSynthDoneCBPointer(events->user_data, 22050, AudioSystem::PCM_16_BIT, 1, silence, silenceBufferSize, TTS_SYNTH_DONE);
      return 1;
    }
    LOGI("eSpeak callback received! Sample count: %d", numsamples);
    bufferSize = numsamples * sizeof(short);    
    ttsSynthDoneCBPointer(events->user_data, 22050, AudioSystem::PCM_16_BIT, 1, castedWav, bufferSize, TTS_SYNTH_PENDING);
    LOGI("eSpeak callback processed!");
    return 0;  // continue synthesis (1 is to abort)
}


// Initializes the TTS engine and returns whether initialization succeeded
tts_result TtsEngine::init(synthDoneCB_t synthDoneCBPtr)
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

// Shutsdown the TTS engine
tts_result TtsEngine::shutdown( void )
{
    espeak_Terminate();
    return TTS_SUCCESS;
}


tts_result TtsEngine::loadLanguage(const char *lang, const char *country, const char *variant)
{   
    return TTS_FAILURE;
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
tts_result TtsEngine::setLanguage( const char * lang, const char * country, const char * variant )
{ 
    LOGE("lang input param: %s   country input param: %s", lang, country);

    char language[10];
    int langIndex = -1;
    for (int i = 0; i < languageCount; i ++)
        {
        if (strcmp(lang, supportedLangIso3[i]) == 0)
            {
            langIndex = i;
            break;
            }
        }
    if (langIndex < 0)
        {
        /* The language isn't supported.    */
        LOGE("TtsEngine::setLanguage called with unsupported language");
        return TTS_FAILURE;
        }

    
    strcpy(currentLang, lang);
    strcpy(currentCountry, country);

    strcpy(language, supportedLang[langIndex]);

    if (strcmp(language, "en") == 0){
      if (strcmp(country, "USA") == 0){
        strcpy(language, "en-rUS");
      }
      if (strcmp(country, "GBR") == 0){
        strcpy(language, "en-rGB");
      }
    }

    if (strcmp(language, "es") == 0){
      if (strcmp(country, "MEX") == 0){
        strcpy(language, "es-rMX");
      }
    }

    LOGE("Language: %s", language);


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


tts_support_result TtsEngine::isLanguageAvailable(const char *lang, const char *country,
            const char *variant) {
    // TODO: Make this account for data files!
    for (int i = 0; i < languageCount; i ++)
        {
        if (strcmp(lang, supportedLangIso3[i]) == 0)
            {
            return TTS_LANG_AVAILABLE;
            }
        }
    return TTS_LANG_NOT_SUPPORTED;
}

tts_result TtsEngine::getLanguage(char *language, char *country, char *variant)
{
    strcpy(language, currentLang);
    strcpy(country, currentCountry);
    strcpy(variant, "");
    return TTS_SUCCESS;
}


/** setAudioFormat
 * sets the audio format to use for synthesis, returns what is actually used.
 * @encoding - reference to encoding format
 * @rate - reference to sample rate
 * @channels - reference to number of channels
 * return tts_result
 * */
tts_result TtsEngine::setAudioFormat(AudioSystem::audio_format& encoding, uint32_t& rate,
            int& channels)
{
    // TODO: Fix this!
    return TTS_SUCCESS;
}

// Sets the property with the specified value
//
// TODO: add pitch property here
tts_result TtsEngine::setProperty(const char *property, const char *value, const size_t size)
{
    int rate;
    int pitch;
    int volume;

    /* Set a specific property for the engine.
       Supported properties include: language (locale), rate, pitch, volume.    */
    /* Sanity check */
    if (property == NULL) {
        LOGE("setProperty called with property NULL");
        return TTS_PROPERTY_UNSUPPORTED;
    }

    if (value == NULL) {
        LOGE("setProperty called with value NULL");
        return TTS_VALUE_INVALID;
    }

    if (strncmp(property, "language", 8) == 0) {
        // TODO: Fix this
        return TTS_SUCCESS;
    } else if (strncmp(property, "rate", 4) == 0) {
        rate = atoi(value);
        espeak_SetParameter(espeakRATE, rate, 0);
        // TODO: Fix this - use the return value here, don't just automatically return success!
        return TTS_SUCCESS;
    } else if (strncmp(property, "pitch", 5) == 0) {
        // TODO: Fix this
        return TTS_SUCCESS;
    } else if (strncmp(property, "volume", 6) == 0) {
        // TODO: Fix this
        return TTS_SUCCESS;
    }
    return TTS_PROPERTY_UNSUPPORTED;
}


// Sets the property with the specified value
//
// TODO: add pitch property here
tts_result TtsEngine::getProperty(const char *property, char *value, size_t *iosize)
{
    /* Get the property for the engine.
       This property was previously set by setProperty or by default.       */
    /* sanity check */
    if (property == NULL) {
        LOGE("getProperty called with property NULL");
        return TTS_PROPERTY_UNSUPPORTED;
    }

    if (value == NULL) {
        LOGE("getProperty called with value NULL");
        return TTS_VALUE_INVALID;
    }

    if (strncmp(property, "language", 8) == 0) {
        // TODO: Fix this
        return TTS_SUCCESS;
    } else if (strncmp(property, "rate", 4) == 0) {
        // TODO: Fix this
        return TTS_SUCCESS;
    } else if (strncmp(property, "pitch", 5) == 0) {
        // TODO: Fix this
        return TTS_SUCCESS;
    } else if (strncmp(property, "volume", 6) == 0) {
        // TODO: Fix this
        return TTS_SUCCESS;
    }

    /* Unknown property */
    LOGE("Unsupported property");
    return TTS_PROPERTY_UNSUPPORTED;
}

/** synthesizeText
 *  Synthesizes a text string.
 *  The text string could be annotated with SSML tags.
 *  @text     - text to synthesize
 *  @buffer   - buffer which will receive generated samples
 *  @bufferSize - size of buffer
 *  @userdata - pointer to user data which will be passed back to callback function
 *  return tts_result
*/
tts_result TtsEngine::synthesizeText( const char * text, int8_t * buffer, size_t bufferSize, void * userdata )
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
tts_result TtsEngine::synthesizeIpa( const char * ipa, int8_t * buffer, size_t bufferSize, void * userdata )
{
    // deprecated call
    return TTS_FAILURE;
}


// Interrupts synthesis
tts_result TtsEngine::stop()
{
    espeak_Cancel();
    return TTS_SUCCESS;
}



TtsEngine* getTtsEngine()
{
    return new TtsEngine();
}

}; // namespace android
