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
#include <media/AudioSystem.h>

using namespace android;

// The callback for synthesis completed takes:
//    void *       - The userdata pointer set in the original synth call
//    uint32_t     - Track sampling rate in Hz
//    audio_format - The AudioSystem::audio_format enum
//    int          - The number of channels
//    int8_t *     - A buffer of audio data
//    size_t       - The size of the buffer
// Note about memory management:
//    the implementation of TtsSynthInterface is responsible for the management of the memory 
//    it allocates to store the synthesized speech. After the execution of the callback
//    to hand the synthesized data to the client of TtsSynthInterface, the TTS engine is
//    free to reuse or free the previously allocated memory.
//    In other words, the implementation of the "synthDoneCB" callback cannot use
//    the pointer to the buffer of audio samples outside of the callback itself.
typedef void (synthDoneCB_t)(void *, uint32_t, AudioSystem::audio_format, int, int8_t *, size_t);

class TtsSynthInterface;
extern "C" TtsSynthInterface* getTtsSynth();

enum tts_result {
    TTS_SUCCESS              = 0,
    TTS_FAILURE              = -1,
    TTS_PROPERTY_UNSUPPORTED = -2,
    TTS_VALUE_INVALID        = -3,
    TTS_FEATURE_UNSUPPORTED  = -4
};

class TtsSynthInterface
{
public:    
    // Initializes the TTS engine and returns whether initialization succeeded.
    virtual tts_result init(synthDoneCB_t synthDoneCBPtr);
    
    // Shuts down the TTS engine and releases all associated resouces.
    virtual tts_result shutdown();
    
    // Interrupts synthesis and flushes any synthesized data that hasn't been output yet.
    virtual tts_result stop();
    
    // Sets a property for the the TTS engine
    virtual tts_result set(const char *property, const char *value);
    
    // Retrieves a property from the TTS engine
    // Note that for "value" the caller will pass in a pointer to a char buffer
    // of size 100 - the string that the engine outputs for the requested value
    // must fit within that buffer.
    virtual tts_result get(const char *property, char *value);
    
    // Synthesizes the text. When synthesis completes, the engine must call the given callback to notify the TTS API.
    // Note about the format of the input: the text parameter may use the following elements
    // and their respective attributes as defined in the SSML 1.0 specification:
    //    * lang
    //    * say-as:
    //          o interpret-as
    //    * phoneme
    //    * voice:
    //          o gender,
    //          o age,
    //          o variant,
    //          o name
    //    * emphasis
    //    * break:
    //          o strength,
    //          o time
    //    * prosody:
    //          o pitch,
    //          o contour,
    //          o range,
    //          o rate,
    //          o duration,
    //          o volume
    //    * mark
    // Differences between this text format and SSML are:
    //    * full SSML documents are not supported
    //    * namespaces are not supported
    //    * language values are based on the Android conventions for localization as described in 
    //      the Android platform documentation on internationalization. This implies that language
    //      data is specified in the format xx-rYY, where xx is a two letter ISO 639-1 language code
    //      in lowercase and rYY is a two letter ISO 3166-1-alpha-2 language code in uppercase 
    //      preceded by a lowercase "r".
    virtual tts_result synth(const char *text, void *userdata);
    
    // Synthesizes IPA text. When synthesis completes, the engine must call the given callback to notify the TTS API.
    // returns TTS_FEATURE_UNSUPPORTED if IPA is not supported, otherwise TTS_SUCCESS or TTS_FAILURE
    virtual tts_result synthIPA(const char *text, void *userdata);
};

