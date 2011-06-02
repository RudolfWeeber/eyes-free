/*
 * Copyright 2010 Google Inc.
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

package com.android.accessibility;

import junit.framework.TestCase;

import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import java.io.File;

/**
 * Unit tests for an AccessibilityValidationContentHandler object
 * 
 * @author dtseng@google.com (David Tseng)
 */
public class AccessibilityValidationContentHandlerTest extends TestCase {
    private AccessibilityValidationContentHandler mObjectUnderTest;
    private Locator mLocator = new LocatorImpl();

    private static final String FILE_PATH_PROPERTY = "filePath";
    private static final String ANDROID_PATH_PROPERTY = "androidSdkPath";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mObjectUnderTest = new AccessibilityValidationContentHandler(
                System.getProperty(FILE_PATH_PROPERTY), new File(
                        System.getProperty(ANDROID_PATH_PROPERTY)));
        mObjectUnderTest.setDocumentLocator(mLocator);
    }

    public void testStartElement_withNoContentDescriptionAttributeHasOneValidationError()
            throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "testing", "", "", "");
        mObjectUnderTest.startElement("", "ImageView", "", atts);
        assertTrue(mObjectUnderTest.getValidationErrors() == 1);
    }

    public void testStartElement_withContentDescriptionAttributeHasNoValidationErrors()
            throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "contentDescription", "", "", "");
        mObjectUnderTest.startElement("", "ImageView", "", atts);
        assertTrue(mObjectUnderTest.getValidationErrors() == 0);
    }

    public void testStartElement_withMultipleAttributesIncludingContentDescriptionAttributeHasNoValidationErrors()
            throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "contentDescription", "", "", "");
        atts.addAttribute("", "testing", "", "", "");
        atts.addAttribute("", "testing2", "", "", "");
        atts.addAttribute("", "contentDescription", "", "", "");
        mObjectUnderTest.startElement("", "ImageView", "", atts);
        assertTrue(mObjectUnderTest.getValidationErrors() == 0);
    }

    public void testStartElement_caseInsensitivityForContentDescriptionAttribute()
            throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "ContentDescription", "", "", "");
        mObjectUnderTest.startElement("", "ImageView", "", atts);
        assertTrue(mObjectUnderTest.getValidationErrors() == 0);
    }

    public void testStartElement_withNoAttributesResultsInNoExceptions()
            throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        mObjectUnderTest.startElement("", "ImageView", "", atts);
        // passes if there's no exception thrown
    }
}
