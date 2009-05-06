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
#include <media/AudioTrack.h>

#include <dlfcn.h>

using namespace android;


typedef void (synthDoneCB_t)(short *, int);  //Move this to a .h file!

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


typedef unsigned int uint32;
typedef unsigned short uint16;


static AudioTrack* audout;
tts_callback_cookie mCallbackData;



void * engine_lib_handle;
bool (*engineInit)();
void (*engineSynth)(const char *, synthDoneCB_t);
void (*engineSynthIPA)(const char *, synthDoneCB_t);
void (*engineSet)(const char *, const char *);
void (*engineStop)();
void (*engineShutdown)();




/* Callback from espeak.  Directly speaks using AudioTrack. */
static void ttsSynthDoneCBSpeak(short *wav, int numsamples) {
    char buf[100];
    sprintf(buf, "ttsSynthDoneCallback: %d samples", numsamples);
    LOGI(buf);

    if (wav == NULL) {
        LOGI("Null: speech has completed");
    }

      if (numsamples > 0){
        int bufferSize = sizeof(short) * numsamples;
        audout->write(wav, bufferSize);
        sprintf(buf, "AudioTrack wrote: %d bytes", bufferSize);
        LOGI(buf);
      }

    return;
}

// TODO: How do we get the user_data back here?
static void ttsSynthDoneCBSave(short *wav, int numsamples) {
    // The user data should contain the file pointer of the file to write to
//    void* user_data = events->user_data;
//    FILE* fp = static_cast<FILE *>(user_data);

    // Write all of the samples
//    fwrite(wav, sizeof(short), numsamples, fp);
}


static void
com_google_tts_SpeechSynthesis_native_setup(
    JNIEnv *env, jobject thiz, jobject weak_this, jstring language,
    int languageVariant, int speechRate)
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

    audout = new AudioTrack(AudioSystem::MUSIC, 22050, AudioSystem::PCM_16_BIT, 1, 4096, 0, 0, 0, 0);
    if (audout->initCheck() != NO_ERROR) {
      LOGI("AudioTrack error");
    } else {
      LOGI("AudioTrack OK");
      audout->start();
      LOGI("AudioTrack started");
    }

    // TODO: Handle dynamically getting the filename for dlopen
    engine_lib_handle = dlopen("/data/data/com.google.tts/lib/libespeakengine.so", RTLD_NOW | RTLD_LOCAL);
//    engine_lib_handle = dlopen("/data/data/com.google.marvin.espeak/lib/libespeakengine.so", RTLD_NOW | RTLD_LOCAL);
    if(engine_lib_handle==NULL) {
       LOGI("engine_lib_handle==NULL");
    }


    engineInit = reinterpret_cast<bool (*)()>(dlsym(engine_lib_handle, "init"));
    if(engineInit==NULL) {
       LOGI("engineInit==NULL");
    }
    engineInit();

    engineSynth = reinterpret_cast<void (*)(const char *, synthDoneCB_t)>(dlsym(engine_lib_handle, "synth"));
    if(engineSynth==NULL) {
       LOGI("engineSynth==NULL");
    }

    engineSynthIPA = reinterpret_cast<void (*)(const char *, synthDoneCB_t)>(dlsym(engine_lib_handle, "synthIPA"));
    if(engineSynthIPA==NULL) {
       LOGI("engineSynthIPA==NULL");
    }

    engineStop = reinterpret_cast<void (*)()>(dlsym(engine_lib_handle, "stop"));
    if(engineStop==NULL) {
       LOGI("engineStop==NULL");
    }

    engineSet = reinterpret_cast<void (*)(const char *, const char *)>(dlsym(engine_lib_handle, "set"));
    if(engineSet==NULL) {
       LOGI("engineSet==NULL");
    }

    engineShutdown = reinterpret_cast<void (*)()>(dlsym(engine_lib_handle, "shutdown"));
    if(engineShutdown==NULL) {
       LOGI("engineShutdown==NULL");
    }
}

// TODO: Remove the "variant" param
static void
com_google_tts_SpeechSynthesis_setLanguage(JNIEnv *env, jobject thiz, 
                                           jstring language, int variant)
{   
    const char *langNativeString = env->GetStringUTFChars(language, 0);
    engineSet("language", langNativeString);
    env->ReleaseStringUTFChars(language, langNativeString);
}

static void
com_google_tts_SpeechSynthesis_setSpeechRate(JNIEnv *env, jobject thiz, 
                                             int speechRate)
{
    char buffer [10];
    sprintf(buffer, "%d", speechRate);
    engineSet("rate", buffer);
}

static void
com_google_tts_SpeechSynthesis_native_finalize(JNIEnv *env,
					       jobject thiz)
{
    engineShutdown();
    delete audout;
}

// TODO: Do something for synthing to file
static void
com_google_tts_SpeechSynthesis_synthesizeToFile(JNIEnv *env, jobject thiz,
						jstring textJavaString,
						jstring filenameJavaString)
{
/*
    espeak_SetSynthCallback(AndroidEspeakSynthToFileCallback);
    int sampleRate = (int)env->GetIntField(thiz, fields.mNativeContext);
    const char *textNativeString = env->GetStringUTFChars(textJavaString, 0);
    const char *filenameNativeString = env->GetStringUTFChars(
        filenameJavaString, 0);

    LOGI("text:::");
    LOGI(textNativeString);
    LOGI("filename:::");
    LOGI(filenameNativeString);

    FILE *fp = fopen(filenameNativeString, "wb");
    // Write 44 blank bytes for WAV header, then come back and fill them in
    // after we've written the audio data
    char header[44];
    fwrite(header, 1, 44, fp);

    unsigned int unique_identifier;
    void* user_data = (void *)fp;
    espeak_ERROR err;

    LOGI("Calling synth");

    err = espeak_Synth(textNativeString,
                       strlen(textNativeString),
                       0,  // position
                       POS_CHARACTER,
                       0,  // end position (0 means no end position)
                       espeakCHARS_UTF8,
                       &unique_identifier,
                       user_data);

    char buf[100];
    sprintf(buf, "synth err: %d\n", err);
    LOGI(buf);

    err = espeak_Synchronize();

    sprintf(buf, "synchronize err: %d\n", err);
    LOGI(buf);

    LOGI("synth Done");

    long filelen = ftell(fp);

    sprintf(buf, "file len: %ld\n", filelen);
    LOGI(buf);


    int samples = (((int)filelen) - 44) / 2;
    header[0] = 'R';
    header[1] = 'I';
    header[2] = 'F';
    header[3] = 'F';
    ((uint32 *)(&header[4]))[0] = filelen - 8;
    header[8] = 'W';
    header[9] = 'A';
    header[10] = 'V';
    header[11] = 'E';

    header[12] = 'f';
    header[13] = 'm';
    header[14] = 't';
    header[15] = ' ';

    ((uint32 *)(&header[16]))[0] = 16;  // size of fmt

    ((uint16 *)(&header[20]))[0] = 1;  // format
    ((uint16 *)(&header[22]))[0] = 1;  // channels
    ((uint32 *)(&header[24]))[0] = 22050;  // samplerate
    ((uint32 *)(&header[28]))[0] = 44100;  // byterate
    ((uint16 *)(&header[32]))[0] = 2;  // block align
    ((uint16 *)(&header[34]))[0] = 16;  // bits per sample

    header[36] = 'd';
    header[37] = 'a';
    header[38] = 't';
    header[39] = 'a';

    ((uint32 *)(&header[40]))[0] = samples * 2;  // size of data

    // Skip back to the beginning and rewrite the header
    fseek(fp, 0, SEEK_SET);
    fwrite(header, 1, 44, fp);

    fflush(fp);
    fclose(fp);

    env->ReleaseStringUTFChars(textJavaString, textNativeString);
    env->ReleaseStringUTFChars(filenameJavaString, filenameNativeString);
*/
}


static void
com_google_tts_SpeechSynthesis_speak(JNIEnv *env, jobject thiz,
						jstring textJavaString)
{
    audout->stop();
    audout->start();
    const char *textNativeString = env->GetStringUTFChars(textJavaString, 0);
    engineSynth(textNativeString, ttsSynthDoneCBSpeak);
    env->ReleaseStringUTFChars(textJavaString, textNativeString);
}


static void
com_google_tts_SpeechSynthesis_stop(JNIEnv *env, jobject thiz)
{
    engineStop();
    audout->stop();
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
        "(Ljava/lang/String;I)V",
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
    {   "native_setup",
        "(Ljava/lang/Object;Ljava/lang/String;II)V",
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
