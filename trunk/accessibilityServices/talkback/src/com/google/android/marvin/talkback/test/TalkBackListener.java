package com.google.android.marvin.mytalkback.test;

import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.mytalkback.Utterance;

public interface TalkBackListener {
    public void onAccessibilityEvent(AccessibilityEvent event);
    public void onUtteranceQueued(Utterance utterance);
}
