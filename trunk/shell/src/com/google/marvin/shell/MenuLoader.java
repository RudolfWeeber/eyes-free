package com.google.marvin.shell;

import com.google.marvin.shell.Param;
import com.google.marvin.widget.TouchGestureControlOverlay.Gesture;

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

public class MenuLoader {

  static public HashMap<Gesture, MenuItem> loadMenu(String filename) {
    HashMap<Gesture, MenuItem> menu = new HashMap<Gesture, MenuItem>();
    try {
      FileInputStream fis = new FileInputStream(filename);
      DocumentBuilder docBuild = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document doc = docBuild.parse(fis);

      NodeList items = doc.getElementsByTagName("item");
      for (int i = 0; i < items.getLength(); i++) {
        NamedNodeMap attribs = items.item(i).getAttributes();
        int gestInt = Integer.parseInt(attribs.getNamedItem("gesture").getNodeValue());
        Gesture g = intToGesture(gestInt);
        if (g != null) {
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

  static public Gesture intToGesture(int gestInt) {
    Gesture gest = null;
    switch (gestInt) {
      case 1:
        gest = Gesture.UPLEFT;
        break;
      case 2:
        gest = Gesture.UP;
        break;
      case 3:
        gest = Gesture.UPRIGHT;
        break;
      case 4:
        gest = Gesture.LEFT;
        break;
      case 6:
        gest = Gesture.RIGHT;
        break;
      case 7:
        gest = Gesture.DOWNLEFT;
        break;
      case 8:
        gest = Gesture.DOWN;
        break;
      case 9:
        gest = Gesture.DOWNRIGHT;
        break;
      default:
    }
    return gest;
  }

}
