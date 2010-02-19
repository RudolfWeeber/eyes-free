
package com.marvin.rocklock;

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
