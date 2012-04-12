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
#include <string.h>
#include <unistd.h>

#define LOG_TAG "PlaceHolder TTS Engine"

//#include <utils/Log.h>
//#include <android_runtime/AndroidRuntime.h>
//#include <speak_lib.h>
#include <tts/TtsEngine.h>
#include <PlaceHolderTTS.h>

#if 0
  #define LOGI(...) printf(__VA_ARGS__)
  #define LOGE(...) frintf(stderr, __VA_ARGS__)
#else
  #define LOGI(...)
  #define LOGE(...)
#endif

namespace android {

static const char *MY_LANGUAGE = "en";

// Callback to the TTS API
static synthDoneCB_t* ttsSynthDoneCBPointer;

/* Functions exposed to the TTS API */

// Initializes the TTS engine and returns whether initialization succeeded
tts_result TtsEngine::init(synthDoneCB_t synthDoneCBPtr)
{
    LOGI("TtsEngine::init");
    ttsSynthDoneCBPointer = synthDoneCBPtr;
    PlaceHolderTTS_Init();
    return TTS_SUCCESS;
}

// Shutsdown the TTS engine
tts_result TtsEngine::shutdown( void )
{
    LOGI("TtsEngine::shutdown");
    return TTS_SUCCESS;
}


tts_result TtsEngine::loadLanguage(const char *lang, const char *country, const char *variant)
{
    LOGI("TtsEngine::loadLanguage: lang=%s, country=%s, variant=%s", lang, country, variant);
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
    LOGI("TtsEngine::setLanguage: lang=%s, country=%s, variant=%s", lang, country, variant);
    return strcmp(lang, MY_LANGUAGE) == 0 ? TTS_SUCCESS : TTS_FAILURE;
}


tts_support_result TtsEngine::isLanguageAvailable(const char *lang, const char *country,
            const char *variant) {
    LOGI("TtsEngine::isLanguageAvailable: lang=%s, country=%s, variant=%s", lang, country, variant);
    return strcmp(lang, MY_LANGUAGE) == 0 ? TTS_LANG_AVAILABLE : TTS_LANG_NOT_SUPPORTED;
}

tts_result TtsEngine::getLanguage(char *language, char *country, char *variant)
{
    LOGI("TtsEngine::getLanguage");
    strcpy(language, MY_LANGUAGE);
    strcpy(country, "");
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
    LOGI("TtsEngine::setAudioFormat");
    // TODO: Fix this!
    return TTS_SUCCESS;
}

// Sets the property with the specified value.
tts_result TtsEngine::setProperty(const char *property, const char *value,
            const size_t size)
{
    LOGI("TtsEngine::setProperty: property=%s, value=%s, size=%lu",
         property, value, (unsigned long) size);

    /* Set a specific property for the engine.  */

    /* Sanity check */
    if (property == NULL) {
        LOGE("setProperty called with property NULL");
        return TTS_PROPERTY_UNSUPPORTED;
    }

    if (value == NULL) {
        LOGE("setProperty called with value NULL");
        return TTS_VALUE_INVALID;
    }

#if 1 // This is an example only...
    if (strncmp(property, "foo", 3) == 0) { // FIXME(fergus): can we use strcmp rather than strncmp here?
        if (strcmp(value, "bar") == 0) {
            return TTS_SUCCESS;
        } else {
            LOGE("can't set property 'foo' to anything except 'bar'");
            return TTS_VALUE_INVALID;
        }
    }
#endif

    return TTS_PROPERTY_UNSUPPORTED;
}


tts_result TtsEngine::getProperty(const char *property, char *value, size_t *iosize)
{
    LOGI("TtsEngine::getProperty: property=%s, value=%s, iosize=%lu",
         property, value, (unsigned long) *iosize);

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

#if 1 // Example only...
    if (strncmp(property, "foo", 3) == 0) {
        strncpy(value, "bar", *iosize); // FIXME(fergus): is this right?
        *iosize = strlen("bar");
        return TTS_SUCCESS;
    }
#endif

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
    LOGI("TtsEngine::synthesizeText: text=%s, bufferSize=%lu", text, (unsigned long) bufferSize);

    // STUB: you will need to implement this.
    // Your implementation will need to call ttsSynthDoneCBPointer(...).

    return TTS_SUCCESS;
}

// Synthesizes IPA text.
tts_result TtsEngine::synthesizeIpa( const char * ipa, int8_t * buffer, size_t bufferSize, void * userdata )
{
    LOGI("TtsEngine::synthesizeIpa");

    // This API function is deprecated, so don't implementing this!
    return TTS_FAILURE;
}


// Interrupts synthesis.
tts_result TtsEngine::stop()
{
    LOGI("TtsEngine::stop");

    // STUB: you will need to implement this.

    return TTS_SUCCESS;
}



TtsEngine* getTtsEngine()
{
    LOGI("TtsEngine::getTtsEngine");
    return new TtsEngine();
}

}; // namespace android
