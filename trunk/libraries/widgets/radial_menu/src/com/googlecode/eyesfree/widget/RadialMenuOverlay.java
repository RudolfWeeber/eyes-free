/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.widget;

import android.content.Context;
import android.content.DialogInterface;

/**
 * Implements a radial menu as a system overlay, and optionally uses a node
 * provider for accessibility. If a node provider is not used, the client is
 * responsible for handling speech output and related accessibility feedback.
 */
public class RadialMenuOverlay extends SimpleOverlay implements DialogInterface {
    private final RadialMenuView mMenuView;
    private final RadialMenu mMenu;

    /**
     * Constructs a new radial menu overlay, optionally using a node provider to
     * handle accessibility.
     *
     * @param context The parent context.
     * @param useNodeProvider {@code true} to use a node provider for
     *            accessibility.
     */
    public RadialMenuOverlay(Context context, boolean useNodeProvider) {
        super(context);

        mMenu = new RadialMenu(context, this);
        mMenuView = new RadialMenuView(context, mMenu, useNodeProvider);

        setContentView(mMenuView);
    }

    public void showWithDot() {
        super.show();

        mMenuView.displayDot();
    }

    public void showAt(float centerX, float centerY) {
        super.show();

        mMenuView.displayAt(centerX, centerY);
    }

    public RadialMenu getMenu() {
        return mMenu;
    }

    @Override
    public void cancel() {
        hide();
    }

    @Override
    public void dismiss() {
        hide();
    }
}
