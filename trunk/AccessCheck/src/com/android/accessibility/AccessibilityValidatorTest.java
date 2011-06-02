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

import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Unit tests for the AccessibilityValidator object.
 * 
 * @author dtseng@google.com (David Tseng)
 */
public class AccessibilityValidatorTest extends TestCase {
    private AccessibilityValidator mObjectUnderTest;
    private String mMockFile = "";
    private String mAndroidSdkPath;
    private static final String ANDROID_FILE_PATH_PROPERTY = "androidSdkPath";
    private static final Logger sLogger =
            Logger.getLogger("android.accessibility.test");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAndroidSdkPath = System.getProperty(ANDROID_FILE_PATH_PROPERTY);
        sLogger.info(mAndroidSdkPath + " set as android sdk path");
    }

    /* Positive unit tests */
    public void testRun_validationOnValidPathAndValidXmlFile() {
        mMockFile = "<html>well formed</html>";
        assertTrue(mockValidateFiles("./"));
        assertTrue(mockValidateFiles("/tmp"));
    }

    public void testRun_onXmlLayoutFileWithNoValidationErrors() {
        mMockFile = "<ImageView anroid=\"value1\" "
                + "androidAi=\"value2\" contentDescription=\"hi\" />";
        assertTrue(mockValidateFiles("./"));
        assertTrue(mObjectUnderTest.getTotalValidationErrors() == 0);
    }

    /* negative unit tests */
    public void testRun_onBadFilePath() {
        assertFalse(mockValidateFiles(""));
        assertFalse(mockValidateFiles(null));
    }

    public void testRun_onBadXmlLayoutFile() {
        mMockFile = "Not well formed";
        assertFalse(mockValidateFiles("~/"));
    }

    public void testRun_onXmlLayoutFileWithValidationErrors() {
        mMockFile = "<ImageView anroid=\"value1\" androidAi=\"value2\" />";
        assertTrue(mockValidateFiles("./"));
        assertTrue(mObjectUnderTest.getTotalValidationErrors() == 1);
    }

    public void testRun_onXmlLayoutWithTwoValidationErrors() {
        mMockFile = "<html>"
                + "<ImageView anroid=\"value1\" androidAi=\"value2\" />"
                + "<ImageView asdf=\"a\" />"
                + "<ImageView contentDescription=\"a\" />" + "</html>";
        assertTrue(mockValidateFiles("./"));
        assertTrue(mObjectUnderTest.getTotalValidationErrors() == 2);
    }

    /**
     * returns whether run has no general errors. General errors are caught
     * exceptions. We also look for NullPointerException and
     * IllegalArgumentExceptions as uncaught exceptions. We want the program to
     * crash in these cases.
     */
    private boolean mockValidateFiles(String filePath) {
        try {
            mObjectUnderTest = new AccessibilityValidator(filePath,
                    mAndroidSdkPath);
        } catch (IllegalArgumentException iAException) {
            return false;
        } catch (NullPointerException npExcepption) {
            return false;
        }

        mObjectUnderTest.setLayoutFiles(new ArrayList<InputSource>());
        mObjectUnderTest.getLayoutFiles().add(
                new InputSource(new StringReader(mMockFile)));
        mObjectUnderTest.validateFiles();
        for (String msg : mObjectUnderTest.getGeneralErrors()) {
            sLogger.info("Caught error: " + msg);
        }

        return mObjectUnderTest.getGeneralErrors().size() == 0;
    }
}
