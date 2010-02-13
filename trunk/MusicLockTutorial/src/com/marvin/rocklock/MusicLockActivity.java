
package com.marvin.rocklock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MusicLockActivity extends Activity {

    private MusicPlayer mp;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = new Intent(this, BootHandlerService.class);
        this.startService(i);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
