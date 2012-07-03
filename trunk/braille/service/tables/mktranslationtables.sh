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

tableglob="$srcdir/*.ctb $srcdir/*.utb $srcdir/*.dis"
# Files that are large or have missing dependencies and are not currently
# used.
# The excludes are basenames only.
excludes="-X compress.ctb -X boxes.ctb -X zh-tw.ctb -X zh-hk.ctb \
-X de-g2-core.ctb"

echo "Copying translation tables..."
$scriptdir/copywithdeps.py $excludes $tableglob $tempdir/liblouis/tables
echo "Creating archive..."
(cd "$tempdir" && zip -9 translationtables.zip liblouis/tables/*)
mv "$tempdir/translationtables.zip" "$dstdir"

echo "Translation table archive successfully created."
