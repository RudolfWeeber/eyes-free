/*
 * Copyright (C) 2009 Google Inc.
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
package com.android.ocr.service;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

import com.android.ocr.R;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Displays available and installed languages, allows downloading of new
 * language packs.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class LanguagesActivity extends Activity implements OnItemClickListener {
  private static final String TAG = "LanguagesActivity";
  private static final String XML_SOURCE =
      "http://eyes-free.googlecode.com/svn/trunk/thirdparty/tesseract/languages.xml";
  private static final String DATA_SOURCE =
      "http://eyes-free.googlecode.com/svn/trunk/thirdparty/tesseract/";
  private static final String DATA_TARGET = "/sdcard/tessdata/";

  private static final int ACTION_INSTALL = 0;
  private static final int ACTION_UNINSTALL = 1;
  private static final int ACTION_LOADED = 2;
  private static final int ACTION_CANCELED = 3;
  private static final int ACTION_ERROR = 4;
  private static final int ACTION_ADD = 5;

  private static final int RESULT_COMPLETED = 0;
  private static final int RESULT_ABORTED = 1;
  private static final int RESULT_FAILED = 2;

  private static final int VERSION = 3;

  private LanguageAdapter mAdapter;
  private ListView mListView;
  private ProgressDialog mProgress;

  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case ACTION_INSTALL: {
          onInstall((LanguageData) msg.obj, msg.arg1);
          break;
        }
        case ACTION_UNINSTALL: {
          onUninstall((LanguageData) msg.obj, msg.arg1);
          break;
        }
        case ACTION_LOADED: {
          mProgress.dismiss();
          break;
        }
        case ACTION_CANCELED: {
          finish();
          break;
        }
        case ACTION_ERROR: {
          onError(msg.arg1);
          break;
        }
        case ACTION_ADD: {
          if (msg.obj instanceof LanguageData)
            mAdapter.add((LanguageData) msg.obj);
          else
            Log.e(TAG, "Handler received ACTION_ADD with wrong object type");
          break;
        }
      }
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.languages);

    mAdapter = new LanguageAdapter(this, R.id.text_languages);
    mAdapter.setNotifyOnChange(true);

    mListView = (ListView) findViewById(R.id.list_languages);
    mListView.setAdapter(mAdapter);
    mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    mListView.setOnItemClickListener(this);

    String message = getString(R.string.languages_loading);
    mProgress = new ProgressDialog(this);
    mProgress.setMessage(message);
    mProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    mProgress.setIndeterminate(true);
    mProgress.setCancelable(true);

    mProgress.setOnCancelListener(new OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        Message msg = mHandler.obtainMessage(ACTION_CANCELED);
        msg.sendToTarget();
      }
    });

    XmlLoader xmlLoader = new XmlLoader();

    mProgress.show();
    xmlLoader.start();
  }

  private class XmlLoader extends Thread {
    @Override
    public void run() {
      int action = ACTION_LOADED;
      int arg1 = 0;

      if (!loadXml()) {
        action = ACTION_ERROR;
        arg1 = R.string.languages_error;
      }

      Message msg = mHandler.obtainMessage(action, arg1, 0);
      msg.sendToTarget();
    }

    public boolean loadXml() {
      Log.i(TAG, "Loading languages XML...");

      Document xmldoc;

      try {
        URL url = new URL(XML_SOURCE);
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        xmldoc = builder.parse(http.getInputStream());
      } catch (IOException e) {
        Log.e(TAG, e.toString());
        return false;
      } catch (ParserConfigurationException e) {
        Log.e(TAG, e.toString());
        return false;
      } catch (SAXException e) {
        Log.e(TAG, e.toString());
        return false;
      }

      Node languages = xmldoc.getFirstChild().getNextSibling();

      if (!languages.getNodeName().equals("languages")) {
        Log.e(TAG, "Missing languages node");
        return false;
      }

      NamedNodeMap attr = languages.getAttributes();
      Node node = attr.getNamedItem("version");

      int version = Integer.parseInt(node.getNodeValue());
      if (version != VERSION) {
        Log.e(TAG, "Incorrect version (is " + VERSION + ", should be " + version);
        return false;
      }

      String[] available = OcrLib.getLanguages();
      Node language = languages.getFirstChild().getNextSibling();

      while (language != null) {
        inflateLanguage(language, available);
        language = language.getNextSibling();
      }

      return true;
    }

    private void inflateLanguage(Node language, String[] available) {
      String type = language.getNodeName();
      if (!type.equals("language")) {
        return;
      }

      LanguageData data = new LanguageData();
      NamedNodeMap attr = language.getAttributes();
      Node node;

      node = attr.getNamedItem("size");
      data.size = node.getNodeValue();
      node = attr.getNamedItem("file");
      data.file = node.getNodeValue();
      node = attr.getNamedItem("iso6391");
      data.iso6391 = node.getNodeValue();
      node = attr.getNamedItem("iso6392");
      data.iso6392 = node.getNodeValue();
      node = attr.getNamedItem("name");
      data.name = node.getNodeValue();
      node = attr.getNamedItem("hidden");

      data.installed = (Arrays.binarySearch(available, data.iso6392) >= 0);

      if (node == null || data.installed) {
        Message msg = mHandler.obtainMessage(ACTION_ADD, data);
        msg.sendToTarget();
      }
    }
  }

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    if (parent != mListView) return;

    final LanguageData data = mAdapter.getItem(position);

    if (data == null) return;

    if (!data.installed) {
      installLanguage(data);
    } else {
      String message = getString(R.string.uninstall_confirm, data.name);
      showConfirm(message, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          if (which == DialogInterface.BUTTON_POSITIVE) uninstallLanguage(data);
        }
      });
    }
  }

  private void showConfirm(String text, DialogInterface.OnClickListener onClick) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setCancelable(true);
    builder.setMessage(text);
    builder.setPositiveButton("Yes", onClick);
    builder.setNegativeButton("Cancel", null);
    AlertDialog alert = builder.create();
    alert.show();
  }

  private void installLanguage(final LanguageData data) {
    Log.i(TAG, "Installing " + data.name + "...");

    DownloadDialog.OnCompleteListener onComplete = new DownloadDialog.OnCompleteListener() {
      @Override
      public void onComplete(DownloadDialog dialog) {
        Message msg = mHandler.obtainMessage(ACTION_INSTALL, RESULT_COMPLETED, 0, data);
        msg.sendToTarget();
      }
    };

    DownloadDialog.OnFailListener onFail = new DownloadDialog.OnFailListener() {
      @Override
      public void onFail(DownloadDialog dialog) {
        Message msg = mHandler.obtainMessage(ACTION_INSTALL, RESULT_FAILED, 0, data);
        msg.sendToTarget();
      }
    };

    DownloadDialog.OnCancelListener onCancel = new DownloadDialog.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        Message msg = mHandler.obtainMessage(ACTION_INSTALL, RESULT_ABORTED, 0, data);
        msg.sendToTarget();
      }
    };

    DownloadDialog download = new DownloadDialog(this, DATA_SOURCE, data.file, DATA_TARGET);
    download.setOnCompleteListener(onComplete);
    download.setOnFailListener(onFail);
    download.setOnCancelListener(onCancel);
    download.show();
  }

  private void onInstall(LanguageData data, int result) {
    switch (result) {
      case RESULT_COMPLETED: {
        data.installed = true;
        mAdapter.notifyDataSetChanged();
        String message = getString(R.string.install_completed, data.name);
        Toast.makeText(this, message, 3).show();
        break;
      }
      case RESULT_FAILED: {
        String message = getString(R.string.install_failed, data.name);
        Toast.makeText(this, message, 3).show();
        break;
      }
      case RESULT_ABORTED: {
        String message = getString(R.string.install_canceled, data.name);
        Toast.makeText(this, message, 3).show();
        break;
      }
    }
  }

  private void uninstallLanguage(final LanguageData data) {
    Log.i(TAG, "Uninstalling " + data.name + "...");

    File installed = new File(DATA_TARGET, data.iso6392 + ".traineddata");

    boolean deleted = installed.delete();
    int result = deleted ? RESULT_COMPLETED : RESULT_FAILED;

    Message msg = mHandler.obtainMessage(ACTION_UNINSTALL, result, 0, data);
    msg.sendToTarget();
  }

  private void onUninstall(LanguageData data, int result) {
    if (result == RESULT_COMPLETED) {
      data.installed = false;
      mAdapter.notifyDataSetChanged();
      String message = getString(R.string.uninstall_completed, data.name);
      Toast.makeText(this, message, 3).show();
    } else if (result == RESULT_FAILED) {
      String message = getString(R.string.uninstall_failed, data.name);
      Toast.makeText(this, message, 3).show();
    }
  }

  private void onError(int resId) {
    AlertDialog.Builder builder = new AlertDialog.Builder(LanguagesActivity.this);
    builder.setMessage(resId);
    builder.setCancelable(false);
    builder.setNeutralButton(android.R.string.ok, null);
    builder.setOnItemSelectedListener(new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        finish();
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
        // Do nothing
      }
    });

    AlertDialog alert = builder.create();
    alert.show();
  }

  private class LanguageAdapter extends ArrayAdapter<LanguageData> {
    private final int mColorRed;

    public LanguageAdapter(Context context, int textViewResourceId) {
      super(context, textViewResourceId);

      mColorRed = context.getResources().getColor(R.color.red);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      LanguageData data = getItem(position);

      LayoutInflater inflate = LayoutInflater.from(getContext());
      View view = inflate.inflate(R.layout.language, null);
      ((TextView) view.findViewById(R.id.text_size)).setText(data.size);
      ((TextView) view.findViewById(R.id.text_iso6392)).setText(data.iso6392);
      ((TextView) view.findViewById(R.id.text_name)).setText(data.name);

      TextView installed = ((TextView) view.findViewById(R.id.text_installed));
      installed.setText(data.installed ? R.string.installed : R.string.not_installed);
      if (!data.installed) installed.setTextColor(mColorRed);

      return view;
    }
  }

  private class LanguageData {
    String size;
    String file;
    String iso6391;
    String iso6392;
    String name;
    boolean installed;
  }
}
