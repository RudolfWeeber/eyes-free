#!/bin/bash

set -e

scriptdir="$(dirname $0)"
basedir="$scriptdir/.."

tempdir="$basedir/keytables-$$"
srcdir="$basedir/jni/brlttywrapper/brltty/Tables"
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

mkdir "$tempdir"
trap cleanup exit

tablefiles="\
  $srcdir/brl-bm-default.ktb \
  $srcdir/brl-eu-esys_large.ktb \
  $srcdir/brl-eu-esys_medium.ktb \
  $srcdir/brl-eu-esys_small.ktb \
  $srcdir/brl-fs-focus_small.ktb \
  $srcdir/brl-hw-all.ktb \
  $srcdir/brl-hm-sense.ktb \
  $srcdir/brl-pm-trio.ktb \
  $srcdir/brl-vo-bp.ktb"

echo "Copying key tables..."
$scriptdir/copywithdeps.py $tablefiles $tempdir
echo "Patching key tables..."
patch -d "$tempdir" < "$scriptdir/keytables.diff"

echo "Creating archive..."
zip -j -9 "$tempdir/keytables.zip" "$tempdir"/*.k??
mv "$tempdir/keytables.zip" "$dstdir"

echo "Keyboard table archive successfully created."
