package com.google.tts;

import java.util.HashMap;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.view.Menu;
import android.view.MenuItem;

public class OldPrefsActivity extends PreferenceActivity {
  private static final int TTS_VOICE_DATA_CHECK_CODE = 42;
  private static final int TTS_VOICE_DATA_INSTALL_CODE = 43;
  private TTS myTts;
  private HashMap<String, Integer> hellos;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    myTts = new TTS(this, ttsInitListener, true);
  }

  private TTS.InitListener ttsInitListener = new TTS.InitListener() {
    public void onInit(int version) {
      addPreferencesFromResource(R.xml.oldprefs);
      loadEngines();
      loadHellos();
      Preference previewPref = findPreference("preview");
      previewPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
        public boolean onPreferenceClick(Preference preference) {
          sayHello();
          return true;
        }
      });
    }
  };

  private void loadEngines() {
    ListPreference enginesPref = (ListPreference) findPreference("engine_pref");

    Intent intent = new Intent("android.intent.action.START_TTS_ENGINE");

    ResolveInfo[] enginesArray = new ResolveInfo[0];
    PackageManager pm = getPackageManager();
    enginesArray = pm.queryIntentActivities(intent, 0).toArray(enginesArray);

    CharSequence entries[] = new CharSequence[enginesArray.length];
    CharSequence values[] = new CharSequence[enginesArray.length];
    for (int i = 0; i < enginesArray.length; i++) {
      entries[i] = enginesArray[i].loadLabel(pm);
      values[i] = enginesArray[i].activityInfo.packageName;
    }
    enginesPref.setEntries(entries);
    enginesPref.setEntryValues(values);

    // This is somewhat hacky because the eSpeak engine isn't fully ported
    // over yet. In addition, the framework has an Install Data option, so the
    // workflow is different. Therefore, do NOT take this into the framework!
    enginesPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        String chosenEngine = newValue.toString();
        if (chosenEngine.equals("com.svox.pico")) {
          Intent intent = new Intent();
          intent.setAction(TextToSpeechBeta.Engine.ACTION_CHECK_TTS_DATA);
          intent.setClassName("com.svox.pico", "com.svox.pico.CheckVoiceData");
          startActivityForResult(intent, TTS_VOICE_DATA_CHECK_CODE);
        }
        return true;
      }
    });
  }

  private void loadHellos() {
    hellos = new HashMap<String, Integer>();
    hellos.put("afr", R.string.af);
    hellos.put("bos", R.string.bs);
    hellos.put("yue", R.string.zhrHK);
    hellos.put("cmn", R.string.zh);
    hellos.put("hrv", R.string.hr);
    hellos.put("ces", R.string.cz);
    hellos.put("nld", R.string.nl);
    hellos.put("eng-USA", R.string.enrUS);
    hellos.put("eng-GBR", R.string.enrGB);
    hellos.put("epo", R.string.eo);
    hellos.put("fin", R.string.fi);
    hellos.put("fra", R.string.fr);
    hellos.put("deu", R.string.de);
    hellos.put("ell", R.string.el);
    hellos.put("hin", R.string.hi);
    hellos.put("hun", R.string.hu);
    hellos.put("isl", R.string.is);
    hellos.put("ind", R.string.id);
    hellos.put("ita", R.string.it);
    hellos.put("kur", R.string.ku);
    hellos.put("lat", R.string.la);
    hellos.put("mkd", R.string.mk);
    hellos.put("nor", R.string.no);
    hellos.put("pol", R.string.pl);
    hellos.put("por", R.string.pt);
    hellos.put("ron", R.string.ro);
    hellos.put("rus", R.string.ru);
    hellos.put("srp", R.string.sr);
    hellos.put("slk", R.string.sk);
    hellos.put("spa", R.string.es);
    hellos.put("spa-MEX", R.string.esrMX);
    hellos.put("swe", R.string.sw);
    hellos.put("swe", R.string.sv);
    hellos.put("tam", R.string.ta);
    hellos.put("tur", R.string.tr);
    hellos.put("vie", R.string.vi);
    hellos.put("cym", R.string.cy);
  }

  private void sayHello() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    // Change this to the system/lib path when in the framework
    String DEFAULT_TTS_BINARY = "com.svox.pico";
    String engine = prefs.getString("engine_pref", DEFAULT_TTS_BINARY);
    myTts.setEngine(engine);

    String languageCode = prefs.getString("lang_pref", "eng-USA");
    int rate = Integer.parseInt(prefs.getString("rate_pref", "140"));

    myTts.setLanguage(languageCode);
    myTts.setSpeechRate(rate);
    if (!hellos.containsKey(languageCode)) {
      languageCode = "eng-USA";
    }
    String hello = getString(hellos.get(languageCode));
    myTts.speak(hello, 0, null);
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(0, R.string.tts_apps, 0, R.string.tts_apps).setIcon(android.R.drawable.ic_menu_search);
    menu.add(0, R.string.homepage, 0, R.string.homepage).setIcon(
        android.R.drawable.ic_menu_info_details);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Intent i = new Intent();
    ComponentName comp =
        new ComponentName("com.android.browser", "com.android.browser.BrowserActivity");
    i.setComponent(comp);
    i.setAction("android.intent.action.VIEW");
    i.addCategory("android.intent.category.BROWSABLE");
    Uri uri;
    switch (item.getItemId()) {
      case R.string.tts_apps:
        uri = Uri.parse("http://eyes-free.googlecode.com/svn/trunk/documentation/tts_apps.html");
        i.setData(uri);
        startActivity(i);
        break;
      case R.string.homepage:
        uri = Uri.parse("http://eyes-free.googlecode.com/");
        i.setData(uri);
        startActivity(i);
        break;
    }
    return super.onOptionsItemSelected(item);
  }

  // TODO: This should be generic, not hardcoded to the SVOX installer.
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == TTS_VOICE_DATA_CHECK_CODE) {
      if (resultCode != TextToSpeechBeta.Engine.CHECK_VOICE_DATA_PASS) {
        Intent intent = new Intent();
        intent.setAction(TextToSpeechBeta.Engine.ACTION_INSTALL_TTS_DATA);
        intent.setClassName("com.svox.pico", "com.svox.pico.DownloadVoiceData");
        startActivityForResult(intent, TTS_VOICE_DATA_INSTALL_CODE);
      }
    }
  }

  @Override
  protected void onDestroy() {
    if (myTts != null) {
      myTts.shutdown();
    }
    super.onDestroy();
  }

}
