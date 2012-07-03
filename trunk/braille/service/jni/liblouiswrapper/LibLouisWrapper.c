/*
 * Copyright 2012 Google Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>
#include "alog.h"
#include "liblouis/liblouis/liblouis.h"
#include "liblouis/liblouis/louis.h"  // for MAXSTRING

#define LOG_TAG "LibLouisWrapper_Native"

jboolean
Java_com_googlecode_eyesfree_braille_service_translate_LibLouisWrapper_checkTableNative
(JNIEnv* env, jclass clazz, jstring tableName) {
  jboolean ret = JNI_FALSE;
  const jbyte *tableNameUtf8 = (*env)->GetStringUTFChars(env, tableName, NULL);
  if (lou_getTable(tableNameUtf8) == NULL) {
    goto out;
  }
  ret = JNI_TRUE;
 out:
  (*env)->ReleaseStringUTFChars(env, tableName, tableNameUtf8);
  return ret;
}

jbyteArray
Java_com_googlecode_eyesfree_braille_service_translate_LibLouisWrapper_translateNative
(JNIEnv* env, jclass clazz, jstring text, jstring tableName) {
  jbyteArray ret = NULL;
  const jchar* textUtf16 = (*env)->GetStringChars(env, text, NULL);
  const jbyte* tableNameUtf8 = (*env)->GetStringUTFChars(env, tableName, NULL);
  int inlen = (*env)->GetStringLength(env, text);
  int outlen = inlen * 2;
  // TODO: Need to do this in a buffer like usual character encoding
  // translations, but for now we assume that double size is good enough.
  jchar* outbuf = malloc(sizeof(jchar) * outlen);
  int result = lou_translateString(tableNameUtf8, textUtf16, &inlen,
				   outbuf, &outlen,
				   NULL/*typeform*/, NULL/*spacing*/,
				   dotsIO/*mode*/);
  if (result == 0) {
    LOGE("Translation failed.");
    goto freeoutbuf;
  }
  LOGV("Successfully translated %d characters to %d cells, "
       "consuming %d characters", (*env)->GetStringLength(env, text),
       outlen, inlen);
  ret = (*env)->NewByteArray(env, outlen);
  if (!ret) {
    goto freeoutbuf;
  }
  jbyte* retbuf = (*env)->GetByteArrayElements(env, ret, NULL);
  if (!retbuf) {
    ret = NULL;
    goto freeoutbuf;
  }
  int i;
  for (i = 0; i < outlen; ++i) {
    retbuf[i] = outbuf[i] & 0xff;
  }
 releaseretbuf:
  (*env)->ReleaseByteArrayElements(env, ret, retbuf, 0);
 freeoutbuf:
  free(outbuf);
 out:
  (*env)->ReleaseStringChars(env, text, textUtf16);
  (*env)->ReleaseStringUTFChars(env, tableName, tableNameUtf8);
  return ret;
}

jstring
Java_com_googlecode_eyesfree_braille_service_translate_LibLouisWrapper_backTranslateNative
(JNIEnv* env, jclass clazz, jbyteArray cells, jstring tableName) {
  jstring ret = NULL;
  const jbyte* tableNameUtf8 = (*env)->GetStringUTFChars(env, tableName, NULL);
  if (!tableNameUtf8) {
    goto out;
  }
  int inlen = (*env)->GetArrayLength(env, cells);
  jbyte* cellsBytes = (*env)->GetByteArrayElements(env, cells, NULL);
  widechar* inbuf = malloc(sizeof(widechar) * inlen);
  int i;
  for (i = 0; i < inlen; ++i) {
    // Cast to avoid sign extension.
    inbuf[i] = ((unsigned char) cellsBytes[i]) | 0x8000;
  }
  (*env)->ReleaseByteArrayElements(env, cells, cellsBytes, JNI_ABORT);
  int outlen = inlen * 2;
  // TODO: Need to do this in a loop like usual character encoding
  // translations, but for now we assume that double size is good enough.
  jchar* outbuf = malloc(sizeof(jchar) * outlen);
  int result = lou_backTranslateString(tableNameUtf8, inbuf, &inlen,
				   outbuf, &outlen,
				   NULL/*typeform*/, NULL/*spacing*/,
				   dotsIO);
  free(inbuf);
  if (result == 0) {
    LOGE("Back translation failed.");
    goto freeoutbuf;
  }
  LOGV("Successfully translated %d cells into %d characters, "
       "consuming %d cells", (*env)->GetArrayLength(env, cells),
       outlen, inlen);
  ret = (*env)->NewString(env, outbuf, outlen);
 freeoutbuf:
  free(outbuf);
 releasetablename:
  (*env)->ReleaseStringUTFChars(env, tableName, tableNameUtf8);
 out:
  return ret;
}

void
Java_com_googlecode_eyesfree_braille_service_translate_LibLouisWrapper_setTablesDirNative
(JNIEnv* env, jclass clazz, jstring path) {
  // liblouis has a static buffer, which we don't want to overflow.
  if ((*env)->GetStringUTFLength(env, path) >= MAXSTRING) {
    LOGE("Braille table path too long");
    return;
  }
  const jbyte* pathUtf8 = (*env)->GetStringUTFChars(env, path, NULL);
  if (!pathUtf8) {
    return;
  }
  // The path gets copied.
  // Cast needed to get rid of const.
  LOGV("Setting tables path to: %s", pathUtf8);
  lou_setDataPath((char*)pathUtf8);
  (*env)->ReleaseStringUTFChars(env, path, pathUtf8);
}
