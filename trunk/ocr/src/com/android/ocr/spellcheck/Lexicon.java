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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.TreeSet;

/**
 * This class implements a dictionary backed by a directed acyclic word graph.
 * It also supports adding additional words from a plain-text file.
 * 
 * This class is based on the Lexicon presented in Stanford's CS 106X class by
 * Julie Zelenski.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class Lexicon {
  private TreeSet<String> userWords;
  private int[] edges;
  private int start;
  private int numEdges;
  private int wordCount;

  /**
   * This class provides accessors for a 32-bit integer edge of a DAWG. Each
   * edge is formatted as follows: 24 bits index of first child 1 bit (unused) 1
   * bit is word ending point 1 bit is last edge in child row 5 bits letter
   * indexed 1 through 26
   */
  private static class Edge {
    public static int getFirstChildIndex(int data) {
      return (data >> 8);
    }

    public static boolean getExtraBit(int data) {
      return (((data >> 7) & 0x01) == 1);
    }

    public static boolean isWord(int data) {
      return (((data >> 6) & 0x01) == 1);
    }

    public static boolean isLastEdge(int data) {
      return (((data >> 5) & 0x01) == 1);
    }

    public static char getLetter(int data) {
      return (char) ('a' + (data & 0x1F) - 1);
    }
  }

  /**
   * Creates a new, empty Lexicon.
   */
  public Lexicon() {
    edges = null;
    start = -1;
    numEdges = 0;
    wordCount = 0;

    userWords = new TreeSet<String>();
  }

  /**
   * Created a Lexicon and adds DAWG-packed words from a binary file and user
   * words from a plain-text file.
   * 
   * @param packedWords binary file containing DAWG-packed words
   * @param userWords plain-text file containing user words
   * @throws IOException
   */
  public Lexicon(File packedWords, File userWords) throws IOException {
    this();

    if (packedWords != null) {
      addPackedWords(packedWords);
    }

    if (userWords != null) {
      addUserWords(userWords);
    }
  }

  /**
   * Reads packed DAWG (directed acyclic word graph) data into the Lexicon. The
   * input file must be in the following format:
   * 
   * DAWG:<startnode index>:<num bytes>:<num bytes block of edge data>:<data>
   * 
   * @param file the file to add from
   * @throws IOException
   */
  public void addPackedWords(File file) throws IOException {
    RandomAccessFile f = new RandomAccessFile(file, "r");

    byte[] expected = new byte[] {'D', 'A', 'W', 'G'};
    byte[] firstFour = new byte[4];

    f.read(firstFour);

    int startIndex;
    long numBytes;
    int nextInteger;
    byte[] aryStartIndex = new byte[5];
    byte[] aryNumBytes = new byte[6];

    f.skipBytes(1); // skip first colon
    f.read(aryStartIndex);
    f.skipBytes(1); // skip second colon
    f.read(aryNumBytes);
    f.skipBytes(1); // skip third colon

    startIndex = Integer.parseInt(new String(aryStartIndex));
    numBytes = Integer.parseInt(new String(aryNumBytes));

    if (!Arrays.equals(firstFour, expected) || startIndex < 0 || numBytes < 0) {
      throw new IOException("Improperly formed lexicon file");
    }

    numEdges = (int) (numBytes / 4);
    edges = new int[numEdges];

    for (int i = 0; i < numEdges; i++) {
      edges[i] = f.readInt();

      if (Edge.isWord(edges[i])) {
        wordCount++;
      }
    }

    start = startIndex;

    f.close();

    wordCount = -1;
  }

  /**
   * Reads plain-text words from a text file. Each word must be on a separate
   * line.
   * 
   * @param file the file to read words from
   * @throws IOException
   */
  public void addUserWords(File file) throws IOException {
    RandomAccessFile f = new RandomAccessFile(file, "r");

    String line;
    while ((line = f.readLine()) != null) {
      add(line);
    }

    f.close();
  }

  public int size() {
    return wordCount + userWords.size();
  }

  public boolean isEmpty() {
    return size() == 0;
  }


  public void clear() {
    edges = null;
    start = -1;
    numEdges = 0;
    wordCount = 0;
    userWords.clear();
  }

  /**
   * Attempts to find the index of a given character within a row of child
   * edges. Returns -1 if not found.
   * 
   * @param children the index of the first edge in a child row
   * @param chr the character to search for
   * @return edge index for chr, or -1 if not found
   */
  private int findEdgeForChar(int children, char chr) {
    int curEdge = children;
    while (true) {
      if (Edge.getLetter(edges[curEdge]) == chr) {
        return curEdge;
      } else if (Edge.isLastEdge(edges[curEdge])) {
        return -1;
      }
      curEdge++;
    }
  }

  /**
   * Attempts to trace a string through the DAWG. Returns the index of the last
   * character of the word or -1 if not found.
   * 
   * @param str the string to trace
   * @return the index of the last character in the word, or -1 if not found
   */
  private int traceToLastEdge(String str) {
    if (start < 1 || str == null || str.length() == 0) {
      return 0;
    }

    int curEdge = findEdgeForChar(start, str.charAt(0));

    for (int i = 1; i < str.length() && curEdge >= 0; i++) {
      int firstChild = Edge.getFirstChildIndex(edges[curEdge]);

      if (firstChild >= 0) {
        curEdge = findEdgeForChar(firstChild, str.charAt(i));
      } else {
        curEdge = -1;
      }
    }

    return curEdge;
  }

  /**
   * Returns true if word exists in the DAWG or user word list.
   * 
   * @param word
   * @return true if word exists in the DAWG or user word list
   */
  public boolean containsWord(String word) {
    int lastEdge = traceToLastEdge(word);

    if (lastEdge >= 0 && Edge.isWord(edges[lastEdge])) {
      return true;
    }

    return userWords.contains(word.toLowerCase());
  }

  /**
   * Adds a word to the user list if it doesn't already exist in the DAWG.
   * 
   * @param word
   */
  public void add(String word) {
    if (!containsWord(word)) {
      userWords.add(word.toLowerCase());
    }
  }
}
