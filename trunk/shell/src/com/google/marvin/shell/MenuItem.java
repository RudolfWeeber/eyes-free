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

/**
 * Holds the information for an application in the shell that is needed to start
 * that application from the shell.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */

public class MenuItem {
    
    private static String XML_ITEM_OPEN_TAG = "<item gesture='%s' label='%s' action='%s' data='%s'>\n";
    
    private static String XML_ITEM_CLOSE_TAG = "</item>\n";
    
    public String label;

    public String action;

    public String data;

    public AppInfo appInfo;

    public MenuItem(String itemLabel, String itemAction, String itemData, AppInfo applicationInfo) {
        label = itemLabel;
        action = itemAction;
        data = itemData;
        appInfo = applicationInfo;
    }

    /**
     * Returns a string XML representation of this item element.
     */
    public String toXml(int gesture) {
        StringBuilder xmlBuilder = new StringBuilder();
        xmlBuilder.append(String.format(XML_ITEM_OPEN_TAG,
                String.valueOf(gesture),
                MenuManager.escapeEntities(label),
                MenuManager.escapeEntities(action),
                MenuManager.escapeEntities(data)));
        if (appInfo != null) {
            xmlBuilder.append(appInfo.toXml());
        }
        xmlBuilder.append(XML_ITEM_CLOSE_TAG);
        return xmlBuilder.toString();
    }
}
