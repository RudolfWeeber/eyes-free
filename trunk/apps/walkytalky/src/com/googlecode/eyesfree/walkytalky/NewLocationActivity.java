/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

/**
 * UI for launching WalkyTalky.
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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.speech.RecognizerIntent;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * Allows users to search for new locations. Mark locations as favorite, browse
 * recent locations, delete locations from favorite. Returns (Lat, Lon) pair
 * corresponding to the searched address, to the caller Activity.
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

    private HashMap<String, String> contactDestinations;

    private AutoCompleteTextView destinationEditText;

    private Button goTextInputButton;

    private Button goFavoriteButton;

    private Button goRecentButton;

    private Button goContactButton;

    private Button markFavoriteButton;

    private Button removeFavoriteButton;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        self = this;
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        loadContactAddresses();

        setContentView(R.layout.new_loc);

        destinationEditText = (AutoCompleteTextView) findViewById(R.id.dest_EditText);
        destinationEditText.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN)
                        && (keyCode == KeyEvent.KEYCODE_ENTER)) {
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

        goContactButton = (Button) findViewById(R.id.goContact_Button);
        goContactButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                goContactHandler();
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
            // Back button pressed in NewLocationActivity, caller application
            // may also exit.
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
        if (favoriteDestinationsSize == 0) {
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
        if (recentDestinationsSize == 0) {
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
        if (recentDestinationsSize == 0) {
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
        if (favoriteDestinationsSize == 0) {
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

    private void loadContactAddresses() {
        contactDestinations = new HashMap<String, String>();
        // Get the base URI for People table in Contacts content provider.
        // ie. content://contacts/people/
        Uri mContacts = StructuredPostal.CONTENT_URI;

        // An array specifying which columns to return.
        String[] projection = new String[] {
                StructuredPostal.DISPLAY_NAME, StructuredPostal.FORMATTED_ADDRESS
        };

        // Best way to retrieve a query; returns a managed query.
        Cursor managedCursor = managedQuery(mContacts, projection, // Which
                // columns
                // to return.
                null, // WHERE clause--we won't specify.
                null, // no selection args
                Phone.DISPLAY_NAME + " ASC"); // Order-by clause.
        boolean moveSucceeded = managedCursor.moveToFirst();

        ArrayList<String> contactNames = new ArrayList<String>();
        while (moveSucceeded) {
            contactDestinations.put(managedCursor.getString(0) + " at "
                    + managedCursor.getString(1), managedCursor.getString(1));
            moveSucceeded = managedCursor.moveToNext();
        }
    }

    private void goContactHandler() {
        int contactDestinationsSize = contactDestinations.size();
        if (contactDestinationsSize == 0) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(self);
        final CharSequence[] items = new CharSequence[contactDestinationsSize];

        Iterator<Entry<String, String>> it = contactDestinations.entrySet().iterator();
        int i = 0;
        while (it.hasNext()) {
            Entry<String, String> entry = it.next();
            items[i] = entry.getKey();
            i++;
        }

        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String dest = contactDestinations.get(items[which]);
                if (dest.length() > 0) {
                    returnToCaller(dest);
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.getListView().setTextFilterEnabled(true);
        dialog.show();
    }

    /**
     * Returns (Lat, Lon) pair corresponding to the searched address, to the
     * caller Activity.
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
