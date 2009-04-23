package com.google.marvin.brailler;

import android.content.Context;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

/**
 * Custom View that implements the Brailler
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class BraillerView extends View {
  private static final long[] PATTERN = {0, 1, 40, 41};
  private boolean dot1;
  private boolean dot2;
  private boolean dot3;
  private boolean dot4;
  private boolean dot5;
  private boolean dot6;
  private Brailler parent;
  private Vibrator vibe;

  public BraillerView(Context context) {
    super(context);
    parent = (Brailler) context;
    vibe = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

    dot1 = false;
    dot2 = false;
    dot3 = false;
    dot4 = false;
    dot5 = false;
    dot6 = false;

    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);
    requestFocus();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    // Ignore any events that involve the user going off the edge of the screen
    int edgeFlags = event.getEdgeFlags();
    if ((edgeFlags == MotionEvent.EDGE_BOTTOM) || (edgeFlags == MotionEvent.EDGE_LEFT)
        || (edgeFlags == MotionEvent.EDGE_RIGHT) || (edgeFlags == MotionEvent.EDGE_TOP)) {
      vibe.vibrate(PATTERN, -1);
      return true;
    }
    int action = event.getAction();
    switch (action) {
      case MotionEvent.ACTION_UP:
        processInput();
        break;
      default:
        float x = event.getX();
        float y = event.getY();
        if (x < (getWidth() * .4)) {
          if (y < (getHeight() * .25)) {
            if (!dot1) {
              parent.tts.speak("[tock]", 0, null);
              vibe.vibrate(PATTERN, -1);
            }
            dot1 = true;
          } else if (y < (getHeight() * .375)) {
            //Nothing - alley
          } else if (y < (getHeight() * .625)) {
            if (!dot2) {
              parent.tts.speak("[tock]", 0, null);
              vibe.vibrate(PATTERN, -1);
            }
            dot2 = true;
          } else if (y < (getHeight() * .75)) {
            //Nothing - alley
          } else {
            if (!dot3) {
              parent.tts.speak("[tock]", 0, null);
              vibe.vibrate(PATTERN, -1);
            }
            dot3 = true;
          }
        } else if (x < (getWidth() * .6)) {
        //Nothing - alley
        } else {
          if (y < (getHeight() * .25)) {
            if (!dot4) {
              parent.tts.speak("[tock]", 0, null);
              vibe.vibrate(PATTERN, -1);
            }
            dot4 = true;
          } else if (y < (getHeight() * .375)) {
            //Nothing - alley
          } else if (y < (getHeight() * .625)) {
            if (!dot5) {
              parent.tts.speak("[tock]", 0, null);
              vibe.vibrate(PATTERN, -1);
            }
            dot5 = true;
          } else if (y < (getHeight() * .75)) {
            //Nothing - alley
          } else {
            if (!dot6) {
              parent.tts.speak("[tock]", 0, null);
              vibe.vibrate(PATTERN, -1);
            }
            dot6 = true;
          }
        }
        break;
    }
    return true;
  }


  private void processInput() { 
    parent.tts.speak(letterFromBraille(), 0, null);
    dot1 = false;
    dot2 = false;
    dot3 = false;
    dot4 = false;
    dot5 = false;
    dot6 = false;
  }

  private String letterFromBraille() {
    if ( dot1 && !dot4 && 
        !dot2 && !dot5 && 
        !dot3 && !dot6 ) {
      return "a";
    }
    if (dot1 && !dot4 && dot2 && !dot5 && !dot3 && !dot6) {
      return "b";
    }
    if (dot1 && dot4 && !dot2 && !dot5 && !dot3 && !dot6) {
      return "c";
    }
    if (dot1 && dot4 && !dot2 && dot5 && !dot3 && !dot6) {
      return "d";
    }
    if (dot1 && !dot4 && !dot2 && dot5 && !dot3 && !dot6) {
      return "e";
    }
    if (dot1 && dot4 && dot2 && !dot5 && !dot3 && !dot6) {
      return "f";
    }
    if (dot1 && dot4 && dot2 && dot5 && !dot3 && !dot6) {
      return "g";
    }
    if (dot1 && !dot4 && dot2 && dot5 && !dot3 && !dot6) {
      return "h";
    }
    if (!dot1 && dot4 && dot2 && !dot5 && !dot3 && !dot6) {
      return "i";
    }
    if (!dot1 && dot4 && dot2 && dot5 && !dot3 && !dot6) {
      return "j";
    }
    if (dot1 && !dot4 && !dot2 && !dot5 && dot3 && !dot6) {
      return "k";
    }
    
    if ( dot1 && !dot4 && 
         dot2 && !dot5 && 
         dot3 && !dot6 ) {
      return "l";
    }
    if ( dot1 &&  dot4 && 
        !dot2 && !dot5 && 
         dot3 && !dot6 ) {
      return "m";
    }
    if ( dot1 &&  dot4 && 
        !dot2 &&  dot5 && 
         dot3 && !dot6 ) {
      return "n";
    }
    if ( dot1 && !dot4 && 
        !dot2 &&  dot5 && 
         dot3 && !dot6 ) {
      return "o";
    }
    if ( dot1 &&  dot4 && 
         dot2 && !dot5 && 
         dot3 && !dot6 ) {
      return "p";
    }    
    if ( dot1 &&  dot4 && 
         dot2 &&  dot5 && 
         dot3 && !dot6 ) {
      return "q";
    }
    if ( dot1 && !dot4 && 
         dot2 &&  dot5 && 
         dot3 && !dot6 ) {
      return "r";
    }
    if (!dot1 &&  dot4 && 
         dot2 && !dot5 && 
         dot3 && !dot6 ) {
      return "s";
    }
    if (!dot1 &&  dot4 && 
         dot2 &&  dot5 && 
         dot3 && !dot6 ) {
      return "t";
    }
    
    
    
    if ( dot1 && !dot4 && 
        !dot2 && !dot5 && 
         dot3 &&  dot6 ) {
      return "u";
    }    
    if ( dot1 && !dot4 && 
         dot2 && !dot5 && 
         dot3 &&  dot6 ) {
      return "v";
    }   
    if (!dot1 &&  dot4 && 
         dot2 &&  dot5 && 
        !dot3 &&  dot6 ) {
      return "w";
    }   
    if ( dot1 &&  dot4 && 
        !dot2 && !dot5 && 
         dot3 &&  dot6 ) {
      return "x";
    }   
    if ( dot1 &&  dot4 && 
        !dot2 &&  dot5 && 
         dot3 &&  dot6 ) {
      return "y";
    }   
    if ( dot1 && !dot4 && 
        !dot2 &&  dot5 && 
         dot3 &&  dot6 ) {
      return "z";
    }   
    
    return "";
  }

}
