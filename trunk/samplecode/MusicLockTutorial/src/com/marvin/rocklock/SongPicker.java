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

package com.marvin.rocklock;

/**
 * Interface for traversing through the songs on the device. Individual
 * implementations can handle traversal differently - for example, traversal
 * through tagged content vs directory structure vs playlists.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public interface SongPicker {

    public String peekNextArtist();

    public String peekPrevArtist();

    public String goNextArtist();

    public String goPrevArtist();

    public String peekNextAlbum();

    public String goNextAlbum();

    public String peekPrevAlbum();

    public String goPrevAlbum();

    public String peekNextTrack();

    public String goNextTrack();

    public String peekPrevTrack();

    public String goPrevTrack();

    public String getCurrentSongFile();

    public String getCurrentSongInfo();
}
