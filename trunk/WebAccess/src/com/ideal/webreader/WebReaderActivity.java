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

package com.ideal.webreader;

import com.ideal.webaccess.R;
import com.ideal.webaccess.TtsContentProvider;
import com.ideal.webaccess.Unzipper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.Prediction;
import android.gesture.GestureOverlayView.OnGesturePerformedListener;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.Browser;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.webkit.ConsoleMessage;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Main browser activity that uses a GestureOverlay to allow the user to
 * navigate the page content using touch screen gestures.
 */
public class WebReaderActivity extends Activity implements OnGesturePerformedListener {
    private static final long[] VIBE_PATTERN = {
            0, 10, 70, 80
    };

    private static final int PREFS_REQUEST_CODE = 42;

    private static final int BOOKMARKS_REQUEST_CODE = 43;

    private GestureOverlayView mGesturesView;

    private GestureLibrary mLibrary;

    private WebView mWebView;

    private String jsonlib = "";

    private String loaderScript = "";

    private String mLastTriedUrl = "";

    private TextToSpeech mTts;

    private Vibrator mVibe;

    private WakeLock mWakeLock;

    private SharedPreferences mPrefs;

    private AlertDialog mInputUrlAlertDialog;

    private AlertDialog mSearchAlertDialog;

    private ArrayList<String> mSessionHistory;

    private final WebViewClient mWebViewClient = new WebViewClient() {
        @Override
        public void onPageFinished(WebView view, String url) {
            boolean addUrlToHistory = true;
            if ((mSessionHistory.size() > 0)
                    && (mSessionHistory.get(mSessionHistory.size() - 1).equals(url))) {
                addUrlToHistory = false;
            }
            if (addUrlToHistory) {
                mSessionHistory.add(url);
            }
            runScript(loaderScript);
            loadPrefs();
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description,
                String failingUrl) {
            if (failingUrl.contains(mLastTriedUrl)) {
                doSearch(mLastTriedUrl.replace("http://", ""));
                mLastTriedUrl = "";
            }
            super.onReceivedError(view, errorCode, description, failingUrl);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (mTts != null) {
                mTts.speak("Loading", 2, null);
            }
            super.onPageStarted(view, url, favicon);
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        doSetup();

        mTts = new TextToSpeech(this, null);
        mVibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mSessionHistory = new ArrayList<String>();

        try {
            String customLoaderFilename = Environment.getExternalStorageDirectory()
                    + "/ideal-webaccess/js/ideal-loader_custom.js";
            if (new File(customLoaderFilename).exists()) {
                loaderScript = Util.getRawString(getResources(), R.raw.idealwebaccess_custom);
            } else {
                loaderScript = Util.getRawString(getResources(), R.raw.idealwebaccess_webreader);
            }
            jsonlib = Util.getRawString(getResources(), R.raw.json2);
        } catch (Exception e) {
            Log.e("Greasemonkey", "Problem loading raw json library", e);
        }

        setContentView(R.layout.browser);

        mGesturesView = (GestureOverlayView) findViewById(R.id.gestureoverlayview);
        loadGestures();

        mGesturesView.addOnGesturePerformedListener(this);
        mGesturesView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mVibe.vibrate(VIBE_PATTERN, -1);
                stopSpeech();
                return false;
            }

        });

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        mWebView = (WebView) findViewById(R.id.webview);
        // Inject the defintion lookup script
        mWebView.addJavascriptInterface(new DefinitionLookupHelper(), "definitionLookupHelper");

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings
                .setLoadsImagesAutomatically(mPrefs.getBoolean("toggle_load_images_settings", true));
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "WEB_READER");

        mWebView.setWebViewClient(mWebViewClient);

//         mWebView.setWebChromeClient(new WebChromeClient() {
//         public boolean onConsoleMessage(ConsoleMessage cm) {
//         Log.e("ChromeVox", cm.message() + " -- From line "
//         + cm.lineNumber() + " of "
//         + cm.sourceId() );
//         return true;
//         }
//         });

        String url = "";
        if (getIntent().getData() != null) {
            url = getIntent().getData().toString();
        }
        if (url.length() < 1) {
            url = "http://apps4android.org/web-reader-tutorial/index.htm";
        }
        
        mWebView.setOnKeyListener(new OnKeyListener(){
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (inWebReaderMode) {
                    if (keyCode == KeyEvent.KEYCODE_M) {
                        startAutoRead();
                        return true;
                    }
                }
                return false;
            }            
        });
        
        mWebView.loadUrl(url);
        inWebReaderMode = false;
    }

    private void loadGestures() {
        try {
            mLibrary = GestureLibraries.fromFile(Environment.getExternalStorageDirectory()
                    + "/ideal-webaccess/gestures");
            mLibrary.load();
        } catch (Exception e) {

        }

        if (mLibrary != null) {
            if (mLibrary.getGestureEntries().size() != 8) {
                mLibrary = null;
            }
        }

        if (mLibrary == null) {
            mLibrary = GestureLibraries.fromRawResource(this, R.raw.gestures);
            mLibrary.load();
        }
    }

    private void loadPrefs() {
        WebSettings settings = mWebView.getSettings();
        settings.setDefaultFontSize(Integer.parseInt(mPrefs.getString("text_size", "20")));

        // Lens settings
        // Commented out since the ChromeVox lens is not optimized for mobile.
//        if (mPrefs.getBoolean("toggle_use_lens_settings", true)) {
//            runScript("IDEAL_LENS_ENABLED=true;");
//        } else {
//            runScript("IDEAL_LENS_ENABLED=false;");
//        }
        runScript("IDEAL_LENS_ENABLED=false;");
        String lensFontColor = mPrefs.getString("lens_fg_color", "default");
        if (!lensFontColor.equals("default")) {
            runScript("IDEAL_LENS_TEXTCOLOR='" + lensFontColor + "';");
        } else {
            runScript("IDEAL_LENS_TEXTCOLOR='yellow';");
        }
        String lensBgColor = mPrefs.getString("lens_bg_color", "default");
        if (!lensBgColor.equals("default")) {
            runScript("IDEAL_LENS_BGCOLOR='" + lensBgColor + "';");
        } else {
            runScript("IDEAL_LENS_BGCOLOR='black';");
        }

        // Web page settings
        String fontColor = mPrefs.getString("text_fg_color", "default");
        if (!fontColor.equals("default")) {
            runScript("IDEAL_STYLE_FONTCOLOR='" + fontColor + "';");
        }
        String bgColor = mPrefs.getString("text_bg_color", "default");
        if (!bgColor.equals("default")) {
            runScript("IDEAL_STYLE_BGCOLOR='" + bgColor + "';");
        }
        String letterSpacing = mPrefs.getString("letter_spacing", "default");
        if (!letterSpacing.equals("default")) {
            runScript("IDEAL_STYLE_LETTERSPACING=" + letterSpacing + ";");
        }
        String lineSpacing = mPrefs.getString("line_spacing", "default");
        if (!lineSpacing.equals("default")) {
            runScript("IDEAL_STYLE_LINESPACING=" + lineSpacing + ";");
        }

        // Image settings
        if (mPrefs.getBoolean("toggle_ignore_image_alt_settings", false)) {
            runScript("IDEAL_IGNORE_ALTTEXT=true;");
        } else {
            runScript("IDEAL_IGNORE_ALTTEXT=false;");
        }
        settings
                .setLoadsImagesAutomatically(mPrefs.getBoolean("toggle_load_images_settings", true));
    }

    @Override
    public void onNewIntent(Intent intent) {
        // pull new url from query
        if ((intent != null) && (intent.getData() != null)) {
            String url = intent.getData().toString();
            mWebView.loadUrl(url);
            inWebReaderMode = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if ((mWakeLock != null) && mPrefs.getBoolean("toggle_use_wakelock", false)) {
            mWakeLock.acquire();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopSpeech();
        if ((mWakeLock != null) && (mWakeLock.isHeld())) {
            mWakeLock.release();
        }
    }

    @Override
    public void onDestroy() {
        keepAutoReading = false;
        if (mWebView != null) {
            mWebView.stopLoading();
        }
        stopSpeech();
        if (mTts != null) {
            mTts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            stopSpeech();
            showSearchDialog();
            return true;
        }
        if (mSessionHistory.size() > 1) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                stopSpeech();
                return true;
            }
        }
        if ((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_UP)
                || (keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
                || (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if ((mWebView != null) && (mWebView.getHitTestResult() != null)) {
                speakDpadFocusedLink();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    // Search within the page for the link text since the HitTestResult only
    // contains the link type and the URL, not the actual link text.
    private void speakDpadFocusedLink() {
        String currentLink = mWebView.getHitTestResult().getExtra();
        if ((currentLink != null) && !currentLink.equals(lastSpokenLink)) {
            lastSpokenLink = currentLink;
            runScript("IDEAL_INTERFACE_SpeakLinkText('" + currentLink + "');");
        }
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        if ((mWebView != null) && (mWebView.getHitTestResult() != null)) {
            speakDpadFocusedLink();
        }
        return super.onTrackballEvent(event);
    }

    private String lastSpokenLink = "";

    private void stopSpeech() {
        keepAutoReading = false;
        if (mTts != null) {
            mTts.speak(" ", 2, null);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mSessionHistory.size() > 1) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                mSessionHistory.remove(mSessionHistory.size() - 1);
                mWebView.loadUrl(mSessionHistory.get(mSessionHistory.size() - 1));
                inWebReaderMode = false;
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    public void onGesturePerformed(GestureOverlayView overlay, Gesture gesture) {
        keepAutoReading = false;
        ArrayList<Prediction> predictions = mLibrary.recognize(gesture);
        // We want at least one prediction
        if (predictions.size() > 0) {
            Prediction prediction = predictions.get(0);
            // We want at least some confidence in the result
            if (prediction.score > 1.0) {
                mVibe.vibrate(VIBE_PATTERN, -1);
                if (prediction.name.equals("next")) {
                    runScript("IDEAL_INTERFACE_ReadNext();");
                } else if (prediction.name.equals("previous")) {
                    runScript("IDEAL_INTERFACE_ReadPrevious();");
                } else if (prediction.name.equals("action")) {
                    runScript("IDEAL_INTERFACE_ActOnCurrentElem();");
                } else if (prediction.name.equals("read all")) {
                    startAutoRead();
                } else if (prediction.name.equals("up")) {
                    runScript("IDEAL_INTERFACE_LessGranular();");
                } else if (prediction.name.equals("down")) {
                    runScript("IDEAL_INTERFACE_MoreGranular();");
                } else if (prediction.name.equals("switch web reader mode")) {
                    switchWebReaderMode();
                } else if (prediction.name.equals("add bookmark")) {
                    bookmarkCurrentPage();
                } else if (prediction.name.equals("get definition")) {
                    if (mTts != null) {
                        mTts.speak("Looking up definition", 2, null);
                    }
                    runScript("IDEAL_INTERFACE_DefineCurrentPhrase();");
                }
            }
        }
    }

    private boolean inWebReaderMode = false;

    public void switchWebReaderMode() {
        if (inWebReaderMode) {
            inWebReaderMode = false;
        } else {
            inWebReaderMode = true;
        }
        runScript("IDEAL_INTERFACE_SwitchWebReaderMode();");
    }

    public void runScript(String script) {
        mWebView.loadUrl(String.format("javascript:(function() { %s %s })();", jsonlib, script));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        final Context self = this;

        MenuItem viewBookmarks = menu.add("View Bookmarks");
        viewBookmarks.setIcon(android.R.drawable.ic_menu_myplaces);
        viewBookmarks.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                Intent i = new Intent();
                i.setClass(self, BookmarksActivity.class);
                startActivityForResult(i, BOOKMARKS_REQUEST_CODE);
                return true;
            }
        });

        MenuItem gourl = menu.add("Go to URL");
        gourl.setIcon(R.drawable.ic_menu_goto);
        gourl.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                showInputUrlDialog();
                return true;
            }
        });

        MenuItem search = menu.add("Search");
        search.setIcon(android.R.drawable.ic_menu_search);
        search.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                showSearchDialog();
                return true;
            }
        });

        MenuItem settings = menu.add("Settings");
        settings.setIcon(android.R.drawable.ic_menu_manage);
        settings.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                Intent i = new Intent();
                i.setClass(self, PrefsActivity.class);
                startActivityForResult(i, PREFS_REQUEST_CODE);
                return true;
            }
        });

        MenuItem moreApps = menu.add("More Apps");
        moreApps.setIcon(android.R.drawable.ic_menu_search);
        moreApps.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                String marketUrl = "market://search?q=pub:\"IDEAL Group, Inc. Android Development Team\"";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(marketUrl));
                try {
                    startActivity(i);
                } catch (ActivityNotFoundException anf) {
                }
                return true;
            }
        });

        return true;
    }

    public void showInputUrlDialog() {
        Builder inputUrlDialogBuilder = new Builder(this);
        EditText inputTextView = new EditText(this);
        inputTextView.setHint("Enter URL here");
        inputTextView.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                goToUrlOrSearch(v.getText().toString());
                mInputUrlAlertDialog.dismiss();
                return true;
            }
        });
        inputUrlDialogBuilder.setView(inputTextView);
        mInputUrlAlertDialog = inputUrlDialogBuilder.create();
        mInputUrlAlertDialog.show();
    }

    public void showSearchDialog() {
        Builder searchDialogBuilder = new Builder(this);
        EditText inputTextView = new EditText(this);
        inputTextView.setHint("Enter search term here");
        inputTextView.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                doSearch(v.getText().toString());
                mSearchAlertDialog.dismiss();
                return true;
            }
        });
        searchDialogBuilder.setView(inputTextView);
        mSearchAlertDialog = searchDialogBuilder.create();
        mSearchAlertDialog.show();
    }

    private void goToUrlOrSearch(String target) {
        String workingUrl = target;
        if (!URLUtil.isValidUrl(workingUrl)) {
            workingUrl = "http://" + workingUrl;
        }
        if (!URLUtil.isValidUrl(workingUrl)) {
            workingUrl = "";
        }
        if (workingUrl.length() > 0) {
            mLastTriedUrl = target;
            mWebView.loadUrl(workingUrl);
            inWebReaderMode = false;
        } else {
            doSearch(target);
        }
    }

    private void doSearch(String query) {
        mWebView.loadUrl("http://www.google.com/m?q=" + query);
        inWebReaderMode = false;
    }

    private boolean keepAutoReading = false;

    private void startAutoRead() {
        keepAutoReading = true;
        runAutoReader();
    }

    private void runAutoReader() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (keepAutoReading) {
                    if (!TtsContentProvider.isSpeaking) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if (!TtsContentProvider.isSpeaking) {
                          runScript("IDEAL_INTERFACE_ReadNext();");
                        }
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    runAutoReader();
                }
            }
        }).start();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PREFS_REQUEST_CODE) {
            loadGestures();
            mWebView.reload();
        } else if (requestCode == BOOKMARKS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                String url = data.getStringExtra("URL");
                // Safety check to avoid double-running our own script
                if (!url.startsWith("javascript:")) {
                    mWebView.loadUrl(url);
                    inWebReaderMode = false;
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void bookmarkCurrentPage() {
        if (mWebView == null) {
            return;
        }
        String bookmarkTitle = mWebView.getTitle();
        String bookmarkUrl = mWebView.getUrl();
        if (bookmarkUrl.length() < 1) {
            return;
        }
        if (bookmarkTitle.length() < 1) {
            bookmarkTitle = bookmarkUrl;
        }
        String[] columnStrings = {
                BaseColumns._ID, Browser.BookmarkColumns.TITLE, Browser.BookmarkColumns.URL,
                Browser.BookmarkColumns.BOOKMARK, Browser.BookmarkColumns.VISITS
        };
        boolean alreadyBookmarked = false;
        Cursor c = getContentResolver().query(Browser.BOOKMARKS_URI, columnStrings, null, null,
                null);
        if (c != null) {
            boolean keepGoing = c.moveToFirst();
            while (keepGoing) {
                if ((c.getString(3) != null) && (c.getString(3).equals("1"))
                        && (c.getString(1) != null) && (c.getString(1).equals(bookmarkTitle))
                        && (c.getString(2) != null) && (c.getString(2).equals(bookmarkUrl))) {
                    alreadyBookmarked = true;
                }
                keepGoing = c.moveToNext();
            }
        }
        if (!alreadyBookmarked) {
            ContentValues bookmarkletContent = new ContentValues();
            bookmarkletContent.put(Browser.BookmarkColumns.TITLE, bookmarkTitle);
            bookmarkletContent.put(Browser.BookmarkColumns.URL, bookmarkUrl);
            bookmarkletContent.put(Browser.BookmarkColumns.BOOKMARK, 1);
            bookmarkletContent.put(Browser.BookmarkColumns.VISITS, 9999);
            getContentResolver().insert(Browser.BOOKMARKS_URI, bookmarkletContent);
        }
        mTts.speak("Bookmark added", 2, null);
    }

    final class DefinitionLookupHelper {
        public void lookup(String phrase) {
            DefinitionLookup.lookupAndSpeak(phrase, mTts);
        }
    }

    // Does the initial setup tasks:
    // Unzips needed JS files.
    // Adds government resource sites bookmark.
    private void doSetup() {
        try {
            Resources res = getResources();
            AssetFileDescriptor jsZipFd = res.openRawResourceFd(R.raw.ideal_js);
            InputStream stream = jsZipFd.createInputStream();
            (new Thread(new dataCheckAndUnzip(stream))).start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        boolean hasResourceSites = false;
        final String[] columnStrings = {
                BaseColumns._ID, Browser.BookmarkColumns.TITLE, Browser.BookmarkColumns.URL,
                Browser.BookmarkColumns.BOOKMARK, Browser.BookmarkColumns.VISITS
        };
        String bookmarkTitle = "Government Mobile Websites";
        String bookmarkUrl = "http://apps4android.org/govt/";
        Cursor c = getContentResolver().query(Browser.BOOKMARKS_URI, columnStrings, null, null,
                null);
        if (c != null) {
            boolean keepGoing = c.moveToFirst();
            while (keepGoing) {
                if ((c.getString(3) != null) && (c.getString(3).equals("1"))
                        && (c.getString(1) != null) && (c.getString(1).equals(bookmarkTitle))
                        && (c.getString(2) != null) && (c.getString(2).equals(bookmarkUrl))) {
                    hasResourceSites = true;
                }
                keepGoing = c.moveToNext();
            }
        }
        if (!hasResourceSites) {
            ContentValues bookmarkletContent = new ContentValues();
            bookmarkletContent.put(Browser.BookmarkColumns.TITLE, bookmarkTitle);
            bookmarkletContent.put(Browser.BookmarkColumns.URL, bookmarkUrl);
            bookmarkletContent.put(Browser.BookmarkColumns.BOOKMARK, 1);
            bookmarkletContent.put(Browser.BookmarkColumns.VISITS, 9999);
            getContentResolver().insert(Browser.BOOKMARKS_URI, bookmarkletContent);
        }
    }

    private class dataCheckAndUnzip implements Runnable {
        public InputStream stream;

        public dataCheckAndUnzip(InputStream is) {
            stream = is;
        }

        public void run() {
            Unzipper.doDataCheckAndUnzip(stream);
        }
    }
}
