
package com.google.android.marvin.talkback;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Activity for broadcasting {@link TalkBackService#ACTION_PERFORM_GESTURE} to
 * open the TalkBack breakout menu from a search button long-press.
 */
public class ShortcutProxyActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = new Intent();
        intent.setPackage(getPackageName());
        intent.setAction(TalkBackService.ACTION_PERFORM_GESTURE);
        intent.putExtra(TalkBackService.EXTRA_GESTURE_NAME,
                TalkBackService.SHORTCUT_TALKBACK_BREAKOUT);

        sendBroadcast(intent, TalkBackService.PERMISSION_TALKBACK);

        finish();
    }
}
