/*
 * Copyright (C) 2012 Google Inc.
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

package com.googlecode.eyesfree.brailleback;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.translate.BrailleTranslator;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * Input method service for keys from the connected braille display.
 */
public class BrailleIME extends InputMethodService implements NavigationMode {
    private static final int MAX_REQUEST_CHARS = 1000;
    /** Marks the extent of the editable text. */
    private static final MarkingSpan EDIT_TEXT_SPAN = new MarkingSpan();
    /** Marks the extent of the action button. */
    private static final MarkingSpan ACTION_LABEL_SPAN = new MarkingSpan();

    private static BrailleIME sInstance;

    private InputMethodManager mInputMethodManager;

    private ExtractedText mExtractedText;
    private int mExtractedTextToken = 0;
    /**
     * Start of current selection, relative to the start of the extracted
     * text.
     */
    private int mSelectionStart;
    /**
     * End (inclusive) of current selection, relative to the start of the
     * extracted text.
     */
    private int mSelectionEnd;
    /**
     * The text that is shown on the display.  Might be only part of the
     * full edit field if it is larger than {@code MAX_REQUEST_CHARS}.
     */
    private StringBuffer mCurrentText = new StringBuffer();
    // Whether accessibility and input focus are both on the same
    // node and that node represents an edit field.
    private boolean mEditFieldFocused = false;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mInputMethodManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        LogUtils.log(this, Log.VERBOSE, "Created Braille IME");
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        LogUtils.log(this, Log.VERBOSE,
                "onStartInput: inputType: %d, imeOption: %d, " +
                ", label: %s, hint: %s, package: %s, ",
                attribute.inputType, attribute.imeOptions, attribute.label,
                attribute.hintText, attribute.packageName);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ExtractedTextRequest req = new ExtractedTextRequest();
            req.token = ++mExtractedTextToken;
            req.hintMaxChars = MAX_REQUEST_CHARS;
            mExtractedText = getCurrentInputConnection().getExtractedText(req,
                    InputConnection.GET_EXTRACTED_TEXT_MONITOR);
        } else {
            mExtractedText = null;
        }
        updateCurrentText();
        if (isInEditField()) {
            updateDisplay();
        }
        updateState();
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        LogUtils.log(this, Log.VERBOSE, "onFinishInput");
        mExtractedText = null;
        updateCurrentText();
        updateState();
    }

    private void updateState() {
        BrailleBackService service = BrailleBackService.getActiveInstance();
        if (service != null) {
            if (isInEditField()) {
                service.imeOpened(this);
            } else {
                service.imeClosed();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // onFinishInput is not called when switching away from this IME
        // to another one, so clear the state here as well.
        mExtractedText = null;
        updateCurrentText();
        updateState();
        sInstance = null;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public View onCreateInputView() {
        final LayoutInflater inflater = getLayoutInflater();
        final View inputView = inflater.inflate(R.layout.braille_ime, null);
        final View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchAwayFromThisIme();
            }
        };

        inputView.setOnClickListener(clickListener);

        return inputView;
    }

    @Override
    public void onUpdateExtractedText(int token, ExtractedText text) {
        // The superclass only deals with fullscreen support, which we've
        // disabled, so don't call it here.
        if (mExtractedText == null || token != mExtractedTextToken) {
            return;
        }
        mExtractedText = text;
        updateCurrentText();
        updateDisplay();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        if (mExtractedText != null) {
            int off = mExtractedText.startOffset;
            int len = mCurrentText.length();
            newSelStart -= off;
            newSelEnd -= off;
            newSelStart = newSelStart < 0 ? 0 :
                    (newSelStart > len ? len : newSelStart);
            newSelEnd = newSelEnd < 0 ? 0 :
                    (newSelEnd > len ? len : newSelEnd);
            mSelectionStart = newSelStart;
            mSelectionEnd = newSelEnd;
            updateDisplay();
        }
    }

    private void updateCurrentText() {
        if (mExtractedText == null) {
            mCurrentText.setLength(0);
            mSelectionStart = mSelectionEnd = 0;
            return;
        }
        if (mExtractedText.text != null) {
            int len = mCurrentText.length();
            if (mExtractedText.partialStartOffset < 0) {
                // Complete update.
                mCurrentText.replace(0, len, mExtractedText.text.toString());
            } else {
                int start = Math.min(mExtractedText.partialStartOffset, len);
                int end = Math.min(mExtractedText.partialEndOffset, len);
                mCurrentText.replace(start, end,
                        mExtractedText.text.toString());
            }
        }

        // Update selection, keeping it within the text range even if the
        // client messed up.
        int len = mCurrentText.length();
        int start = mExtractedText.selectionStart;
        start = start < 0 ? 0 : (start > len ? len : start);
        int end = mExtractedText.selectionEnd;
        end = end < 0 ? 0 : (end > len ? len : end);
        mSelectionStart = start;
        mSelectionEnd = end;
    }

    public static BrailleIME getActiveInstance() {
        return sInstance;
    }

    @Override
    public boolean onMappedInputEvent(BrailleInputEvent event,
            DisplayManager.Content content) {
        switch (event.getCommand()) {
            case BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS:
                // TODO: Stop sending key events.
                return sendAndroidKey(KeyEvent.KEYCODE_DPAD_LEFT);
            case BrailleInputEvent.CMD_NAV_ITEM_NEXT:
                // TODO: Stop sending key events.
                return sendAndroidKey(KeyEvent.KEYCODE_DPAD_RIGHT);
            case BrailleInputEvent.CMD_KEY_DEL:
                return sendAndroidKey(KeyEvent.KEYCODE_DEL);
            case BrailleInputEvent.CMD_KEY_ENTER:
                return sendAndroidKey(KeyEvent.KEYCODE_ENTER);
            case BrailleInputEvent.CMD_ACTIVATE_CURRENT:
                return sendDefaultAction();
            case BrailleInputEvent.CMD_KEY_FORWARD_DEL:
                return sendAndroidKey(KeyEvent.KEYCODE_FORWARD_DEL);
            case BrailleInputEvent.CMD_BRAILLE_KEY:
                return handleBrailleKey(event.getArgument());
            case BrailleInputEvent.CMD_ROUTE:
                return route(event.getArgument(), content);
        }
        return false;
    }

    private boolean route(int position, DisplayManager.Content content) {
        InputConnection ic = getCurrentInputConnection();
        Spanned text = content.getSpanned();
        if (ic != null && text != null) {
            MarkingSpan[] spans = text.getSpans(position, position,
                    MarkingSpan.class);
            if (spans.length == 1) {
                if (spans[0] == ACTION_LABEL_SPAN) {
                    return emitFeedbackOnFailure(
                        sendDefaultAction(),
                        FeedbackManager.TYPE_COMMAND_FAILED);
                } else if (spans[0] == EDIT_TEXT_SPAN) {
                    return emitFeedbackOnFailure(
                        moveSelection(ic,
                                position - text.getSpanStart(EDIT_TEXT_SPAN)),
                        FeedbackManager.TYPE_COMMAND_FAILED);
                }
            } else if (spans.length == 0) {
                // Most likely, the user clicked on the label/hint part of the
                // content.
                emitFeedback(FeedbackManager.TYPE_NAVIGATE_OUT_OF_BOUNDS);
                return true;
            } else if (spans.length > 1) {
                LogUtils.log(this, Log.ERROR,
                        "Conflicting spans in Braille IME");
            }
        }
        emitFeedback(FeedbackManager.TYPE_COMMAND_FAILED);
        return true;
    }

    private boolean sendDefaultAction() {
        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        if (ei == null || ic == null) {
            return false;
        }

        int actionId = ei.actionId;
        if (actionId != 0) {
            return ic.performEditorAction(actionId);
        } else {
            return sendDefaultEditorAction(false);
        }
    }

    private boolean moveSelection(InputConnection ic, int pos) {
        if (mCurrentText == null) {
            return false;
        }
        int textLen = mCurrentText.length();
        pos = (pos < 0) ? 0 : ((pos <= textLen) ? pos : textLen);
        return ic.setSelection(pos, pos);
    }

    /**
     * Returns {@code true} if the input method is currently connected to an
     * active edit field and that field can handle direct text edits, in
     * contrast to just raw key events.
     */
    private boolean isInEditField() {
        return mExtractedText != null && mEditFieldFocused;
    }

    @Override
    public boolean onPanLeftOverflow(DisplayManager.Content content) {
        return false;
    }

    @Override
    public boolean onPanRightOverflow(DisplayManager.Content content) {
        return false;
    }

    @Override
    public void onActivate() {
        updateDisplay();
    }

    @Override
    public void onDeactivate() {
    }

    @Override
    public void onObserveAccessibilityEvent(AccessibilityEvent event) {
        checkEditFieldFocused();
        updateState();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Nothing to do.
    }

    /**
     * Attempts to send down and up key events for a raw {@code keyCode}
     * through an input connection.
     */
    private boolean sendAndroidKey(int keyCode) {
        return emitFeedbackOnFailure(
            sendAndroidKeyInternal(keyCode),
            FeedbackManager.TYPE_COMMAND_FAILED);
    }

    private boolean sendAndroidKeyInternal(int keyCode) {
        LogUtils.log(this, Log.VERBOSE, "sendAndroidKey: %d", keyCode);
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        long eventTime = SystemClock.uptimeMillis();
        if (!ic.sendKeyEvent(new KeyEvent(eventTime, eventTime,
                                KeyEvent.ACTION_DOWN, keyCode, 0 /*repeat*/))) {
            return false;
        }
        return ic.sendKeyEvent(
            new KeyEvent(eventTime, eventTime,
                    KeyEvent.ACTION_UP, keyCode, 0 /*repeat*/));
    }

    private boolean handleBrailleKey(int dots) {
        return emitFeedbackOnFailure(
            handleBrailleKeyInternal(dots),
            FeedbackManager.TYPE_COMMAND_FAILED);
    }

    private boolean handleBrailleKeyInternal(int dots) {
        // TODO: Support more than computer braille.  This means that
        // there's not a 1:1 correspondence between a cell and a character,
        // so requires more book-keeping.
        BrailleTranslator translator = getCurrentBrailleTranslator();
        InputConnection ic = getCurrentInputConnection();
        if (translator == null || ic == null) {
            return false;
        }
        CharSequence text = translator.backTranslate(
            new byte[] {(byte) dots});
        if (!TextUtils.isEmpty(text)) {
            return ic.commitText(text, 1);
        }
        return true;
    }

    private BrailleTranslator getCurrentBrailleTranslator() {
        BrailleBackService service = BrailleBackService.getActiveInstance();
        if (service != null) {
            return service.mTranslator;
        }
        return null;
    }

    private DisplayManager getCurrentDisplayManager() {
        BrailleBackService service = BrailleBackService.getActiveInstance();
        if (service != null) {
            return service.mDisplayManager;
        }
        return null;
    }

    private FeedbackManager getCurrentFeedbackManager() {
        BrailleBackService service = BrailleBackService.getActiveInstance();
        if (service != null) {
            return service.mFeedbackManager;
        }
        return null;
    }

    private void updateDisplay() {
        DisplayManager displayManager = getCurrentDisplayManager();
        if (displayManager == null) {
            return;
        }
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) {
            LogUtils.log(this, Log.WARN, "No input editor info");
            return;
        }
        CharSequence label = ei.label;
        CharSequence hint = ei.hintText;
        if (TextUtils.isEmpty(label)) {
            label = hint;
            hint = null;
        }
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (!TextUtils.isEmpty(label)) {
            text.append(label);
            text.append(": ");  // TODO: Put in a resource.
        }
        int editStart = text.length();
        text.append(mCurrentText);
        addMarkingSpan(text, EDIT_TEXT_SPAN, editStart);
        CharSequence actionLabel = getActionLabel();
        if (actionLabel != null) {
            text.append(" [");
            text.append(actionLabel);
            text.append("]");
            addMarkingSpan(text, ACTION_LABEL_SPAN,
                    text.length() - (actionLabel.length() + 2));
        }
        DisplaySpans.addSelection(text,
            editStart + mSelectionStart, editStart + mSelectionEnd);
        displayManager.setContent(
            new DisplayManager.Content(text));
    }

    private CharSequence getActionLabel() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) {
            return null;
        }
        if (ei.actionLabel != null) {
            return ei.actionLabel;
        }
        return getTextForImeAction(ei.imeOptions);
    }

    private void addMarkingSpan(Spannable spannable, MarkingSpan span,
            int position) {
        if (position < spannable.length()) {
            spannable.setSpan(span, position, spannable.length(),
               Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void switchAwayFromThisIme() {
        LogUtils.log(this, Log.DEBUG, "Switching to last IME");
        mInputMethodManager.switchToNextInputMethod(
            getWindow().getWindow().getAttributes().token,
            false);
    }

    private void checkEditFieldFocused() {
        boolean result = false;
        AccessibilityNodeInfoCompat root = null;
        AccessibilityNodeInfoCompat inputFocused = null;
        AccessibilityNodeInfoCompat accessibilityFocused = null;
        try {
            BrailleBackService service = BrailleBackService.getActiveInstance();
            if (service == null) {
                return;
            }
            root = getRootInActiveWindow(service);
            if (root == null) {
                return;
            }
            inputFocused = root.findFocus(
                AccessibilityNodeInfoCompat.FOCUS_INPUT);
            if (!AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                    this,
                    inputFocused,
                    EditText.class)) {
                return;
            }
            accessibilityFocused = root.findFocus(
                AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY);
            result = inputFocused.equals(accessibilityFocused);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(
                root, inputFocused, accessibilityFocused);
            mEditFieldFocused = result;
        }
    }

    /**
     * Returns the root node of the active window.
     */
    private static AccessibilityNodeInfoCompat getRootInActiveWindow(
        AccessibilityService service) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root != null) {
            return new AccessibilityNodeInfoCompat(root);
        } else {
            return null;
        }
    }

    private void emitFeedback(int type) {
        FeedbackManager feedbackManager = getCurrentFeedbackManager();
        if (feedbackManager != null) {
            feedbackManager.emitFeedback(type);
        }
    }

    private boolean emitFeedbackOnFailure(boolean result, int type) {
        if (!result) {
            emitFeedback(type);
        }
        return true;
    }

    private static class MarkingSpan {}

}
