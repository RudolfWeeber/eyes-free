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

import com.google.marvin.talkingdialer.ShakeDetector.ShakeListener;
import com.google.tts.TTSEarcon;
import com.google.tts.TTSParams;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Vibrator;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

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
    // BEGIN OF WORKAROUND FOR DONUT COMPATIBILITY
    private static Method MotionEvent_getX;
    private static Method MotionEvent_getY;
    static {
        initCompatibility();
    }
    private static void initCompatibility() {
        try {
            MotionEvent_getX = MotionEvent.class.getMethod(
                    "getX", new Class[] { Integer.TYPE } );
            MotionEvent_getY = MotionEvent.class.getMethod(
                    "getY", new Class[] { Integer.TYPE } );
            /* success, this is a newer device */
        } catch (NoSuchMethodException nsme) {
            /* failure, must be older device */
        }
    }
    private static float getX(MotionEvent event, int x) {
        try {
            Object retobj = MotionEvent_getX.invoke(event, x);
            return (Float) retobj;
        }  catch (IllegalAccessException ie) {
            System.err.println("unexpected " + ie);
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return -1;
    }
    private static float getY(MotionEvent event, int y) {
        try {
            Object retobj = MotionEvent_getY.invoke(event, y);
            return (Float) retobj;
        }  catch (IllegalAccessException ie) {
            System.err.println("unexpected " + ie);
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return -1;
    }    
    // END OF WORKAROUND FOR DONUT COMPATIBILITY
    
    
    private static final long[] PATTERN = {
            0, 1, 40, 41
    };

    private static final int NAME = 0;

    private static final int NUMBER = 1;

    private static final int TYPE = 2;

    private static final int AE = 0;

    private static final int IM = 1;

    private static final int QU = 2;

    private static final int Y = 4;

    private static final int NONE = 5;

    private final double left = 0;

    private final double upleft = Math.PI * .25;

    private final double up = Math.PI * .5;

    private final double upright = Math.PI * .75;

    private final double downright = -Math.PI * .75;

    private final double down = -Math.PI * .5;

    private final double downleft = -Math.PI * .25;

    private final double right = Math.PI;

    private final double rightWrap = -Math.PI;

    // An array specifying which columns to return.
    private static final String[] PROJECTION = new String[] {
            Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE,
            Phone.CUSTOM_RINGTONE
    };

    private SlideDial parent;

    private Cursor managedCursor;

    private FilterableContactsList filteredContacts;

    private boolean confirmed;

    private double downX;

    private double downY;

    private double lastX;

    private double lastY;

    private float p2DownX;

    private float p2DownY;

    private int currentValue;

    private boolean screenIsBeingTouched;

    private Vibrator vibe;

    private int currentWheel;

    private String currentCharacter;

    private String currentString;

    private String currentContact;

    private int trackballTimeout = 500;

    private boolean trackballEnabled = true;

    private ShakeDetector shakeDetector;

    private boolean inDPadMode = false;

    public ContactsView(Context context) {
        super(context);

        parent = ((SlideDial) context);

        vibe = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        // Get the base URI for People table in Contacts content provider.
        // ie. content://contacts/people/
        Uri mContacts = Phone.CONTENT_URI;

        // Best way to retrieve a query; returns a managed query.
        managedCursor = parent.managedQuery(mContacts, PROJECTION, // Which
                // columns
                // to return.
                null, // WHERE clause--we won't specify.
                null, // no selection args
                Phone.DISPLAY_NAME + " ASC"); // Order-by clause.
        boolean moveSucceeded = managedCursor.moveToFirst();
        
        ArrayList<String> contactNames = new ArrayList<String>();
        while (moveSucceeded) {
            contactNames.add(managedCursor.getString(NAME));
            moveSucceeded = managedCursor.moveToNext();
        }
        filteredContacts = new FilterableContactsList(contactNames);
        currentString = "";
        currentContact = "";

        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();

        shakeDetector = new ShakeDetector(context, new ShakeListener() {
            public void onShakeDetected() {
                backspace();
            }
        });
    }

    public void shutdown() {
        shakeDetector.shutdown();
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        if (trackballEnabled == false) {
            return true;
        }
        Log.i("Motion", Float.toString(event.getY()));
        if (event.getY() > .16) {
            trackballEnabled = false;
            (new Thread(new trackballTimeout())).start();
            nextContact();
        }
        if (event.getY() < -.16) {
            trackballEnabled = false;
            (new Thread(new trackballTimeout())).start();
            prevContact();
        }
        return true;
    }

    class trackballTimeout implements Runnable {
        public void run() {
            try {
                Thread.sleep(trackballTimeout);
                trackballEnabled = true;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void resetContactList() {
        currentString = "";
        managedCursor.moveToFirst();
        // Keep going if the entry doesn't have a name
        String name = managedCursor.getString(NAME);
        if (name == null) {
            nextContact();
            return;
        }
    }

    private void nextContact() {
        currentString = "";
        boolean moveSucceeded = managedCursor.moveToNext();
        if (!moveSucceeded) {
            managedCursor.moveToFirst();
        }
        // Keep going if the entry doesn't have a name
        String name = managedCursor.getString(NAME);
        if (name == null) {
            nextContact();
            return;
        }
        vibe.vibrate(PATTERN, -1);
        speakCurrentContact(true);
    }

    private void prevContact() {
        currentString = "";
        boolean moveSucceeded = managedCursor.moveToPrevious();
        if (!moveSucceeded) {
            managedCursor.moveToLast();
        }
        // Keep going if the entry doesn't have a name
        String name = managedCursor.getString(NAME);
        if (name == null) {
            prevContact();
            return;
        }
        vibe.vibrate(PATTERN, -1);
        speakCurrentContact(true);
    }

    private void jumpToFirstFilteredResult() {
        ContactEntry entry = filteredContacts.next();
        if (entry == null) {
            parent.tts.playEarcon(TTSEarcon.TOCK.toString(), 0, null);
            if (currentString.length() > 0) {
                currentString = currentString.substring(0, currentString.length() - 1);
                if (currentString.length() > 0) {
                    filteredContacts.filter(currentString);
                    jumpToFirstFilteredResult();
                } else {
                    parent.tts.speak(parent.getString(R.string.no_contacts_found), 0, null);
                }
            }
            return;
        }
        managedCursor.moveToPosition(entry.index);
        speakCurrentContact(true);
    }

    private void speakCurrentContact(boolean interrupt) {
        String name = null;
        try {
            name = managedCursor.getString(NAME);
        } catch (CursorIndexOutOfBoundsException e) {
            // Cursor was not actually ready yet.
            name = null;
        }
        if (name == null) {
            // There is nothing to speak because something when wrong when
            // retrieving the name.
            return;
        }
        if (interrupt) {
            parent.tts.speak(name, 0, null);
        } else {
            parent.tts.speak(name, 1, null);
        }
        int phoneType = Integer.parseInt(managedCursor.getString(TYPE));
        String type = "";
        if (phoneType == Phone.TYPE_HOME) {
            type = parent.getString(R.string.home);
        } else if (phoneType == Phone.TYPE_MOBILE) {
            type = parent.getString(R.string.cell);
        } else if (phoneType == Phone.TYPE_WORK) {
            type = parent.getString(R.string.work);
        }
        if (type.length() > 0) {
            parent.tts.speak(type, 1, null);
        }
        currentContact = name + " " + type;
        invalidate();
    }

    private void dialActionHandler() {
        if (!confirmed) {
            if(currentContact.length() != 0) {
                parent.tts.speak(parent.getString(R.string.you_are_about_to_dial), 0, null);
                speakCurrentContact(false);
                confirmed = true;   
            } else {
                parent.tts.speak(parent.getString(R.string.invalid_contact), 0, null);
            }
        } else {
            parent.returnResults(managedCursor.getString(NUMBER));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String input = "";
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
                dialActionHandler();
                return true;
            case KeyEvent.KEYCODE_SEARCH:
                dialActionHandler();
                return true;
            case KeyEvent.KEYCODE_CALL:
                dialActionHandler();
                return true;
            case KeyEvent.KEYCODE_MENU:
                parent.switchToDialingView();
                return true;
            case KeyEvent.KEYCODE_A:
                input = "a";
                break;
            case KeyEvent.KEYCODE_B:
                input = "b";
                break;
            case KeyEvent.KEYCODE_C:
                input = "c";
                break;
            case KeyEvent.KEYCODE_D:
                input = "d";
                break;
            case KeyEvent.KEYCODE_E:
                input = "e";
                break;
            case KeyEvent.KEYCODE_F:
                input = "f";
                break;
            case KeyEvent.KEYCODE_G:
                input = "g";
                break;
            case KeyEvent.KEYCODE_H:
                input = "h";
                break;
            case KeyEvent.KEYCODE_I:
                input = "i";
                break;
            case KeyEvent.KEYCODE_J:
                input = "j";
                break;
            case KeyEvent.KEYCODE_K:
                input = "k";
                break;
            case KeyEvent.KEYCODE_L:
                input = "l";
                break;
            case KeyEvent.KEYCODE_M:
                input = "m";
                break;
            case KeyEvent.KEYCODE_N:
                input = "n";
                break;
            case KeyEvent.KEYCODE_O:
                input = "o";
                break;
            case KeyEvent.KEYCODE_P:
                input = "p";
                break;
            case KeyEvent.KEYCODE_Q:
                input = "q";
                break;
            case KeyEvent.KEYCODE_R:
                input = "r";
                break;
            case KeyEvent.KEYCODE_S:
                input = "s";
                break;
            case KeyEvent.KEYCODE_T:
                input = "t";
                break;
            case KeyEvent.KEYCODE_U:
                input = "u";
                break;
            case KeyEvent.KEYCODE_V:
                input = "v";
                break;
            case KeyEvent.KEYCODE_W:
                input = "w";
                break;
            case KeyEvent.KEYCODE_X:
                input = "x";
                break;
            case KeyEvent.KEYCODE_Y:
                input = "y";
                break;
            case KeyEvent.KEYCODE_Z:
                input = "z";
                break;
            case KeyEvent.KEYCODE_DEL:
                backspace();
                return true;
        }
        if (input.length() > 0) {
            currentString = currentString + input;
            filteredContacts.filter(currentString);
            jumpToFirstFilteredResult();
        }
        confirmed = false;
        return false;
    }

    private void confirmEntry() {
        screenIsBeingTouched = false;
        int prevVal = currentValue;
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
        filteredContacts.filter(currentString);
        jumpToFirstFilteredResult();
    }

    private void initiateMotion(double x, double y) {
        downX = x;
        downY = y;
        lastX = x;
        lastY = y;
        currentValue = -1;
        currentWheel = NONE;
        currentCharacter = "";
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        float x = event.getX();
        float y = event.getY();

        // Treat the screen as a dpad
        if (action == 261) { // 261 == ACTION_POINTER_2_DOWN - using number for Android 1.6 compat
            inDPadMode = true;
            screenIsBeingTouched = false;
            p2DownX = getX(event, 1);
            p2DownY = getY(event, 1);
            vibe.vibrate(PATTERN, -1);
        } else if (action == 262) { // 262 == MotionEvent.ACTION_POINTER_2_UP - using number for Android 1.6 compat
            float p2DeltaX = getX(event, 1) - p2DownX;
            float p2DeltaY = getY(event, 1) - p2DownY;
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

        if (action == MotionEvent.ACTION_DOWN) {
            initiateMotion(x, y);
            return true;
        } else if (action == MotionEvent.ACTION_UP) {
            if (inDPadMode == false) {
                confirmEntry();
            } else {
                inDPadMode = false;
            }
            return true;
        } else {
            if (!inDPadMode) {
                screenIsBeingTouched = true;
                lastX = x;
                lastY = y;
                int prevVal = currentValue;
                currentValue = evalMotion(x, y);
                // Do nothing since we want a deadzone here;
                // restore the state to the previous value.
                if (currentValue == -1) {
                    currentValue = prevVal;
                    return true;
                }
                // There is a wheel that is active
                if (currentValue != 5) {
                    if (currentWheel == NONE) {
                        currentWheel = getWheel(currentValue);
                    }
                    currentCharacter = getCharacter(currentWheel, currentValue);
                } else {
                    currentCharacter = "";
                }
                invalidate();
                if (prevVal != currentValue) {
                    if (currentCharacter.equals("")) {
                      parent.tts.playEarcon(TTSEarcon.TOCK.toString(), 0, null);
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

    public int getWheel(int value) {
        switch (value) {
            case 1:
                return AE;
            case 2:
                return IM;
            case 3:
                return QU;
            case 4:
                return Y;
            case 5:
                return NONE;
            case 6:
                return Y;
            case 7:
                return QU;
            case 8:
                return IM;
            case 9:
                return AE;
            default:
                return NONE;
        }
    }

    public String getCharacter(int wheel, int value) {
        switch (wheel) {
            case AE:
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
            case IM:
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
            case QU:
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
            case Y:
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

    public int evalMotion(double x, double y) {
        float rTolerance = 25;
        double thetaTolerance = (Math.PI / 16);

        double r = Math.sqrt(((downX - x) * (downX - x)) + ((downY - y) * (downY - y)));

        if (r < rTolerance) {
            return 5;
        }

        double theta = Math.atan2(downY - y, downX - x);

        if (Math.abs(theta - left) < thetaTolerance) {
            return 4;
        } else if (Math.abs(theta - upleft) < thetaTolerance) {
            return 1;
        } else if (Math.abs(theta - up) < thetaTolerance) {
            return 2;
        } else if (Math.abs(theta - upright) < thetaTolerance) {
            return 3;
        } else if (Math.abs(theta - downright) < thetaTolerance) {
            return 9;
        } else if (Math.abs(theta - down) < thetaTolerance) {
            return 8;
        } else if (Math.abs(theta - downleft) < thetaTolerance) {
            return 7;
        } else if ((theta > right - thetaTolerance) || (theta < rightWrap + thetaTolerance)) {
            return 6;
        }

        // Off by more than the threshold, so it doesn't count
        return -1;
    }

    @Override
    public void onDraw(Canvas canvas) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
            String[] lines = currentContact.split(" ");
            for (int i = 0; i < lines.length; i++) {
                canvas.drawText(lines[i], x, y + (i * 45), paint);
            }
        }
        paint.setTextSize(50);

        if (!screenIsBeingTouched) {
            x = 5;
            y = getHeight() - 60;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText("Press MENU for dialing mode.", x, y, paint);

            x = 5;
            y = getHeight() - 40;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText("Scroll contacts with trackball.", x, y, paint);

            x = 5;
            y = getHeight() - 20;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            canvas.drawText("Press CALL twice to confirm.", x, y, paint);
        } else {
            int offset = 90;

            int x1 = (int) downX - offset;
            int y1 = (int) downY - offset;
            int x2 = (int) downX;
            int y2 = (int) downY - offset;
            int x3 = (int) downX + offset;
            int y3 = (int) downY - offset;
            int x4 = (int) downX - offset;
            int y4 = (int) downY;
            int x6 = (int) downX + offset;
            int y6 = (int) downY;
            int x7 = (int) downX - offset;
            int y7 = (int) downY + offset;
            int x8 = (int) downX;
            int y8 = (int) downY + offset;
            int x9 = (int) downX + offset;
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
                case AE:
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
                case IM:
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
                case QU:
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
                case Y:
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

    private void drawCharacter(String character, int x, int y, Canvas canvas, Paint paint,
            boolean isSelected) {
        int regSize = 50;
        int selectedSize = regSize * 2;
        if (isSelected) {
            paint.setTextSize(selectedSize);
        } else {
            paint.setTextSize(regSize);
        }
        canvas.drawText(character, x, y, paint);
    }

    public void backspace() {
        confirmed = false;
        String deletedCharacter = "";
        if (currentString.length() > 0) {
            deletedCharacter = "" + currentString.charAt(currentString.length() - 1);
            currentString = currentString.substring(0, currentString.length() - 1);
        }
        if (!deletedCharacter.equals("")) {
            // parent.tts.speak(deletedCharacter + " deleted.", 0, null);
            // parent.tts.speak(deletedCharacter, 0, new String[]
            // {TTSParams.VOICE_ROBOT.toString()});
        } else {
            parent.tts.playEarcon(TTSEarcon.TOCK.toString(), 0, null);
            parent.tts.playEarcon(TTSEarcon.TOCK.toString(), 1, null);
        }
        if (currentString.length() > 0) {
            filteredContacts.filter(currentString);
            jumpToFirstFilteredResult();
        } else {
            resetContactList();
        }
        invalidate();
    }
}