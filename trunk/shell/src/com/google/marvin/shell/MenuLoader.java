package com.google.marvin.shell;

import com.google.marvin.shell.TouchGestureControlOverlay.Gesture;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
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
          String data = attribs.getNamedItem("data").getNodeValue();
          menu.put(g, new MenuItem(label, action, data));
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
