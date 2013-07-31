package com.google.android.marvin.talkback.test;

import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.Utterance;

public interface TalkBackListener {
    public void onAccessibilityEvent(AccessibilityEvent event);
    public void onUtteranceQueued(Utterance utterance);
}