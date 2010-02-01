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
package com.google.marvin.shell;

import com.google.marvin.widget.TouchGestureControlOverlay.Gesture;

import java.util.HashMap;

/**
 * Holds the information for an application in the shell that is needed to start
 * that application from the shell.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */

public class MenuItem {
  private static final HashMap<Gesture, Integer> gestureToNumberMapping =
    new HashMap<Gesture, Integer>();
  static {
    gestureToNumberMapping.put(Gesture.UPLEFT, 1);
    gestureToNumberMapping.put(Gesture.UP, 2);
    gestureToNumberMapping.put(Gesture.UPRIGHT, 3);
    gestureToNumberMapping.put(Gesture.LEFT, 4);
    gestureToNumberMapping.put(Gesture.RIGHT, 6);
    gestureToNumberMapping.put(Gesture.DOWNLEFT, 7);
    gestureToNumberMapping.put(Gesture.DOWN, 8);
    gestureToNumberMapping.put(Gesture.DOWNRIGHT, 9);
  }
  
  public String label;
  public String action;
  public String data;
  public AppEntry appInfo;

  public MenuItem(String itemLabel, String itemAction, String itemData, AppEntry applicationInfo) {
    label = itemLabel;
    action = itemAction;
    data = itemData;
    appInfo = applicationInfo;
  }
  
  /** 
   * Returns a string xml representation of this item element.
   * @return
   */
  public String toXml(Gesture gesture){
    String xmlStr = "  <item gesture='" + gestureToNumberMapping.get(gesture) + "' label='" + label + "' action='" + action + "'>\n";
    xmlStr = xmlStr + appInfo.toXml();
    xmlStr = xmlStr + "  </item>\n";
    return xmlStr;
  }
}
