#!/bin/bash

set -e

scriptdir="$(dirname $0)"
basedir="$scriptdir/.."

tempdir="$basedir/translationtables-$$"
srcdir="$basedir/jni/liblouiswrapper/liblouis/tables"
if [ -d "$tempdir" ]; then
  echo "Please remove $tempdir"
  exit 1
fi
if [ ! -d $srcdir ]; then
  echo "Can't find original table directory $srcdir"
  exit 1
fi

dstdir="$basedir/res/raw"
mkdir -p $dstdir

function cleanup() {
  rm -rf "$tempdir"
}

mkdir -p "$tempdir/liblouis/tables"
trap cleanup exit

# Use the files refered in the table list resource file.  This depends on the
# lexical format of this file, notably the fileName attributes being
# on the same lines as their values and the values being quoted with
# double quotes.
tablefiles=$(egrep 'fileName ?=' $basedir/res/xml/tablelist.xml \
  |sed -re "s#^.*fileName ?= ?\"([^\"]+)\".*\$#${srcdir}/\1#")

echo "Copying translation tables..."
$scriptdir/copywithdeps.py $tablefiles $tempdir/liblouis/tables
echo "Creating archive..."
(cd "$tempdir" && zip -9 translationtables.zip liblouis/tables/*)
mv "$tempdir/translationtables.zip" "$dstdir"

echo "Translation table archive successfully created."
