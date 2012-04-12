// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.marvin.talkback.formatter;

import com.google.android.marvin.talkback.R;
import android.content.res.Resources;

import java.util.HashMap;

/**
 * A wrapper for WebContentHandler containing Android-specific code for loading
 * tag descriptions from resources.
 *
 * @author credo@google.com (Tim Credo)
 */
public class TalkBackWebContentHandler extends WebContentHandler {

    public TalkBackWebContentHandler(Resources res) {
        super(loadMapFromStringArrays(
                res, R.array.html_input_to_desc_keys, R.array.html_input_to_desc_values),
                loadMapFromStringArrays(
                        res, R.array.html_role_to_desc_keys, R.array.html_role_to_desc_values),
                loadMapFromStringArrays(
                        res, R.array.html_tag_to_desc_keys, R.array.html_tag_to_desc_values));
    }

    /**
     * Load a HashMap from resources. Keys and values are provided as two string
     * arrays.
     */
    private static HashMap<String, String> loadMapFromStringArrays(
            Resources res, int keysResource, int valuesResource) {
        String[] keys = res.getStringArray(keysResource);
        String[] values = res.getStringArray(valuesResource);
        int maxIndex = Math.min(keys.length, values.length);
        HashMap<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < maxIndex; i++) {
            map.put(keys[i], values[i]);
        }
        return map;
    }
}
