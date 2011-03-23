// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands.impls;

import com.google.android.marvin.commands.CommandExecutor;

import android.content.Context;

/**
 * A command that provides the current location.
 * 
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public class LocationCommand implements CommandExecutor {

//    private boolean mUseGpsThisTime = true;
    
    @Override
    public String executeCommand(Context context) {
        // TODO: Refactor location service to return a single string.

//      Guide mGuide = new Guide(context);
//      String res = mGuide.speakLocation(mUseGpsThisTime);
//      mUseGpsThisTime = !mUseGpsThisTime;
//      return res;
        return "";
    }

}
