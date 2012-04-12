/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.android.marvin.talkback;

import android.app.ActivityManager;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.android.marvin.talkback.TalkBackShortcutHandler.TalkBackShortcut;

/**
 * Allows the user to select a TalkBack-specific shortcut. This activity is
 * displayed when the user assigns a keyboard shortcut.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class TalkBackShortcutActivity extends ListActivity {
    /** The list of available shortcuts. */
    private ArrayAdapter<ShortcutWrapper> mListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mListAdapter = new ArrayAdapter<ShortcutWrapper>(this, android.R.layout.simple_list_item_1);

        // Add all defined shortcuts to the list.
        for (TalkBackShortcut shortcutType : TalkBackShortcut.values()) {
            mListAdapter.add(new ShortcutWrapper(shortcutType));
        }

        setContentView(R.layout.shortcut_picker);
        setListAdapter(mListAdapter);
    }

    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        final TalkBackShortcut shortcut = mListAdapter.getItem(position).shortcut;
        if (shortcut == null) {
            return;
        }

        onShortcutSelected(shortcut);
    }

    /**
     * Sets the activity's result to a populated
     * {@link Intent#ACTION_CREATE_SHORTCUT} intent. This is used to let the
     * system know what type of shortcut to create.
     *
     * @param shortcut The shortcut with which to populate the intent.
     */
    private void onShortcutSelected(TalkBackShortcut shortcut) {
        final Intent shortcutIntent = new Intent(this, TalkBackShortcutHandler.class);
        shortcutIntent.setAction(TalkBackShortcutHandler.ACTION_SHORTCUT);
        shortcutIntent.putExtra(TalkBackShortcutHandler.EXTRA_SHORTCUT_NAME, shortcut.name());

        final Bitmap icon = createIcon(this, R.drawable.icon);
        final String name = getString(shortcut.resId);

        final Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);

        setResult(RESULT_OK, intent);

        finish();
    }

    /**
     * Renders a {@link Drawable} resource as a {@link Bitmap}.
     *
     * @param context The parent context.
     * @param resId The resource identifier of the {@link Drawable} to render.
     * @return The rendered resource.
     */
    private static Bitmap createIcon(Context context, int resId) {
        final ActivityManager am =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final Resources res = context.getResources();
        final int iconDimension = res.getDimensionPixelSize(android.R.dimen.app_icon_size);
        final BitmapFactory.Options opts = new BitmapFactory.Options();

        final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resId, opts);

        final float aspect = iconDimension / (float) Math.max(opts.outWidth, opts.outHeight);
        final int dstWidth = (int) ((opts.outWidth * aspect) + 0.5f);
        final int dstHeight = (int) ((opts.outHeight * aspect) + 0.5f);

        final Bitmap scaled = Bitmap.createScaledBitmap(bitmap, dstWidth, dstHeight, true);

        // Sometimes createScaledBitmap() returns the same bitmap.
        if (scaled != bitmap) {
            bitmap.recycle();
        }

        return scaled;
    }

    /**
     * Wrapper class for {@link TalkBackShortcut} that returns the localized
     * value for {@link TalkBackShortcut#resId} from
     * {@link ShortcutWrapper#toString()}.
     *
     * @author alanv@google.com (Alan Viverette)
     */
    private class ShortcutWrapper {
        /** The wrapped shortcut. */
        private final TalkBackShortcut shortcut;

        /**
         * Constructs a new wrapper around the specified shortcut.
         *
         * @param shortcut
         */
        public ShortcutWrapper(TalkBackShortcut shortcut) {
            this.shortcut = shortcut;
        }

        @Override
        public String toString() {
            return getString(shortcut.resId);
        }
    }
}
