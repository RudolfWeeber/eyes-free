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

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Utility class for loading a menu from a file
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
final public class MenuLoader {

    private MenuLoader() {
    }

    public static HashMap<Integer, MenuItem> loadMenu(String filename) {
        HashMap<Integer, MenuItem> menu = new HashMap<Integer, MenuItem>();
        try {
            FileInputStream fis = new FileInputStream(filename);
            DocumentBuilder docBuild = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = docBuild.parse(fis);

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                NamedNodeMap attribs = items.item(i).getAttributes();
                int g = Integer.parseInt(attribs.getNamedItem("gesture").getNodeValue());

                String label = attribs.getNamedItem("label").getNodeValue();
                String action = attribs.getNamedItem("action").getNodeValue();
                String data = null;
                Node dataAttrNode = attribs.getNamedItem("data");
                if (dataAttrNode != null) {
                    data = dataAttrNode.getNodeValue();
                }
                AppEntry appInfo = null;
                if (action.equalsIgnoreCase("launch") || action.equalsIgnoreCase("ase")) {
                    Node appInfoNode = null;
                    ArrayList<Param> params = new ArrayList<Param>();
                    NodeList nodes = items.item(i).getChildNodes();
                    for (int j = 0; j < nodes.getLength(); j++) {
                        Node currentNode = nodes.item(j);
                        String tagName = currentNode.getNodeName();
                        // Only process actual nodes
                        if (tagName != null) {
                            if (tagName.equalsIgnoreCase("appInfo")) {
                                appInfoNode = currentNode;
                            } else if (tagName.equalsIgnoreCase("param")) {
                                NamedNodeMap paramAttr = currentNode.getAttributes();
                                Param param = new Param();
                                param.name = paramAttr.getNamedItem("name").getNodeValue();
                                param.value = paramAttr.getNamedItem("value").getNodeValue();
                                params.add(param);
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
                    String scriptName = "";
                    Node scriptAttrNode = appInfoAttr.getNamedItem("script");
                    if (scriptAttrNode != null) {
                        scriptName = scriptAttrNode.getNodeValue();
                    }
                    appInfo = new AppEntry(null, packageName, className, scriptName, null, params);
                }

                menu.put(g, new MenuItem(label, action, data, appInfo));
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (FactoryConfigurationError e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return menu;
    }
}
