/*
 * Copyright (C) 2008 Google Inc.
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

package com.google.marvin.talkingdialer;

import android.content.Context;
import android.content.Intent;
import android.database.CursorIndexOutOfBoundsException;
import android.database.StaleDataException;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

import com.google.marvin.talkingdialer.ContactsManager.Contact;
import com.google.marvin.talkingdialer.ContactsManager.ContactData;
import com.google.marvin.talkingdialer.ShakeDetector.ShakeListener;

import com.googlecode.eyesfree.compat.view.MotionEventCompatUtils;

/**
 * Allows the user to select the contact they wish to call by moving through
 * their phonebook. The contact name being spoken is actually the contact's
 * ringtone. By setting the ringtone to an audio file that is the same as the
 * contact's name, the user gets a talking caller ID feature automatically
 * without needing any additional code.
 *
 * @author clchen@google.com (Charles L. Chen)
 */
public class ContactsView extends TextView {
    private static final long[] PATTERN = {
            0, 1, 40, 41 };

    private static final int GROUP_AE = 0;
    private static final int GROUP_IM = 1;
    private static final int GROUP_QU = 2;
    private static final int GROUP_Y = 4;
    private static final int GROUP_NONE = 5;

    private static final double LEFT = 0;
    private static final double UP_LEFT = Math.PI * .25;
    private static final double UP = Math.PI * .5;
    private static final double UP_RIGHT = Math.PI * .75;
    private static final double DOWN_RIGHT = -Math.PI * .75;
    private static final double DOWN = -Math.PI * .5;
    private static final double DOWN_LEFT = -Math.PI * .25;
    private static final double RIGHT = Math.PI;
    private static final double RIGHT_WRAP = -Math.PI;

    private final ContactsHandler contactsHandler = new ContactsHandler();

    private final TalkingDialer parent;
    private final ShakeDetector shakeDetector;
    private final Vibrator vibe;

    private double screenWidth;

    private double downX;
    private double downY;
    private double lastX;
    private double lastY;
    private double curX;
    private double curY;

    private int currentValue;
    private int currentWheel;

    private String currentCharacter;
    private String currentString;
    private Contact currentContact;
    private ContactData currentData;

    private boolean screenIsBeingTouched = false;
    private boolean trackballEnabled = true;
    private boolean confirmed;

    /** Maximum tolerance for double tap (before J release). */
    private long doubleTapWindow = 700;
    private long lastTapTime = 0;

    private boolean inVideoMode = false;

    public ContactsView(TalkingDialer context, final boolean inVideoMode) {
        super(context);

        parent = context;

        vibe = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        this.inVideoMode = inVideoMode;

        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();

        Display display = parent.getWindowManager().getDefaultDisplay();
        if (Build.VERSION.SDK_INT > 12) {
            Point size = new Point();
            display.getSize(size);
            screenWidth = size.x;
        } else {
            screenWidth = display.getWidth();
        }

        currentString = "";
        shakeDetector = new ShakeDetector(context, shakeListener);

        // Return if we have no contacts
        if (parent.contactManager.hasContacts()) {
            currentContact = parent.contactManager.reset();
            currentData = currentContact.nextData();
        }
    }

    public boolean isInVideoMode() {
        return inVideoMode;
    }

    public void shutdown() {
        shakeDetector.shutdown();
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        final int action = event.getAction();

        if (trackballEnabled == false) {
            return true;
        }

        if (event.getY() > .16) {
            trackballEnabled = false;
            contactsHandler.startTrackballTimeout();
            nextContact();
        }

        if (event.getY() < -.16) {
            trackballEnabled = false;
            contactsHandler.startTrackballTimeout();
            prevContact();
        }

        if (action == MotionEvent.ACTION_DOWN) {
            contactsHandler.startLongPressTimeout();
        } else if (action == MotionEvent.ACTION_UP) {
            contactsHandler.cancelLongPress();
        }

        return true;
    }

    public void nextContact() {
        currentString = "";

        currentData = currentContact.nextData();
        int pos = parent.contactManager.getPos();
        boolean found = true;
        while (currentData == null) {
            currentContact = parent.contactManager.getNextContact();
            currentData = currentContact.nextData();
            if (parent.contactManager.getPos() == pos) {
                found = false;
                break;
            }
        }

        vibe.vibrate(PATTERN, -1);
        if (!found) {
            parent.tts.speak(parent.getString(R.string.no_contacts_found), 0,
                    null);
        } else {
            speakCurrentContact(true);
        }
    }

    public void prevContact() {
        currentString = "";

        currentData = currentContact.prevData();
        int pos = parent.contactManager.getPos();
        boolean found = true;
        while (currentData == null) {
            currentContact = parent.contactManager.getPreviousContact();
            currentData = currentContact.prevData();
            if (parent.contactManager.getPos() == pos) {
                found = false;
                break;
            }
        }

        vibe.vibrate(PATTERN, -1);
        if (!found) {
            parent.tts.speak(parent.getString(R.string.no_contacts_found), 0,
                    null);
        } else {
            speakCurrentContact(true);
        }
    }

    private void jumpToFirstFilteredResult(String partialName) {

        int currentPos = parent.contactManager.getPos();

        partialName = partialName.toLowerCase();

        String name = "";
        boolean looped = false;
        try {
            name = currentContact.name;

            boolean isMatch = name.toLowerCase().startsWith(partialName);
            while (!isMatch) {
                name = parent.contactManager.getNextName();
                if (parent.contactManager.getPos() == currentPos) {
                    looped = true;
                    break;
                }
                isMatch = name != null && name.toLowerCase().startsWith(partialName);
                if (isMatch) {
                    currentContact = parent.contactManager.getCurrentContact();
                    isMatch = currentContact != null;
                    if (isMatch) {
                        currentData = currentContact.nextData();
                        isMatch = currentData != null;
                    }
                }
            }
        } catch (final CursorIndexOutOfBoundsException e) {
            e.printStackTrace();
        } catch (final StaleDataException e) {
            e.printStackTrace();
        }
        
        if (looped) {
            parent.tts.playEarcon(parent.getString(R.string.earcon_tock), 0, null);
            if (currentString.length() > 0) {
                currentString = currentString.substring(0, currentString.length() - 1);
            }
        } else {
            speakCurrentContact(true);
        }

    }

    /**
     * Speaks the currently selected contact and sets the internal current
     * contact.
     *
     * @param interrupt Set to {@code true} to flush queued speech and speak
     *            immediately.
     */
    private void speakCurrentContact(boolean interrupt) {
        final String contactName = currentContact.name;

        if (TextUtils.isEmpty(contactName)) {
            return;
        }

        final int mode = interrupt ? TextToSpeech.QUEUE_FLUSH
                : TextToSpeech.QUEUE_ADD;

        parent.tts.speak(contactName, mode, null);
        if (currentData == null) {
            return;
        }
        if (currentData.data != null && !TextUtils.isEmpty(currentData.data)) {
            parent.tts.speak(currentData.type, TextToSpeech.QUEUE_ADD, null);
        }

        if (parent.mIntentMode == TalkingDialer.DIAL && !currentData.isNumber) {
            parent.tts.speak(parent.getString(R.string.video_chat),
                    TextToSpeech.QUEUE_ADD, null);
        }

        invalidate();
    }

    public void dialActionHandler() {
        if (!confirmed) {
            if (!TextUtils.isEmpty(currentContact.name)) {
                if (parent.mIntentMode != TalkingDialer.DIAL) {
                    parent.tts.speak(parent.getString(
                            R.string.you_have_selected, currentContact.name),
                            0, null);
                } else if (!currentData.isNumber) {
                    parent.tts.speak(parent.getString(
                            R.string.you_are_about_to_video_call,
                            currentContact.name), 0, null);
                } else {
                    parent.tts.speak(parent
                            .getString(R.string.you_are_about_to_dial,
                                    currentContact.name), 0, null);
                }

                confirmed = true;
            }
        } else {
            parent.returnResults(currentContact, currentData);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final char keyLabel = event.getDisplayLabel();

        if (Character.isLetterOrDigit(keyLabel)) {
            currentString = currentString + keyLabel;
            jumpToFirstFilteredResult(currentString);
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                nextContact();
                currentString = "";
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                prevContact();
                currentString = "";
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_CALL:
                dialActionHandler();
                return true;
            case KeyEvent.KEYCODE_MENU:
                // Don't allow user to switch views when selecting video contact
                if (parent.mIntentMode != TalkingDialer.SELECT_EMAIL) {
                    parent.switchToDialingView();
                }
                return true;
            case KeyEvent.KEYCODE_DEL:
                backspace();
                return true;
        }

        confirmed = false;
        return false;
    }

    private void confirmEntry(int action) {
        screenIsBeingTouched = false;

        invalidate();

        final int prevVal = currentValue;

        currentValue = evalMotion(lastX, lastY);

        // Do some correction if the user lifts up on deadspace
        if (currentValue == -1) {
            currentValue = prevVal;
        }

        // The user never got a number that wasn't deadspace,
        // so assume 5.
        if (currentValue == -1) {
            currentValue = 5;
        }

        currentCharacter = getCharacter(currentWheel, currentValue);

        if (currentCharacter.equals("<-")) {
            currentContact = parent.contactManager.reset();
            currentData = currentContact.nextData();
            initiateMotion(lastX, lastY);
            backspace();
            return;
        } else if (currentCharacter.equals("TAP")) {

            double deltaX = curX - downX;
            double deltaY = curY - downY;
            boolean touchExploring = parent.isTouchExplorationEnabled();
            if (touchExploring) {
                if (deltaX == 0 && deltaY == 0
                        && action == MotionEvent.ACTION_UP) {
                    dialActionHandler();
                }
            } else {
                if (lastTapTime + doubleTapWindow > System.currentTimeMillis()) {
                    dialActionHandler();
                } else {
                    lastTapTime = System.currentTimeMillis();
                }
            }
            return;

        } else {
            currentString = currentString + currentCharacter;
        }

        parent.tts.speak(currentCharacter, 0, null);

        initiateMotion(lastX, lastY);

        currentString = currentString + currentCharacter;
        jumpToFirstFilteredResult(currentString);
    }

    private void initiateMotion(double x, double y) {
        downX = x;
        downY = y;
        lastX = x;
        lastY = y;
        currentValue = -1;
        currentWheel = GROUP_NONE;
        currentCharacter = "";
    }

    private boolean inScrollZone(double x) {
        return x > (screenWidth - 100) || x < 100;
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return onTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        final int action = event.getAction();
        final float x = event.getX();
        final float y = event.getY();

        boolean touchExploring = parent.isTouchExplorationEnabled();

        if ((!touchExploring && action == MotionEvent.ACTION_UP)
                || (touchExploring && action == MotionEventCompatUtils.ACTION_HOVER_EXIT)) {

            invalidate();

            if (inScrollZone(downX)) {
                if (inScrollZone(x)) {
                    if (downY - y < -100) {
                        nextContact();
                    } else if (downY - y > 100) {
                        prevContact();
                    } else {
                        // TAP : navigate up or down depending on side
                        if (x < 100) {
                            prevContact();
                        } else {
                            nextContact();
                        }
                    }
                }
                contactsHandler.cancelLongPress();
                screenIsBeingTouched = false;
                return true;
            }
        }

        if ((action == MotionEvent.ACTION_DOWN)
                || (action == MotionEventCompatUtils.ACTION_HOVER_ENTER)) {
            initiateMotion(x, y);
            contactsHandler.startLongPressTimeout();
            return true;
        } else if ((action == MotionEvent.ACTION_UP)
                || (action == MotionEventCompatUtils.ACTION_HOVER_EXIT)) {

            curX = x;
            curY = y;

            confirmEntry(action);

            contactsHandler.cancelLongPress();
            return true;
        } else {

            // ignore motion events if side scrolling
            if (x > (screenWidth - 100) || x < 100) {
                return true;
            }

            screenIsBeingTouched = true;
            lastX = x;
            lastY = y;

            final int prevVal = currentValue;

            currentValue = evalMotion(x, y);

            // Do nothing since we want a deadzone here;
            // restore the state to the previous value.
            if (currentValue == -1) {
                currentValue = prevVal;
                return true;
            }

            // There is a wheel that is active
            if (currentValue != 5) {
                if (currentWheel == GROUP_NONE) {
                    currentWheel = getWheel(currentValue);
                    // User has entered a wheel so invalidate the long press
                    // callback.
                    contactsHandler.cancelLongPress();
                }

                currentCharacter = getCharacter(currentWheel, currentValue);
            } else {
                currentCharacter = "";
            }

            invalidate();

            if (prevVal != currentValue) {
                if (currentCharacter.equals("")) {
                    parent.tts.playEarcon(
                            parent.getString(R.string.earcon_tock), 0, null);
                } else {
                    if (currentCharacter.equals(".")) {
                        parent.tts.speak(parent.getString(R.string.period), 0,
                                null);
                    } else if (currentCharacter.equals("!")) {
                        parent.tts.speak(
                                parent.getString(R.string.exclamation_point),
                                0, null);
                    } else if (currentCharacter.equals("?")) {
                        parent.tts.speak(
                                parent.getString(R.string.question_mark), 0,
                                null);
                    } else if (currentCharacter.equals(",")) {
                        parent.tts.speak(parent.getString(R.string.comma), 0,
                                null);
                    } else if (currentCharacter.equals("<-")) {
                        parent.tts.speak(parent.getString(R.string.backspace),
                                0, null);
                    } else {
                        parent.tts.speak(currentCharacter, 0, null);
                    }
                }
            }

            vibe.vibrate(PATTERN, -1);
        }

        return true;
    }

    private int getWheel(int value) {
        switch (value) {
            case 1:
                return GROUP_AE;
            case 2:
                return GROUP_IM;
            case 3:
                return GROUP_QU;
            case 4:
                return GROUP_Y;
            case 5:
                return GROUP_NONE;
            case 6:
                return GROUP_Y;
            case 7:
                return GROUP_QU;
            case 8:
                return GROUP_IM;
            case 9:
                return GROUP_AE;
            default:
                return GROUP_NONE;
        }
    }

    private String getCharacter(int wheel, int value) {
        switch (wheel) {
            case GROUP_AE:
                switch (value) {
                    case 1:
                        return "A";
                    case 2:
                        return "B";
                    case 3:
                        return "C";
                    case 4:
                        return "H";
                    case 5:
                        return "";
                    case 6:
                        return "D";
                    case 7:
                        return "G";
                    case 8:
                        return "F";
                    case 9:
                        return "E";
                    default:
                        return "";
                }
            case GROUP_IM:
                switch (value) {
                    case 1:
                        return "P";
                    case 2:
                        return "I";
                    case 3:
                        return "J";
                    case 4:
                        return "O";
                    case 5:
                        return "";
                    case 6:
                        return "K";
                    case 7:
                        return "N";
                    case 8:
                        return "M";
                    case 9:
                        return "L";
                    default:
                        return "";
                }
            case GROUP_QU:
                switch (value) {
                    case 1:
                        return "W";
                    case 2:
                        return "X";
                    case 3:
                        return "Q";
                    case 4:
                        return "V";
                    case 5:
                        return "";
                    case 6:
                        return "R";
                    case 7:
                        return "U";
                    case 8:
                        return "T";
                    case 9:
                        return "S";
                    default:
                        return "";
                }
            case GROUP_Y:
                switch (value) {
                    case 1:
                        return ",";
                    case 2:
                        return "!";
                    case 3:
                        return "";
                    case 4:
                        return "<-";
                    case 5:
                        return "";
                    case 6:
                        return "Y";
                    case 7:
                        return ".";
                    case 8:
                        return "?";
                    case 9:
                        return "Z";
                    default:
                        return "";
                }
            default:
                return "TAP";
        }
    }

    private int evalMotion(double x, double y) {
        /*
         * Pie menu slices have deadspace in between to avoid quick jumps
         * between menu items
         */
        final double thetaTolerance = (Math.PI / 16);
        final float rTolerance = 25;

        final double r = Math.sqrt(((downX - x) * (downX - x))
                + ((downY - y) * (downY - y)));

        if (r < rTolerance) {
            return 5;
        }

        final double theta = Math.atan2(downY - y, downX - x);

        if (Math.abs(theta - LEFT) < thetaTolerance) {
            return 4;
        } else if (Math.abs(theta - UP_LEFT) < thetaTolerance) {
            return 1;
        } else if (Math.abs(theta - UP) < thetaTolerance) {
            return 2;
        } else if (Math.abs(theta - UP_RIGHT) < thetaTolerance) {
            return 3;
        } else if (Math.abs(theta - DOWN_RIGHT) < thetaTolerance) {
            return 9;
        } else if (Math.abs(theta - DOWN) < thetaTolerance) {
            return 8;
        } else if (Math.abs(theta - DOWN_LEFT) < thetaTolerance) {
            return 7;
        } else if ((theta > (RIGHT - thetaTolerance))
                || (theta < (RIGHT_WRAP + thetaTolerance))) {
            return 6;
        }

        // Off by more than the threshold, so it doesn't count
        return -1;
    }

    @Override
    public void onDraw(Canvas canvas) {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        int x = 5;
        int y = 50;

        paint.setTextSize(50);
        paint.setTextAlign(Paint.Align.LEFT);
        y -= paint.ascent() / 2;

        canvas.drawText(currentString, x, y, paint);

        if (currentContact != null && currentContact.name.length() > 0) {
            y = 140;
            paint.setTextSize(45);

            final String[] lines = currentContact.name.split(" ");

            int i;
            for (i = 0; i < lines.length; i++) {
                canvas.drawText(lines[i], x, y + (i * 45), paint);
            }

            if (currentData != null && currentData.type != null
                    && currentData.type.length() > 0) {
                canvas.drawText(currentData.type, x, y + (i * 45), paint);
            }
        }

        paint.setTextSize(50);

        if (!screenIsBeingTouched) {
            x = 5;
            y = getHeight() - 260;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText("Press MENU for dialing mode.", x, y, paint);

            x = 5;
            y = getHeight() - 240;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText("Scroll contacts with trackball.", x, y, paint);

            x = 5;
            y = getHeight() - 220;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText("Press CALL twice to confirm.", x, y, paint);
        } else {
            final int offset = 90;

            final int x1 = (int) downX - offset;
            int y1 = (int) downY - offset;
            final int x2 = (int) downX;
            int y2 = (int) downY - offset;
            final int x3 = (int) downX + offset;
            int y3 = (int) downY - offset;
            final int x4 = (int) downX - offset;
            int y4 = (int) downY;
            final int x6 = (int) downX + offset;
            int y6 = (int) downY;
            final int x7 = (int) downX - offset;
            int y7 = (int) downY + offset;
            final int x8 = (int) downX;
            int y8 = (int) downY + offset;
            final int x9 = (int) downX + offset;
            int y9 = (int) downY + offset;

            y1 -= paint.ascent() / 2;
            y2 -= paint.ascent() / 2;
            y3 -= paint.ascent() / 2;
            y4 -= paint.ascent() / 2;
            y6 -= paint.ascent() / 2;
            y7 -= paint.ascent() / 2;
            y8 -= paint.ascent() / 2;
            y9 -= paint.ascent() / 2;

            switch (currentWheel) {
                case GROUP_AE:
                    paint.setColor(Color.RED);
                    drawCharacter("A", x1, y1, canvas, paint,
                            currentCharacter.equals("A"));
                    drawCharacter("B", x2, y2, canvas, paint,
                            currentCharacter.equals("B"));
                    drawCharacter("C", x3, y3, canvas, paint,
                            currentCharacter.equals("C"));
                    drawCharacter("H", x4, y4, canvas, paint,
                            currentCharacter.equals("H"));
                    drawCharacter("D", x6, y6, canvas, paint,
                            currentCharacter.equals("D"));
                    drawCharacter("G", x7, y7, canvas, paint,
                            currentCharacter.equals("G"));
                    drawCharacter("F", x8, y8, canvas, paint,
                            currentCharacter.equals("F"));
                    drawCharacter("E", x9, y9, canvas, paint,
                            currentCharacter.equals("E"));
                    break;
                case GROUP_IM:
                    paint.setColor(Color.BLUE);
                    drawCharacter("P", x1, y1, canvas, paint,
                            currentCharacter.equals("P"));
                    drawCharacter("I", x2, y2, canvas, paint,
                            currentCharacter.equals("I"));
                    drawCharacter("J", x3, y3, canvas, paint,
                            currentCharacter.equals("J"));
                    drawCharacter("O", x4, y4, canvas, paint,
                            currentCharacter.equals("O"));
                    drawCharacter("K", x6, y6, canvas, paint,
                            currentCharacter.equals("K"));
                    drawCharacter("N", x7, y7, canvas, paint,
                            currentCharacter.equals("N"));
                    drawCharacter("M", x8, y8, canvas, paint,
                            currentCharacter.equals("M"));
                    drawCharacter("L", x9, y9, canvas, paint,
                            currentCharacter.equals("L"));
                    break;
                case GROUP_QU:
                    paint.setColor(Color.GREEN);
                    drawCharacter("W", x1, y1, canvas, paint,
                            currentCharacter.equals("W"));
                    drawCharacter("X", x2, y2, canvas, paint,
                            currentCharacter.equals("X"));
                    drawCharacter("Q", x3, y3, canvas, paint,
                            currentCharacter.equals("Q"));
                    drawCharacter("V", x4, y4, canvas, paint,
                            currentCharacter.equals("V"));
                    drawCharacter("R", x6, y6, canvas, paint,
                            currentCharacter.equals("R"));
                    drawCharacter("U", x7, y7, canvas, paint,
                            currentCharacter.equals("U"));
                    drawCharacter("T", x8, y8, canvas, paint,
                            currentCharacter.equals("T"));
                    drawCharacter("S", x9, y9, canvas, paint,
                            currentCharacter.equals("S"));
                    break;
                case GROUP_Y:
                    paint.setColor(Color.YELLOW);
                    drawCharacter(",", x1, y1, canvas, paint,
                            currentCharacter.equals(","));
                    drawCharacter("!", x2, y2, canvas, paint,
                            currentCharacter.equals("!"));
                    drawCharacter("<-", x4, y4, canvas, paint,
                            currentCharacter.equals("<-"));
                    drawCharacter("Y", x6, y6, canvas, paint,
                            currentCharacter.equals("Y"));
                    drawCharacter(".", x7, y7, canvas, paint,
                            currentCharacter.equals("."));
                    drawCharacter("?", x8, y8, canvas, paint,
                            currentCharacter.equals("?"));
                    drawCharacter("Z", x9, y9, canvas, paint,
                            currentCharacter.equals("Z"));
                    break;
                default:
                    paint.setColor(Color.RED);
                    canvas.drawText("A", x1, y1, paint);
                    canvas.drawText("E", x9, y9, paint);
                    paint.setColor(Color.BLUE);
                    canvas.drawText("I", x2, y2, paint);
                    canvas.drawText("M", x8, y8, paint);
                    paint.setColor(Color.GREEN);
                    canvas.drawText("Q", x3, y3, paint);
                    canvas.drawText("U", x7, y7, paint);
                    paint.setColor(Color.YELLOW);
                    canvas.drawText("Y", x6, y6, paint);
                    canvas.drawText("<-", x4, y4, paint);
                    break;
            }
        }
    }

    private void drawCharacter(String character, int x, int y, Canvas canvas,
            Paint paint, boolean isSelected) {
        final int regSize = 50;
        final int selectedSize = regSize * 2;

        if (isSelected) {
            paint.setTextSize(selectedSize);
        } else {
            paint.setTextSize(regSize);
        }

        canvas.drawText(character, x, y, paint);
    }

    private void backspace() {
        confirmed = false;

        if (currentString.length() > 0) {
            currentString = currentString.substring(0,
                    currentString.length() - 1);
            if (currentString.length() > 0) {
                jumpToFirstFilteredResult(currentString);
            }
        } else {
            parent.tts.playEarcon(parent.getString(R.string.earcon_tock), 0,
                    null);
            parent.tts.playEarcon(parent.getString(R.string.earcon_tock), 1,
                    null);
        }

        invalidate();
    }

    private void displayContactDetails() {
        if (currentContact != null) {
            final String text = parent.getString(R.string.load_detail,
                    currentContact.name);

            parent.tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);

            final String uri = parent.getString(R.string.people_uri,
                    currentData.id);
            final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));

            parent.startActivity(intent);
        }
    }

    private final ShakeListener shakeListener = new ShakeListener() {
            @Override
        public void onShakeDetected() {
            backspace();
        }
    };

    private class ContactsHandler extends Handler {
        private static final int LONG_PRESS_TIMEOUT = 1;
        private static final int TRACKBALL_TIMEOUT = 2;

        private static final int LONG_PRESS_DELAY = 2000;
        private static final int TRACKBALL_DELAY = 500;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LONG_PRESS_TIMEOUT:
                    onLongPressTimeout();
                    break;
                case TRACKBALL_TIMEOUT:
                    onTrackballTimeout();
                    break;
            }
        }

        public void startLongPressTimeout() {
            removeMessages(LONG_PRESS_TIMEOUT);
            sendEmptyMessageDelayed(LONG_PRESS_TIMEOUT, LONG_PRESS_DELAY);
        }

        public void cancelLongPress() {
            removeMessages(LONG_PRESS_TIMEOUT);
        }

        public void startTrackballTimeout() {
            removeMessages(TRACKBALL_TIMEOUT);
            sendEmptyMessageDelayed(TRACKBALL_TIMEOUT, TRACKBALL_DELAY);
        }

        private void onLongPressTimeout() {
            invalidate();
            displayContactDetails();
        }

        private void onTrackballTimeout() {
            trackballEnabled = true;
        }
    }
}
