#!/usr/bin/python2.4
# Copyright (C) 2010 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#
# Mass install script for quickly installing several APKs.
#
#
# Usage:
# 1. Install the Android SDK (http://developer.android.com/sdk/index.html)
# 2. On your Android device, turn on USB debugging. Menu > Settings > Applications > Development > USB debugging
# 3. Put this script inside the "tools" directory in the Android SDK.
# 4. Make a directory called "apps" under "tools" and put all the APKs you want installed in there. 
# 5. Run this script.
#
__author__ = 'Charles L Chen (clchen@google.com)'

import subprocess
import os

for filename in os.listdir("apps"):
  subprocess.Popen([r"adb", "install", "-r", "apps/" + filename]).wait()