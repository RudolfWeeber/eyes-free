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
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/**
 * Enables the user to configure TTS settings. This activity has not been
 * implemented yet.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class ConfigurationManager extends Activity {

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (!allFilesExist()) {
            setContentView(R.layout.downloading);
            (new Thread(new dataDownloader())).start();
        } else {
            int version = Integer.parseInt(Build.VERSION.SDK);
            Intent intent = new Intent(this, TextToSpeechSettings.class);
            if (version < 4) {
                intent = new Intent(this, OldPrefsActivity.class);
            }
            startActivityForResult(intent, 42);
            finish();
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
        String[] datafiles = {
                "af_dict", "config", "cs_dict", "cy_dict", "de_dict", "el_dict", "en_dict",
                "eo_dict", "es_dict", "fi_dict", "fr_dict", "grc_dict", "hbs_dict", "hi_dict",
                "hu_dict", "id_dict", "is_dict", "it_dict", "jbo_dict", "ku_dict", "la_dict",
                "mk_dict", "nl_dict", "no_dict", "phondata", "phonindex", "phontab", "pl_dict",
                "pt_dict", "ro_dict", "ru_dict", "sk_dict", "sv_dict", "sw_dict", "ta_dict",
                "tr_dict", "vi_dict", "zh_dict", "zhy_dict", "mbrola/dummyfile",
                "mbrola_ph/af1_phtrans", "mbrola_ph/ca1_phtrans", "mbrola_ph/cr1_phtrans",
                "mbrola_ph/cs_phtrans", "mbrola_ph/de2_phtrans", "mbrola_ph/de4_phtrans",
                "mbrola_ph/de6_phtrans", "mbrola_ph/en1_phtrans", "mbrola_ph/es_phtrans",
                "mbrola_ph/es4_phtrans", "mbrola_ph/fr1_phtrans", "mbrola_ph/gr2_phtrans",
                "mbrola_ph/grc-de6_phtrans", "mbrola_ph/hu1_phtrans", "mbrola_ph/id1_phtrans",
                "mbrola_ph/in1_phtrans", "mbrola_ph/it3_phtrans", "mbrola_ph/la1_phtrans",
                "mbrola_ph/nl_phtrans", "mbrola_ph/pl1_phtrans", "mbrola_ph/pt_phtrans",
                "mbrola_ph/ptbr_phtrans", "mbrola_ph/ptbr4_phtrans", "mbrola_ph/ro1_phtrans",
                "mbrola_ph/sv_phtrans", "mbrola_ph/sv2_phtrans", "mbrola_ph/us_phtrans",
                "mbrola_ph/us3_phtrans", "soundicons/dummyfile", "voices/af", "voices/bs",
                "voices/cs", "voices/cy", "voices/de", "voices/default", "voices/el", "voices/eo",
                "voices/es", "voices/es-la", "voices/fi", "voices/fr", "voices/fr-be",
                "voices/grc", "voices/hi", "voices/hr", "voices/hu", "voices/id", "voices/is",
                "voices/it", "voices/jbo", "voices/ku", "voices/la", "voices/mk", "voices/nl",
                "voices/no", "voices/pl", "voices/pt", "voices/pt-pt", "voices/ro", "voices/ru",
                "voices/sk", "voices/sr", "voices/sv", "voices/sw", "voices/ta", "voices/tr",
                "voices/vi", "voices/zh", "voices/zhy", "voices/!v/croak", "voices/!v/f1",
                "voices/!v/f2", "voices/!v/f3", "voices/!v/f4", "voices/!v/m1", "voices/!v/m2",
                "voices/!v/m3", "voices/!v/m4", "voices/!v/m5", "voices/!v/m6",
                "voices/!v/whisper", "voices/en/en", "voices/en/en-n", "voices/en/en-r",
                "voices/en/en-rp", "voices/en/en-sc", "voices/en/en-wi", "voices/en/en-wm",
                "voices/mb/mb-af1", "voices/mb/mb-af1-en", "voices/mb/mb-br1", "voices/mb/mb-br3",
                "voices/mb/mb-br4", "voices/mb/mb-cr1", "voices/mb/mb-cz2", "voices/mb/mb-de2",
                "voices/mb/mb-de4", "voices/mb/mb-de4-en", "voices/mb/mb-de5",
                "voices/mb/mb-de5-en", "voices/mb/mb-de6", "voices/mb/mb-de6-grc",
                "voices/mb/mb-de7", "voices/mb/mb-en1", "voices/mb/mb-es1", "voices/mb/mb-es2",
                "voices/mb/mb-fr1", "voices/mb/mb-fr1-en", "voices/mb/mb-fr4",
                "voices/mb/mb-fr4-en", "voices/mb/mb-gr2", "voices/mb/mb-gr2-en",
                "voices/mb/mb-hu1", "voices/mb/mb-hu1-en", "voices/mb/mb-id1", "voices/mb/mb-it3",
                "voices/mb/mb-it4", "voices/mb/mb-la1", "voices/mb/mb-nl2", "voices/mb/mb-nl2-en",
                "voices/mb/mb-pl1", "voices/mb/mb-pl1-en", "voices/mb/mb-ro1",
                "voices/mb/mb-ro1-en", "voices/mb/mb-sw1", "voices/mb/mb-sw1-en",
                "voices/mb/mb-sw2", "voices/mb/mb-sw2-en", "voices/mb/mb-us1", "voices/mb/mb-us2",
                "voices/mb/mb-us3"
        };

        File espeakDataDir = new File(espeakDataDirStr);
        boolean directoryExists = espeakDataDir.isDirectory();

        if (!directoryExists) {
            return false;
        } else {
            for (int i = 0; i < datafiles.length; i++) {
                File tempFile = new File(espeakDataDirStr + datafiles[i]);
                if (!tempFile.exists()) {
                    for (int j = 0; j < datafiles.length; j++) {
                        File delFile = new File(espeakDataDirStr + datafiles[j]);
                        delFile.delete();
                    }
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
            String fileUrl = "http://eyes-free.googlecode.com/svn/trunk/thirdparty/espeak-data.zip";
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
            e.printStackTrace();
        }
    }
}
