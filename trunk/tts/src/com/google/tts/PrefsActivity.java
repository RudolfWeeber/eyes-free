package com.google.tts;


import java.util.HashMap;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;

public class PrefsActivity extends PreferenceActivity {
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
      addPreferencesFromResource(R.xml.prefs);
      loadHellos();
      Preference previewPref = findPreference("preview");
      previewPref.setOnPreferenceClickListener(new OnPreferenceClickListener(){
        public boolean onPreferenceClick(Preference preference) {
          sayHello();
          return true;
        }
      });
    }
  };
  
  

  private void loadHellos() {
    hellos = new HashMap<String, Integer>();
    hellos.put("af", R.string.af);
    hellos.put("bs", R.string.bs);
    hellos.put("zh-yue", R.string.zhyue);
    hellos.put("zh", R.string.zh);
    hellos.put("hr", R.string.hr);
    hellos.put("cz", R.string.cz);
    hellos.put("nl", R.string.nl);
    hellos.put("en-us", R.string.enus);
    hellos.put("en-uk", R.string.enuk);
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
    hellos.put("es-la", R.string.esla);
    hellos.put("sw", R.string.sw);
    hellos.put("sv", R.string.sv);
    hellos.put("ta", R.string.ta);
    hellos.put("tr", R.string.tr);
    hellos.put("vi", R.string.vi);
    hellos.put("cy", R.string.cy);
  }

  private void sayHello() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    String languageCode = prefs.getString("lang_pref", "en-us");
    int rate = Integer.parseInt(prefs.getString("rate_pref", "140"));
    
    myTts.setLanguage(languageCode);
    myTts.setSpeechRate(rate);
    String hello = getString(hellos.get(languageCode));
    myTts.speak(hello, 0, null);
  }
  
  
  
}
