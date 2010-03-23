This is what the SVOX Pico TTS engine that is currently shipping with Android would look like if it were to be turned into a standalone unbundled plugin engine.

------------------------

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