Building TalkBack from the command line using Ant
-------------------------------------------------

To get started with Ant, first make sure you have the latest version of Ant 
installed. If not:

sudo apt-get install ant1.8

The default version on Linux is 1.7, which is too old for the latest Android 
SDK. 

Next, generate a build script with the Android tools. From the talkback 
directory, run:

android update project --path . --target android-8 --name TalkBack

This will create some default build files for Android. These are build.xml,
default.properties, and local.properties.

However we will still need to tell Ant where to find external libraries. So 
edit the file default.properties (create it if it doesn't exist) and add the 
following lines:

android.library.reference.1=../commandslib/
android.library.reference.2=../actionslib/
android.library.reference.3=../ime/latinime/

You'll need to have actionslib, commandslib, ime/aimelib, and ime/latinime
checked out from marvin. Now you can build an unsigned TalkBack apk by running:

ant release

The resulting apk can be found at bin/Talkback-unsigned.apk, ready to be 
signed, zipaligned, and installed on a phone.


Notes about TalkBack keyboard library
-------------------------------------

The following files have been copied from the TalkBack keyboard library:
  res/layout/input_gingerbread.xml
  res/layout/keyboard_popup.xml

Additionally, the contents of the TalkBack keyboard library's manifest file
have been merged into AndroidManifest.xml. 
