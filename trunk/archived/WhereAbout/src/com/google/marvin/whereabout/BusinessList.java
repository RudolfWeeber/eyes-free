package com.google.marvin.whereabout;

import java.util.StringTokenizer;

import com.google.tts.TTS;
import com.google.tts.TTS.InitListener;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class BusinessList extends ListActivity
    implements OnFocusChangeListener {
	
  private Business[] bs;
  private TTS tts = null;
  private ListView listView = null;
  private String[] bussTitles = null;
  
  private boolean ttsLoaded = false;
  private int selItem = -1;
  
  @Override
  public void onCreate(Bundle savedInstance) {
      super.onCreate(savedInstance);
      
      tts = new TTS(this, new InitListener() {
        public void onInit(int arg0) {
           ttsLoaded = true;
        }}, true);
      
      Bundle bundle = getIntent().getExtras();
      String bsList[] =
          bundle.getStringArray("com.google.marvin.whereabout.Business");
      bs = new Business[bsList.length];
      bussTitles = new String[bsList.length];
      for (int i = 0; i < bsList.length; i++) {
        StringTokenizer st = new StringTokenizer(bsList[i], "$");
        bs[i] = new Business();
        bs[i].title = st.nextToken();
        bussTitles[i] = bs[i].title;
        bs[i].address = st.nextToken();
        bs[i].tel = st.nextToken();
        bs[i].lat = Double.parseDouble(st.nextToken());
        bs[i].lon = Double.parseDouble(st.nextToken());
        bs[i].dir = st.nextToken();
      }
      // Use an existing ListAdapter that will map an array
      // of strings to TextViews
      ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
              android.R.layout.simple_list_item_1, bussTitles);
      setListAdapter(adapter);
      listView = getListView();
      listView.setTextFilterEnabled(true);
      listView.setFocusableInTouchMode(true);
      for (int i = 0; i < listView.getChildCount(); i++) {
        View v = listView.getChildAt(i);
        v.setOnFocusChangeListener(
            new View.OnFocusChangeListener() {
          @Override
          public void onFocusChange(View arg0, boolean arg1) {
            tts.speak("new change", 0, null);
          }
        });
      }

      if (ttsLoaded) {
        tts.speak(getString(R.string.list_loaded_msg), 0, null);
      }
  }

  @Override
  public boolean onTrackballEvent(MotionEvent event) {
    return false;
  }
  
  @Override
  public void onDestroy() {
    tts.shutdown();
    super.onDestroy();
  }
  
  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    if (position >= 0) {
      /*Intent mapIntent = new Intent(this, RouteMap.class);
      mapIntent.putExtra("file", routeFileNames.get(position));
      startActivity(mapIntent);*/
      if (ttsLoaded) {
        tts.speak(bussTitles[position], 0, null);
      }
      finish();
    }
  }

  @Override
  public void onFocusChange(View v, boolean hasFocus) {
    // TODO Auto-generated method stub
    if (ttsLoaded) {
      tts.speak("change", 0, null);
    }
  }    
}
