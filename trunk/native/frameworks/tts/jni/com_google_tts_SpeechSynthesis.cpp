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

#define wchar_t owchar_t

#include <stdio.h>
#include <unistd.h>

#define LOG_TAG "SpeechSynthesis"

#include <utils/Log.h>
#include <nativehelper/jni.h>
#include <nativehelper/JNIHelp.h>
#include <android_runtime/AndroidRuntime.h>
#include <speak_lib.h>

using namespace android;

static struct fields_t {
    jfieldID    mNativeContext;
    jclass      mSpeechSynthesisClass;
} fields;

typedef unsigned int uint32;
typedef unsigned short uint16;

/* Callback from espeak.  Write whatever bytes are returned to the file
   pointer found in the user data. */
static int AndroidEspeakSynthCallback(short *wav, int numsamples,
				      espeak_EVENT *events) {


    char buf[100];
    sprintf(buf, "AndroidEspeakSynthCallback: %d samples", numsamples);
    LOGI(buf);

    if (wav == NULL) {
        LOGI("Null: speech has completed");
    }

    // The user data should contain the file pointer of the file to write to
    void* user_data = events->user_data;
    FILE* fp = static_cast<FILE *>(user_data);

    // Write all of the samples
    fwrite(wav, sizeof(short), numsamples, fp);

    return 0;  // continue synthesis (1 is to abort)
}

static void
com_google_tts_SpeechSynthesis_native_setup(
    JNIEnv *env, jobject thiz, jobject weak_this, jstring language, int speechRate)
{
    // TODO Make sure that the speech data is loaded in 
    // the directory /sdcard/espeak-data before calling this.
    int sampleRate = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS,
                                       4096, "/sdcard", 0);

    if (sampleRate <= 0) {
        jniThrowException(env, "java/lang/RuntimeException",
                          "Unable to initialize espeak");
        return;
    }

    env->SetIntField(thiz, fields.mNativeContext, sampleRate);

    espeak_SetSynthCallback(AndroidEspeakSynthCallback);
    espeak_ERROR err = espeak_SetParameter(espeakRATE, speechRate, 0);

    espeak_VOICE voice;
    voice.languages = env->GetStringUTFChars(language, 0);

    err = espeak_SetVoiceByProperties(&voice);
    char buf[100];
    sprintf(buf, "Language: %s\n", voice.languages);
    LOGI(buf);
    sprintf(buf, "set voice: %d\n", err);
    LOGI(buf);
}

static void
com_google_tts_SpeechSynthesis_native_finalize(JNIEnv *env,
					       jobject thiz)
{
    int sampleRate = (int)env->GetIntField(thiz, fields.mNativeContext);
}

static void
com_google_tts_SpeechSynthesis_synthesizeToFile(JNIEnv *env, jobject thiz,
						jstring textJavaString,
						jstring filenameJavaString)
{
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
}

// Dalvik VM type signatures
static JNINativeMethod gMethods[] = {
    {   "synthesizeToFile",             
        "(Ljava/lang/String;Ljava/lang/String;)V",
        (void*)com_google_tts_SpeechSynthesis_synthesizeToFile
    },
    {   "native_setup",
        "(Ljava/lang/Object;Ljava/lang/String;I)V",
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
