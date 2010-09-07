/*
 * Copyright (C) 2010 The IDEAL Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ideal.webaccess;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for unzipping the JavaScript files.
 */
public class Unzipper {
    public static boolean unzip(InputStream stream) {
        String rootDirectory = Environment.getExternalStorageDirectory() + "/";
        FileOutputStream out;
        byte buf[] = new byte[16384];
        try {
            ZipInputStream zis = new ZipInputStream(stream);
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (entry.isDirectory()) {
                    File newDir = new File(rootDirectory + entry.getName());
                    newDir.mkdir();
                } else {
                    String name = entry.getName();
                    File outputFile = new File(rootDirectory + name);
                    String outputPath = outputFile.getCanonicalPath();
                    name = outputPath.substring(outputPath.lastIndexOf("/") + 1);
                    outputPath = outputPath.substring(0, outputPath.lastIndexOf("/"));
                    File outputDir = new File(outputPath);
                    outputDir.mkdirs();
                    outputFile = new File(outputPath, name);
                    outputFile.createNewFile();
                    out = new FileOutputStream(outputFile);

                    int numread = 0;
                    do {
                        numread = zis.read(buf);
                        if (numread <= 0) {
                            break;
                        } else {
                            out.write(buf, 0, numread);
                        }
                    } while (true);
                    out.close();
                }
                entry = zis.getNextEntry();
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

    }

    public static boolean doDataCheckAndUnzip(InputStream dataSourceStream) {
        final String basePath = Environment.getExternalStorageDirectory() + "/ideal-webaccess/js/";

        final String[] dataFiles = {
                "clc-domUtils.js", "ideal-dom.js", "ideal-globals.js", "ideal-interface.js",
                "ideal-keyhandler.js", "ideal-lens.js", "ideal-loader.js",
                "ideal-loader_webreader.js", "ideal-nav.js", "ideal-styler.js", "ideal-tts.js",
                "ideal-webaccess.user.js", "sitescripts/googlesearch/mgws.js"
        };

        for (int i = 0; i < dataFiles.length; i++) {
            if (!(new File(basePath + dataFiles[i])).exists()) {
                return unzip(dataSourceStream);
            }
        }
        return true;
    }
}
