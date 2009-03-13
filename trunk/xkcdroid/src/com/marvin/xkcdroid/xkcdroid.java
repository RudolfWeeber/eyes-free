package com.marvin.xkcdroid;


import com.google.tts.TTS;

import org.htmlparser.Parser;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.tags.ImageTag;
import org.htmlparser.tags.HeadingTag;
import org.htmlparser.tags.TextareaTag;
import org.htmlparser.tags.ParagraphTag;

import java.io.IOException;
import java.net.URL;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class xkcdroid extends Activity implements WebDataLoadedListener {
  private static final int PREFS_UPDATED = 42;
  private static final String randomUrl = "http://dynamic.xkcd.com/comic/random/";
  private static final String comicImagesBaseUrl = "http://imgs.xkcd.com/comics/";
  private static final String transcriptsUrlStart =
      "http://www.ohnorobot.com/transcribe.pl?comicid=apKHvCCc66NMg&url=http:%2F%2Fxkcd.com%2F";
  private static final String transcriptsUrlEnd = "%2F";
  private static final String permaLinkText = "Permanent link to this comic: http://xkcd.com/";

  private TTS tts;
  private String speakButtonPref;

  private WebView web;
  private TextView title;

  private String currentComment = "";
  private String currentComicNumber = "";
  private String currentComicTitle = "";



  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

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
    if (!speakButtonPref.equals("None")) {
      if (tts.isSpeaking()) {
        tts.stop();
      } else {
        tts.speak(currentComment, 0, null);
      }
    }
    Toast.makeText(this, currentComment, 1).show();
  }



  private void loadRandomComic() {
    class comicLoader implements Runnable {
      public void run() {
        String url = "";
        try {
          String html = HttpUtil.getResult(new URL(randomUrl.toString()));
          Parser p = new Parser();

          p.setInputHTML(html);
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

          p.setInputHTML(html);
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

          if (speakButtonPref.equals("Transcript")) {
            fetchTranscript();
          }
        } catch (IOException e) {
          Log.d("xkcdroid", "ioexception - network error");
        } catch (ParserException pce) {
          Log.d("xkcdroid", "parsing error - wtf?");
        }
        web.loadUrl(url);
      }
    }
    Thread loadThread = (new Thread(new comicLoader()));
    loadThread.start();
  }

  private void fetchTranscript() {
    String transcriptUrl = transcriptsUrlStart + currentComicNumber + transcriptsUrlEnd;
    String transcript = "";
    try {
      String html = HttpUtil.getResult(new URL(transcriptUrl.toString()));
      Parser p = new Parser();

      // Sometimes transcripts are inside a textarea
      p.setInputHTML(html);
      NodeList textareas = p.extractAllNodesThatMatch(new NodeClassFilter(TextareaTag.class));
      if (textareas.size() > 0) {
        TextareaTag n = (TextareaTag) textareas.elementAt(0);
        transcript = n.getStringText();
        if (transcript.length() > 0) {
          currentComment = transcript.toLowerCase().replaceAll("<br>", "\n");
          return;
        }
      }

      // Othertimes, they are inside a P
      p.setInputHTML(html);
      NodeList paragraphs = p.extractAllNodesThatMatch(new NodeClassFilter(ParagraphTag.class));
      if (paragraphs.size() > 0) {
        ParagraphTag n = (ParagraphTag) paragraphs.elementAt(0);
        transcript = n.getStringText();
        if (transcript.length() > 0) {
          currentComment = transcript.toLowerCase().replaceAll("<br>", "\n");
          return;
        }
      }
    } catch (IOException e) {
      Log.d("xkcdroid", "ioexception - network error");
    } catch (ParserException pce) {
      Log.d("xkcdroid", "parsing error - wtf?");
    }
  }


  @Override
  protected void onDestroy() {
    if (tts != null) {
      tts.shutdown();
    }
    super.onDestroy();
  }



  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(0, 0, 0, "Preferences").setIcon(android.R.drawable.ic_menu_preferences);
    menu.add(0, 1, 0, "About").setIcon(android.R.drawable.ic_menu_info_details);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Intent intent;
    switch (item.getItemId()) {
      case 0:
        intent = new Intent(this, PrefsActivity.class);
        startActivityForResult(intent, PREFS_UPDATED);
        break;
      case 1:
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
    speakButtonPref = prefs.getString("speak_pref", "Commentary");
  }

  private void displayAbout() {
    Builder about = new Builder(this);

    String titleText = "xkcdroid";

    about.setTitle(titleText);
    String message =
        "xkcd by Randall Munroe\n\nTranscripts by various xkcd fans and hosted on ohnorobot.com\n\nxkcdroid by Charles L. Chen";

    about.setMessage(message);

    final Activity self = this;

    about.setNeutralButton("xkcdroid Source", new Dialog.OnClickListener() {
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

    about.setPositiveButton("xkcd Website", new Dialog.OnClickListener() {
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

    about.setNegativeButton("Close", new Dialog.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {

      }
    });

    about.show();
  }


  public void onDataLoaded() {
    title.setText(currentComicTitle);
  }

}
