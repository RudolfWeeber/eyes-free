/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.google.android.marvin.talkback;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.util.Log;

import com.google.android.marvin.utils.MappedSoundPool;
import com.google.android.marvin.utils.MappedVibrator;
import com.googlecode.eyesfree.utils.LogUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Parses XML feedback themes and populates the results into mapped sound pool
 * and vibrator objects.
 */
class MappedThemeLoader {
    /** Standard XML namespace separator. */
    private static final String XML_NAMESPACE_SEPARATOR = ":";

    /**
     * Prefix used to map user-specified haptic resource names to internal
     * resource names.
     */
    private static final String PREFIX_HAPTIC = "patterns_";

    /**
     * Prefix used to map user-specified auditory resource names to internal
     * resource names.
     */
    private static final String PREFIX_AUDITORY = "sounds_";

    /** Element used to assign auditory feedback items. */
    private static final String TYPE_AUDITORY = "auditory";

    /** Element used to assign haptic feedback items. */
    private static final String TYPE_HAPTIC = "haptic";

    /**
     * Optional element used inside {@link #TYPE_AUDITORY} and
     * {@link #TYPE_HAPTIC} elements to assign a feedback item defined by a
     * haptic or auditory resource.
     */
    private static final String TYPE_RESOURCE = "resource";

    /**
     * Attribute for {@link #TYPE_AUDITORY} and {@link #TYPE_HAPTIC} elements
     * that specifies the haptic or auditory resource being assigned.
     */
    private static final String ATTR_ID = "id";

    /**
     * Optional attribute for {@link #TYPE_AUDITORY} elements that specifies the
     * stream on which to play the auditory feedback.
     */
    private static final String ATTR_STREAM = "stream";

    /**
     * Value for {@link #ATTR_STREAM} attribute used to specify the
     * {@link AudioManager#STREAM_MUSIC} stream.
     */
    private static final String ATTR_STREAM_VALUE_MUSIC = "music";

    /**
     * Value for {@link #ATTR_STREAM} attribute used to specify the
     * {@link AudioManager#STREAM_RING} stream.
     */
    private static final String ATTR_STREAM_VALUE_RING = "ring";

    private final Context mContext;
    private final MappedSoundPool mSoundPool;
    private final MappedVibrator mVibrator;

    /**
     * Creates a new mapped theme loader that loads sounds into the specified
     * sound pool and vibrator.
     *
     * @param context The parent context.
     * @param soundPool The sound pool into which auditory-type items should be
     *            loaded.
     * @param vibrator The vibrator into which haptic-type items should be
     *            loaded.
     */
    public MappedThemeLoader(
            Context context, MappedSoundPool soundPool, MappedVibrator vibrator) {
        mContext = context;
        mSoundPool = soundPool;
        mVibrator = vibrator;
    }

    /**
     * Loads a theme from the specifed XML resource.
     *
     * @param context The parent context.
     * @param resId The resource identifier for the XML theme.
     */
    public void loadTheme(Context context, int resId) {
        try {
            final Resources res = context.getResources();
            final InputStream inputStream = res.openRawResource(resId);
            final DocumentBuilder builder = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder();
            final Document document = builder.parse(inputStream);
            inputStream.close();

            final Element feedbackTheme = document.getDocumentElement();
            parseFeedbackTheme(feedbackTheme);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Parses the top-level XML element for a feedback theme.
     *
     * @param feedbackTheme The top-level XML element for a feedback theme.
     */
    private void parseFeedbackTheme(Element feedbackTheme) {
        final NodeList childNodes = feedbackTheme.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            final Node childNode = childNodes.item(i);
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            final String name = getUnqualifiedNodeName(childNode);
            if (TYPE_AUDITORY.equals(name)) {
                parseAuditory(childNode);
            } else if (TYPE_HAPTIC.equals(name)) {
                parseHaptic(childNode);
            } else {
                LogUtils.log(this, Log.ERROR, "Unknown node type: %s", name);
            }
        }
    }

    /**
     * Parses an auditory-type XML node.
     *
     * @param auditoryNode The node for an auditory-type item.
     */
    private void parseAuditory(Node auditoryNode) {
        final NamedNodeMap attributes = auditoryNode.getAttributes();
        final int id = parseIdFromNodeMap(attributes, ATTR_ID, PREFIX_AUDITORY, 0);
        if (id == 0) {
            LogUtils.log(this, Log.ERROR, "Missing resource id for auditory item %s", attributes);
            return;
        }

        final int streamType = parseStreamFromNodeMap(
                attributes, ATTR_STREAM, MappedSoundPool.DEFAULT_STREAM_TYPE);
        final NodeList childNodes = auditoryNode.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            final Node childNode = childNodes.item(i);
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            final String name = getUnqualifiedNodeName(childNode);
            if (TYPE_RESOURCE.equals(name)) {
                final String value = childNode.getTextContent();
                final Resources res = mContext.getResources();
                final int resId = res.getIdentifier(value, null, mContext.getPackageName());
                if (resId == 0) {
                    LogUtils.log(
                            this, Log.ERROR, "Missing resource id for auditory item %s", value);
                    continue;
                }

                mSoundPool.load(id, resId, streamType);
            } else {
                LogUtils.log(this, Log.ERROR, "Unknown node type: %s", name);
            }
        }
    }

    /**
     * Parses a resource identifier from a named node in a node map. Resources
     * are mapped as {@code prefix}{@code name} in the current package.f
     *
     * @param nodeMap A node map containing named nodes.
     * @param name The name of the node to parse.
     * @param prefix The prefix to add to the node name before resolving it to
     *            an identifier.
     * @param defaultValue The default value to return if the named node is not
     *            found.
     * @return The resource identifier, or {@code defaultValue} if not found.
     */
    private int parseIdFromNodeMap(
            NamedNodeMap nodeMap, String name, String prefix, int defaultValue) {
        final Node node = nodeMap.getNamedItem(name);
        if (node == null) {
            return defaultValue;
        }

        final String value = prefix + node.getNodeValue();
        final Resources res = mContext.getResources();
        final int id = res.getIdentifier(value, ATTR_ID, mContext.getPackageName());
        if (id == 0) {
            return defaultValue;
        }

        return id;
    }

    /**
     * Parses a stream identifier from a named node in a node map.
     *
     * @param nodeMap A node map containing named nodes.
     * @param name The name of the node to parse.
     * @param defaultValue The default value to return if the named node is not
     *            found.
     * @return The stream identifier, or {@code defaultValue} if not found.
     *         Possible return values include:
     *         <ul>
     *         <li>{@link AudioManager#STREAM_MUSIC}
     *         <li>{@link AudioManager#STREAM_RING}
     *         </ul>
     */
    private static int parseStreamFromNodeMap(NamedNodeMap nodeMap, String name, int defaultValue) {
        final Node node = nodeMap.getNamedItem(name);
        if (node == null) {
            return defaultValue;
        }

        final String value = node.getNodeValue().toLowerCase();
        if (ATTR_STREAM_VALUE_MUSIC.equals(value)) {
            return AudioManager.STREAM_MUSIC;
        } else if (ATTR_STREAM_VALUE_RING.equals(value)) {
            return AudioManager.STREAM_RING;
        } else {
            return defaultValue;
        }
    }

    /**
     * Parses a haptic-type XML node.
     *
     * @param hapticNode The node for a haptic-type item.
     */
    private void parseHaptic(Node hapticNode) {
        final NamedNodeMap attributes = hapticNode.getAttributes();
        final int id = parseIdFromNodeMap(attributes, ATTR_ID, PREFIX_HAPTIC, 0);
        if (id == 0) {
            LogUtils.log(this, Log.ERROR, "Missing resource id for haptic item %s", attributes);
            return;
        }

        final NodeList childNodes = hapticNode.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            final Node childNode = childNodes.item(i);
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            final String name = getUnqualifiedNodeName(childNode);
            if (TYPE_RESOURCE.equals(name)) {
                final String value = childNode.getTextContent();
                final Resources res = mContext.getResources();
                final int resId = res.getIdentifier(value, null, mContext.getPackageName());
                if (resId == 0) {
                    LogUtils.log(this, Log.ERROR, "Missing resource id for haptic item %s", value);
                    continue;
                }

                mVibrator.load(id, resId);
            } else {
                LogUtils.log(this, Log.ERROR, "Unknown node type: %s", name);
            }
        }
    }

    /**
     * Returns the unqualified <code>node</code> name i.e. without the prefix.
     *
     * @param node The node.
     * @return The unqualified name.
     */
    private static String getUnqualifiedNodeName(Node node) {
        final String nodeName = node.getNodeName();
        final int colonIndex = nodeName.indexOf(XML_NAMESPACE_SEPARATOR);
        if (colonIndex > -1) {
            return nodeName.substring(colonIndex + 1);
        }

        return nodeName;
    }
}
