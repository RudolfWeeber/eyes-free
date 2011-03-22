/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.eyesfree.inputmethod.latin.tutorial;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * This class extends the {@link EditText} widget by providing a listener
 * interface for selection changes. See {@link SelectionListener}.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class SelectionEditText extends EditText {
    private SelectionListener mSelectionListener = null;

    private int mOldSelStart = 0;
    private int mOldSelEnd = 0;

    public SelectionEditText(Context context) {
        super(context);
    }

    public SelectionEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SelectionEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setSelectionListener(SelectionListener selectionListener) {
        mSelectionListener = selectionListener;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);

        if (mSelectionListener != null) {
            mSelectionListener.onSelectionChanged(this, mOldSelStart, mOldSelEnd, selStart, selEnd);
        }

        mOldSelStart = selStart;
        mOldSelEnd = selEnd;
    }

    public static interface SelectionListener {
        public void onSelectionChanged(SelectionEditText editText, int oldSelStart, int oldSelEnd,
                int selStart, int selEnd);
    }
}
