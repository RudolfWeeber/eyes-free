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

import android.content.pm.ResolveInfo;

/**
 * Class for encapsulating the information needed to start up an application
 *
 * @author clchen@google.com (Charles L. Chen)
 * @author credo@google.com (Tim Credo)
 */
public class AppInfo implements Comparable<AppInfo> {
    private String title;

    private String packageName;

    private String className;

    AppInfo(String appTitle, String appPackageName, String appClassName) {
        title = appTitle;
        packageName = appPackageName;
        className = appClassName;
    }

    AppInfo(String appTitle, ResolveInfo info) {
        title = appTitle;
        packageName = info.activityInfo.packageName;
        className = info.activityInfo.name;
    }

    public String getTitle() {
        return title;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public int compareTo(AppInfo o) {
        String title0 = this.getTitle().toLowerCase();
        String title1 = o.getTitle().toLowerCase();
        return title0.compareTo(title1);
    }

    /**
     * Returns a String xml representation of this appInfo element.
     *
     * @return String xml representation of this appInfo object
     */
    public String toXml() {
        StringBuilder xmlBuilder = new StringBuilder();
        xmlBuilder.append("<appInfo");
        if (addToXml(getPackageName())) {
            xmlBuilder.append(" package='" + MenuManager.escapeEntities(getPackageName()) + "'");
        }
        if (addToXml(getClassName())) {
            xmlBuilder.append(" class='" + MenuManager.escapeEntities(getClassName()) + "'");
        }
        xmlBuilder.append("/>\n");
        return xmlBuilder.toString();
    }

    private boolean addToXml(String str) {
        return ((null != str) && (str.length() > 0));
    }
}
