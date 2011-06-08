// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.talkback.formatter;


import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import junit.framework.TestCase;

/**
 * @author credo@google.com (Tim Credo)
 */
public class WebContentHandlerTest extends TestCase {

    private static final String XML_TEMPLATE =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><div>%s</div>";
    
    HashMap<String, String> htmlInputMap;

    HashMap<String, String> htmlRoleMap;

    HashMap<String, String> htmlTagMap;

    WebContentHandler mHandler;

    SAXParser mParser;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        mParser = factory.newSAXParser();
        HashMap<String, String> htmlInputMap = new HashMap<String, String>();
        htmlInputMap.put("button", "Button");
        htmlInputMap.put("text", "Edit text");
        htmlInputMap.put("checkbox", "Check box");
        htmlInputMap.put("radio", "Radio button");
        HashMap<String, String> htmlRoleMap = new HashMap<String, String>();
        htmlRoleMap.put("checkbox", "Check box");
        htmlRoleMap.put("link", "Link");
        HashMap<String, String> htmlTagMap = new HashMap<String, String>();
        htmlTagMap.put("a", "Link");
        htmlTagMap.put("button", "Button");
        htmlTagMap.put("h2", "Heading two");
        htmlTagMap.put("textarea", "Text area");
        mHandler = new WebContentHandler(htmlInputMap, htmlRoleMap, htmlTagMap);
    }

    /**
     * A helper function to make it easy to parse strings with the handler.
     */
    public void parse(String xmlString) throws SAXException, IOException {
        String xml = String.format(XML_TEMPLATE,xmlString);
        mParser.parse(new ByteArrayInputStream(xml.getBytes()), mHandler);
    }
    
    /**
     * Parsing an unknown tag should cause the handler to ignore it when
     * producing output.
     */
    public void testUnknownTag() throws Exception {
        parse("<unknownTag>Foo</unknownTag>");
        assertEquals("Foo", mHandler.getOutput());
    }

    /**
     * Content should have a space inserted if necessary to preserve separation
     * after tags are removed.
     */
    public void testFixWhitespace() throws Exception {
        parse("<tag>Hello,</tag><tag>world!</tag>");
        assertEquals("Hello, world!", mHandler.getOutput());
        parse("<tag>Hello,</tag> <tag>world!</tag>");
        assertEquals("Hello, world!", mHandler.getOutput());
    }

    /**
     * Input type should be spoken after the content of the element.
     */
    public void testInputType() throws Exception {
        parse("<input type=\"button\"/>");
        assertEquals("Button", mHandler.getOutput());
    }

    /**
     * ARIA roles should be spoken after the content of the element.
     */
    public void testAriaRole() throws Exception {
        parse("<span role=\"link\">Foo</span>");
        assertEquals("Foo Link", mHandler.getOutput());
    }

    /**
     * Tags appearing in the tag map should be described to the user.
     */
    public void testTagMap() throws Exception {
        parse("<h2>Foo</h2> <a>Bar</a>");
        assertEquals("Foo Heading two Bar Link", mHandler.getOutput());
    }

    /**
     * For most input types, the value of the element should be spoken. There
     * are two exceptions which are tested below.
     */
    public void testInputValue() throws Exception {
        parse("<input type=\"button\" value=\"Search\"/>");
        assertEquals("Search Button", mHandler.getOutput());
        parse("<textarea value=\"Content here\"/>");
        assertEquals("Content here Text area", mHandler.getOutput());
    }

    /**
     * The value attribute of a checkbox input element should not be spoken.
     */
    public void testCheckboxValue() throws Exception {
        parse("<input type=\"checkbox\" value=\"do not speak\"/>");
        assertEquals("Check box", mHandler.getOutput());
    }

    /**
     * The value attribute of a radio input element should not be spoken.
     */
    public void testRadioValue() throws Exception {
        parse("<input type=\"radio\" value=\"do not speak\"/>");
        assertEquals("Radio button", mHandler.getOutput());
    }

}
