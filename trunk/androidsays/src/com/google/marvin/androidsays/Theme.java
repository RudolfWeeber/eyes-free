// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.marvin.androidsays;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;


/**
 * Reads a .androidsays theme file and creates a theme object that is used to
 * apply that theme to the game interface.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class Theme {
  public String backgroundImg;

  public String touchedRedImg;
  public String touchedGreenImg;
  public String touchedBlueImg;
  public String touchedYellowImg;

  public String redImg;
  public String greenImg;
  public String blueImg;
  public String yellowImg;

  public String redSnd;
  public String greenSnd;
  public String blueSnd;
  public String yellowSnd;

  public Theme() {
    // Empty constructor
  }

  public boolean loadTheme(String fileUriString) {
    boolean loadedSuccessfully = false;
    try {
      FileInputStream fis = new FileInputStream(fileUriString);

      DocumentBuilder docBuild = DocumentBuilderFactory.newInstance().newDocumentBuilder();

      Document themeDoc = docBuild.parse(fis);
      Node root = themeDoc.getElementsByTagName("theme").item(0);

      // Go through the nodes, for each known type, if it exists, then apply the
      // skin.
      Node tempNode = themeDoc.getElementsByTagName("background_img").item(0);
      backgroundImg = tempNode.getAttributes().getNamedItem("src").getNodeValue();

      tempNode = themeDoc.getElementsByTagName("touched_red_img").item(0);
      touchedRedImg = tempNode.getAttributes().getNamedItem("src").getNodeValue();
      tempNode = themeDoc.getElementsByTagName("touched_green_img").item(0);
      touchedGreenImg = tempNode.getAttributes().getNamedItem("src").getNodeValue();
      tempNode = themeDoc.getElementsByTagName("touched_blue_img").item(0);
      touchedBlueImg = tempNode.getAttributes().getNamedItem("src").getNodeValue();
      tempNode = themeDoc.getElementsByTagName("touched_yellow_img").item(0);
      touchedYellowImg = tempNode.getAttributes().getNamedItem("src").getNodeValue();

      tempNode = themeDoc.getElementsByTagName("red_img").item(0);
      redImg = tempNode.getAttributes().getNamedItem("src").getNodeValue();
      tempNode = themeDoc.getElementsByTagName("green_img").item(0);
      greenImg = tempNode.getAttributes().getNamedItem("src").getNodeValue();
      tempNode = themeDoc.getElementsByTagName("blue_img").item(0);
      blueImg = tempNode.getAttributes().getNamedItem("src").getNodeValue();
      tempNode = themeDoc.getElementsByTagName("yellow_img").item(0);
      yellowImg = tempNode.getAttributes().getNamedItem("src").getNodeValue();

      tempNode = themeDoc.getElementsByTagName("red_snd").item(0);
      redSnd = tempNode.getAttributes().getNamedItem("src").getNodeValue();
      tempNode = themeDoc.getElementsByTagName("green_snd").item(0);
      greenSnd = tempNode.getAttributes().getNamedItem("src").getNodeValue();
      tempNode = themeDoc.getElementsByTagName("blue_snd").item(0);
      blueSnd = tempNode.getAttributes().getNamedItem("src").getNodeValue();
      tempNode = themeDoc.getElementsByTagName("yellow_snd").item(0);
      yellowSnd = tempNode.getAttributes().getNamedItem("src").getNodeValue();

      // This statement will not be reached if there were any exceptions
      loadedSuccessfully = true;
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
    return loadedSuccessfully;
  }


}
