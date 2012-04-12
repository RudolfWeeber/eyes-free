
package com.marvin.circleime;

import com.google.tts.TTSEarcon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.inputmethodservice.KeyboardView;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * A transparent overlay which catches all touch events and uses a call back to
 * return the gesture that the user performed.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */

public class CircleGestureView extends KeyboardView {
    private static final char alphabet[] = {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
            'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
    };

    private static final long[] PATTERN = {
            0, 1, 40, 41
    };

    private static final int AE = 0;

    private static final int IM = 1;

    private static final int QU = 2;

    private static final int Y = 4;

    private static final int NONE = 5;

    private static final int NUM0 = 6;
    
    private static final int NUM1 = 7;
    

    private final double left = 0;

    private final double upleft = Math.PI * .25;

    private final double up = Math.PI * .5;

    private final double upright = Math.PI * .75;

    private final double downright = -Math.PI * .75;

    private final double down = -Math.PI * .5;

    private final double downleft = -Math.PI * .25;

    private final double right = Math.PI;

    private final double rightWrap = -Math.PI;

    private double downX;

    private double downY;

    private int currentWheel = 5;

    private String currentCharacter = "";

    private String currentString = "";

    private double lastX;

    private double lastY;

    private int currentValue;

    private boolean screenIsBeingTouched;

    private Vibrator vibe;

    private SoftKeyboard parent;

    public CircleGestureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        parent = (SoftKeyboard) context;
        vibe = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        float x = event.getX();
        float y = event.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            initiateMotion(x, y);
            return true;
        } else if (action == MotionEvent.ACTION_UP) {
            confirmEntry();
            return true;
        } else {
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
                parent.mTts.playEarcon("[tock]", 2, null);
                if (currentCharacter.equals("")) {
                    // parent.tts.playEarcon(TTSEarcon.TOCK.toString(), 0,
                    // null);
                } else {
                    String[] params = new String[1];
                    // params[0] = TTSParams.VOICE_FEMALE.toString();
                    // parent.tts.speak(currentCharacter, 0, null); //TODO: Fix
                    // me!
                }
            }
            vibe.vibrate(PATTERN, -1);
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
                        return "SPACE"; // return "MODE";
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
            case NUM0:
                switch (value) {
                    case 1:
                        return "0";
                    case 2:
                        return "1";
                    case 3:
                        return "2"; // return "MODE";
                    case 4:
                        return "3";
                    case 5:
                        return "";
                    case 6:
                        return "4";
                    case 7:
                        return "5";
                    case 8:
                        return "6";
                    case 9:
                        return "7";
                    default:
                        return "";
                } 
            case NUM1:
                switch (value) {
                    case 1:
                        return "8";
                    case 2:
                        return "9";
                    case 3:
                        return "<-";
                    case 4:
                        return "*";
                    case 5:
                        return "";
                    case 6:
                        return "@";
                    case 7:
                        return "&";
                    case 8:
                        return "#";
                    case 9:
                        return "$";
                    default:
                        return "%";
                }                 
            default:
                return "";
        }
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
            currentCharacter = "";
            backspace();
        } else {
            if (currentCharacter.equals("SPACE")) {
                currentString = currentString + " ";
            }
            /*
             * else if (currentCharacter.equals("MODE")) { // Do nothing }
             */
            else {
                currentString = currentString + currentCharacter;
            }
            // parent.tts.speak(currentCharacter, 0, null);
            if (currentCharacter.equals("SPACE")) {
                currentCharacter = " ";
            }
            if (currentCharacter.length() > 0) {
                parent.sendKeyChar(currentCharacter.toLowerCase().charAt(0));
            }
        }
        invalidate();
        initiateMotion(lastX, lastY);
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
    public void onDraw(Canvas canvas) {
        // super.onDraw(canvas);

        setBackgroundColor(Color.TRANSPARENT);

        // Draw an indication that the IME is up - doing a border for now
        Paint imeStatusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        imeStatusPaint.setColor(Color.RED);
        imeStatusPaint.setTextSize(14);
        imeStatusPaint.setTypeface(Typeface.DEFAULT_BOLD);
        Paint imeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        imeBgPaint.setColor(Color.BLACK);
        int fudgeFactor = 15; 
        int startY = 0;

        canvas.drawRect(0, startY, getWidth(), startY + fudgeFactor, imeBgPaint);
        canvas.drawText("IME Active", 10, startY + 13, imeStatusPaint);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        int x = 5;
        int y = 50;
        paint.setTextSize(50);
        paint.setTextAlign(Paint.Align.LEFT);
        y -= paint.ascent() / 2;

        // canvas.drawText(currentString, x, y, paint);

        if (!screenIsBeingTouched) {
            x = 5;
            y = getHeight() - 40;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            // canvas.drawText("Scroll apps with trackball.", x, y, paint);

            x = 5;
            y = getHeight() - 20;
            paint.setTextSize(20);
            paint.setTextAlign(Paint.Align.LEFT);
            y -= paint.ascent() / 2;
            // canvas.drawText("Press CALL to launch app.", x, y, paint);
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
                    drawCharacter("SPACE", x3, y3, canvas, paint, currentCharacter.equals("SPACE"));
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

    public void backspace() {
        parent.handleBackspace();
        String deletedCharacter = "";
        if (currentString.length() > 0) {
            deletedCharacter = "" + currentString.charAt(currentString.length() - 1);
            currentString = currentString.substring(0, currentString.length() - 1);
        }
        if (!deletedCharacter.equals("")) {
            // parent.tts.speak(deletedCharacter + " deleted.", 0, null);
        } else {
            // parent.tts.playEarcon(TTSEarcon.TOCK.toString(), 0, null);
            // parent.tts.playEarcon(TTSEarcon.TOCK.toString(), 1, null);
        }
        invalidate();
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
    
    
    private int keyboardMode = 0;
    
    private void toggleKeyboardMode(){
        if (keyboardMode == 0){
            keyboardMode = 1;
            parent.mTts.speak("Numbers", 2, null);
        } else {
            keyboardMode = 0;
            parent.mTts.speak("Alpha", 2, null);
        }
    }
    

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
      String input = "";
      switch (keyCode) {        
        case KeyEvent.KEYCODE_MENU:
          toggleKeyboardMode();
          return true;
      }
      return false;
    }
    

}
