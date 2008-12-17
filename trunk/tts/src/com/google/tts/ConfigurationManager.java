/*
 * Copyright (C) 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.tts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.app.Activity;
import android.os.Bundle;

/**
 * Enables the user to configure TTS settings. This activity has not been
 * implemented yet.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class ConfigurationManager extends Activity {
  // Write code for configuration manager here

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    File espeakDataDir = new File("/sdcard/espeak-data/");
    if (!allFilesExist()) {
      setContentView(R.layout.downloading);
      (new Thread(new dataDownloader())).start();
    }
  }

  public class dataDownloader implements Runnable {
    public void run() {
      downloadEspeakData();
      finish();
    }
  }

  public static boolean allFilesExist() {
    String espeakDataDirStr = "/sdcard/espeak-data/";
    String[] datafiles =
        {"config", "en_dict", "phondata", "phonindex", "phontab", "soundicons/dummyfile",
            "voices/default", "voices/en/en", "voices/en/en-n", "voices/en/en-r",
            "voices/en/en-rp", "voices/en/en-sc", "voices/en/en-wi", "voices/en/en-wm"};

    File espeakDataDir = new File(espeakDataDirStr);
    boolean directoryExists = espeakDataDir.isDirectory();

    if (!directoryExists) {
      return false;
    } else {
      for (int i = 0; i < datafiles.length; i++) {
        File tempFile = new File(espeakDataDirStr + datafiles[i]);
        if (!tempFile.isFile()) {
          espeakDataDir.delete();
          return false;
        }
      }
    }
    return true;
  }

  public static void downloadEspeakData() {
    try {
      // Make sure the SD card is writable
      if (!new File("/sdcard/").canWrite()) {
        return;
      }

      // Create the espeak-data directory
      File espeakDataDir = new File("/sdcard/espeak-data/");
      espeakDataDir.mkdir();

      // Download the espeak-data zip file
      String fileUrl =
          "http://eyes-free.googlecode.com/svn/trunk/thirdparty/espeak-data.zip";
      fileUrl = (new URL(new URL(fileUrl), fileUrl)).toString();
      URL url = new URL(fileUrl);
      URLConnection cn = url.openConnection();
      cn.connect();
      InputStream stream = cn.getInputStream();

      File dlFile = new File("/sdcard/espeak-data/data.zip");
      dlFile.createNewFile();
      FileOutputStream out = new FileOutputStream(dlFile);

      byte buf[] = new byte[16384];
      do {
        int numread = stream.read(buf);
        if (numread <= 0) {
          break;
        } else {
          out.write(buf, 0, numread);
        }
      } while (true);

      stream.close();
      out.close();

      // Unzip into the espeak-data directory on the SD card
      ZipFile zip = new ZipFile("/sdcard/espeak-data/data.zip");
      Enumeration<? extends ZipEntry> zippedFiles = zip.entries();
      while (zippedFiles.hasMoreElements()) {
        ZipEntry entry = zippedFiles.nextElement();
        if (entry.isDirectory()) {
          File newDir = new File(espeakDataDir + entry.getName());
          newDir.mkdir();
        } else {
          InputStream is = zip.getInputStream(entry);
          String name = entry.getName();
          File outputFile = new File("/sdcard/espeak-data/" + name);
          String outputPath = outputFile.getCanonicalPath();
          name = outputPath.substring(outputPath.lastIndexOf("/") + 1);
          outputPath = outputPath.substring(0, outputPath.lastIndexOf("/"));
          File outputDir = new File(outputPath);
          outputDir.mkdirs();
          outputFile = new File(outputPath, name);
          outputFile.createNewFile();
          out = new FileOutputStream(outputFile);

          buf = new byte[16384];
          do {
            int numread = is.read(buf);
            if (numread <= 0) {
              break;
            } else {
              out.write(buf, 0, numread);
            }
          } while (true);

          is.close();
          out.close();
        }
      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}
