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

import com.googlecode.eyesfree.widget.GestureOverlay.Gesture;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Parcel;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Manages a set of menus and provides functions for saving/loading XML and
 * editing menus.
 *
 * @author clchen@google.com (Charles L. Chen)
 * @author credo@google.com (Tim Credo)
 */
public final class MenuManager extends HashMap<String, Menu> {

    private static String XML_SHELL_TAG = "<shell>\n";

    private static String XML_VERSION_TAG = "<version number='0.1' />\n";

    private static String XML_SHELL_CLOSE_TAG = "</shell>\n";

    /**
     * Write out the currently loaded set of menus to an XML string.
     */
    public String toXml() {
        StringBuffer xml = new StringBuffer();
        xml.append(XML_SHELL_TAG);
        xml.append(XML_VERSION_TAG);
        for (String menuName : keySet()) {
            xml.append(get(menuName).toXml());
        }
        xml.append(XML_SHELL_CLOSE_TAG);
        return xml.toString();
    }

    /**
     * Write currently loaded menus to an XML file.
     */
    public void save(String filename) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(filename);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
            outputStreamWriter.write(toXml());
            outputStreamWriter.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Insert a new menu.
     */
    public void insertMenu(Menu currentMenu, Integer gesture, String menuName) {

        String id = menuName;
        int n = 1;
        // if the id is a duplicate, add a number
        while (containsKey(id)) {
            n = n + 1;
            id = menuName + " " + String.valueOf(n);
        }
        int oppositeGesture;
        if (gesture == Gesture.EDGELEFT) {
            oppositeGesture = Gesture.EDGERIGHT;
        } else if (gesture == Gesture.EDGERIGHT) {
            oppositeGesture = Gesture.EDGELEFT;
        } else {
            return;
        }
        Menu nextMenu = null;
        MenuItem item = currentMenu.get(gesture);
        if (item.action.equalsIgnoreCase("MENU")) {
            nextMenu = get(item.data);
        }
        Menu newMenu = new Menu(menuName);
        put(id, newMenu);
        newMenu.setID(id);
        MenuItem link = new MenuItem(menuName, "MENU", id);
        MenuItem homeLink = new MenuItem(currentMenu.getName(), "MENU", currentMenu.getID());
        currentMenu.put(gesture, link);
        newMenu.put(oppositeGesture, homeLink);
        if (nextMenu != null) {
            MenuItem nextLink = new MenuItem(nextMenu.getName(), "MENU", nextMenu.getID());
            newMenu.put(gesture, nextLink);
            nextMenu.put(oppositeGesture, link);
        }
    }

    /**
     * Load a set of menus from an XML file.
     */
    public static MenuManager loadMenus(MarvinShell context, String filename) {
        MenuManager manager = new MenuManager();
        try {
            FileInputStream fis = new FileInputStream(filename);
            manager = loadMenus(context, fis);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return manager;
    }

    /**
     * Load a set of menus from an XML input stream.
     */
    public static MenuManager loadMenus(MarvinShell context, InputStream is) {
        HashMap<String, Menu> shortcutMenus = new HashMap<String, Menu>();
        try {
            DocumentBuilder docBuild = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = docBuild.parse(is);
            String versionNumber;
            NodeList versionNodes = doc.getElementsByTagName("version");
            if (versionNodes.getLength() == 0) {
                versionNumber = "0.0";
            } else {
                Node versionNumberNode = versionNodes.item(0)
                        .getAttributes().getNamedItem("number");
                if (versionNumberNode == null) {
                    versionNumber = "0.0";
                } else {
                    versionNumber = versionNumberNode.getNodeValue();
                }
            }
            if (versionNumber.equalsIgnoreCase("0.0")) {
                // Load new default and wipe over one screen with old shortcuts
                Resources res = context.getResources();
                InputStream defaultIs = res.openRawResource(R.raw.default_shortcuts);
                shortcutMenus = loadMenus(context, defaultIs);
                NodeList items = doc.getElementsByTagName("item");
                shortcutMenus.get("Shortcuts Left").putAll(readItems(context, items));

            } else {
                // Load everything normally
                NodeList menus = doc.getElementsByTagName("menu");
                for (int i = 0; i < menus.getLength(); i++) {
                    NamedNodeMap attribs = menus.item(i).getAttributes();
                    String label = attribs.getNamedItem("label").getNodeValue();
                    String wallpaper = "";
                    Node wallpaperNode = attribs.getNamedItem("wallpaper");
                    if (wallpaperNode != null) {
                        wallpaper = wallpaperNode.getNodeValue();
                    }
                    Node idAttrNode = attribs.getNamedItem("id");
                    if (idAttrNode != null) {
                        String id = idAttrNode.getNodeValue();
                        Menu menu = new Menu(label, readItems(
                                context, menus.item(i).getChildNodes()), wallpaper);
                        menu.setID(id);
                        shortcutMenus.put(id, menu);
                    } else {
                        Menu menu = new Menu(label, readItems(
                                context, menus.item(i).getChildNodes()), wallpaper);
                        shortcutMenus.put(label, menu);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        MenuManager manager = new MenuManager();
        manager.putAll(shortcutMenus);
        return manager;
    }

    /**
     * Loads menu items from a list of XML nodes containing menu items.
     */
    public static HashMap<Integer, MenuItem> readItems(MarvinShell context, NodeList itemNodes) {
        HashMap<Integer, MenuItem> menu = new HashMap<Integer, MenuItem>();
        for (int i = 0; i < itemNodes.getLength(); i++) {
            if (itemNodes.item(i).getNodeName().equalsIgnoreCase("item")) {
                NamedNodeMap attribs = itemNodes.item(i).getAttributes();
                int g = Integer.parseInt(attribs.getNamedItem("gesture").getNodeValue());

                String label = attribs.getNamedItem("label").getNodeValue();
                String action = attribs.getNamedItem("action").getNodeValue();
                String data = null;
                Node dataAttrNode = attribs.getNamedItem("data");
                if (dataAttrNode != null) {
                    data = dataAttrNode.getNodeValue();
                }
                AppInfo appInfo = null;
                if (action.equalsIgnoreCase("launch")) {
                    Node appInfoNode = null;
                    NodeList nodes = itemNodes.item(i).getChildNodes();
                    for (int j = 0; j < nodes.getLength(); j++) {
                        Node currentNode = nodes.item(j);
                        String tagName = currentNode.getNodeName();
                        // Only process actual nodes
                        if (tagName != null) {
                            if (tagName.equalsIgnoreCase("appInfo")) {
                                appInfoNode = currentNode;
                            }
                        }
                    }
                    NamedNodeMap appInfoAttr = appInfoNode.getAttributes();
                    String packageName = "";
                    Node packageAttrNode = appInfoAttr.getNamedItem("package");
                    if (packageAttrNode != null) {
                        packageName = packageAttrNode.getNodeValue();
                    }
                    String className = "";
                    Node classAttrNode = appInfoAttr.getNamedItem("class");
                    if (classAttrNode != null) {
                        className = classAttrNode.getNodeValue();
                    }
                    appInfo = new AppInfo(null, packageName, className);

                    // Check to see if the package is still installed
                    if (packageExists(context, appInfo)) {
                        menu.put(g, new MenuItem(label, action, data, appInfo));
                    }

                } else if (action.equalsIgnoreCase("android_widget")) {

                    Node widgetInfoNode = null;
                    NodeList nodes = itemNodes.item(i).getChildNodes();
                    for (int j = 0; j < nodes.getLength(); j++) {
                        Node currentNode = nodes.item(j);
                        String tagName = currentNode.getNodeName();
                        // Only process actual nodes
                        if (tagName != null) {
                            if (tagName.equalsIgnoreCase("widgetInfo")) {
                                widgetInfoNode = currentNode;
                            }
                        }
                    }
                    AppWidgetProviderInfo widgetInfo = null;
                    // Get widget id to grab widget
                    NamedNodeMap idInfoAttr = widgetInfoNode.getAttributes();
                    String widgetId = "";
                    Node idAttrNode = idInfoAttr.getNamedItem("widget_id");
                    if (idAttrNode != null) {
                        widgetId = idAttrNode.getNodeValue();
                    }
                    // Get widget info parcel (back up for case when app has
                    // been uninstalled and reinstalled)
                    NamedNodeMap parcelInfoAttr = widgetInfoNode.getAttributes();
                    String fileName = "";
                    Node fileAttrNode = parcelInfoAttr.getNamedItem("parcel");
                    if (fileAttrNode != null) {
                        fileName = fileAttrNode.getNodeValue();
                    }
                    Parcel parcel = Parcel.obtain();
                    try {
                        byte[] buffer = new byte[1024];
                        FileInputStream fis = new FileInputStream(fileName);
                        // Inflate widget info
                        int length = fis.read(buffer);
                        parcel.unmarshall(buffer, 0, length);
                        // read parcel file
                        parcel.setDataPosition(0);
                        // configParcel.setDataPosition(0);
                        widgetInfo = new AppWidgetProviderInfo(parcel);
                    } catch (Exception e) {
                        Log.e("MenuManager", "EXCEPTION: " + e);
                    }
                    // Load widget and get its new widget id (changes if
                    // reloaded after uninstall)
                    widgetId = context.loadAndroidWidget(widgetId, widgetInfo);
                    // Get widget index in holder
                    int newData = context.getAndroidWidgetKey();
                    menu.put(g, new MenuItem(label, action, Integer.toString(newData), widgetId));
                } else {
                    menu.put(g, new MenuItem(label, action, data, appInfo));
                }
            }
        }
        return menu;
    }

    /**
     * Check to see if application is installed.
     */
    private static boolean packageExists(Context context, AppInfo application) {
        PackageManager manager = context.getPackageManager();
        try {
            manager.getApplicationInfo(application.getPackageName(), 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Escape entities in text for XML.
     */
    public static String escapeEntities(String string) {
        return string.replaceAll("\"", "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
                .replaceAll("'", "&apos;").replaceAll("&", "&amp;");
    }
}
