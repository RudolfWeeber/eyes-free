#!/bin/bash

# This shell script builds brailleback with its dependencies.
# If the external dependencies are not present, it tries
# to check them out from subversion first.
# This script has been tested on Ubuntu Linux 10.04.4.

set -e

function die() {
  echo "Error: $*" >&2
  exit 1
}

function ensureSvnDependency() {
  local name="$1"
  local dir="$2"
  local svnurl="$3"
  if [ ! -d "${dir}" ]; then
    svn checkout "${svnurl}" "${dir}"
    (cd "${dir}/.." && patch -p1 < "${name}.patch")
  else
    svn info "${dir}" | grep -q "^URL: ${svnurl}" || \
      die "Your ${name} is checked out of the wrong subverison URL."
    echo "Using existing ${name} directory."
  fi
}

scriptdir=$(dirname "$0")
brailledir="${scriptdir}/.."
eyesfreedir="${brailledir}/.."
brlttydir="${brailledir}/service/jni/brlttywrapper/brltty"
brlttysvnurl="svn://mielke.cc/releases/brltty-4.4"
liblouisdir="${brailledir}/service/jni/liblouiswrapper/liblouis"
liblouissvnurl="http://liblouis.googlecode.com/svn/tags/liblouis_2_5_1"
apkname="${scriptdir}/bin/BrailleBack-debug.apk"

which android > /dev/null || \
  die "Make sure the 'android' tool from the android SDK is in your path"
which ndk-build > /dev/null || \
  die "Make sure the 'ndk-build' tool from the android NDK is in your path"
which ant > /dev/null || \
  die "Make sure the 'ant' tool (version 1.8 or later) is in your path"

ensureSvnDependency "brltty" "${brlttydir}" "${brlttysvnurl}"
ensureSvnDependency "liblouis" "${liblouisdir}" "${liblouissvnurl}"

for dir in libraries/compatutils libraries/utils braille/client \
    braille/service braille/brailleback; do
  android update project -p "${eyesfreedir}/${dir}"
  (cd "${eyesfreedir}/${dir}" && ant clean)
done

(cd "${brailledir}/service" && ndk-build clean && ndk-build -j16)
(cd "${brailledir}/brailleback" && ant debug)
if [ -f "${apkname}" ]; then
  echo "Successfully built ${apkname}"
  echo "Use the following command to install on a device:"
  echo "  adb install -r ${apkname}"
  echo "(If this fails, try uninstalling BrailleBack from the device first)."
else
  echo "Can't find ${apkname} after build"
fi
