package com.google.marvin.espeak;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class eSpeak extends Activity {
	private static final String SO_FILE = "/data/data/com.google.marvin.espeak/lib/libespeakengine.so";


	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent data = new Intent();
		data.putExtra("nativelib", SO_FILE);
		setResult(Activity.RESULT_OK, data);
		finish();
	}

}
