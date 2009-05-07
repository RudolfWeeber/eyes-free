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

using namespace android;

typedef unsigned int uint32; // that might have to go
typedef unsigned short uint16;

typedef void (synthDoneCB_t)(int, short *, int);

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
    // Synthesizes the text. When synthesis completes, the engine must call the given callback to notify the TTS API.
    virtual tts_result synth(const char *text, int code);
    // Synthesizes IPA text. When synthesis completes, the engine must call the given callback to notify the TTS API.
    // returns TTS_FEATURE_UNSUPPORTED if IPA is not supported, otherwise TTS_SUCCESS or TTS_FAILURE
    virtual tts_result synthIPA(const char *text, int code);
};

