package com.google.marvin.espeak;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class eSpeak extends Activity {
	private static final String ENGINE_NAME = "eSpeak";
	private static final String SO_FILE = "/data/data/com.google.marvin.espeak/lib/libespeakengine.so";


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent data = new Intent();
		data.putExtra("nativelib", SO_FILE);
		data.putExtra("name", ENGINE_NAME);
		setResult(Activity.RESULT_OK, data);
		finish();
	}

}
