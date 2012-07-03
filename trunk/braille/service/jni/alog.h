/*
 * Logging macros.  A file that includes this file is expected
 * to define LOG_TAG to be used when logging to the Android logging
 * system.
 */

#ifndef ALOG_H_
#define ALOG_H_

#include <android/log.h>

#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#endif /* ALOG_H_ */
