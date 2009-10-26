/*
 * Copyright (C) 2009 Google Inc.
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
package com.android.ocr.spellcheck;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Stack;

/**
 * Provides basic spell-checking.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class SpellCheck {
  @SuppressWarnings("unused")
  private static final String TAG = "SpellCheck";

  // Minimum length of a word that we will bother spellchecking
  private static final int MINIMUM_LENGTH = 3;
  // Default threshold for edit distance
  private static final int EDIT_THRESHOLD = 1;

  private Lexicon mLexicon;

  private class Pair<E, F> {
    public E e;
    public F f;

    public Pair(E e, F f) {
      this.e = e;
      this.f = f;
    }
  }

  public SpellCheck(File file, File custom) throws IOException {
    mLexicon = new Lexicon(file, custom);
  }

  public String autoCorrect(String string) {
    Log.i(TAG, "Received: " + string);
    Stack<String> stack = new Stack<String>();
    StringBuffer output = new StringBuffer(string.length());

    String[] strings = string.split("\\s+");
    for (int i = strings.length - 1; i >= 0; i--) {
      stack.push(strings[i]);
    }

    String suggest, word;

    while (!stack.empty()) {
      word = stack.pop();

      if (isWordPreservedBefore(word)) {
        output.append(word);
        output.append(' ');
        continue;
      }

      strings = word.replaceAll("\\|", "l").replaceAll("\\W", " ").split("\\s+");
      for (int i = strings.length - 1; i >= 0; i--) {
        stack.push(strings[i]);
      }

      if (strings.length == 0) continue;

      word = stack.pop();

      if (isWordPreservedAfter(word)) {
        output.append(word);
        output.append(' ');
        continue;
      }

      suggest = getSuggestion(word, EDIT_THRESHOLD);

      if (suggest != null) {
        output.append(suggest);
        output.append(' ');
      }
    }

    return output.toString();
  }

  private boolean isWordPreservedBefore(String word) {
    if (word.contains("http")) return true;
    if (word.contains("www\\.")) return true;
    if (word.matches("^\\w+@\\w+(i:\\.[a-z]{2,4})+$")) return true;
    return false;
  }

  private boolean isWordPreservedAfter(String word) {
    if (word.matches("^[A-Z][a-z]{2,}$")) return true;
    if (word.matches("^\\d+$")) return true;
    if (word.matches("^\\d+(?-i:th|rd|st|nd)$")) return true;
    return false;
  }

  public String getSuggestion(String word, int threshold) {
    Pair<String, Integer> suggestion = new Pair<String, Integer>("", -1);

    word = word.toLowerCase();

    recurseOnString(suggestion, word, threshold);

    if (suggestion.f < 0) {
      return null;
    } else {
      return suggestion.e;
    }
  }

  public void recurseOnString(Pair<String, Integer> suggestion, String word, int depth) {
    if (mLexicon.containsWord(word)) {
      if (suggestion.f < depth) {
        suggestion.e = word;
        suggestion.f = depth;
      }
      return;
    }

    if (depth == 0 || word.length() < MINIMUM_LENGTH) {
      return;
    }

    String modified;

    // Deletion
    for (int i = 0; i < word.length(); i++) {
      modified = word.substring(0, i) + word.substring(i + 1);
      recurseOnString(suggestion, modified, depth - 1);
    }

    // Insertion
    for (int i = 0; i <= word.length(); i++) {
      for (char j = 'a'; j <= 'z'; j++) {
        modified = word.substring(0, i) + j + word.substring(i);
        recurseOnString(suggestion, modified, depth - 1);
      }
    }

    // Replacement
    for (int i = 0; i <= word.length(); i++) {
      for (char j = 'a'; j <= 'z'; j++) {
        modified = word.substring(0, i) + j + (i < word.length() ? word.substring(i + 1) : "");
        recurseOnString(suggestion, modified, depth - 1);
      }
    }
  }

}
