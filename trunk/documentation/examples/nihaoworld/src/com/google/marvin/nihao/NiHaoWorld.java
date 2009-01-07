package com.google.marvin.nihao;

import com.google.tts.ConfigurationManager;
import com.google.tts.TTS;

import java.util.HashMap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Spinner;

public class NiHaoWorld extends Activity {
  private TTS myTts;
  private HashMap<String, Integer> hellos;
  private static final int ttsCheckReqCode = 42;

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == ttsCheckReqCode) {
      if (checkTtsRequirements(this, ttsCheckReqCode)) {
        myTts = new TTS(this, ttsInitListener, true);
      }
    }
  }

  /** Checks to make sure that all the requirements for the TTS are there */
  private boolean checkTtsRequirements(Activity activity, int resultCode) {
    if (!TTS.isInstalled(activity)) {
      Uri marketUri = Uri.parse("market://search?q=pname:com.google.tts");
      Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
      activity.startActivityForResult(marketIntent, resultCode);
      return false;
    }
    if (!ConfigurationManager.allFilesExist()) {
      int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
      Context myContext;
      try {
        myContext = createPackageContext("com.google.tts", flags);
        Class<?> appClass =
            myContext.getClassLoader().loadClass("com.google.tts.ConfigurationManager");
        Intent intent = new Intent(myContext, appClass);
        startActivityForResult(intent, resultCode);
      } catch (NameNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return false;
    }
    return true;
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    loadHellos();
    if (checkTtsRequirements(this, ttsCheckReqCode)) {
      myTts = new TTS(this, ttsInitListener, true);
    }
  }

  private TTS.InitListener ttsInitListener = new TTS.InitListener() {
    public void onInit(int version) {
      setContentView(R.layout.main);
      Button myButton = (Button) findViewById(R.id.sayhello);
      myButton.setOnClickListener(new OnClickListener() {
        public void onClick(View v) {
          sayHello();
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
    Spinner comboBox = (Spinner) findViewById(R.id.list);
    String selection = comboBox.getSelectedItem().toString();
    String languageCode = selection.substring(selection.indexOf("[") + 1, selection.length() - 1);
    myTts.setLanguage(languageCode);
    String hello = getString(hellos.get(languageCode));
    myTts.speak(hello, 0, null);
  }

  @Override
  protected void onDestroy() {
    myTts.shutdown();
    super.onDestroy();
  }


  
}
