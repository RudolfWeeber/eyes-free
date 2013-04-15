#!/bin/bash

set -e

scriptdir="$(dirname $0)"
basedir="$scriptdir/.."

srcdir="$scriptdir/keytables"
if [ ! -d $srcdir ]; then
  echo "Can't find original table directory $srcdir"
  exit 1
fi

dstdir="$basedir/res/raw"
mkdir -p $dstdir

echo "Creating archive..."
zip -j -9 "$dstdir/keytables.zip" "$srcdir/"*.k??

echo "Keyboard table archive successfully created."
