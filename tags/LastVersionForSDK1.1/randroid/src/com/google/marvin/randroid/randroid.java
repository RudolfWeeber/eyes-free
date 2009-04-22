package com.google.marvin.randroid;


import com.google.tts.TTS;

import org.htmlparser.Parser;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.tags.ImageTag;
import org.htmlparser.tags.HeadingTag;
import org.htmlparser.tags.TextareaTag;
import org.htmlparser.tags.ParagraphTag;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class randroid extends Activity {
  private static final int PREFS_UPDATED = 42;
  private static final String randomUrl = "http://dynamic.xkcd.com/comic/random/";
  private static final String comicImagesBaseUrl = "http://imgs.xkcd.com/comics/";
  private static final String transcriptsUrlStart =
      "http://www.ohnorobot.com/transcribe.pl?comicid=apKHvCCc66NMg&url=http:%2F%2Fxkcd.com%2F";
  private static final String transcriptsUrlEnd = "%2F";
  private static final String permaLinkText = "Permanent link to this comic: http://xkcd.com/";

  private randroid self;

  private TTS tts;
  private String speakButtonPref;
  private Boolean displayToastPref;

  private WebView web;
  private TextView title;

  private String currentComment = "";
  private String currentComicNumber = "";
  private String currentComicTitle = "";


  private ProgressDialog loadingDialog = null;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    self = this;

    loadPrefs();

    setContentView(R.layout.main);
    web = (WebView) findViewById(R.id.webView);
    title = (TextView) findViewById(R.id.titleText);

    Button randomButton = (Button) findViewById(R.id.randomButton);
    randomButton.setOnClickListener(new OnClickListener() {
      public void onClick(View arg0) {
        loadRandomComic();
      }
    });

    ImageButton speakButton = (ImageButton) findViewById(R.id.speakButton);
    speakButton.setOnClickListener(new OnClickListener() {
      public void onClick(View arg0) {
        speak();
      }
    });

    tts = new TTS(this, null, false);

    loadRandomComic();
  }

  private void speak() {
    if (!speakButtonPref.equals(getString(R.string.none))) {
      if (tts.isSpeaking()) {
        tts.stop();
      } else {
        tts.speak(currentComment, 0, null);
      }
    }
    if (displayToastPref) {
      Toast.makeText(this, currentComment, 1).show();
    }
  }



  private void loadRandomComic() {
    // Clear the cache before loading so that the size of the app doesn't keep growing.
    web.clearCache(true);
    web.clearHistory();
    loadingDialog =
        ProgressDialog.show(self, getString(R.string.loading), getString(R.string.please_wait),
            true);
    class comicLoader implements Runnable {
      public void run() {
        String url = "";
        try {
          Parser p = new Parser();
          p.setURL(randomUrl.toString());

          NodeList headings = p.extractAllNodesThatMatch(new NodeClassFilter(HeadingTag.class));
          for (int i = 0; i < headings.size(); i++) {
            HeadingTag n = (HeadingTag) headings.elementAt(i);
            String headingText = n.getStringText();
            if (headingText.startsWith(permaLinkText)) {
              currentComicNumber =
                  headingText.substring(permaLinkText.length(), headingText.length() - 1);
              break;
            }
          }

          p.reset();
          NodeList images = p.extractAllNodesThatMatch(new NodeClassFilter(ImageTag.class));
          for (int i = 0; i < images.size(); i++) {
            ImageTag n = (ImageTag) images.elementAt(i);
            String imageUrl = n.getImageURL();
            if (imageUrl.startsWith(comicImagesBaseUrl)) {
              url = imageUrl;
              currentComment = n.getAttribute("title");
              currentComicTitle = n.getAttribute("alt");
              break;
            }
          }

          if (speakButtonPref.equals(getString(R.string.transcript))) {
            fetchTranscript();
          }
        } catch (ParserException pce) {
          currentComicTitle = self.getString(R.string.net_error);
        }
        web.loadUrl(url);
        updateDisplay();
        loadingDialog.dismiss();
      }
    }
    Thread loadThread = (new Thread(new comicLoader()));
    loadThread.start();
  }

  private void updateDisplay() {
    class titleTextUpdater implements Runnable {
      public void run() {
        title.setText(currentComicTitle);
      }
    }
    title.post(new titleTextUpdater());
  }


  private void fetchTranscript() {
    String transcriptUrl = transcriptsUrlStart + currentComicNumber + transcriptsUrlEnd;
    String transcript = "";
    try {
      Parser p = new Parser();
      p.setURL(transcriptUrl.toString());

      // Sometimes transcripts are inside a textarea
      NodeList textareas = p.extractAllNodesThatMatch(new NodeClassFilter(TextareaTag.class));
      if (textareas.size() > 0) {
        TextareaTag n = (TextareaTag) textareas.elementAt(0);
        transcript = n.getStringText();
        if (transcript.length() > 0) {
          currentComment = transcript.toLowerCase().replaceAll("<br>", "\n");
          return;
        }
      }

      // Other times, they are inside a P
      p.reset();
      NodeList paragraphs = p.extractAllNodesThatMatch(new NodeClassFilter(ParagraphTag.class));
      if (paragraphs.size() > 0) {
        ParagraphTag n = (ParagraphTag) paragraphs.elementAt(0);
        transcript = n.getStringText();
        if (transcript.length() > 0) {
          currentComment = transcript.toLowerCase().replaceAll("<br>", "\n");
          return;
        }
      }
    } catch (ParserException pce) {
      // This can happen if there is a network error. Do nothing here.
    }
  }


  @Override
  protected void onDestroy() {
    if (tts != null) {
      tts.shutdown();
    }
    // Clean up the cache
    web.clearCache(true);
    web.clearHistory();
    super.onDestroy();
  }



  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(0, R.string.preferences, 0, R.string.preferences).setIcon(
        android.R.drawable.ic_menu_preferences);
    menu.add(0, R.string.about, 0, R.string.about).setIcon(android.R.drawable.ic_menu_info_details);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Intent intent;
    switch (item.getItemId()) {
      case R.string.preferences:
        intent = new Intent(this, PrefsActivity.class);
        startActivityForResult(intent, PREFS_UPDATED);
        break;
      case R.string.about:
        displayAbout();
        break;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == PREFS_UPDATED) {
      loadPrefs();
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  private void loadPrefs() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    // From the game mode setting
    speakButtonPref = prefs.getString("speak_pref", getString(R.string.commentary));
    displayToastPref = prefs.getBoolean("display_toast_pref", true);
  }

  private void displayAbout() {
    Builder about = new Builder(this);

    about.setTitle(R.string.app_name);
    about.setMessage(R.string.about_message);

    final Activity self = this;

    about.setNeutralButton(R.string.source, new Dialog.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Intent i = new Intent();
        ComponentName comp =
            new ComponentName("com.android.browser", "com.android.browser.BrowserActivity");
        i.setComponent(comp);
        i.setAction("android.intent.action.VIEW");
        i.addCategory("android.intent.category.BROWSABLE");
        Uri uri = Uri.parse("http://eyes-free.googlecode.com");
        i.setData(uri);
        self.startActivity(i);
      }
    });

    about.setPositiveButton(R.string.xkcd_website, new Dialog.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Intent i = new Intent();
        ComponentName comp =
            new ComponentName("com.android.browser", "com.android.browser.BrowserActivity");
        i.setComponent(comp);
        i.setAction("android.intent.action.VIEW");
        i.addCategory("android.intent.category.BROWSABLE");
        Uri uri = Uri.parse("http://xkcd.com");
        i.setData(uri);
        self.startActivity(i);
      }
    });

    about.setNegativeButton(R.string.close, new Dialog.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {

      }
    });

    about.show();
  }

}
