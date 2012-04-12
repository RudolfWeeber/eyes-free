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

package com.cvox.browser;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.MenuItem.OnMenuItemClickListener;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class BrowserActivity extends Activity {

  public final static String TAG = BrowserActivity.class.toString();

  private TextToSpeech mTts;

  private WebView webview = null;

  private long magickey = new Random().nextLong();

  private Semaphore resultWait = new Semaphore(0);
  private int resultCode = Activity.RESULT_CANCELED;
  private Intent resultData = null;

  private ScriptDatabase scriptdb = null;

  public final static String LAST_VIEWED = "lastviewed";

  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);

    mTts = new TextToSpeech(this, null);

    requestWindowFeature(Window.FEATURE_PROGRESS);
    setContentView(R.layout.act_browse);

    scriptdb = new ScriptDatabase(this);
    scriptdb.onUpgrade(scriptdb.getWritableDatabase(), -10, 10);

    webview = (WebView) findViewById(R.id.browse_webview);

    WebSettings settings = webview.getSettings();
    settings.setSavePassword(false);
    settings.setSaveFormData(false);
    settings.setJavaScriptEnabled(true);
    settings.setSupportZoom(true);
    settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);

    FrameLayout zoomholder = (FrameLayout) this.findViewById(R.id.browse_zoom);
    zoomholder.addView(webview.getZoomControls());
    webview.getZoomControls().setVisibility(View.GONE);

    webview.setWebViewClient(new OilCanClient());
    webview.setWebChromeClient(new OilCanChrome());

    webview.addJavascriptInterface(new IntentHelper(), "intentHelper");
    webview.addJavascriptInterface(new TtsHelper(), "ttsHelper");

    // load the last-viewed page into browser
    String url = "http://www.google.com/search?q=clcworld";
    if (icicle != null && icicle.containsKey(LAST_VIEWED)) url = icicle.getString(LAST_VIEWED);

    // or watch for incoming requested urls
    if (getIntent().getExtras() != null && getIntent().getExtras().containsKey(SearchManager.QUERY))
      url = getIntent().getStringExtra(SearchManager.QUERY);

    webview.loadUrl(url);

  }

  private void loadNewPage(String url) {
    // reset blocked flag (when implemented) and load new page
    webview.loadUrl(url);
  }

  public void onNewIntent(Intent intent) {
    // pull new url from query
    String url = intent.getStringExtra(SearchManager.QUERY);
    this.loadNewPage(url);
  }

  protected void onSaveInstanceState(Bundle outState) {
    outState.putString(LAST_VIEWED, webview.getUrl());
  }

  public void onDestroy() {
    super.onDestroy();
    this.scriptdb.close();
  }

  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

    MenuItem gourl = menu.add(R.string.browse_gotourl);
    gourl.setIcon(R.drawable.ic_menu_goto);
    gourl.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        BrowserActivity.this.startSearch(webview.getUrl(), true, null, false);
        return true;
      }
    });

    MenuItem refresh = menu.add(R.string.browse_refresh);
    refresh.setIcon(R.drawable.ic_menu_refresh);
    refresh.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        webview.reload();
        return true;
      }
    });

    MenuItem scripts = menu.add(R.string.browse_manage);
    scripts.setIcon(android.R.drawable.ic_menu_agenda);
    scripts.setIntent(new Intent(BrowserActivity.this, ScriptListActivity.class));

    MenuItem example = menu.add(R.string.browse_example);
    example.setIcon(R.drawable.ic_menu_bookmark);
    example.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        final String[] examples =
            BrowserActivity.this.getResources().getStringArray(R.array.list_examples);
        new AlertDialog.Builder(BrowserActivity.this).setTitle(R.string.browse_example_title)
            .setItems(examples, new OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                BrowserActivity.this.loadNewPage(examples[which]);
              }
            }).create().show();

        return true;
      }
    });

    return true;
  }

  /**
   * Pass a resulting intent down to the waiting script call.
   */
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    this.resultCode = resultCode;
    this.resultData = data;
    this.resultWait.release();
  }

  public final static String MATCH = "intentHelper.startActivity(",
      MATCH_RESULT = "intentHelper.startActivityForResult(";


  /**
   * Prepare the given script for execution, specifically by injecting our magic
   * key for any {@link IntentHelper} calls.
   */
  private String prepareScript(String script) {
    String jsonlib = "";
    try {
      jsonlib = Util.getRawString(getResources(), R.raw.json2);
    } catch (Exception e) {
      Log.e(TAG, "Problem loading raw json library", e);
    }

    script = String.format("javascript:(function() { %s %s })();", jsonlib, script);

    script = script.replace(MATCH, String.format("%s'%d',", MATCH, magickey));
    script = script.replace(MATCH_RESULT, String.format("%s'%d',", MATCH_RESULT, magickey));

    return script;

  }


  /**
   * Javascript bridge to help launch intents and return results. Any callers
   * will need to provide the "magic key" to help protect against intent calls
   * from non-injected code.
   * 
   * @author jsharkey
   */
  final class IntentHelper {

    /**
     * Resolve Intent constants, like Intent.ACTION_PICK
     */
    private String getConstant(String key) {
      try {
        key = (String) Intent.class.getField(key).get(null);
      } catch (Exception e) {
      }
      return key;
    }

    /**
     * Parse the given JSON string into an Intent. This would be a good place to
     * add security checks in the future.
     */
    private Intent parse(String jsonraw) {
      Intent intent = new Intent();

      Log.d(TAG, String.format("parse(jsonraw=%s)", jsonraw));

      try {
        JSONObject json = new JSONObject(jsonraw);

        // look for specific known variables, otherwise assume extras
        // {"action":"ACTION_PICK","category":["CATEGORY_DEFAULT"],"type":"image/*"}

        Iterator keys = json.keys();
        while (keys.hasNext()) {
          String key = (String) keys.next();

          if ("action".equals(key)) {
            intent.setAction(getConstant(json.optString(key)));
          } else if ("category".equals(key)) {
            JSONArray categ = json.optJSONArray(key);
            for (int i = 0; i < categ.length(); i++)
              intent.addCategory(getConstant(categ.optString(i)));
          } else if ("type".equals(key)) {
            intent.setType(json.optString(key));
          } else if ("data".equals(key)) {
            intent.setData(Uri.parse(json.optString(key)));
          } else if ("class".equals(key)) {
            intent.setClassName(BrowserActivity.this, json.optString(key));
          } else {
            // first try parsing extra as number, otherwise fallback to string
            Object obj = json.get(key);
            if (obj instanceof Integer)
              intent.putExtra(getConstant(key), json.optInt(key));
            else if (obj instanceof Double)
              intent.putExtra(getConstant(key), (float) json.optDouble(key));
            else
              intent.putExtra(getConstant(key), json.optString(key));
          }

        }

      } catch (Exception e) {
        Log.e(TAG, "Problem while parsing JSON into Intent", e);
        intent = null;
      }

      return intent;
    }

    /**
     * Launch the intent described by JSON. Will only launch if magic key
     * matches for this browser instance.
     */
    public void startActivity(String trykey, String json) {
      if (magickey != Long.parseLong(trykey)) {
        Log.e(TAG, "Magic key from caller doesn't match, so we might have a malicious caller.");
        return;
      }

      Intent intent = parse(json);
      if (intent == null) return;

      try {
        BrowserActivity.this.startActivity(intent);
      } catch (ActivityNotFoundException e) {
        Log.e(TAG, "Couldn't find activity to handle the requested intent", e);
        Toast.makeText(BrowserActivity.this, R.string.browse_nointent, Toast.LENGTH_SHORT).show();
      }

    }

    /**
     * Launch the intent described by JSON and block until result is returned.
     * Will package and return the result as a JSON string. Will only launch if
     * the magic key matches for this browser instance.
     */
    public String startActivityForResult(String trykey, String json) {
      if (magickey != Long.parseLong(trykey)) {
        Log.e(TAG, "Magic key from caller doesn't match, so we might have a malicous caller.");
        return null;
      }

      Intent intent = parse(json);
      if (intent == null) return null;
      resultCode = Activity.RESULT_CANCELED;

      // start this intent and wait for result
      synchronized (this) {
        try {
          BrowserActivity.this.startActivityForResult(intent, 1);
          resultWait.acquire();
        } catch (ActivityNotFoundException e) {
          Log.e(TAG, "Couldn't find activity to handle the requested intent", e);
          Toast.makeText(BrowserActivity.this, R.string.browse_nointent, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
          Log.e(TAG, "Problem while waiting for activity result", e);
        }
      }

      JSONObject result = new JSONObject();
      result.optInt("resultCode", resultCode);

      // parse our response into json before handing back
      if (resultCode == Activity.RESULT_OK) {
        if (resultData.getExtras() != null) {
          try {
            JSONObject extras = new JSONObject();
            for (String key : resultData.getExtras().keySet())
              extras.put(key, resultData.getExtras().get(key));
            result.put("extras", extras);

          } catch (JSONException e1) {
            Log.e(TAG, "Problem while parsing extras", e1);
          }
        }

        if (resultData.getData() != null) {
          try {
            // assume that we are handling one contentresolver response
            Cursor cur = managedQuery(resultData.getData(), null, null, null, null);
            cur.moveToFirst();

            JSONObject data = new JSONObject();
            for (int i = 0; i < cur.getColumnCount(); i++)
              data.put(cur.getColumnName(i), cur.getString(i));
            result.put("data", data);

          } catch (Exception e) {
            Log.e(TAG, "Problem while parsing data result", e);
          }
        }
      }

      String resultraw = result.toString();
      Log.d(TAG, String.format("startActivityForResult() result=%s", resultraw));
      return resultraw;

    }

  }

  final class TtsHelper {

    public void speak(String message, int queueMode) {
      mTts.speak(message, queueMode, null);
    }

    public boolean isSpeaking() {
      return mTts.isSpeaking();
    }

    public int stop() {
      return mTts.stop();
    }


  }


  final class OilCanChrome extends WebChromeClient {

    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
      new AlertDialog.Builder(BrowserActivity.this).setMessage(message).setPositiveButton(
          android.R.string.ok, null).create().show();

      result.confirm();
      return true;

    }

    public void onProgressChanged(WebView view, int newProgress) {
      BrowserActivity.this.setProgress(newProgress * 100);
    }

    public void onReceivedTitle(WebView view, String title) {
      BrowserActivity.this.setTitle(BrowserActivity.this.getString(R.string.browse_title, title));
    }

  };

  public final static String USERSCRIPT_EXTENSION = ".user.js";

  final class OilCanClient extends WebViewClient {

    /**
     * Watch each newly loaded page for userscript extensions (.user.js) to
     * prompt user with install helper.
     */
    public void onPageStarted(WebView view, final String url, Bitmap favicon) {

      // if url matches userscript extension, launch installer helper dialog
      if (url.endsWith(BrowserActivity.USERSCRIPT_EXTENSION)) {
        new AlertDialog.Builder(BrowserActivity.this).setTitle(R.string.install_title).setMessage(
            getString(R.string.install_message, url)).setPositiveButton(android.R.string.ok,
            new OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                try {
                  String raw = Util.getUrlString(url);
                  scriptdb.insertScript(null, raw);
                  Toast.makeText(BrowserActivity.this, R.string.manage_import_success,
                      Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                  Log.e(TAG, "Problem while trying to import script", e);
                  Toast.makeText(BrowserActivity.this, R.string.manage_import_fail,
                      Toast.LENGTH_SHORT).show();
                }
              }
            }).setNegativeButton(android.R.string.cancel, null).create().show();

      }

    }

    /**
     * Handle finished loading of each page. Specifically this checks for any
     * active scripts based on the URL. When found a matching site, we inject
     * the JSON library and the applicable script.
     */
    public void onPageFinished(WebView view, String url) {
      if (scriptdb == null) {
        Log.e(TAG, "ScriptDatabase wasn't ready for finished page");
        return;
      }

      // for each finished page, try looking for active scripts
      List<String> active = scriptdb.getActive(url);
      Log.d(TAG, String.format("Found %d active scripts on url=%s", active.size(), url));
      if (active.size() == 0) return;

      // inject each applicable script into page
      for (String script : active) {
        script = BrowserActivity.this.prepareScript(script);
        webview.loadUrl(script);

      }

    }

    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      return false;
    }

  }

}
