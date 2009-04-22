/*
 * Copyright (C) 2008 Google Inc.
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

/**
 * Holds the information for an application in the shell that is needed to start
 * that application from the shell.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class MenuItem {
  public String label;
  public String action;
  public String data;
  public AppEntry appInfo;

  public MenuItem(String itemLabel, String itemAction, String itemData, AppEntry applicationInfo) {
    label = itemLabel;
    action = itemAction;
    data = itemData;
    appInfo = applicationInfo;
  }
}
