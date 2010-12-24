/*
 * Copyright (C) 2010 The IDEAL Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ideal.textenlarger;


import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Class needed by ApplicationsListActivity.
 *
 * This was taken from a checkbox list tutorial at anddev.org:
 * http://www.anddev.org/extended_checkbox_list__extension_of_checkbox_text_list_tu-t5734.html
 */
public class ExtendedCheckBoxListView extends LinearLayout {
    
    private TextView mText;
    private CheckBox mCheckBox;
    private ExtendedCheckBox mCheckBoxText;
    
    public ExtendedCheckBoxListView(final Context context, ExtendedCheckBox aCheckBoxifiedText) {
         super(context);
         
         // Set orientation to be horizontal
         this.setOrientation(HORIZONTAL);
         
         mCheckBoxText = aCheckBoxifiedText;
         mCheckBox = new CheckBox(context);
         mCheckBox.setPadding(0, 0, 20, 0);
         
         // Set the initial state of the checkbox.
         mCheckBox.setChecked(aCheckBoxifiedText.getChecked());
         
         // Set the right listener for the checkbox, used to update
         // our data holder to change it's state after a click too
         mCheckBox.setOnClickListener( new OnClickListener()
         {
         	/**
         	 *  When clicked change the state of the 'mCheckBoxText' too!
         	 */
			public void onClick(View v) {
				mCheckBoxText.setChecked(getCheckBoxState());
				((ApplicationsListActivity) context).setCheckedState(mText.getText().toString(), getCheckBoxState());
			}
         });         
         
         // Add the checkbox
         addView(mCheckBox,  new LinearLayout.LayoutParams(
                   android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
         
         mText = new TextView(context);
         mText.setText(aCheckBoxifiedText.getText());;
         addView(mText, new LinearLayout.LayoutParams(
                   android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
         
         // Remove some controls in order to prevent a strange flickering when clicking on the TextView!
         mText.setClickable(false);
         mText.setFocusable(false);
         mText.setFocusableInTouchMode(false);

         setOnClickListener( new OnClickListener()
         {
        	// Check or unchecked the current checkbox!        	
			public void onClick(View v) {
				toggleCheckBoxState();
				((ApplicationsListActivity) context).setCheckedState(mText.getText().toString(), getCheckBoxState());
			}

         });    
    }
    
    public void setText(String words) {
         mText.setText(words);
    }
    
    public void toggleCheckBoxState()
    {
    	setCheckBoxState(!getCheckBoxState());
    }
    
    public void setCheckBoxState(boolean bool)
    {
    	mCheckBox.setChecked(bool);
    	mCheckBoxText.setChecked(bool);
    }
    
    public boolean getCheckBoxState()
    {
    	return mCheckBox.isChecked();
    }
}
