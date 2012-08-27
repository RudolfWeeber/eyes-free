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

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Spanned;
import android.util.Log;

import com.googlecode.eyesfree.braille.display.BrailleInputEvent;
import com.googlecode.eyesfree.braille.display.Display;
import com.googlecode.eyesfree.braille.translate.BrailleTranslator;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;

import java.util.Arrays;

/**
 * Keeps track of the current display content and handles panning.
 */
public class DisplayManager
        implements Display.OnConnectionStateChangeListener,
        Display.OnInputEventListener {

    /** Dot pattern used to overlay characters under a selection. */
    // TODO: Make customizable.
    private static final int SELECTION_DOTS = 0xC0;
    /** Dot pattern used to overlay characters in a focused element. */
    // TODO: Make customizable.
    private static final int FOCUS_DOTS = 0xC0;

    private static final long BLINK_OFF_MILLIS = 800;
    private static final long BLINK_ON_MILLIS = 600;

    /**
     * Callback interface for notifying interested callers when the display is
     * panned out of the available content.  A typical reaction to such an
     * event would be to move focus to a different area of the screen and
     * display it.
     */
    public interface OnPanOverflowListener {
        void onPanLeftOverflow(Content content);
        void onPanRightOverflow(Content content);
    }

    /**
     * Listener for input events that also get information about the current
     * display content and position mapping for commands with a positional
     * argument.
     */
    public interface OnMappedInputEventListener {
        /**
         * Handles an input {@code event} that was received when
         * {@code content} was present on the display.
         *
         * If the input event has a positional argument, it is mapped
         * according to the display pan position in the content so that
         * it corresponds to the character that the user touched.
         *
         * {@code event} and {@code content} are owned by the caller and may
         * not be referenced after this method returns.
         *
         * NOTE: Since the display is updated asynchronously, there is a chance
         * that the actual content on the display when the user invoked
         * the command is different from {@code content}.
         */
        void onMappedInputEvent(BrailleInputEvent event, Content content);
    }

    /**
     * Builder-like class used to construct the content to put on the display.
     *
     * This object contains a {@link CharSequence} that represents what
     * characters to put on the display.  This sequence can be a
     * {@link Spannable} so that the characters can be annotated with
     * information about cursors and focus which will affect how the content
     * is presented on the display.  Arbitrary java objects may also be
     * included in the {@link Spannable} which can be used to determine what
     * action to take when the user invokes key commands related to a
     * particular position on the display (i.e. involving a cursor routing
     * key).  In particular, {@link AccessibilityNodeInfoCompat}s may be
     * included, in which case they will be recycled by the
     * {@link Content#recycle} method.  To facilitate movement outside the
     * bounds of the current {@link Content},
     * {@link AccessibilityNodeInfoCompat}s that represent the extent of the
     * content can also be added, but in that case, they are not included in
     * the {@link Spannable}.
     */
    public static class Content {

        private CharSequence mText;
        private AccessibilityNodeInfoCompat mFirstNode;
        private AccessibilityNodeInfoCompat mLastNode;

        public Content() {
        }

        /**
         * Shortcut to just set text for a one-off use.
         */
        public Content(CharSequence text) {
            mText = text;
        }

        public Content setText(CharSequence text) {
            mText = text;
            return this;
        }

        public CharSequence getText() {
            return mText;
        }

        public Spanned getSpanned() {
            if (mText instanceof Spanned) {
                return (Spanned) mText;
            }
            return null;
        }

        public Content setFirstNode(AccessibilityNodeInfoCompat node) {
            AccessibilityNodeInfoUtils.recycleNodes(mFirstNode);
            mFirstNode = AccessibilityNodeInfoCompat.obtain(node);
            return this;
        }

        public AccessibilityNodeInfoCompat getFirstNode() {
            return mFirstNode;
        }

        public Content setLastNode(AccessibilityNodeInfoCompat node) {
            AccessibilityNodeInfoUtils.recycleNodes(mLastNode);
            mLastNode = AccessibilityNodeInfoCompat.obtain(node);
            return this;
        }

        public AccessibilityNodeInfoCompat getLastNode() {
            return mLastNode;
        }

        public void recycle() {
            AccessibilityNodeInfoUtils.recycleNodes(
                mFirstNode, mLastNode);
            mFirstNode = mLastNode = null;
            DisplaySpans.recycleSpans(mText);
            mText = null;
        }
    }

    private final BrailleTranslator mTranslator;
    private final Context mContext;
    // Not final, because it is initialized in the handler thread.
    private Display mDisplay;
    private final OnPanOverflowListener mPanOverflowListener;
    private final Display.OnConnectionStateChangeListener
            mConnectionStateChangeListener;
    private final OnMappedInputEventListener mMappedInputEventListener;
    private final DisplayHandler mDisplayHandler;
    private final CallbackHandler mCallbackHandler;
    private final HandlerThread mHandlerThread;
    private final PowerManager.WakeLock mWakeLock;

    // Read and written in display handler thread only.

    private boolean mConnected = false;
    /** Display content without overlays for cursors, focus etc. */
    private byte[] mBrailleContent = new byte[0];
    /**
     * Braille content, potentially with dots overlaid for cursors and focus.
     */
    private byte[] mOverlaidBrailleContent = mBrailleContent;
    private boolean mOverlaysOn;
    // Position in cells of the leftmost cell of the dipslay.
    private int mDisplayPosition;
    private Content mCurrentContent;

    /**
     * Creates an instance of this class and starts the internal thread to
     * connect to the braille display service.  {@code context} is used to
     * connect to the display service.  {@code translator} is used for braille
     * translation.  The various listeners will be called as appropriate and
     * on the same thread that was used to create this object.  The current
     * thread must have a prepared looper.
     */
    public DisplayManager(BrailleTranslator translator,
            Context context,
            OnPanOverflowListener panOverflowListener,
            Display.OnConnectionStateChangeListener connectionStateChangeListener,
            OnMappedInputEventListener mappedInputEventListener) {
        mTranslator = translator;
        mContext = context;
        mPanOverflowListener = panOverflowListener;
        mConnectionStateChangeListener = connectionStateChangeListener;
        mMappedInputEventListener = mappedInputEventListener;
        PowerManager pm = (PowerManager) context.getSystemService(
            Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
            "BrailleBack");
        mHandlerThread = new HandlerThread("DisplayManager") {
            @Override
            public void onLooperPrepared() {
                mDisplay = new Display(mContext, DisplayManager.this);
                mDisplay.setOnInputEventListener(DisplayManager.this);
            }
        };
        mHandlerThread.start();
        mDisplayHandler = new DisplayHandler(mHandlerThread.getLooper());
        mCallbackHandler = new CallbackHandler();
    }

    public void shutdown() {
        mDisplayHandler.stop();
    }

    /**
     * Asynchronously updates the display to reflect {@code content}.
     * {@code content} must not be modified after this function is called, and
     * will eventually be recycled by the display manager.
     */
    public void setContent(Content content) {
        mDisplayHandler.setContent(content);
    }

    private boolean markSelection(CharSequence chars) {
        if (!(chars instanceof Spanned)) {
            return false;
        }
        Spanned spanned = (Spanned) chars;
        DisplaySpans.SelectionSpan[] spans =
                spanned.getSpans(0, spanned.length(),
                        DisplaySpans.SelectionSpan.class);
        for (DisplaySpans.SelectionSpan span : spans) {
            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);
            if (start == end) {
                end = start + 1;
            }
            // TODO: For anything but computer braille, we need to
            // translate positions.
            // Special case: the selection (cursor) can extend to after
            // the last character.  Allow it to extend one cell
            // beyond.
            if (end > mBrailleContent.length) {
                extendContentForCursor();
            }
            copyOverlaidContent();
            for (int i = start;
                 i < end && i < mOverlaidBrailleContent.length;
                 ++i) {
                mOverlaidBrailleContent[i] |= SELECTION_DOTS;
            }
            panTo(start);
        }
        return spans.length > 0;
    }

    /**
     * Makes sure that the overlaid content has its own copy.  Call before
     * adding overlay dots.
     */
    private void copyOverlaidContent() {
        if (mOverlaidBrailleContent == mBrailleContent) {
            mOverlaidBrailleContent = mBrailleContent.clone();
        }
    }

    private void extendContentForCursor() {
        mBrailleContent = Arrays.copyOf(mBrailleContent,
                mBrailleContent.length + 1);
        // Always create a new copy of the overlaid content because there will
        // be a cursor, so we will need a copy anyway.
        mOverlaidBrailleContent = Arrays.copyOf(mOverlaidBrailleContent,
                mOverlaidBrailleContent.length + 1);
    }

    private void markFocus(CharSequence chars) {
        if (!(chars instanceof Spanned)) {
            return;
        }
        Spanned spanned = (Spanned) chars;
        DisplaySpans.FocusSpan[] spans =
                spanned.getSpans(0, spanned.length(),
                        DisplaySpans.FocusSpan.class);
        for (DisplaySpans.FocusSpan span : spans) {
            int start = spanned.getSpanStart(span);
            if (start < mOverlaidBrailleContent.length) {
                copyOverlaidContent();
                // TODO: When not using computer braille, map char position
                // to braille position.
                mOverlaidBrailleContent[start] |= FOCUS_DOTS;
                panTo(start);
            }
        }
    }

    private void panTo(int position) {
        if (mDisplayPosition <= position
                && position < mDisplayPosition + getNumTextCells()) {
            // Display overlaps desired position.
        } else {
            mDisplayPosition =
                    (position / getNumTextCells()) * getNumTextCells();
        }
    }

    @Override
    public void onConnectionStateChanged(int state) {
        if (state == Display.STATE_CONNECTED) {
            mConnected = true;
            refresh();
        } else {
            mConnected = false;
        }
        mCallbackHandler.onConnectionStateChanged(state);
    }

    @Override
    public void onInputEvent(BrailleInputEvent event) {
        keepAwake();
        LogUtils.log(this, Log.VERBOSE, "InputEvent: %s", event);
        // We're called from within the handler thread, so we forward
        // the call only if we are going to invoke the user's callback.
        switch (event.getCommand()) {
            case BrailleInputEvent.CMD_NAV_PAN_LEFT:
                panLeft();
                break;
            case BrailleInputEvent.CMD_NAV_PAN_RIGHT:
                panRight();
                break;
            default:
                sendMappedEvent(event);
                break;
        }
    }

    private void sendMappedEvent(BrailleInputEvent event) {
        if (BrailleInputEvent.argumentType(event.getCommand())
                == BrailleInputEvent.ARGUMENT_POSITION) {
            // Create a new event with the position ofset to the display
            // pan position.
            // TODO: when we support non-computer braille, map from cell to
            // character.
            int newArgument = event.getArgument() + mDisplayPosition;
            event = new BrailleInputEvent(event.getCommand(),
                    newArgument, event.getEventTime());
        }
        mCallbackHandler.onMappedInputEvent(event);
    }

    private void panLeft() {
        if (mDisplayPosition <= 0) {
            mCallbackHandler.onPanLeftOverflow();
            return;
        }
        mDisplayPosition = Math.max(
            0,
            mDisplayPosition - getNumTextCells());
        refresh();
    }

    private void panRight() {
        int newPosition = mDisplayPosition + getNumTextCells();
        if (newPosition >= mBrailleContent.length) {
            mCallbackHandler.onPanRightOverflow();
            return;
        }
        mDisplayPosition = newPosition;
        refresh();
    }

    private class DisplayHandler extends Handler {
        private static final int MSG_SET_CONTENT = 1;
        private static final int MSG_PULSE = 2;
        private static final int MSG_STOP = 3;

        public DisplayHandler(Looper looper) {
            super(looper);
        }

        public void setContent(Content content) {
            obtainMessage(MSG_SET_CONTENT, content).sendToTarget();
        }

        public void schedulePulse() {
            if (hasMessages(MSG_PULSE)) {
                return;
            }
            sendEmptyMessageDelayed(MSG_PULSE,
                    mOverlaysOn ? BLINK_ON_MILLIS : BLINK_OFF_MILLIS);
        }

        public void cancelPulse() {
            removeMessages(MSG_PULSE);
            mOverlaysOn = true;
        }

        public void stop() {
            sendEmptyMessage(MSG_STOP);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SET_CONTENT:
                    handleSetContent((Content) msg.obj);
                    break;
                case MSG_PULSE:
                    handlePulse();
                    break;
                case MSG_STOP:
                    handleStop();
                    break;
            }
        }

        private void handleSetContent(Content content) {
            if (mCurrentContent != null) {
                // Have the callback handler recycle the old content so that
                // the thread in which the callbck handler is running is the
                // only thread modifying it.  It is safe for the callback
                // thread to recycle the event when it receives this message
                // because the display handler thread will not send any more
                // input event containing this content and the events that
                // have already been sent will be processed by trhe callback
                // thread before the recycle message arrives because of the
                // guaranteed ordering of message handling.
                mCallbackHandler.recycleContent(mCurrentContent);
            }
            mCurrentContent = content;
            String textContent = content.mText.toString();
            mBrailleContent = mTranslator.translate(textContent);
            mOverlaidBrailleContent = mBrailleContent;
            // TODO: Try to be smarter about when to reset the pan position.
            mDisplayPosition = 0;
            cancelPulse();
            if (!markSelection(content.mText)) {
                markFocus(content.mText);
            }
            refresh();
        }

        private void handlePulse() {
            mOverlaysOn = !mOverlaysOn;
            refresh();
        }

        private void handleStop() {
            mDisplay.shutdown();
            mHandlerThread.quit();
        }
    }

    private class OnMappedInputEventArgs {
        public BrailleInputEvent mEvent;
        public Content mContent;

        public OnMappedInputEventArgs(BrailleInputEvent event,
                Content content) {
            mEvent = event;
            mContent = content;
        }
    }

    private class CallbackHandler extends Handler {
        private static final int MSG_ON_CONNECTION_STATE_CHANGED = 1;
        private static final int MSG_ON_MAPPED_INPUT_EVENT = 2;
        private static final int MSG_ON_PAN_LEFT_OVERFLOW = 3;
        private static final int MSG_ON_PAN_RIGHT_OVERFLOW = 4;
        private static final int MSG_RECYCLE_CONTENT = 5;

        public void onConnectionStateChanged(int state) {
            obtainMessage(MSG_ON_CONNECTION_STATE_CHANGED, state, 0)
                    .sendToTarget();
        }

        public void onMappedInputEvent(BrailleInputEvent event) {
            OnMappedInputEventArgs args = new OnMappedInputEventArgs(
                event, mCurrentContent);
            obtainMessage(MSG_ON_MAPPED_INPUT_EVENT, args).sendToTarget();
        }

        public void onPanLeftOverflow() {
            obtainMessage(MSG_ON_PAN_LEFT_OVERFLOW, mCurrentContent)
                    .sendToTarget();
        }

        public void onPanRightOverflow() {
            obtainMessage(MSG_ON_PAN_RIGHT_OVERFLOW, mCurrentContent)
                    .sendToTarget();
        }

        public void recycleContent(Content content) {
            obtainMessage(MSG_RECYCLE_CONTENT, content).sendToTarget();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ON_CONNECTION_STATE_CHANGED:
                    handleOnConnectionStateChanged(msg.arg1);
                    break;
                case MSG_ON_MAPPED_INPUT_EVENT:
                    OnMappedInputEventArgs args =
                            (OnMappedInputEventArgs) msg.obj;
                    handleOnMappedInputEvent(args.mEvent, args.mContent);
                    break;
                case MSG_ON_PAN_LEFT_OVERFLOW:
                    handleOnPanLeftOverflow((Content) msg.obj);
                    break;
                case MSG_ON_PAN_RIGHT_OVERFLOW:
                    handleOnPanRightOverflow((Content) msg.obj);
                    break;
                case MSG_RECYCLE_CONTENT:
                    handleRecycleContent((Content) msg.obj);
                    break;
            }
        }

        private void handleOnConnectionStateChanged(int state) {
            mConnectionStateChangeListener.onConnectionStateChanged(state);
        }

        private void handleOnMappedInputEvent(BrailleInputEvent event,
                                              Content content) {
            mMappedInputEventListener.onMappedInputEvent(event, content);
        }

        private void handleOnPanLeftOverflow(Content content) {
            mPanOverflowListener.onPanLeftOverflow(content);
        }

        private void handleOnPanRightOverflow(Content content) {
            mPanOverflowListener.onPanRightOverflow(content);
        }

        private void handleRecycleContent(Content content) {
            content.recycle();
        }
    }

    private void refresh() {
        if (!mConnected) {
            return;
        }
        byte[] content = mOverlaysOn
                ? mOverlaidBrailleContent
                : mBrailleContent;
        int length = Math.min(
            content.length - mDisplayPosition,
            getNumTextCells());
        byte[] toDisplay = Arrays.copyOfRange(content,
                mDisplayPosition, mDisplayPosition + length);
        mDisplay.displayDots(toDisplay);
        if (blinkNeeded()) {
            mDisplayHandler.schedulePulse();
        } else {
            mDisplayHandler.cancelPulse();
        }
    }

    /**
     * Returns {@code true} if the current display content is such that
     * it requires blinking.
     */
    private boolean blinkNeeded() {
        if (mBrailleContent == mOverlaidBrailleContent) {
            return false;
        }
        int max = Math.min(mDisplayPosition + getNumTextCells(),
                mBrailleContent.length);
        for (int i = mDisplayPosition; i < max; ++i) {
            if (mBrailleContent[i] != mOverlaidBrailleContent[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps the phone awake as if there was a 'user activity' registered
     * by the system.
     */
    private void keepAwake() {
        // Acquiring the lock and immediately releasing it keesp the phone
        // awake.  We don't use aqcuire() with a timeout because it just
        // adds an unnecessary context switch.
        mWakeLock.acquire();
        mWakeLock.release();
    }

    /**
     * Returns the size of the connected display, or {@code 1} if
     * no display is connected.
     */
    private int getNumTextCells() {
        if (!mConnected) {
            return 1;
        }
        return mDisplay.getDisplayProperties().getNumTextCells();
    }
}
