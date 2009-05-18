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

//#define wchar_t owchar_t

#include <stdio.h>
#include <unistd.h>

#define LOG_TAG "SpeechSynthesis"

#include <utils/Log.h>
#include <nativehelper/jni.h>
#include <nativehelper/JNIHelp.h>
#include <android_runtime/AndroidRuntime.h>
#include <speak_lib.h>
#include <tts/TtsSynthInterface.h>
#include <media/AudioTrack.h>

#include <dlfcn.h>

using namespace android;


static struct fields_t {
    jfieldID    mNativeContext;
    jclass      mSpeechSynthesisClass;
    jmethodID   postNativeSpeechSynthesizedInJava; 
} fields;
static fields_t javaTTSFields;

struct tts_callback_cookie {
    jclass      tts_class;
    jobject     tts_ref;
};


tts_callback_cookie mCallbackData;
TtsSynthInterface * nativeSynthInterface;
FILE* targetFilePointer;

static AudioTrack* audout;
uint32_t audioTrack_sampleRate;
AudioSystem::audio_format audioTrack_format;
int audioTrack_channelCount;

void prepAudioTrack(uint32_t rate, AudioSystem::audio_format format, int channel)
{   
    // Don't bother creating a new audiotrack object if the current 
    // object is already set.
    if ( (rate == audioTrack_sampleRate) && 
         (format == audioTrack_format) && 
         (channel == audioTrack_channelCount) ){
        return;
    }
    if (audout){
        audout->stop();
        delete audout;
    }
    audioTrack_sampleRate = rate;
    audioTrack_format = format;
    audioTrack_channelCount = channel;
    audout = new AudioTrack(AudioSystem::MUSIC, rate, format, channel, 4096, 0, 0, 0, 0);
    if (audout->initCheck() != NO_ERROR) {
      LOGI("AudioTrack error");
    } else {
      LOGI("AudioTrack OK");
      audout->start();
      LOGI("AudioTrack started");
    }
}

/* Callback from espeak.  Directly speaks using AudioTrack. */
static void ttsSynthDoneCB(void * userdata, uint32_t rate, AudioSystem::audio_format format, int channel, int8_t *wav, size_t bufferSize) {
    char buf[100];
    sprintf(buf, "ttsSynthDoneCallback: %d bytes", bufferSize);
    LOGI(buf);

    if ((int)userdata == 0){
        LOGI("Direct speech");
        if (wav == NULL) {
            LOGI("Null: speech has completed");
        }
        if (bufferSize > 0){
            prepAudioTrack(rate, format, channel);
            audout->write(wav, bufferSize);
            sprintf(buf, "AudioTrack wrote: %d bytes", bufferSize);
            LOGI(buf);
        }
    } if ((int)userdata == 1){
        LOGI("Save to file");
        if (wav == NULL) {
            LOGI("Null: speech has completed");
        }
        if (bufferSize > 0){
            fwrite(wav, 1, bufferSize, targetFilePointer);
        }
    }
    return;
}

static void
com_google_tts_SpeechSynthesis_native_setup(
    JNIEnv *env, jobject thiz, jobject weak_this, jstring nativeSoLib)
{
    jclass clazz = env->GetObjectClass(thiz);
    mCallbackData.tts_class = (jclass)env->NewGlobalRef(clazz);
    mCallbackData.tts_ref = env->NewGlobalRef(weak_this);
    javaTTSFields.postNativeSpeechSynthesizedInJava = NULL;
    javaTTSFields.postNativeSpeechSynthesizedInJava = env->GetStaticMethodID(
            clazz,
            "postNativeSpeechSynthesizedInJava", "(Ljava/lang/Object;II)V");
    if (javaTTSFields.postNativeSpeechSynthesizedInJava == NULL) {
        LOGE("Can't find TTS.%s", "postNativeSpeechSynthesizedInJava");
        return;
    }
    audout = NULL;

    prepAudioTrack(0, AudioSystem::PCM_16_BIT, 0);

    const char *nativeSoLibNativeString = env->GetStringUTFChars(nativeSoLib, 0);

    void *engine_lib_handle = dlopen(nativeSoLibNativeString, RTLD_NOW | RTLD_LOCAL);
    if(engine_lib_handle==NULL) {
       LOGI("engine_lib_handle==NULL");
    }

    TtsSynthInterface *(*get_TtsSynthInterface)() = reinterpret_cast<TtsSynthInterface* (*)()>(dlsym(engine_lib_handle, "getTtsSynth"));
    nativeSynthInterface = (*get_TtsSynthInterface)();

    nativeSynthInterface->init(ttsSynthDoneCB);
LOGI("Setup complete");
}


static void
com_google_tts_SpeechSynthesis_setLanguage(JNIEnv *env, jobject thiz, jstring language)
{   
    const char *langNativeString = env->GetStringUTFChars(language, 0);
    nativeSynthInterface->set("language", langNativeString);
    env->ReleaseStringUTFChars(language, langNativeString);
}

static void
com_google_tts_SpeechSynthesis_setSpeechRate(JNIEnv *env, jobject thiz, 
                                             int speechRate)
{
    char buffer [10];
    sprintf(buffer, "%d", speechRate);
    nativeSynthInterface->set("rate", buffer);
}

static void
com_google_tts_SpeechSynthesis_native_finalize(JNIEnv *env,
					       jobject thiz)
{
    nativeSynthInterface->shutdown();
    delete audout;
}


static void
com_google_tts_SpeechSynthesis_synthesizeToFile(JNIEnv *env, jobject thiz,
						jstring textJavaString,
						jstring filenameJavaString)
{
    const char *filenameNativeString = env->GetStringUTFChars(filenameJavaString, 0);
    const char *textNativeString = env->GetStringUTFChars(textJavaString, 0);


    targetFilePointer = fopen(filenameNativeString, "wb");
    // Write 44 blank bytes for WAV header, then come back and fill them in
    // after we've written the audio data
    char header[44];
    fwrite(header, 1, 44, targetFilePointer);

    unsigned int unique_identifier;

    nativeSynthInterface->synth(textNativeString, (void *)1);

    long filelen = ftell(targetFilePointer);

    int samples = (((int)filelen) - 44) / 2;
    header[0] = 'R';
    header[1] = 'I';
    header[2] = 'F';
    header[3] = 'F';
    ((uint32_t *)(&header[4]))[0] = filelen - 8;
    header[8] = 'W';
    header[9] = 'A';
    header[10] = 'V';
    header[11] = 'E';

    header[12] = 'f';
    header[13] = 'm';
    header[14] = 't';
    header[15] = ' ';

    ((uint32_t *)(&header[16]))[0] = 16;  // size of fmt

    ((unsigned short *)(&header[20]))[0] = 1;  // format
    ((unsigned short *)(&header[22]))[0] = 1;  // channels
    ((uint32_t *)(&header[24]))[0] = 22050;  // samplerate
    ((uint32_t *)(&header[28]))[0] = 44100;  // byterate
    ((unsigned short *)(&header[32]))[0] = 2;  // block align
    ((unsigned short *)(&header[34]))[0] = 16;  // bits per sample

    header[36] = 'd';
    header[37] = 'a';
    header[38] = 't';
    header[39] = 'a';

    ((uint32_t *)(&header[40]))[0] = samples * 2;  // size of data

    // Skip back to the beginning and rewrite the header
    fseek(targetFilePointer, 0, SEEK_SET);
    fwrite(header, 1, 44, targetFilePointer);

    fflush(targetFilePointer);
    fclose(targetFilePointer);

    env->ReleaseStringUTFChars(textJavaString, textNativeString);
    env->ReleaseStringUTFChars(filenameJavaString, filenameNativeString);
}


static void
com_google_tts_SpeechSynthesis_speak(JNIEnv *env, jobject thiz,
						jstring textJavaString)
{
    audout->stop();
    audout->start();
    const char *textNativeString = env->GetStringUTFChars(textJavaString, 0);
    nativeSynthInterface->synth(textNativeString, (void *)0);
    env->ReleaseStringUTFChars(textJavaString, textNativeString);
}


static void
com_google_tts_SpeechSynthesis_stop(JNIEnv *env, jobject thiz)
{
    nativeSynthInterface->stop();
    audout->stop();
}


static void
com_google_tts_SpeechSynthesis_shutdown(JNIEnv *env, jobject thiz)
{
    nativeSynthInterface->shutdown();
}


static void
com_google_tts_SpeechSynthesis_playAudioBuffer(JNIEnv *env, jobject thiz, int bufferPointer, int bufferSize)
{
        short* wav = (short*) bufferPointer;
        audout->write(wav, bufferSize);
        char buf[100];
        sprintf(buf, "AudioTrack wrote: %d bytes", bufferSize);
        LOGI(buf);
}

JNIEXPORT jstring JNICALL
com_google_tts_SpeechSynthesis_getLanguage(JNIEnv *env, jobject thiz)
{
    char buf[100];
    nativeSynthInterface->get("language", buf);
    return env->NewStringUTF(buf);
}

JNIEXPORT int JNICALL
com_google_tts_SpeechSynthesis_getRate(JNIEnv *env, jobject thiz)
{
    char buf[100];
    nativeSynthInterface->get("rate", buf);
    return atoi(buf);
}

// Dalvik VM type signatures
static JNINativeMethod gMethods[] = {
    {   "stop",             
        "()V",
        (void*)com_google_tts_SpeechSynthesis_stop
    },
    {   "speak",             
        "(Ljava/lang/String;)V",
        (void*)com_google_tts_SpeechSynthesis_speak
    },
    {   "synthesizeToFile",             
        "(Ljava/lang/String;Ljava/lang/String;)V",
        (void*)com_google_tts_SpeechSynthesis_synthesizeToFile
    },
    {   "setLanguage",
        "(Ljava/lang/String;)V",
        (void*)com_google_tts_SpeechSynthesis_setLanguage
    },
    {   "setSpeechRate",
        "(I)V",
        (void*)com_google_tts_SpeechSynthesis_setSpeechRate
    },
    {   "playAudioBuffer",
        "(II)V",
        (void*)com_google_tts_SpeechSynthesis_playAudioBuffer
    },
    {   "getLanguage",             
        "()Ljava/lang/String;",
        (void*)com_google_tts_SpeechSynthesis_getLanguage
    },
    {   "getRate",             
        "()I",
        (void*)com_google_tts_SpeechSynthesis_getRate
    },
    {   "shutdown",             
        "()V",
        (void*)com_google_tts_SpeechSynthesis_shutdown
    },
    {   "native_setup",
        "(Ljava/lang/Object;Ljava/lang/String;)V",
        (void*)com_google_tts_SpeechSynthesis_native_setup
    },
    {   "native_finalize",     
        "()V",
        (void*)com_google_tts_SpeechSynthesis_native_finalize
    }
};

static const char* const kClassPathName = "com/google/tts/SpeechSynthesis";

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jint result = -1;
    jclass clazz;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        LOGE("ERROR: GetEnv failed\n");
        goto bail;
    }
    assert(env != NULL);

    clazz = env->FindClass(kClassPathName);
    if (clazz == NULL) {
        LOGE("Can't find com/google/tts/SpeechSynthesis");
        goto bail;
    }

    fields.mNativeContext = env->GetFieldID(clazz, "mNativeContext", "I");
    if (fields.mNativeContext == NULL) {
        LOGE("Can't find SpeechSynthesis.mNativeContext");
        goto bail;
    }

    if (jniRegisterNativeMethods(
            env, kClassPathName, gMethods, NELEM(gMethods)) < 0)
        goto bail;

    /* success -- return valid version number */
    result = JNI_VERSION_1_4;

 bail:
    return result;
}
