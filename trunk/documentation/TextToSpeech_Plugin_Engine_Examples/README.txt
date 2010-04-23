Architecture summary:

1/ frameworks/base/core/java/android/speech/tts/TextToSpeech.java
This is the public API for TTS. It uses a TTS Android service whose interface is described in: frameworks/base/core/java/android/speech/tts/ITts.aidl
All TTS calls an app makes through the TextToSpeech class are routed to the service (see the mITts member variable)

2/ frameworks/base/packages/TtsService/src/android/tts/TtsService.java
This is the implementation of the TTS service. It handles the speech queue, and is responsible for instanciating the actuall speech synthesizer, an instance of the SynthProxy class, implemented in: frameworks/base/packages/TtsService/src/android/tts/SynthProxy.java

3/ frameworks/base/packages/TtsService/src/android/tts/SynthProxy.java
This class, when instanciated, is given the full path of the speech synth native library to load. It is the bridge between Java and native code.

4/ frameworks/base/packages/TtsService/jni/android_tts_SynthProxy.cpp
JNI for the SynthProxy class. It loads the synth native library whose interface is defined in frameworks/base/include/tts/TtsEngine.h.

5/ external/svox/pico/tts/com_svox_picottsengine.cpp
An implementation of TtsEngine.h which is built into a shared library, loaded by android_tts_SynthProxy.cpp

*****************************************************************************

Here are the steps needed to create a TTS plugin that will work with the Android framework:

I. Build the .so file

  The main piece of work that you need to do is write an equivalent of "com_svox_picottsengine.cpp", ie "com_mytts_ttsengine.cpp". This file must implement "TtsEngine.h" as seen here:
http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=include/tts/TtsEngine.h;hb=master 

  There are a few ways to build this .so file.

  A. Recommended - Use the Android NDK. See http://developer.android.com/sdk/ndk/index.html
  
  B. If the NDK does not work for you because you absolutely have to use something which is not exposed in the NDK, first try to import those bits into your NDK project and build it that way.
  
  C. If that still doesn't work, you can build against the framework. However, that approach is NOT recommended and should only be used as a last resort.

    1. Check out and build the Android source code by following the steps here:
       http://source.android.com/download

    2. Create your own "mytts" directory under "external" so that you have something which looks like: /mydroid/external/mytts  The easiest way to do this step is to just clone how the SVOX directory is setup - http://android.git.kernel.org/?p=platform/external/svox.git;a=tree

*****************************************************************************
    
II. Package it inside an Android apk file.

The Java APK wrapper MUST implement the following Activities:
<NameOfEngine> - Declares which languages are potentially supported, name of the engine, etc. This is a dummy Activity that is used to get some basic info about the engine.

DownloadVoiceData - Downloads data; this is the Activity that will be called if the engine is selected under TTS Settings and it needs voice data.

CheckVoiceData - Determines which voices are actually present on the system. This will be used to determine which Languages the user can choose from in TTS Settings.

GetSampleText - Sample text to be spoken to the user when they click "Listen to an example" in TTS Settings.

------------------------
The Java APK wrapper can implement this Activity if it has Settings for the user to configure:
EngineSettings - Engine specific configuration. This is the Activity that will be called if the user clicks on the engine specific settings under "Engines" in TTS Settings. What the user sets here will be passed to the .so as one String; it will be retrieved by the TTS Service by querying it from the engine's providers.SettingsProvider and then passed back to the .so in the native layer. If this is not implemented by your engine, the area under "Engines" will only have the enable/disable checkbox for your engine; there will not be a settings entry for your engine.

------------------------
The Java APK wrapper can also implement this Content Provider:
providers.SettingsProvider - Provides the String of config data that is to be passed to the engine's .so file. Normally this would be some data that you get from what the user has set in your EngineSettings Activity. The framework does not attempt to parse this data or do anything with it. It is up to the plugin vendor's Java wrapper to come up with this String, and it is up to the plugin vendor's engine .so to consume it. If you don't have this Content Provider, the native layer will just get an empty string passed to it.

------------------------

For apps that use TTS, we expect that developers will probably want to use the dummy NameOfEngine Activity to narrow down which engines have support for the languages they are interested in, followed by invoking CheckVoiceData for the specific languages they are interested in.

For examples, see: http://code.google.com/p/eyes-free/source/browse/#svn/trunk/documentation/TextToSpeech_Plugin_Engine_Examples
    
Also, for a 3rd party example, see Flite for Android: http://github.com/happyalu/Flite-TTS-Engine-for-Android
    
*****************************************************************************

III. Stress test your TTS

Make sure your TTS can handle starting and stopping speech rapidly. The easiest way to stress test your TTS is to use it with Android's built in TalkBack screen reader application and scroll around the various menus in Android. To enable TalkBack: Settings > Accessibility > TalkBack.


