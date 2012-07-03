/*
 * Copyright 2011 Google Inc.
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

/*
 * Native code for the Java class
 * com.googlecode.eyesfree.braille.service.BrlttyWrapper.
 */

#include "libbrltty.h"
#include "brldefs.h"
#include "alog.h"
#include "bluetooth_android.h"
#include <assert.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <jni.h>
#include <errno.h>
#include <fcntl.h>


#define LOG_TAG "BrlttyWrapper_native"

#define DISPLAY_PACKAGE "com/googlecode/eyesfree/braille/display/"

// Data structures for command and key code mapping from the brltty constants
// to java constant fields.

typedef struct CommandMapEntry {
  int brlttyValue;
  jint javaValue;
} CommandMapEntry;

typedef struct CommandMap {
  struct CommandMapEntry* entries;
  size_t numEntries;
} CommandMap;

// Maps an integer to a java field name.
typedef struct NamedCommand {
  const char* fieldName;
  int brlttyValue;
} NamedCommand;

// Creates a map from brltty int constants to the corresponding java ints,
// given by a class name and names of static final int fields in
// the named java class.
static CommandMap* createCommandMap(JNIEnv* env,
                                    jclass cls,
                                    NamedCommand* namedCommands,
                                    size_t numNamedCommands);
static void freeCommandMap(CommandMap* commandMap);
// Returns the corresponding java int from the brltty constant given by
// key.
static jint commandMapGet(CommandMap* commandMap, int key);
// Maps a brltty command (including argument if applicable) into
// the corresponding java command and argument.
// *outCommand is set to -1 if there is no mapping and (outArg
// is set to 0 if there is no argument for this command.
static jint mapBrlttyCommand(int brlttyCommand,
                             jint* outCommand, jint* outArg);
// Callback used when listing the brltty keymap.
static int reportKeyBinding(int command, int keyCount, const char* keys[],
                            void* data);

// Maps from brltty command codes (without arguments and flags)
// to constants in the BrailleInputEvent java class.
static CommandMap* brlttyCommandMap = NULL;
// Maps brltty special key constants to constants in the BrailleInputEvent
// java class.
static CommandMap* brlttyKeyMap = NULL;
// Command that is special-cased when mapping.
static jint cmdActivateCurrent = -1;

static jclass class_BrlttyWrapper;
static jclass class_BrailleDisplayProperties;
static jclass class_BrailleKeyBinding;
static jclass class_IndexOutOfBoundsException;
static jclass class_OutOfMemoryError;
static jclass class_NullPointerException;
static jclass class_RuntimeException;
static jclass class_String;
static jfieldID field_mNativeData;
static jfieldID field_mDriverCode;
static jfieldID field_mBrailleDevice;
static jfieldID field_mTablesDir;
static jmethodID method_sendBytesToDevice;
static jmethodID method_BrailleDisplayProperties_ctor;
static jmethodID method_BrailleKeyBinding_ctor;

// Data for the reportKeyBinding callback.
typedef struct ListKeyMapData {
  JNIEnv* env;
  jobjectArray *bindings;
  jsize bindingsSize;
  jsize bindingsCapacity;
} ListKeyMapData;

// Returns an array of BrailleKeyBinding objects for the current display.
static jobjectArray listKeyMap(JNIEnv* env);

typedef struct NativeData {
  int pipefd[2];
  JavaVM* vm;
  int envVer;
  jobject me;
  BluetoothAndroidConnection bluetoothAndroidConnection;
} NativeData;

static NativeData *getNativeData(JNIEnv* env, jobject object);
static ssize_t writeDataToDevice(BluetoothAndroidConnection* conn,
                                 const void* buffer,
                                 size_t size);
static jclass getGlobalClassRef(JNIEnv* env, const char *name);
static jboolean initCommandTables(JNIEnv* env);

jboolean
Java_com_googlecode_eyesfree_braille_service_display_BrlttyWrapper_initNative
(JNIEnv* env, jobject thiz) {
  NativeData *nat = calloc(1, sizeof(*nat));
  if (!nat) {
    (*env)->ThrowNew(env, class_OutOfMemoryError, NULL);
    return JNI_FALSE;
  }
  if (pipe(nat->pipefd) < 0) {
    LOGE("Can't create pipe");
    goto freenat;
  }
  // Make the reading end of the pipe non-blocking, which is what
  // brltty expects.
  if (fcntl(nat->pipefd[0], F_SETFL, O_NONBLOCK) == -1) {
    LOGE("Couldn't make read end of pipe non-blocking: %s", strerror(errno));
    goto closepipe;
  }
  (*env)->GetJavaVM(env, &(nat->vm));
  nat->envVer = (*env)->GetVersion(env);
  nat->me = (*env)->NewGlobalRef(env, thiz);
  nat->bluetoothAndroidConnection.read_fd = nat->pipefd[0];
  nat->bluetoothAndroidConnection.data = nat;
  nat->bluetoothAndroidConnection.writeData = writeDataToDevice;
  bluetoothAndroidSetConnection(&nat->bluetoothAndroidConnection);
  (*env)->SetIntField(env, thiz, field_mNativeData, (jint) nat);
  return JNI_TRUE;

closepipe:
  close(nat->pipefd[0]);
  close(nat->pipefd[1]);
freenat:
  free(nat);
  return JNI_FALSE;
}

jboolean
Java_com_googlecode_eyesfree_braille_service_display_BrlttyWrapper_startNative
(JNIEnv* env, jobject thiz) {
  jboolean result = JNI_FALSE;
  LOGI("Starting braille driver");
  NativeData *nat = getNativeData(env, thiz);
  if (!nat) {
    LOGE("Trying to start a destroyed object");
    goto out;
  }
  jstring driverCode = (*env)->GetObjectField(env, thiz, field_mDriverCode);
  if (!driverCode) {
    (*env)->ThrowNew(env, class_NullPointerException, NULL);
    goto out;
  }
  const char *driverCodeChars = (*env)->GetStringUTFChars(env, driverCode,
                                                          NULL);
  if (!driverCodeChars) {
    // Out of memory already thrown.
    goto out;
  }
  jstring brailleDevice = (*env)->GetObjectField(env, thiz,
                                                 field_mBrailleDevice);
  if (!brailleDevice) {
    (*env)->ThrowNew(env, class_NullPointerException, NULL);
    goto releaseDriverCodeChars;
  }
  const char *brailleDeviceChars =
      (*env)->GetStringUTFChars(env, brailleDevice, NULL);
  if (!brailleDeviceChars) {
    // Out of memory already thrown.
    goto releaseDriverCodeChars;
  }
  jstring tablesDir = (*env)->GetObjectField(env, thiz, field_mTablesDir);
  if (!tablesDir) {
    (*env)->ThrowNew(env, class_NullPointerException, NULL);
    goto releaseBrailleDeviceChars;
  }
  const char *tablesDirChars = (*env)->GetStringUTFChars(env, tablesDir, NULL);
  if (!tablesDirChars) {
    // Out of memory already thrown.
    goto releaseBrailleDeviceChars;
  }
  if (!brltty_initialize(driverCodeChars, brailleDeviceChars,
                         tablesDirChars)) {
    LOGE("Couldn't initialize braille driver");
    goto releaseDriverCodeChars;
  }
  LOGI("Braille driver initialized");
  result = JNI_TRUE;
releaseDriverCodeChars:
  (*env)->ReleaseStringUTFChars(env, driverCode, driverCodeChars);
releaseBrailleDeviceChars:
  (*env)->ReleaseStringUTFChars(env, brailleDevice, brailleDeviceChars);
releaseTablesDirChars:
  (*env)->ReleaseStringUTFChars(env, tablesDir, tablesDirChars);
out:
  return result;
}

void
Java_com_googlecode_eyesfree_braille_service_display_BrlttyWrapper_stopNative(
    JNIEnv* env, jobject thiz) {
  LOGI("Stopping braille driver");
  brltty_destroy();
  NativeData *nat = getNativeData(env, thiz);
  if (nat == NULL) {
    LOGE("Driver already stopped");
    return;
  }
  (*env)->SetIntField(env, thiz, field_mNativeData, 0);
  bluetoothAndroidSetConnection(NULL);
  close(nat->pipefd[0]);
  close(nat->pipefd[1]);
  (*env)->DeleteGlobalRef(env, nat->me);
  free(nat);
}

jobject
Java_com_googlecode_eyesfree_braille_service_display_BrlttyWrapper_getDisplayPropertiesNative(
    JNIEnv* env, jobject thiz) {
  jobjectArray keyBindings = listKeyMap(env);
  if (keyBindings == NULL) {
    return NULL;
  }
  return (*env)->NewObject(
      env, class_BrailleDisplayProperties,
      method_BrailleDisplayProperties_ctor,
      brltty_getTextCells(),
      brltty_getStatusCells(),
      keyBindings);
}

jboolean
Java_com_googlecode_eyesfree_braille_service_display_BrlttyWrapper_writeWindowNative(
    JNIEnv* env, jobject thiz, jbyteArray pattern) {
  jboolean ret = JNI_FALSE;
  jsize patternLen = (*env)->GetArrayLength(env, pattern);
  jbyte *bytes = (*env)->GetByteArrayElements(env, pattern, NULL);
  if (!bytes) {
    goto out;
  }
  if (!brltty_writeWindow(bytes, patternLen)) {
    goto releasebytes;
  }
  ret = JNI_TRUE;
releasebytes:
  (*env)->ReleaseByteArrayElements(env, pattern, bytes, JNI_ABORT);
out:
  return ret;

}

jint
Java_com_googlecode_eyesfree_braille_service_display_BrlttyWrapper_readCommandNative(
    JNIEnv* env, jobject thiz) {
  while (1) {
    int brlttyCommand = brltty_readCommand();
    if (brlttyCommand == EOF) {
      return -1;
    }
    jint mappedCommand, mappedArg;
    mapBrlttyCommand(brlttyCommand, &mappedCommand, &mappedArg);
    if (mappedCommand < 0) {
      // Filter out commands that we don't handle, including BRL_NOOP.
      // Get the next command, until we get a valid command or EOF, in both
      // of which cases the loop will terminate.
      continue;
    }
    return (mappedArg << 16) | mappedCommand;
  }
}

// TODO: This method should throw more exceptions.
void
Java_com_googlecode_eyesfree_braille_service_display_BrlttyWrapper_addBytesFromDeviceNative(
    JNIEnv* env, jobject thiz, jbyteArray bytes, jint size) {
  // TODO: Get rid of the race condition here.
  NativeData *nat = getNativeData(env, thiz);
  if (!nat) {
    LOGE("Writing to destoyed driver, ignoring");
    return;
  }
  jsize bytesLen = (*env)->GetArrayLength(env, bytes);
  if (bytesLen < 0) {
    LOGE("Negative array length");
    return;
  }
  if (size > bytesLen) {
    (*env)->ThrowNew(env, class_IndexOutOfBoundsException, NULL);
    return;
  }
  jbyte *b = (*env)->GetByteArrayElements(env, bytes, NULL);
  if (!b) {
    return;
  }
  char *writeptr = b;
  while (size > 0) {
    ssize_t res = write(nat->pipefd[1], writeptr, size);
    if (res < 0) {
      if (errno == EINTR) {
        continue;
      }
      LOGE("Can't write to driver: %s", strerror(errno));
      goto releasebytes;
    } else if (res == 0) {
      LOGE("Can't write to driver");
      goto releasebytes;
    }
    size -= res;
    writeptr += res;
  }
releasebytes:
  (*env)->ReleaseByteArrayElements(env, bytes, b, JNI_ABORT);
}

void
Java_com_googlecode_eyesfree_braille_service_display_BrlttyWrapper_classInitNative(
    JNIEnv* env, jclass clazz) {
  if (!(class_BrlttyWrapper = (*env)->NewGlobalRef(env, clazz))) {
    LOGE("Couldn't get global ref for BrlttyWrapper class");
    return;
  }
  if (!(method_sendBytesToDevice = (*env)->GetMethodID(
          env, clazz, "sendBytesToDevice", "([B)Z"))) {
    LOGE("Couldn't find sendBytesToDevice method");
    return;
  }
  if (!(field_mNativeData = (*env)->GetFieldID(
          env, clazz, "mNativeData", "I"))) {
    LOGE("Couldn't find mNativeData field");
    return;
  }
  if (!(field_mDriverCode = (*env)->GetFieldID(
          env, clazz, "mDriverCode", "Ljava/lang/String;"))) {
    LOGE("Couldn't find mDriverCode field");
    return;
  }
  if (!(field_mBrailleDevice = (*env)->GetFieldID(
          env, clazz, "mBrailleDevice", "Ljava/lang/String;"))) {
    LOGE("Couldn't find mBrailleDevice field");
    return;
  }
  if (!(field_mTablesDir = (*env)->GetFieldID(
          env, clazz, "mTablesDir", "Ljava/lang/String;"))) {
    LOGE("Couldn't find mTablesDir field");
    return;
  }
  if (!(class_BrailleDisplayProperties = getGlobalClassRef(
          env, DISPLAY_PACKAGE "BrailleDisplayProperties"))) {
    return;
  }
  if (!(method_BrailleDisplayProperties_ctor =
        (*env)->GetMethodID(
            env, class_BrailleDisplayProperties, "<init>",
            "(II[L" DISPLAY_PACKAGE "BrailleKeyBinding;)V"))) {
    return;
  }
  if (!(class_BrailleKeyBinding = getGlobalClassRef(
          env, DISPLAY_PACKAGE "BrailleKeyBinding"))) {
    return;
  }
  if (!(method_BrailleKeyBinding_ctor =
        (*env)->GetMethodID(
            env, class_BrailleKeyBinding, "<init>",
            "(I[Ljava/lang/String;)V"))) {
    return;
  }
  if (!(class_OutOfMemoryError =
        getGlobalClassRef(env, "java/lang/OutOfMemoryError"))) {
    return;
  }
  if (!(class_NullPointerException =
        getGlobalClassRef(env, "java/lang/NullPointerException"))) {
    return;
  }
  if (!(class_IndexOutOfBoundsException =
        getGlobalClassRef(env, "java/lang/IndexOutOfBoundsException"))) {
    return;
  }
  if (!(class_RuntimeException =
        getGlobalClassRef(env, "java/lang/RuntimeException"))) {
    return;
  }
  if (!(class_String =
        getGlobalClassRef(env, "java/lang/String"))) {
    return;
  }
  if (!initCommandTables(env)) {
    LOGE("Couldn't initialize command tables");
    return;
  }
}

//////////////////////////////////////////////////////////////////////

static NativeData *
getNativeData(JNIEnv* env, jobject object) {
  return (NativeData*) (*env)->GetIntField(env, object, field_mNativeData);
}

static ssize_t writeDataToDevice(BluetoothAndroidConnection* conn,
                                 const void* buffer,
                                 size_t size) {
  LOGV("Writing %d bytes to bluetooth", size);
  NativeData *nat = conn->data;
  JNIEnv* env;
  (*nat->vm)->GetEnv(nat->vm, (void**)&env, nat->envVer);
  jbyteArray byteArray = (*env)->NewByteArray(env, size);
  if (!byteArray) {
    errno = ENOMEM;
    return -1;
  }
  (*env)->SetByteArrayRegion(env, byteArray, 0, size, buffer);
  jboolean result = (*env)->CallBooleanMethod(env, nat->me,
                                              method_sendBytesToDevice,
                                              byteArray);
  if (!result || (*env)->ExceptionCheck(env)) {
    errno = EIO;
    return -1;
  }
  return size;
}

static jclass
getGlobalClassRef(JNIEnv* env, const char *name) {
  jclass localRef = (*env)->FindClass(env, name);
  if (!localRef) {
    LOGE("Couldn't find class %s", name);
    return NULL;
  }
  jclass globalRef = (*env)->NewGlobalRef(env, localRef);
  if (globalRef == NULL) {
    LOGE("Couldn't create global ref for class %s", name);
  }
  return globalRef;
}

// Gets the value of a static (presumably final) int field of the
// given class.  Returns the value of the field, or -1 if the field can't
// be found, in which case an exception is thrown as well.
static jint
getStaticIntField(JNIEnv* env, jclass clazz, const char* fieldName) {
  jfieldID id = (*env)->GetStaticFieldID(env, clazz, fieldName, "I");
  if (!id) {
    LOGE("Can't find field: %s", fieldName);
    return -1;
  }
  return (*env)->GetStaticIntField(env, clazz, id);
}

static jboolean
initCommandTables(JNIEnv* env) {
  jboolean ret = JNI_FALSE;
  jclass cls = (*env)->FindClass(env, DISPLAY_PACKAGE "BrailleInputEvent");
  if (!cls) {
    // Exception thrown by the JVM.
    goto cleanup;
  }
  NamedCommand namesToCommands[] = {
    { "CMD_NAV_LINE_PREVIOUS", BRL_CMD_LNUP },
    { "CMD_NAV_LINE_NEXT", BRL_CMD_LNDN },
    { "CMD_NAV_ITEM_PREVIOUS", BRL_CMD_CHRLT },
    { "CMD_NAV_ITEM_NEXT", BRL_CMD_CHRRT },
    { "CMD_NAV_PAN_LEFT", BRL_CMD_FWINLT },
    { "CMD_NAV_PAN_RIGHT", BRL_CMD_FWINRT },
    { "CMD_NAV_TOP", BRL_CMD_TOP },
    { "CMD_NAV_BOTTOM", BRL_CMD_BOT },
    { "CMD_SCROLL_BACKWARD", BRL_CMD_WINUP },
    { "CMD_SCROLL_FORWARD", BRL_CMD_WINDN },
    { "CMD_SELECTION_START", BRL_BLK_CLIP_NEW },
    { "CMD_SELECTION_END", BRL_BLK_COPY_LINE },
    { "CMD_SELECTION_PASTE", BRL_CMD_PASTE },
    { "CMD_ROUTE", BRL_BLK_ROUTE },
    { "CMD_BRAILLE_KEY", BRL_BLK_PASSDOTS },
    { "CMD_HELP", BRL_CMD_LEARN },
  };
  brlttyCommandMap = createCommandMap(
      env, cls, namesToCommands,
      sizeof(namesToCommands) / sizeof(namesToCommands[0]));
  if (brlttyCommandMap == NULL) {
    goto cleanup;
  }

  NamedCommand namesToKeys[] = {
    { "CMD_NAV_ITEM_PREVIOUS", BRL_KEY_CURSOR_LEFT },
    { "CMD_NAV_ITEM_NEXT", BRL_KEY_CURSOR_RIGHT },
    { "CMD_NAV_LINE_PREVIOUS", BRL_KEY_CURSOR_UP },
    { "CMD_NAV_LINE_NEXT", BRL_KEY_CURSOR_DOWN },
    { "CMD_KEY_ENTER", BRL_KEY_ENTER },
    { "CMD_KEY_DEL", BRL_KEY_BACKSPACE },
    { "CMD_KEY_FORWARD_DEL", BRL_KEY_DELETE },
    { "CMD_GLOBAL_BACK", BRL_KEY_ESCAPE },
    // Use function keys for keys without an obvious mapping in brltty.
    { "CMD_GLOBAL_HOME", BRL_KEY_FUNCTION + 0 },
    { "CMD_GLOBAL_RECENTS", BRL_KEY_FUNCTION + 1 },
    { "CMD_GLOBAL_NOTIFICATIONS", BRL_KEY_FUNCTION + 2 },
    { "CMD_SELECTION_SELECT_ALL", BRL_KEY_FUNCTION + 3 },
    { "CMD_SELECTION_CUT", BRL_KEY_FUNCTION + 4 },
    { "CMD_SELECTION_COPY", BRL_KEY_FUNCTION + 5 },
  };
  brlttyKeyMap = createCommandMap(
      env, cls, namesToKeys,
      sizeof(namesToKeys) / sizeof(namesToKeys[0]));
  if (brlttyKeyMap == NULL) {
    goto cleanup;
  }
  cmdActivateCurrent = getStaticIntField(env, cls, "CMD_ACTIVATE_CURRENT");
  if ((*env)->ExceptionCheck(env)) {
    goto cleanup;
  }
  ret = JNI_TRUE;
  goto out;

cleanup:
  freeCommandMap(brlttyKeyMap);
  freeCommandMap(brlttyCommandMap);
out:
  (*env)->DeleteLocalRef(env, cls);
  return ret;
}

static int
commandMapEntryComp(const void* a, const void* b) {
  CommandMapEntry* aEntry = (CommandMapEntry*) a;
  CommandMapEntry* bEntry = (CommandMapEntry*) b;
  return aEntry->brlttyValue - bEntry->brlttyValue;
}

static CommandMap* createCommandMap(JNIEnv* env,
                                    jclass cls,
                                    NamedCommand* namedCommands,
                                    size_t numNamedCommands) {
  CommandMap* commandMap = calloc(1, sizeof(*commandMap));
  if (!commandMap) {
    (*env)->ThrowNew(env, class_OutOfMemoryError, NULL);
    goto cleanup;
  }
  CommandMapEntry* entries = calloc(numNamedCommands, sizeof(*entries));
  if (!entries) {
    (*env)->ThrowNew(env, class_OutOfMemoryError, NULL);
    goto cleanup;
  }
  commandMap->entries = entries;
  commandMap->numEntries = numNamedCommands;
  int i;
  for (i = 0; i < numNamedCommands; ++i) {
    entries[i].brlttyValue = namedCommands[i].brlttyValue;
    entries[i].javaValue = getStaticIntField(
        env, cls, namedCommands[i].fieldName);
    if ((*env)->ExceptionCheck(env)) {
      goto cleanup;
    }
  }
  qsort(entries, numNamedCommands, sizeof(*entries), commandMapEntryComp);
  return commandMap;

cleanup:
  freeCommandMap(commandMap);
  return NULL;
}

static void
freeCommandMap(CommandMap* commandMap) {
  if (commandMap != NULL) {
    free(commandMap->entries);
    free(commandMap);
  }
}

static jint
commandMapGet(CommandMap* commandMap, int key) {
  CommandMapEntry keyEntry = { .brlttyValue = key };
  CommandMapEntry* found = bsearch(&keyEntry, commandMap->entries,
                                   commandMap->numEntries,
                                   sizeof(keyEntry), commandMapEntryComp);
  return found != NULL ? found->javaValue : -1;
}

static jint
mapBrlttyCommand(int brlttyCommand,
                 jint* outCommand, jint* outArg) {
  // Mask away some flags and bits we don't care about.
  int maskedCommand;
  int brlttyArg;
  if ((brlttyCommand & BRL_MSK_BLK) != 0) {
    maskedCommand = (brlttyCommand & BRL_MSK_BLK);
    brlttyArg = (brlttyCommand & BRL_MSK_ARG);
  } else {
    maskedCommand = (brlttyCommand & BRL_MSK_CMD);
    brlttyArg = 0;
  }
  if (maskedCommand == BRL_BLK_PASSKEY) {
    *outCommand = commandMapGet(brlttyKeyMap, brlttyArg);
    *outArg = 0;
  } else if (maskedCommand == BRL_BLK_ROUTE
             && brlttyArg >= brltty_getTextCells()) {
    // Treat a routing command outside of the display as a distinct command.
    *outCommand = cmdActivateCurrent;
    *outArg = 0;
  } else {
    *outCommand = commandMapGet(brlttyCommandMap, maskedCommand);
    *outArg = brlttyArg;
  }
}

static jobjectArray
listKeyMap(JNIEnv* env) {
  ListKeyMapData lkd = {
    .env = env,
    .bindings = NULL,
    .bindingsSize = 0,
    .bindingsCapacity = 0,
  };
  jobject result = NULL;
  if ((*env)->PushLocalFrame(env, 128) < 0) {
    // Exception thrown.
    return NULL;
  }
  if (!brltty_listKeyMap(reportKeyBinding, &lkd)) {
    LOGE("Couldn't list key map");
    goto out;
  }
  jobjectArray array = (*env)->NewObjectArray(
      env, lkd.bindingsSize, class_BrailleKeyBinding, NULL);
  if (array == NULL) {
    // Exception thrown.
    goto out;
  }
  int i;
  for (i = 0; i < lkd.bindingsSize; ++i) {
    (*env)->SetObjectArrayElement(env, array, i, lkd.bindings[i]);
  }
  result = array;
out:
  free(lkd.bindings);
  return (*env)->PopLocalFrame(env, result);
}

static int
reportKeyBinding(
    int command, int keyNameCount, const char* keyNames[], void* data) {
  int mappedCommand, mappedArg;
  mapBrlttyCommand(command, &mappedCommand, &mappedArg);
  if (mappedCommand < 0) {
    // Unsupported command, don't report it.
    return 1;
  }
  ListKeyMapData *lkd = data;
  JNIEnv* env = lkd->env;
  if (lkd->bindingsSize >= lkd->bindingsCapacity) {
    int newCapacity = (lkd->bindingsCapacity == 0)
        ? 64
        : lkd->bindingsCapacity * 2;
    jobjectArray *newBindings = realloc(
        lkd->bindings, sizeof(*newBindings) * newCapacity);
    if (newBindings == NULL) {
      return 0;
    }
    lkd->bindings = newBindings;
    lkd->bindingsCapacity = newCapacity;
    if ((*env)->EnsureLocalCapacity(env, newCapacity + 16) < 0) {
      return 0;
    }
  }
  jobjectArray keys = (*env)->NewObjectArray(
      env, keyNameCount, class_String, NULL);
  if (keys == NULL) {
    return 0;
  }
  int i;
  for (i = 0; i < keyNameCount; ++i) {
    jobject name = (*env)->NewStringUTF(env, keyNames[i]);
    if (name == NULL) {
      return 0;
    }
    (*env)->SetObjectArrayElement(env, keys, i, name);
    (*env)->DeleteLocalRef(env, name);
  }
  jobject binding = (*env)->NewObject(
      env, class_BrailleKeyBinding, method_BrailleKeyBinding_ctor,
      mappedCommand, keys);
  if (binding == NULL) {
    return 0;
  }
  (*env)->DeleteLocalRef(env, keys);
  lkd->bindings[lkd->bindingsSize++] = binding;
  return 1;
}
