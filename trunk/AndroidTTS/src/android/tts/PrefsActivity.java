package android.tts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.tts.R;
import android.tts.R.string;
import android.tts.Tts.OnInitListener;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.view.Menu;
import android.view.MenuItem;

public class PrefsActivity extends PreferenceActivity {
  private Tts myTts;
  private HashMap<String, Integer> hellos;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    myTts = new Tts(this, ttsInitListener, true);
  }

  private OnInitListener ttsInitListener = new OnInitListener() {
    public void onInit(int version) {
      addPreferencesFromResource(R.xml.prefs);
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
  
  private void loadEngines(){
	  ListPreference enginesPref = (ListPreference) findPreference("engine_pref");
	  
	  Intent intent = new Intent("android.intent.action.START_TTS_ENGINE");

	  ResolveInfo[] enginesArray = new ResolveInfo[0];
	  PackageManager pm = getPackageManager();
	  enginesArray = pm.queryIntentActivities(intent, 0).toArray(enginesArray);
	  
	  CharSequence entries[] = new CharSequence[enginesArray.length];
	  CharSequence values[] = new CharSequence[enginesArray.length];
	  for (int i=0; i<enginesArray.length; i++){
		  entries[i] = enginesArray[i].loadLabel(pm);
		  ActivityInfo aInfo = enginesArray[i].activityInfo;
		  values[i] = aInfo.packageName + "/" + aInfo.name;
	  }
	  enginesPref.setEntries(entries);
	  enginesPref.setEntryValues(values);
  }

  private void loadHellos() {
    hellos = new HashMap<String, Integer>();
    hellos.put("af", R.string.af);
    hellos.put("bs", R.string.bs);
    hellos.put("zh-rHK", R.string.zhrHK);
    hellos.put("zh", R.string.zh);
    hellos.put("hr", R.string.hr);
    hellos.put("cz", R.string.cz);
    hellos.put("nl", R.string.nl);
    hellos.put("en-rUS", R.string.enrUS);
    hellos.put("en-rGB", R.string.enrGB);
    hellos.put("eo", R.string.eo);
    hellos.put("fi", R.string.fi);
    hellos.put("fr", R.string.fr);
    hellos.put("de", R.string.de);
    hellos.put("el", R.string.el);
    hellos.put("hi", R.string.hi);
    hellos.put("hu", R.string.hu);
    hellos.put("is", R.string.is);
    hellos.put("id", R.string.id);
    hellos.put("it", R.string.it);
    hellos.put("ku", R.string.ku);
    hellos.put("la", R.string.la);
    hellos.put("mk", R.string.mk);
    hellos.put("no", R.string.no);
    hellos.put("pl", R.string.pl);
    hellos.put("pt", R.string.pt);
    hellos.put("ro", R.string.ro);
    hellos.put("ru", R.string.ru);
    hellos.put("sr", R.string.sr);
    hellos.put("sk", R.string.sk);
    hellos.put("es", R.string.es);
    hellos.put("es-rMX", R.string.esrMX);
    hellos.put("sw", R.string.sw);
    hellos.put("sv", R.string.sv);
    hellos.put("ta", R.string.ta);
    hellos.put("tr", R.string.tr);
    hellos.put("vi", R.string.vi);
    hellos.put("cy", R.string.cy);
  }

  private void sayHello() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    String languageCode = prefs.getString("lang_pref", "en-rUS");
    int rate = Integer.parseInt(prefs.getString("rate_pref", "140"));

    myTts.setLanguage(languageCode);
    myTts.setSpeechRate(rate);
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


  @Override
  protected void onDestroy() {
    if (myTts != null) {
      myTts.shutdown();
    }
    super.onDestroy();
  }

}
