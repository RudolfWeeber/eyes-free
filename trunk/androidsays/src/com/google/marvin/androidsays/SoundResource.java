// Copyright 2008 Google Inc. All Rights Reserved.
package com.google.marvin.androidsays;

/**
 * Contains the information needed to access a sound resource; the name of the
 * package that contains the resource and the resID of the resource within that
 * package.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class SoundResource {
  public int resId;
  public String filename;

  public SoundResource(int id) {
    resId = id;
    filename = null;
  }


  public SoundResource(String file) {
    resId = -1;
    filename = file;
  }
}
