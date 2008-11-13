// Copyright 2008 Google Inc. All Rights Reserved.
package com.google.tts;

/**
 * Contains the information needed to access a sound resource; the name of the
 * package that contains the resource and the resID of the resource within that
 * package.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class SoundResource {
  public String sourcePackageName;
  public int resId;
  public String filename;

  public SoundResource(String packageName, int id) {
    sourcePackageName = packageName;
    resId = id;
    filename = null;
  }


  public SoundResource(String file) {
    sourcePackageName = null;
    resId = -1;
    filename = file;
  }
}
