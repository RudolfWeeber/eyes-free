#!/bin/sh
#
# This script imports the translations from the Translation Console into
# TalkBack. It puts the resulting files directly into the res/values-??/
# directories and does a g4 add or g4 edit on them, but you have to create
# the changelist to submit the changes.
#
# Note that the translation system that Android uses is different than other
# systems in google3, so a special android-internal tool must be built and
# used. Here are the full instructions, from scratch:
#
# 1. Get android-internal
#
# 2. Compile all of Android
#
# bash
# source build/envsetup.sh
# make -j 8
# wait an hour
#
# 3. It's okay if something fails, but make sure transconsole is compiled:
#
# mmm vendor/google/tools/transconsole
#
# 4. Add out/host/linux-x86/bin to your path (transconsole is there)
#
# 5. Update the list of locales below, look at the file
#    vendor/google/tools/localization/import-from-xtb in the android-internal
#    source to get the best list.
#
# 6. Update the list of xml files containing strings (look in res/values/
#    inside TalkBack)
#
# 7. Now run this script from your google3 directory like this:
#      sh research/android/marvin/talkback/import_translations.sh
#
# 8. Make a changelist with research/android/marvin/talkback/res/...

perforce_path=//depot/google3/research/android/marvin/talkback
history_file=/home/build/googlemobile/data/Android/history
xtb_root=/home/build/google3/googledata/transconsole/xtb

# fr it es de nl cs pl ja zh-TW zh-CN ru ko no es-US da el \
#              tr pt-PT pt rm sv ar bg ca en-GB fi hr hu in iw lt lv ro \
#              sk sl sr th uk vi fa tl

if ! test -d research/android/marvin/talkback ; then
  echo 'This must be run from your google3 directory!'
  exit
fi

# This is a stupid hack, but it doesn't seem to work unless there's a symlink
# to talkback named TalkBack (camel case) in the current directory.
if ! test -L TalkBack ; then
  ln -s research/android/marvin/talkback TalkBack
fi

for srclocale in fr it es de nl cs pl ja zh-TW zh-CN ru ko no es-US da el \
              tr pt-PT pt rm sv ar bg ca en-GB fi hr hu in iw lt lv ro \
              sk sl sr th uk vi fa tl
do
  locale=$srclocale;

  # Convert from a Translation Console locale to an Android (Java) locale.
  # Borrowed from 'default-to-translation' in the Android internal source.
  locale=$(echo $locale | sed 's/\([a-z][a-z]\)-\([A-Z][A-Z]\)/\1-r\2/');
  # Norwegian is NO in the translation console, but NB in Java/ICU locales.
  locale=$(echo $locale | sed 's/no/nb/');

  echo "TC locale: $srclocale | Android locale: $locale";

  xtb="$xtb_root/AndroidTalkBack/$srclocale.xtb";
  outdir="research/android/marvin/talkback/res/values-$locale";
  mkdir -p $outdir
  for strings in arrays.xml \
                 strings.xml \
                 strings_googletv.xml \
                 strings_thirdparty.xml
  do
    infile="TalkBack/res/values/$strings";
    outfile="$outdir/$strings";
    echo "Creating $outfile"
    #g4 edit "$outfile" > /dev/null 2>&1 ;
    transconsole -m $perforce_path TalkBack -p AndroidTalkBack \
        -h $history_file -i $xtb $infile > $outfile ;
    #g4 add $outfile  > /dev/null 2>&1 ;
  done
done
