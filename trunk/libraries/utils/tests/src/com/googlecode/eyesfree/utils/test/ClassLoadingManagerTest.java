
package com.googlecode.eyesfree.utils.test;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.googlecode.eyesfree.utils.ClassLoadingManager;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * Tests for the {@link ClassLoadingManager}.
 */
public class ClassLoadingManagerTest extends AndroidTestCase {
    private static final String CLASS_STRING = "java.lang.String";

    private ClassLoadingManager mLoader;
    private int mCachedLogLevel;

    @Override
    public void setUp() {
        mCachedLogLevel = LogUtils.LOG_LEVEL;
        LogUtils.LOG_LEVEL = Log.VERBOSE;

        mLoader = ClassLoadingManager.getInstance();
        mLoader.onInfrastructureStateChange(mContext, true);
    }

    @Override
    public void tearDown() {
        mLoader.onInfrastructureStateChange(mContext, false);
        mLoader = null;

        LogUtils.LOG_LEVEL = mCachedLogLevel;
    }

    @SmallTest
    public void testLoadOrGetCachedClass() {
        final Class<?> stringClass = mLoader.loadOrGetCachedClass(
                mContext, CLASS_STRING, null);
        assertTrue("Load java.lang.String from framework", String.class.equals(stringClass));

        final Class<?> emptyClass = mLoader.loadOrGetCachedClass(mContext, "x.y.Z", null);
        assertNull("Fail to load x.y.Z", emptyClass);

        final String packageClassName = getClass().getName();
        final Class<?> packageClass = mLoader.loadOrGetCachedClass(
                mContext, packageClassName, null);
        assertTrue("Load " + packageClassName + " from default package",
                getClass().equals(packageClass));

        final String systemClassName = "com.android.settings.Settings";
        final String systemPackage = "com.android.settings";
        final Class<?> systemClass = mLoader.loadOrGetCachedClass(
                mContext, systemClassName, systemPackage);
        assertNotNull("Load " + systemClassName + " from system package", systemClass);
    }

    @SmallTest
    public void testCheckInstanceOfClass() {
        assertTrue("String is instance of String",
                mLoader.checkInstanceOf(mContext, CLASS_STRING, null, String.class));
        assertTrue("String is instance of CharSequence",
                mLoader.checkInstanceOf(mContext, CLASS_STRING, null, CharSequence.class));
        assertFalse("String is not instance of StringBuffer",
                mLoader.checkInstanceOf(mContext, CLASS_STRING, null, StringBuffer.class));
    }

    @SmallTest
    public void testCheckInstanceOfString() {
        assertTrue("String is instance of String",
                mLoader.checkInstanceOf(mContext, CLASS_STRING, null, CLASS_STRING));
        assertTrue("String is instance of CharSequence", mLoader.checkInstanceOf(
                mContext, CLASS_STRING, null, "java.lang.CharSequence"));
        assertFalse("String is not instance of StringBuffer", mLoader.checkInstanceOf(
                mContext, CLASS_STRING, null, "java.lang.StringBuffer"));
    }
}
