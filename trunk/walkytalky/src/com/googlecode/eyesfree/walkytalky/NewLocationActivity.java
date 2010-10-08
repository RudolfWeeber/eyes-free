// Copyright 2009 Google Inc. All Rights Reserved.

/**
 *
 * @author clchen@google.com (Charles L. Chen), hiteshk@google.com (Hitesh Khandelwal)
 */

package com.googlecode.eyesfree.walkytalky;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;

import java.util.ArrayList;

/**
 * Allows users to search for new locations. Mark locations as favorite, browse recent locations,
 * delete locations from favorite. Returns (Lat, Lon) pair corresponding to the searched address, to
 * the caller Activity.
 */
public class NewLocationActivity extends Activity {
    private static final int MAX_RECENT_DEST_COUNT = 10;

    public static final String PREFS_NAME = "WalkyTalky";

    private static final String RECENT_DESTINATIONS_PREFS_KEY = "RECENT_DESTINATIONS";

    private static final String FAVORITE_DESTINATIONS_PREFS_KEY = "FAVORITE_DESTINATIONS";

    private NewLocationActivity self;

    private SharedPreferences prefs;

    private ArrayList<String> recentDestinations;

    private ArrayList<String> favoriteDestinations;

    private AutoCompleteTextView destinationEditText;

    private Button goTextInputButton;

    private Button goFavoriteButton;

    private Button goRecentButton;

    private Button markFavoriteButton;

    private Button removeFavoriteButton;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        self = this;
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        setContentView(R.layout.new_loc);

        destinationEditText = (AutoCompleteTextView) findViewById(R.id.dest_EditText);
        destinationEditText.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {
                  // Perform action on key press
                    String dest = destinationEditText.getText().toString();
                    if (dest.length() > 0) {
                        returnToCaller(dest);
                    }
                  return true;
                }
                return false;
            }
        });

        goTextInputButton = (Button) findViewById(R.id.goTextInput_Button);
        goTextInputButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String dest = destinationEditText.getText().toString();
                if (dest.length() > 0) {
                    returnToCaller(dest);
                }
            }
        });

        goFavoriteButton = (Button) findViewById(R.id.goFavorite_Button);
        goFavoriteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                goFavoriteHandler();
            }
        });

        goRecentButton = (Button) findViewById(R.id.goRecent_Button);
        goRecentButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                goRecentHandler();
            }
        });

        markFavoriteButton = (Button) findViewById(R.id.markFavorite_Button);
        markFavoriteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                markFavoriteHandler();
            }
        });

        removeFavoriteButton = (Button) findViewById(R.id.removeFavorite_Button);
        removeFavoriteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                removeFavoriteHandler();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        recentDestinations = new ArrayList<String>();
        String[] savedDests = prefs.getString(RECENT_DESTINATIONS_PREFS_KEY, "").split("\n");
        for (int i = 0; i < savedDests.length && !savedDests[0].equals(""); i++) {
            recentDestinations.add(savedDests[i]);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line, recentDestinations);
        destinationEditText.setAdapter(adapter);

        favoriteDestinations = new ArrayList<String>();
        savedDests = prefs.getString(FAVORITE_DESTINATIONS_PREFS_KEY, "").split("\n");
        for (int i = 0; i < savedDests.length && !savedDests[0].equals(""); i++) {
            favoriteDestinations.add(savedDests[i]);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            doReco();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Back button pressed in NewLocationActivity, caller application may also exit.
            setResult(RESULT_FIRST_USER, getIntent());
            finish();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            // Open settings
            Intent iPrefs = new Intent(this, Settings.class);
            iPrefs.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(iPrefs);
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    private void doReco() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        startActivityForResult(intent, 0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void addRecentDestination(String dest) {
        boolean bumpedDestinationUp = false;
        for (int i = 0; i < recentDestinations.size(); i++) {
            if (recentDestinations.get(i).equals(dest)) {
                recentDestinations.remove(i);
                recentDestinations.add(0, dest);
                bumpedDestinationUp = true;
                break;
            }
        }
        while (recentDestinations.size() > MAX_RECENT_DEST_COUNT) {
            recentDestinations.remove(recentDestinations.size() - 1);
        }
        if (!bumpedDestinationUp) {
            recentDestinations.add(0, dest);
        }
        String savedDests = "";
        for (int i = 0; i < recentDestinations.size(); i++) {
            savedDests = savedDests + recentDestinations.get(i) + "\n";
        }
        Editor editor = prefs.edit();
        editor.putString(RECENT_DESTINATIONS_PREFS_KEY, savedDests);
        editor.commit();
    }

    private void addFavoriteDestination(String dest) {
        favoriteDestinations.add(0, dest);
        String savedDests = "";
        for (int i = 0; i < favoriteDestinations.size(); i++) {
            savedDests = savedDests + favoriteDestinations.get(i) + "\n";
        }
        Editor editor = prefs.edit();
        editor.putString(FAVORITE_DESTINATIONS_PREFS_KEY, savedDests);
        editor.commit();
    }

    private void goFavoriteHandler() {
        int favoriteDestinationsSize = favoriteDestinations.size();
        if(favoriteDestinationsSize == 0) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(self);
        final CharSequence[] items = new CharSequence[favoriteDestinationsSize];
        for (int i = 0; i < favoriteDestinationsSize; i++) {
            items[i] = favoriteDestinations.get(i);
        }
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String dest = (String) items[which];
                if (dest.length() > 0) {
                    returnToCaller(dest);
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.getListView().setTextFilterEnabled(true);
        dialog.show();
    }

    private void goRecentHandler() {
        int recentDestinationsSize = recentDestinations.size();
        if(recentDestinationsSize == 0) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(self);
        final CharSequence[] items = new CharSequence[recentDestinationsSize];
        for (int i = 0; i < recentDestinationsSize; i++) {
            items[i] = recentDestinations.get(i);
        }
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String dest = (String) items[which];
                if (dest.length() > 0) {
                    returnToCaller(dest);
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.getListView().setTextFilterEnabled(true);
        dialog.show();
    }

    private void markFavoriteHandler() {
        int recentDestinationsSize = recentDestinations.size();
        if(recentDestinationsSize == 0) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(self);
        final CharSequence[] items = new CharSequence[recentDestinationsSize];
        for (int i = 0; i < recentDestinationsSize; i++) {
            items[i] = recentDestinations.get(i);
        }
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String dest = (String) items[which];
                addFavoriteDestination(dest);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.getListView().setTextFilterEnabled(true);
        dialog.show();
    }

    private void removeFavoriteHandler() {
        int favoriteDestinationsSize = favoriteDestinations.size();
        if(favoriteDestinationsSize == 0) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(self);
        final CharSequence[] items = new CharSequence[favoriteDestinationsSize];
        for (int i = 0; i < favoriteDestinationsSize; i++) {
            items[i] = favoriteDestinations.get(i);
        }
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                favoriteDestinations.remove(which);
                String savedDests = "";
                for (int i = 0; i < favoriteDestinations.size(); i++) {
                    savedDests = savedDests + favoriteDestinations.get(i) + "\n";
                }
                Editor editor = prefs.edit();
                editor.putString(FAVORITE_DESTINATIONS_PREFS_KEY, savedDests);
                editor.commit();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.getListView().setTextFilterEnabled(true);
        dialog.show();
    }

    /**
     * Returns (Lat, Lon) pair corresponding to the searched address, to the caller Activity.
     *
     * @param destination Address to search
     */
    private void returnToCaller(String destination) {
        if (destination != null) {
            setResult(RESULT_OK, getIntent().putExtra("LOC", destination));
        } else {
            setResult(RESULT_CANCELED, getIntent());
        }
        addRecentDestination(destination);
        finish();
    }
}
