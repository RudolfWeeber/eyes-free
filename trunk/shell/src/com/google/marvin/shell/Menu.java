/*
 * Copyright (C) 2010 Google Inc.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

/**
 * A class for representing menus as a HashMap mapping gesture codes to
 * MenuItems.
 *
 * @author credo@google.com (Tim Credo)
 */
public final class Menu extends HashMap<Integer, MenuItem> {

    private static String XML_MENU_TAG = "<menu label='%s' wallpaper='%s'>\n";

    private static String XML_MENU_TAG_WITH_ID = "<menu label='%s' wallpaper='%s' id='%s'>\n";

    private static String XML_MENU_CLOSE_TAG = "</menu>\n";

    private String mName;

    private String mID = null;

    private String mWallpaper;

    /**
     * A menu is just a HashMap with an extra name String.
     */
    public Menu(String name) {
        super();
        mName = name;
        mID = name;
        mWallpaper = "";
    }

    public Menu(String name, HashMap<Integer, MenuItem> items, String wallpaper) {
        super(items);
        mName = name;
        mID = name;
        mWallpaper = wallpaper;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getID() {
        return mID;
    }

    public void setID(String id) {
        mID = id;
    }

    public String getWallpaper() {
        return mWallpaper;
    }

    public void setWallpaper(String wallpaper) {
        mWallpaper = wallpaper;
    }

    /**
     * Write out this menu to an XML string.
     */
    public String toXml() {
        StringBuilder xmlBuilder = new StringBuilder();
        // don't use the id if not necessary
        if (mID == null || mID == mName) {
            xmlBuilder.append(
                    String.format(XML_MENU_TAG, MenuManager.escapeEntities(mName),
                            MenuManager.escapeEntities(mWallpaper)));
        } else {
            xmlBuilder.append(String.format(XML_MENU_TAG_WITH_ID, MenuManager.escapeEntities(mName),
                    MenuManager.escapeEntities(mWallpaper), MenuManager.escapeEntities(mID)));
        }
        ArrayList<Integer> keyList = new ArrayList<Integer>(keySet());
        Collections.sort(keyList);
        Iterator<Integer> it = keyList.iterator();
        while (it.hasNext()) {
            int gesture = it.next();
            MenuItem item = get(gesture);
            xmlBuilder.append(item.toXml(gesture));
        }
        xmlBuilder.append(XML_MENU_CLOSE_TAG);
        return xmlBuilder.toString();
    }
}
