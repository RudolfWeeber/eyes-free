/* 
**
** Copyright 2008, Google Inc.
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#include <nativehelper/jni.h>
#include <assert.h>
#include <dirent.h>
#include <ctype.h>

#include "baseapi.h"
#include "varable.h"
#include "tessvars.h"
#include "ocrclass.h"

#include "allheaders.h"
#include "textdetect.h"

#define DEBUG 0

#if DEBUG
#include <stdio.h>
BOOL_VAR (tessedit_write_images, TRUE,
          "Capture the image from the IPE");
#endif

#define LOG_NDEBUG 0
#define LOG_TAG "OcrLib(native)"
#include <utils/Log.h>

#define TESSBASE "/sdcard/"

static jfieldID field_mNativeData;

struct native_data_t {
    tesseract::TessBaseAPI api;
    struct ETEXT_STRUCT monitor;
    bool debug;
    PIXA* pixa;
};

static inline native_data_t * get_native_data(JNIEnv *env, jobject object) {
    return (native_data_t *)(env->GetIntField(object, field_mNativeData));
}

struct language_info_t {
    language_info_t(char *lang, int shards) : 
        lang(strdup(lang)), shards(shards) { }
    ~language_info_t() { free(lang); }
    language_info_t *next;
    char *lang;
    int shards;
};
static struct language_info_t *languages = NULL;
static int num_languages;

static language_info_t* find_language(const char *lang)
{
    LOGV(__FUNCTION__);
    language_info_t *trav = languages;
    while (trav) {
        if (!strcmp(trav->lang, lang)) {
            return trav;
        }
        trav = trav->next;
    }
    return NULL;
}

static void add_language(char *lang, int shards)
{
    LOGV(__FUNCTION__);
    language_info_t *trav = find_language(lang);
    if (trav) {
        if (shards > trav->shards) {
            LOGI("UPDATE LANG %s SHARDS %d", lang, shards);
            trav->shards = shards;
        }
        return;
    }
    LOGI("ADD NEW LANG %s SHARDS %d", lang, shards);
    trav = new language_info_t(lang, shards);
    trav->next = languages;
    languages = trav;
    num_languages++;
}

static void free_languages()
{
    LOGV(__FUNCTION__);
    language_info_t *trav = languages, *old;
    while (trav) {
        old = trav;
        LOGI("FREE LANG %s\n", trav->lang);
        trav = trav->next;
        delete old;
    }
    languages = NULL;
    num_languages = 0;
}

static int get_num_languages() {
    return num_languages;
}

static language_info_t *iter;
static language_info_t* language_iter_init()
{
    iter = languages;
    return iter;
}

static language_info_t* language_iter_next()
{
    if (iter)
        iter = iter->next;
    return iter;
}

#if DEBUG

#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>

#define FAILIF(cond, msg...) do {                 \
        if (cond) { 	                          \
	        LOGE("%s(%d): ", __FILE__, __LINE__); \
            LOGE(msg);                            \
            return;                               \
        }                                         \
} while(0)

void test_ocr(const char *infile, int x, int y, int bpp,
              const char *outfile, const char *lang,
              const char *ratings, const char *tessdata)
{
	void *buffer;
	struct stat s;
	int ifd, ofd;

	LOGI("input file %s\n", infile);
	ifd = open(infile, O_RDONLY);
	FAILIF(ifd < 0, "open(%s): %s\n", infile, strerror(errno));
	FAILIF(fstat(ifd, &s) < 0, "fstat(%d): %s\n", ifd, strerror(errno));
	LOGI("file size %lld\n", s.st_size);
	buffer = mmap(NULL, s.st_size, PROT_READ, MAP_PRIVATE, ifd, 0);
	FAILIF(buffer == MAP_FAILED, "mmap(): %s\n", strerror(errno));
	LOGI("infile mmapped at %p\n", buffer);
	FAILIF(!tessdata, "You must specify a path for tessdata.\n");

	tesseract::TessBaseAPI  api;

	LOGI("tessdata %s\n", tessdata);
	LOGI("lang %s\n", lang);
	FAILIF(api.Init(tessdata, lang), "could not initialize tesseract\n");
	if (ratings) {
		LOGI("ratings %s\n", ratings);
		api.ReadConfigFile(ratings, false);
	}

	LOGI("set image x=%d, y=%d bpp=%d\n", x, y, bpp);
	FAILIF(!bpp || bpp == 2 || bpp > 4, 
		"Invalid value %d of bpp\n", bpp);
	api.SetImage((const unsigned char *)buffer, x, y, bpp, bpp*x); 

	LOGI("set rectangle to cover entire image\n");
	api.SetRectangle(0, 0, x, y);

	LOGI("set page seg mode to single character\n");
	api.SetPageSegMode(tesseract::PSM_SINGLE_CHAR);
	LOGI("recognize\n");
	char * text = api.GetUTF8Text();
	if (tessedit_write_images) {
		page_image.write("tessinput.tif");
	}
	FAILIF(text == NULL, "didn't recognize\n");

	FILE* fp = fopen(outfile, "w");
	if (fp != NULL) {
        LOGI("write to output %s\n", outfile);
		fwrite(text, strlen(text), 1, fp);
		fclose(fp);
	}
    else LOGI("could not write to output %s\n", outfile);

	int mean_confidence = api.MeanTextConf();
	LOGI("mean confidence: %d\n", mean_confidence);

	int* confs = api.AllWordConfidences();
	int len, *trav;
	for (len = 0, trav = confs; *trav != -1; trav++, len++)
		LOGI("confidence %d: %d\n", len, *trav);
	free(confs);

	LOGI("clearing api\n");
	api.Clear();
	LOGI("clearing adaptive classifier\n");
	api.ClearAdaptiveClassifier();

	LOGI("clearing text\n");
	delete [] text;
}
#endif

jboolean
ocr_open(JNIEnv *env, jobject thiz, jstring lang)
{
    LOGV(__FUNCTION__);

    native_data_t *nat = get_native_data(env, thiz);

    if (lang == NULL) {
        LOGE("lang string is null!");
        return JNI_FALSE;
    }

    const char *c_lang = env->GetStringUTFChars(lang, NULL);
    if (c_lang == NULL) {
        LOGE("could not extract lang string!");
        return JNI_FALSE;
    }

    jboolean res = JNI_TRUE;

    LOGI("lang %s\n", c_lang);
    if (nat->api.Init(TESSBASE, c_lang)) {
        LOGE("could not initialize tesseract!");
        res = JNI_FALSE;
    }
    else {
        LOGI("lang %s initialization complete\n", c_lang);
    }

    env->ReleaseStringUTFChars(lang, c_lang);
    LOGI("successfully initialized tesseract!");
    return res;
}

void
ocr_load_pix(JNIEnv *env, jobject thiz,
             jbyteArray image)
{
PIX       *pixs, *pixd;
int        length;

    LOGV(__FUNCTION__);

    native_data_t *nat = get_native_data(env, thiz);

    jbyteArray image_obj = (jbyteArray) env->NewGlobalRef(image);
    jbyte *image_buffer = env->GetByteArrayElements(image_obj, NULL);
    LOG_ASSERT(image_buffer != NULL, "image buffer is NULL!");

    length = env->GetArrayLength(image);

    pixs = pixReadMem((const l_uint8 *) image_buffer, length);
    pixd = pixConvertRGBToGrayFast(pixs);
    pixDestroy(&pixs);

    if (nat->pixa != NULL)
        pixaDestroy(&nat->pixa);

    nat->pixa = pixaCreate(1);
    pixaAddPix(nat->pixa, pixd, L_INSERT);

    env->ReleaseByteArrayElements(image_obj, image_buffer, JNI_ABORT);
    env->DeleteGlobalRef(image_obj);
}

void
ocr_load_pix_raw(JNIEnv *env, jobject thiz,
                 jbyteArray image, jint width,
                 jint height, jint bpp)
{
PIX       *pixs, *pixd;
int        length;
l_uint32  *temp;

    LOGV(__FUNCTION__);

    native_data_t *nat = get_native_data(env, thiz);

    jbyteArray image_obj = (jbyteArray) env->NewGlobalRef(image);
    jbyte *image_buffer = env->GetByteArrayElements(image_obj, NULL);
    LOG_ASSERT(image_buffer != NULL, "image buffer is NULL!");

    length = env->GetArrayLength(image);

    pixs = pixCreate(width, height, bpp);
    temp = pixGetData(pixs);

    pixSetData(pixs, (l_uint32 *) image_buffer);

    if (bpp != 8)
        pixd = pixConvertTo8(pixs, FALSE);
    else
        pixd = pixCopy(NULL, pixs);

    pixSetData(pixs, temp);
    pixDestroy(&pixs);

    if (nat->pixa != NULL)
        pixaDestroy(&nat->pixa);

    nat->pixa = pixaCreate(1);
    pixaAddPix(nat->pixa, pixd, L_INSERT);

    env->ReleaseByteArrayElements(image_obj, image_buffer, JNI_ABORT);
    env->DeleteGlobalRef(image_obj);
}

    /* Align text only */
#define   MIN_ANGLE           6.0    /* degrees */
#define   SWEEP_RANGE         15.0   /* degrees */
#define   SWEEP_DELTA         1.0    /* degrees */
#define   SWEEP_REDUCTION     4      /* 1, 2, 4 or 8 */

void
ocr_align_text(JNIEnv *env, jobject thiz)
{
    LOGV(__FUNCTION__);
    // Corrects the rotation of each element in pixa to 0 degrees.

    native_data_t *nat = get_native_data(env, thiz);
    LOG_ASSERT(nat->pixa != NULL, "pixa %p is NULL!", nat->pixa);

    l_float32 deg2rad = 3.1415926535 / 180.0, angle;

    l_int32 count = pixaGetCount(nat->pixa);
    for (l_int32 i = 0; i < count; i++) {
        PIX *pixs = pixaGetPix(nat->pixa, i, L_CLONE);

        if (pixFindSkewSweep(pixs, &angle, SWEEP_REDUCTION,
                             SWEEP_RANGE, SWEEP_DELTA)) {
	    angle = -1.0f;
        }

        if (angle > MIN_ANGLE) {
            PIX *pixd = pixRotate(pixs, deg2rad * angle, L_ROTATE_AREA_MAP,
                                  L_BRING_IN_WHITE, 0, 0);
            pixaReplacePix(nat->pixa, i, pixd, NULL);
        }

        pixDestroy(&pixs);
    }
}

jintArray
ocr_detect_text(JNIEnv *env, jobject thiz)
{
    LOGV(__FUNCTION__);
    // Extracts text components from the source image and replaces the
    // source image with the text components. This should only be used
    // after loadPix() and before nextPix().

    native_data_t *nat = get_native_data(env, thiz);
    LOG_ASSERT(nat->pixa != NULL, "pixa %p is NULL!", nat->pixa);

    l_int32 count = pixaGetCount(nat->pixa);
    LOG_ASSERT(count == 1, "pixa contains %d images (should be 1)", count);

    PIX *pixs = pixaGetPix(nat->pixa, 0, L_COPY);
    PIXA *pixa;

    LOGI("BEFORE TEXT DETECTION\n");
    pixDetectText(pixs, &pixa, nat->debug);
    LOGI("AFTER TEXT DETECTION\n");

    pixDestroy(&pixs);
    pixaDestroy(&nat->pixa);
    nat->pixa = pixa;

    count = pixaGetCount(pixa);

    LOGI("result: %d boxes\n", count);

    count *= 4;
    jintArray array = env->NewIntArray(count);
    jint *elems = env->GetIntArrayElements(array, NULL);

    l_int32 j = 0, x, y, w, h;
    for (l_int32 i = 0; i < count; i += 4) {
        pixaGetBoxGeometry(pixa, j++, &x, &y, &w, &h);
        elems[i] = x;
        elems[i + 1] = y;
        elems[i + 2] = w;
        elems[i + 3] = h;
    }

    env->ReleaseIntArrayElements(array, elems, 0);

    return array;
}

void
ocr_normalize_bg(JNIEnv *env, jobject thiz,
                 jint reduction, jint size, jint bgval)
{
    LOGV(__FUNCTION__);
    // Normalizes the background of each element in pixa.

    native_data_t *nat = get_native_data(env, thiz);
    LOG_ASSERT(nat->pixa != NULL, "pixa %p is NULL!", nat->pixa);

    l_int32 count = pixaGetCount(nat->pixa);
    for (l_int32 i = 0; i < count; i++) {
        PIX *pixs = pixaGetPix(nat->pixa, i, L_CLONE);
        PIX *pixd = pixBackgroundNormMorph(pixs, NULL, reduction, size, bgval);
        pixDestroy(&pixs);
        pixaReplacePix(nat->pixa, i, pixd, NULL);
    }

    LOGI("normalize_bg: pixa has %d pix", count);
}

jboolean
ocr_next_pix(JNIEnv *env, jobject thiz)
{
    LOGV(__FUNCTION__);
    // Consumes the element at the head of pixa

    native_data_t *nat = get_native_data(env, thiz);
    LOG_ASSERT(nat->pixa != NULL, "pixa is NULL!");

    l_int32 count = pixaGetCount(nat->pixa);
    LOGI("next_pix: pixa has %d pix", count);

    if (count == 0) {
        pixaDestroy(&nat->pixa);
        return JNI_FALSE;
    }

    PIX *pixs = pixaGetPix(nat->pixa, 0, L_CLONE);
    pixaRemovePix(nat->pixa, 0);

    nat->api.SetImage(pixs);
    pixDestroy(&pixs);

    return JNI_TRUE;
}

void
ocr_release_image(JNIEnv *env, jobject thiz)
{
    LOGV(__FUNCTION__);
    native_data_t *nat = get_native_data(env, thiz);
    if (nat->pixa != NULL)
        pixaDestroy(&nat->pixa);
}

void
ocr_set_rectangle(JNIEnv *env, jobject thiz,
                  jint left, jint top, 
                  jint width, jint height)
{
    LOGV(__FUNCTION__);
    // Restrict recognition to a sub-rectangle of the image. Call after SetImage.
    // Each SetRectangle clears the recogntion results so multiple rectangles
    // can be recognized with the same image.
    native_data_t *nat = get_native_data(env, thiz);

    LOGI("set rectangle left=%d, top=%d, width=%d, height=%d\n",
         left, top, width, height);

    LOG_ASSERT(nat->image_obj != NULL && nat->image_buffer != NULL,
               "image and/or image_buffer are NULL!");
    nat->api.SetRectangle(left, top, width, height);
}

jstring
ocr_recognize(JNIEnv *env, jobject thiz)
{
    LOGV(__FUNCTION__);

    native_data_t *nat = get_native_data(env, thiz);

    LOG_ASSERT(nat->image_obj != NULL && nat->image_buffer != NULL,
               "image and/or image_buffer are NULL!");

    // Make sure our monitor is reset
    nat->monitor.end_time = 0;

    LOGI("BEFORE RECOGNIZE");
    char * text = nat->api.GetUTF8Text(&nat->monitor);
    LOGI("AFTER RECOGNIZE");

    return env->NewStringUTF(text);
}

jint
ocr_get_progress(JNIEnv *env, jobject thiz)
{
    LOGV(__FUNCTION__);
    // Returns the OCR progress between 0 and 100.
    native_data_t *nat = get_native_data(env, thiz);

    return nat->monitor.progress;
}

void
ocr_stop(JNIEnv *env, jobject thiz)
{
    LOGV(__FUNCTION__);
    // Set the monitor's end time to the current time, which will safely stop
    // Tesseract mid-recognition from another thread.
    native_data_t *nat = get_native_data(env, thiz);

    nat->monitor.end_time = clock();
}

static jint
ocr_mean_confidence(JNIEnv *env, jobject thiz)
{
    LOGV(__FUNCTION__);
    // Returns the (average) confidence value between 0 and 100.
    native_data_t *nat = get_native_data(env, thiz);

    return nat->api.MeanTextConf();
}

static jintArray
ocr_word_confidences(JNIEnv *env, jobject thiz)
{
    LOGV(__FUNCTION__);
    // Returns all word confidences (between 0 and 100) in an array, terminated
    // by -1.  The calling function must delete [] after use.
    // The number of confidences should correspond to the number of space-
    // delimited words in GetUTF8Text.
    native_data_t *nat = get_native_data(env, thiz);

    int* confs = nat->api.AllWordConfidences();
    if (confs == NULL) {
        LOGE("Could not get word-confidence values!");
        return NULL;
    }

    int len, *trav;
    for (len = 0, trav = confs; *trav != -1; trav++, len++);

    LOG_ASSERT(confs != NULL, "Confidence array has %d elements",
               len);

    jintArray ret = env->NewIntArray(len);
    LOG_ASSERT(ret != NULL,
               "Could not create Java confidence array!");

    env->SetIntArrayRegion(ret, 0, len, confs);    
    delete [] confs;
    return ret;
}

static void
ocr_set_debug(JNIEnv *env, jobject thiz,
              jboolean debug)
{
    LOGV(__FUNCTION__);

    native_data_t *nat = get_native_data(env, thiz);

    nat->debug = (debug == JNI_TRUE) ? TRUE : FALSE;
}

static jboolean
ocr_set_variable(JNIEnv *env, jobject thiz,
                 jstring var, jstring value)
{
    LOGV(__FUNCTION__);
    // Set the value of an internal "variable" (of either old or new types).
    // Supply the name of the variable and the value as a string, just as
    // you would in a config file.
    // Returns false if the name lookup failed.
    // Eg SetVariable("tessedit_char_blacklist", "xyz"); to ignore x, y and z.
    // Or SetVariable("bln_numericmode", "1"); to set numeric-only mode.
    // SetVariable may be used before Init, but settings will revert to
    // defaults on End().

    native_data_t *nat = get_native_data(env, thiz);
    
    const char *c_var  = env->GetStringUTFChars(var, NULL);
    const char *c_value  = env->GetStringUTFChars(value, NULL);

    jboolean set = nat->api.SetVariable(c_var, c_value) ? JNI_TRUE : JNI_FALSE;

    env->ReleaseStringUTFChars(var, c_var);
    env->ReleaseStringUTFChars(value, c_value);

    return set;
}

static void
ocr_clear_results(JNIEnv *env, jobject thiz)
{
    LOGV(__FUNCTION__);
    // Free up recognition results and any stored image data, without actually
    // freeing any recognition data that would be time-consuming to reload.
    // Afterwards, you must call SetImage or TesseractRect before doing
    // any Recognize or Get* operation.
    LOGI("releasing all memory");
    native_data_t *nat = get_native_data(env, thiz);
    nat->api.Clear();

    // Call between pages or documents etc to free up memory and forget
    // adaptive data.
    LOGI("clearing adaptive classifier");
    nat->api.ClearAdaptiveClassifier();
}

static void
ocr_close(JNIEnv *env, jobject thiz)
{
    LOGV(__FUNCTION__);
    // Close down tesseract and free up all memory. End() is equivalent to
    // destructing and reconstructing your TessBaseAPI.  Once End() has been
    // used, none of the other API functions may be used other than Init and
    // anything declared above it in the class definition.
    native_data_t *nat = get_native_data(env, thiz);
    nat->api.End();
}

static void
ocr_set_page_seg_mode(JNIEnv *env, jobject thiz, jint mode)
{
    LOGV(__FUNCTION__);
    native_data_t *nat = get_native_data(env, thiz);
    nat->api.SetPageSegMode((tesseract::PageSegMode)mode);
}

static jobjectArray
ocr_get_languages(JNIEnv *env, jclass clazz)
{
    LOGV(__FUNCTION__);

    free_languages();

    DIR *tessdata = opendir(TESSBASE "tessdata");
    if (tessdata == NULL) {
        LOGE("Could not open tessdata directory %s", TESSBASE "tessdata");
        return NULL;
    }

    dirent *ent;
    LOGI("readdir");
    while ((ent = readdir(tessdata))) {
        char *where, *stem;
        int shard = -1;
        if (ent->d_type == 0x08 &&
                (where = strstr(ent->d_name, ".traineddata"))) {
            *where = 0;
            if (where != ent->d_name) {
                where--; // skip the dot
                while(where != ent->d_name) {
                    if(!isdigit(*where))
                        break;
                    where--; // it's a digit, backtrack
                }
                // we backtracked one too much
                char *end = ++where;
                // if there was a number, it will be written in
                // shard, otherwise shard will remain -1.
                sscanf(end, "%d", &shard);
                *end = 0;
                add_language(ent->d_name, shard + 1);
            }
        }
    }

    closedir(tessdata);

    {
        jclass stringClass = env->FindClass("java/lang/String");
        jobjectArray langsArray =
            env->NewObjectArray(get_num_languages(), stringClass, NULL);
        LOG_ASSERT(langsArray != NULL,
                   "Could not create Java object array!");
        int i = 0;
        language_info_t *it = language_iter_init();
        for (; it; i++, it = language_iter_next()) {
            env->SetObjectArrayElement(langsArray, i,
                                       env->NewStringUTF(it->lang));
        }
        return langsArray;
    }
}

static jint
ocr_get_shards(JNIEnv *env, jclass clazz, jstring lang)
{
    int ret = -1;
    const char *c_lang = env->GetStringUTFChars(lang, NULL);
    if (c_lang == NULL) {
        LOGE("could not extract lang string!");
        return ret;
    }

    language_info_t* lang_entry = find_language(c_lang);
    if (lang_entry)
        ret = lang_entry->shards;

    LOGI("shards for lang %s: %d\n", c_lang, ret);

    env->ReleaseStringUTFChars(lang, c_lang);

    return ret;
}

static void class_init(JNIEnv* env, jclass clazz) {
    LOGV(__FUNCTION__);
    field_mNativeData = env->GetFieldID(clazz, "mNativeData", "I");
}

static void initialize_native_data(JNIEnv* env, jobject object) {
    LOGV(__FUNCTION__);
    native_data_t *nat = new native_data_t;
    if (nat == NULL) {
        LOGE("%s: out of memory!", __FUNCTION__);
        return;
    }

    env->SetIntField(object, field_mNativeData, (jint)nat);
}

static void cleanup_native_data(JNIEnv* env, jobject object) {
    LOGV(__FUNCTION__);
    native_data_t *nat = get_native_data(env, object);
    if (nat)
        delete nat;
    free_languages();
}

static JNINativeMethod methods[] = {
     /* name, signature, funcPtr */
    {"classInitNative", "()V", (void*)class_init},
    {"initializeNativeDataNative", "()V", (void *)initialize_native_data},
    {"cleanupNativeDataNative", "()V", (void *)cleanup_native_data},
    {"openNative", "(Ljava/lang/String;)Z", (void*)ocr_open},
    {"loadPixNative", "([BIII)V", (void*)ocr_load_pix_raw},
    {"loadPixNative", "([B)V", (void*)ocr_load_pix},
    {"alignTextNative", "()V", (void*)ocr_align_text},
    {"detectTextNative", "()[I", (void*)ocr_detect_text},
    {"normalizeBgNative", "(III)V", (void*)ocr_normalize_bg},
    {"nextPixNative", "()Z", (void*)ocr_next_pix},
    {"releaseImageNative", "()V", (void*)ocr_release_image},
    {"setRectangleNative", "(IIII)V", (void*)ocr_set_rectangle},
    {"recognizeNative", "()Ljava/lang/String;", (void*)ocr_recognize},
    {"getProgressNative", "()I", (void*)ocr_get_progress},
    {"stopNative", "()V", (void*)ocr_stop},
    {"clearResultsNative", "()V", (void*)ocr_clear_results},
    {"closeNative", "()V", (void*)ocr_close},
    {"meanConfidenceNative", "()I", (void*)ocr_mean_confidence},
    {"wordConfidencesNative", "()[I", (void*)ocr_word_confidences},
    {"setDebugNative", "(Z)V", (void*)ocr_set_debug},
    {"setVariableNative", "(Ljava/lang/String;Ljava/lang/String;)Z", (void*)ocr_set_variable},
    {"setPageSegModeNative", "(I)V", (void*)ocr_set_page_seg_mode},
    {"getLanguagesNative", "()[Ljava/lang/String;", (void*)ocr_get_languages},
    {"getShardsNative", "(Ljava/lang/String;)I", (void*)ocr_get_shards},
};

/*
 * Register several native methods for one class.
 */
static int registerNativeMethods(JNIEnv* env, const char* className,
    JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz = env->FindClass(className);

    if (clazz == NULL) {
        LOGE("Native registration unable to find class %s", className);
        return JNI_FALSE;
    }

    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        LOGE("RegisterNatives failed for %s", className);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
 * Set some test stuff up.
 *
 * Returns the JNI version on success, -1 on failure.
 */

typedef union {
    JNIEnv* env;
    void* venv;
} UnionJNIEnvToVoid;

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    UnionJNIEnvToVoid uenv;
    uenv.venv = NULL;
    JNIEnv* env = NULL;

    if (vm->GetEnv(&uenv.venv, JNI_VERSION_1_4) != JNI_OK) {
        LOGE("GetEnv failed\n");
        return (jint)-1;
    }
    env = uenv.env;

    assert(env != NULL);

    LOGI("In OcrLib JNI_OnLoad\n");

    if (JNI_FALSE ==
        registerNativeMethods(env, 
                              "com/android/ocr/service/OcrLib",
                              methods,
                              sizeof(methods) / sizeof(methods[0]))) {
        LOGE("OcrLib native registration failed\n");
        return (jint)-1;
    }

    /* success -- return valid version number */
    LOGI("OcrLib native registration succeeded!\n");
    return (jint)JNI_VERSION_1_4;
}
