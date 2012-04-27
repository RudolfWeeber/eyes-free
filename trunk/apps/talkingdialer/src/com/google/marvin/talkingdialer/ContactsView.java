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
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.StaleDataException;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

import com.google.marvin.talkingdialer.ShakeDetector.ShakeListener;
import com.googlecode.eyesfree.compat.view.MotionEventCompatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

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

    private static final int COLUMN_NAME = 0;
    private static final int COLUMN_NUMBER = 1;
    private static final int COLUMN_TYPE = 2;
    private static final int COLUMN_PERSON_ID = 3;

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

    // An array specifying which columns to return.
    private static final String[] PROJECTION = new String[] {
            Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.RAW_CONTACT_ID };

    private final ContactsHandler contactsHandler = new ContactsHandler();
    private final ArrayList<String> contacts = new ArrayList<String>();

    private final SlideDial parent;
    private final Cursor managedCursor;
    private final ShakeDetector shakeDetector;
    private final Vibrator vibe;

    private ListIterator<String> contactsIter;

    private double downX;
    private double downY;
    private double lastX;
    private double lastY;

    private float p2DownX;
    private float p2DownY;

    private int currentValue;
    private int currentWheel;

    private String currentCharacter;
    private String currentString;
    private String currentContact;

    private boolean screenIsBeingTouched = false;
    private boolean trackballEnabled = true;
    private boolean inDPadMode = false;
    private boolean confirmed;

    public ContactsView(SlideDial context) {
        super(context);

        parent = context;

        vibe = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        // Best way to retrieve a query; returns a managed query.
        managedCursor = parent.managedQuery(Phone.CONTENT_URI, PROJECTION,
                null, // WHERE clause--we won't specify.
                null, // no selection args
                Phone.DISPLAY_NAME + " ASC"); // Order-by clause.
        managedCursor.moveToFirst();

        do {
            final String name = managedCursor.getString(COLUMN_NAME);

            contacts.add(name);
        } while (managedCursor.moveToNext());

        contactsIter = getFilteredIterator(contacts, "");
        currentString = "";
        currentContact = "";

        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();

        shakeDetector = new ShakeDetector(context, shakeListener);
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

    private void resetContactList() {
        currentString = "";

        if (managedCursor.getCount() > 0) {
            managedCursor.moveToFirst();

            if (!managedCursor.isBeforeFirst() && !managedCursor.isAfterLast()) {
                // Keep going if the entry doesn't have a name
                final String name = managedCursor.getString(COLUMN_NAME);

                if (name == null) {
                    nextContact();
                    return;
                }
            }
        }
    }

    public void nextContact() {
        currentString = "";

        // Make sure we don't try to act on an empty table
        if (managedCursor.getCount() > 0) {
            final boolean moveSucceeded = managedCursor.moveToNext();

            if (!moveSucceeded) {
                managedCursor.moveToFirst();
            }

            // Keep going if the entry doesn't have a name
            final String name = managedCursor.getString(COLUMN_NAME);

            if (name == null) {
                nextContact();
                return;
            }

            vibe.vibrate(PATTERN, -1);
            speakCurrentContact(true);
        }
    }

    public void prevContact() {
        currentString = "";

        // Make sure we don't try to act on an empty table
        if (managedCursor.getCount() > 0) {
            final boolean moveSucceeded = managedCursor.moveToPrevious();

            if (!moveSucceeded) {
                managedCursor.moveToLast();
            }

            // Keep going if the entry doesn't have a name
            final String name = managedCursor.getString(COLUMN_NAME);

            if (name == null) {
                prevContact();
                return;
            }

            vibe.vibrate(PATTERN, -1);
            speakCurrentContact(true);
        }
    }

    private void jumpToFirstFilteredResult() {
        final int entryIndex = contactsIter.nextIndex();
        final String entry = contactsIter.next();

        if (entry == null) {
            parent.tts.playEarcon(parent.getString(R.string.earcon_tock), 0, null);

            if (currentString.length() > 0) {
                currentString = currentString.substring(0, currentString.length() - 1);

                if (currentString.length() > 0) {
                    contactsIter = getFilteredIterator(contacts, currentString);
                    jumpToFirstFilteredResult();
                } else {
                    parent.tts.speak(parent.getString(R.string.no_contacts_found), 0, null);
                }
            }

            return;
        }

        managedCursor.moveToPosition(entryIndex);
        speakCurrentContact(true);
    }

    /**
     * @return a string representing the currently selected contact
     */
    private String getCurrentContact() {
        final StringBuilder contact = new StringBuilder();
        String name = null;

        try {
            name = managedCursor.getString(COLUMN_NAME);
        } catch (final CursorIndexOutOfBoundsException e) {
            e.printStackTrace();
        } catch (final StaleDataException e) {
            e.printStackTrace();
        }

        if (TextUtils.isEmpty(name)) {
            return null;
        } else {
            contact.append(name);
        }

        final int phoneType = Integer.parseInt(managedCursor.getString(COLUMN_TYPE));
        int typeRes = -1;

        switch (phoneType) {
            case Phone.TYPE_HOME:
                typeRes = R.string.home;
                break;
            case Phone.TYPE_MOBILE:
                typeRes = R.string.cell;
                break;
            case Phone.TYPE_WORK:
                typeRes = R.string.work;
                break;
        }

        if (typeRes >= 0) {
            contact.append(' ');
            contact.append(getContext().getString(typeRes));
        }

        return contact.toString();
    }

    /**
     * Speaks the currently selected contact and sets the internal current
     * contact.
     *
     * @param interrupt Set to {@code true} to flush queued speech and speak
     *            immediately.
     */
    private void speakCurrentContact(boolean interrupt) {
        final String contact = getCurrentContact();

        if (TextUtils.isEmpty(contact)) {
            return;
        }

        final int mode = interrupt ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;

        parent.tts.speak(contact, mode, null);

        currentContact = contact;

        invalidate();
    }

    public void dialActionHandler() {
        if (!confirmed) {
            if (!TextUtils.isEmpty(currentContact)) {
                if (parent.contactsPickerMode) {
                    parent.tts.speak(
                            parent.getString(R.string.you_have_selected, currentContact), 0, null);
                } else {
                    parent.tts.speak(
                            parent.getString(R.string.you_are_about_to_dial, currentContact), 0,
                            null);
                }

                confirmed = true;
            } else {
                // If the user attempts to dial with no contact selected, switch
                // to dialing view.
                parent.switchToDialingView();
            }
        } else {
            parent.returnResults(managedCursor.getString(COLUMN_NUMBER), currentContact);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final char keyLabel = event.getDisplayLabel();

        if (Character.isLetterOrDigit(keyLabel)) {
            currentString = currentString + keyLabel;
            contactsIter = getFilteredIterator(contacts, currentString);
            jumpToFirstFilteredResult();
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
                parent.switchToDialingView();
                return true;
            case KeyEvent.KEYCODE_DEL:
                backspace();
                return true;
        }

        confirmed = false;
        return false;
    }

    private void confirmEntry() {
        screenIsBeingTouched = false;

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
            currentContact = "";
            initiateMotion(lastX, lastY);
            backspace();
            return;
        } else {
            currentString = currentString + currentCharacter;
        }

        parent.tts.speak(currentCharacter, 0, null);
        currentContact = "";

        invalidate();
        initiateMotion(lastX, lastY);

        currentString = currentString + currentCharacter;
        contactsIter = getFilteredIterator(contacts, currentString);
        jumpToFirstFilteredResult();
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

    public boolean onHoverEvent(MotionEvent event) {
        return onTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        final float x = event.getX();
        final float y = event.getY();

        // Treat the screen as a dpad
        if (action == MotionEvent.ACTION_POINTER_2_DOWN) {
            contactsHandler.cancelLongPress();
            inDPadMode = true;
            screenIsBeingTouched = false;
            p2DownX = event.getX(1);
            p2DownY = event.getY(1);
            vibe.vibrate(PATTERN, -1);

            invalidate();
        } else if (action == MotionEvent.ACTION_POINTER_2_UP) {
            final float p2DeltaX = event.getX(1) - p2DownX;
            final float p2DeltaY = event.getY(1) - p2DownY;

            if (Math.abs(p2DeltaX) > Math.abs(p2DeltaY)) {
                if (p2DeltaX < -200) {
                    backspace();
                }
            } else {
                if (p2DeltaY < -100) {
                    prevContact();
                } else if (p2DeltaY > 100) {
                    nextContact();
                }
            }
        }

        if ((action == MotionEvent.ACTION_UP)
                || (action == MotionEventCompatUtils.ACTION_HOVER_EXIT)) {
            if (x > 650) {
                nextContact();
                return true;
            }
            if (x < 100) {
                prevContact();
                return true;
            }
        } else {
            if (x > 650) {
                return true;
            }
            if (x < 100) {
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
            if (inDPadMode == false) {
                confirmEntry();
            } else {
                inDPadMode = false;
            }

            contactsHandler.cancelLongPress();
            return true;
        } else {
            if (!inDPadMode) {
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
                        parent.tts.playEarcon(parent.getString(R.string.earcon_tock), 0, null);
                    } else {
                        if (currentCharacter.equals(".")) {
                            parent.tts.speak(parent.getString(R.string.period), 0, null);
                        } else if (currentCharacter.equals("!")) {
                            parent.tts.speak(parent.getString(R.string.exclamation_point), 0, null);
                        } else if (currentCharacter.equals("?")) {
                            parent.tts.speak(parent.getString(R.string.question_mark), 0, null);
                        } else if (currentCharacter.equals(",")) {
                            parent.tts.speak(parent.getString(R.string.comma), 0, null);
                        } else if (currentCharacter.equals("<-")) {
                            parent.tts.speak(parent.getString(R.string.backspace), 0, null);
                        } else {
                            parent.tts.speak(currentCharacter, 0, null);
                        }
                    }
                }

                vibe.vibrate(PATTERN, -1);
            }
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
                        return ""; // return "MODE";
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
                return "";
        }
    }

    private int evalMotion(double x, double y) {
        final float rTolerance = 25;
        final double thetaTolerance = (Math.PI / 16);

        final double r = Math.sqrt(((downX - x) * (downX - x)) + ((downY - y) * (downY - y)));

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
        } else if ((theta > (RIGHT - thetaTolerance)) || (theta < (RIGHT_WRAP + thetaTolerance))) {
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

        if (currentContact.length() > 0) {
            y = 140;
            paint.setTextSize(45);

            final String[] lines = currentContact.split(" ");

            for (int i = 0; i < lines.length; i++) {
                canvas.drawText(lines[i], x, y + (i * 45), paint);
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
                    drawCharacter("A", x1, y1, canvas, paint, currentCharacter.equals("A"));
                    drawCharacter("B", x2, y2, canvas, paint, currentCharacter.equals("B"));
                    drawCharacter("C", x3, y3, canvas, paint, currentCharacter.equals("C"));
                    drawCharacter("H", x4, y4, canvas, paint, currentCharacter.equals("H"));
                    drawCharacter("D", x6, y6, canvas, paint, currentCharacter.equals("D"));
                    drawCharacter("G", x7, y7, canvas, paint, currentCharacter.equals("G"));
                    drawCharacter("F", x8, y8, canvas, paint, currentCharacter.equals("F"));
                    drawCharacter("E", x9, y9, canvas, paint, currentCharacter.equals("E"));
                    break;
                case GROUP_IM:
                    paint.setColor(Color.BLUE);
                    drawCharacter("P", x1, y1, canvas, paint, currentCharacter.equals("P"));
                    drawCharacter("I", x2, y2, canvas, paint, currentCharacter.equals("I"));
                    drawCharacter("J", x3, y3, canvas, paint, currentCharacter.equals("J"));
                    drawCharacter("O", x4, y4, canvas, paint, currentCharacter.equals("O"));
                    drawCharacter("K", x6, y6, canvas, paint, currentCharacter.equals("K"));
                    drawCharacter("N", x7, y7, canvas, paint, currentCharacter.equals("N"));
                    drawCharacter("M", x8, y8, canvas, paint, currentCharacter.equals("M"));
                    drawCharacter("L", x9, y9, canvas, paint, currentCharacter.equals("L"));
                    break;
                case GROUP_QU:
                    paint.setColor(Color.GREEN);
                    drawCharacter("W", x1, y1, canvas, paint, currentCharacter.equals("W"));
                    drawCharacter("X", x2, y2, canvas, paint, currentCharacter.equals("X"));
                    drawCharacter("Q", x3, y3, canvas, paint, currentCharacter.equals("Q"));
                    drawCharacter("V", x4, y4, canvas, paint, currentCharacter.equals("V"));
                    drawCharacter("R", x6, y6, canvas, paint, currentCharacter.equals("R"));
                    drawCharacter("U", x7, y7, canvas, paint, currentCharacter.equals("U"));
                    drawCharacter("T", x8, y8, canvas, paint, currentCharacter.equals("T"));
                    drawCharacter("S", x9, y9, canvas, paint, currentCharacter.equals("S"));
                    break;
                case GROUP_Y:
                    paint.setColor(Color.YELLOW);
                    drawCharacter(",", x1, y1, canvas, paint, currentCharacter.equals(","));
                    drawCharacter("!", x2, y2, canvas, paint, currentCharacter.equals("!"));
                    // drawCharacter("MODE", x3, y3, canvas, paint,
                    // currentCharacter.equals("MODE"));
                    drawCharacter("<-", x4, y4, canvas, paint, currentCharacter.equals("<-"));
                    drawCharacter("Y", x6, y6, canvas, paint, currentCharacter.equals("Y"));
                    drawCharacter(".", x7, y7, canvas, paint, currentCharacter.equals("."));
                    drawCharacter("?", x8, y8, canvas, paint, currentCharacter.equals("?"));
                    drawCharacter("Z", x9, y9, canvas, paint, currentCharacter.equals("Z"));
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

    private void drawCharacter(
            String character, int x, int y, Canvas canvas, Paint paint, boolean isSelected) {
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
            currentString = currentString.substring(0, currentString.length() - 1);
            if (currentString.length() > 0) {
                contactsIter = getFilteredIterator(contacts, currentString);
                jumpToFirstFilteredResult();
            } else {
                resetContactList();
            }
        } else {
            parent.tts.playEarcon(parent.getString(R.string.earcon_tock), 0, null);
            parent.tts.playEarcon(parent.getString(R.string.earcon_tock), 1, null);
        }

        invalidate();
    }

    private void displayContactDetails() {
        if (!managedCursor.isAfterLast()) {
            final String text = parent.getString(
                    R.string.load_detail, managedCursor.getString(COLUMN_NAME));

            parent.tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);

            final String uri = parent.getString(
                    R.string.people_uri, managedCursor.getString(COLUMN_PERSON_ID));
            final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));

            parent.startActivity(intent);
        }
    }

    /**
     * Returns a {@link ListIterator} for a filtered subset of the provided list
     * of contacts.
     *
     * @param fullList The list of contacts.
     * @param partialName The prefix to search for.
     * @return A list iterator for the filtered list of contacts.
     */
    private static ListIterator<String> getFilteredIterator(
            List<String> fullList, String partialName) {
        final ArrayList<String> filteredList = new ArrayList<String>(fullList.size());

        if (!TextUtils.isEmpty(partialName)) {
            partialName = partialName.toLowerCase();

            for (String entry : fullList) {
                if (entry == null) {
                    continue;
                }

                final String name = entry.toLowerCase();

                if (name.startsWith(partialName)) {
                    filteredList.add(entry);
                }
            }
        }

        return filteredList.listIterator();
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
