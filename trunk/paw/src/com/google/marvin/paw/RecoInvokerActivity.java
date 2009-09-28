package com.google.marvin.paw;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.Toast;

public class RecoInvokerActivity extends Activity {
  @Override
  protected void onResume() {
    super.onResume();
    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
        RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
    startActivityForResult(intent, 0);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode != Activity.RESULT_OK) {
      // Something went wrong, bail!
      finish();
      return;
    }
    ArrayList<String> results = data.getExtras().getStringArrayList(RecognizerIntent.EXTRA_RESULTS);

    // TODO: make this take the reco result
    Intent queryIntent = new Intent("com.google.marvin.paw.action.websearch");
    queryIntent.putExtra("query", results.get(0));
    sendBroadcast(queryIntent);

    Toast.makeText(this, "Processing: " + results.get(0), 1).show();

    finish();
  }
}
