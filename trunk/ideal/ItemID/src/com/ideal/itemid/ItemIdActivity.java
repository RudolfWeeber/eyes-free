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

package com.ideal.itemid;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;

/**
 * Main Activity for IDEAL Item ID. Enables users to scan a QR code or barcode.
 * If the user scans a QR code, reads out the contents of the QR code. If the
 * user scans a barcode, does a lookup for the UPC and speaks the result.
 */
public class ItemIdActivity extends Activity {
    private TextToSpeech mTts;

    private HashMap<String, String> mTtsParams;

    private OnUtteranceCompletedListener mUtteranceCompletedListener = new OnUtteranceCompletedListener() {
        @Override
        public void onUtteranceCompleted(String utteranceId) {
            doScan();
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isBarcodeScannerInstalled()) {
            displayNoBarcodeScannerMessage();
            return;
        }
        setContentView(R.layout.main);
        mTtsParams = new HashMap<String, String>();
        // The utterance ID doesn't matter; we don't really care what was said,
        // just that the TTS has finished speaking.
        mTtsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "done");
        mTts = new TextToSpeech(this, new OnInitListener() {
            @Override
            public void onInit(int arg0) {
                mTts.setOnUtteranceCompletedListener(mUtteranceCompletedListener);
                mTts.speak("Ready to scan", 0, mTtsParams);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTts != null) {
            mTts.shutdown();
        }
    }

    // Checks if Barcode Scanner is installed.
    private boolean isBarcodeScannerInstalled() {
        Intent i = new Intent("android.intent.action.MAIN");
        i.setClassName("com.google.zxing.client.android",
                "com.google.zxing.client.android.CaptureActivity");
        List<ResolveInfo> list = getPackageManager().queryIntentActivities(i,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    // Notifies the user that Barcode Scanner is not installed and provides a
    // way to easily find Barcode Scanner on Android Market.
    private void displayNoBarcodeScannerMessage() {
        Builder noBarcodeScannerMessage = new Builder(this);

        String titleText = "Warning: Barcode Scanner not found.";
        noBarcodeScannerMessage.setTitle(titleText);

        noBarcodeScannerMessage
                .setMessage("Ideal Item ID relies on Google's Barcode Scanner app. Please install Barcode Scanner to continue.");

        noBarcodeScannerMessage.setPositiveButton("Install Barcode Scanner",
                new Dialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri
                                .parse("market://search?q=pname:com.google.zxing.client.android"));
                        startActivity(i);
                        finish();
                        return;
                    }
                });

        noBarcodeScannerMessage.setNegativeButton("Quit", new Dialog.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                finish();
                return;
            }
        });

        noBarcodeScannerMessage.setCancelable(false);
        noBarcodeScannerMessage.show();
    }

    // Invokes the Barcode Scanner using the ZXing Team's IntentIntegrator
    // class.
    private void doScan() {
        IntentIntegrator.initiateScan(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        String content = data.getStringExtra("SCAN_RESULT");
        String format = data.getStringExtra("SCAN_RESULT_FORMAT");
        processResults(format, content);
    }

    // Handles the results from the Barcode Scanner.
    private void processResults(String format, String content) {
        if (IntentIntegrator.QR_CODE_TYPES.indexOf(format) != -1) {
            if (content.indexOf("audio://") == 0) {
                String filename = "/sdcard/idealItemId/" + content.replaceAll("audio://", "");
                if (new File(filename).exists()) {
                    MediaPlayer mPlayer = MediaPlayer.create(this, Uri.parse(filename));
                    mPlayer.setOnCompletionListener(new OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            mp.release();
                            doScan();
                        }
                    });
                    mPlayer.start();
                } else {
                    mTts.speak("Error: Unable to locate audio label on SD card.", 0, mTtsParams);
                }
            } else {
                mTts.speak(content, 0, mTtsParams);
            }
        } else {
            mTts.speak("Looking up barcode.", 0, null);
            HtmlDownloadJob currentJob = new HtmlDownloadJob("http://www.upcdatabase.com/item/"
                    + content);
            currentJob.execute();
        }
    }

    // Callback to speak the item descrption after the UPC database lookup is
    // done.
    private void upcLookupDone(String itemDesc) {
        mTts.speak(itemDesc, 0, mTtsParams);
    }

    // Extracts the item description from the UPC database lookup results page.
    private class HtmlDownloadJob extends UserTask<Void, Void, String> {
        private String targetUrl;

        public HtmlDownloadJob(String target) {
            targetUrl = target;
        }

        @Override
        public String doInBackground(Void... params) {
            String itemDesc = "Item not found.";
            try {
                String pageUrl = targetUrl;

                // Download the HTML content
                URL url = new URL(pageUrl);
                URLConnection cn = url.openConnection();
                cn.connect();
                InputStream stream = cn.getInputStream();
                StringBuffer htmlContent = new StringBuffer();
                byte buf[] = new byte[16384];
                do {
                    int numread = stream.read(buf);
                    if (numread <= 0) {
                        break;
                    } else {
                        htmlContent.append(new String(buf, 0, numread));
                    }
                } while (true);
                String descStartWrapperText = "<tr><td>Description</td><td></td><td>";
                int descStart = htmlContent.indexOf(descStartWrapperText);
                if (descStart == -1) {
                    return itemDesc;
                }
                descStart = descStart + descStartWrapperText.length();
                int descEnd = htmlContent.indexOf("</td>", descStart);
                itemDesc = htmlContent.substring(descStart, descEnd);
            } catch (MalformedURLException e) {
            } catch (IOException e) {
            } catch (OutOfMemoryError e) {
            }
            return itemDesc;
        }

        @Override
        public void onPostExecute(String itemDesc) {
            upcLookupDone(itemDesc);
        }
    }
}
