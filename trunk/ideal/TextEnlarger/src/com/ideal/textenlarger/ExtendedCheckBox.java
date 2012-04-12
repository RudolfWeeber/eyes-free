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


/**
 * Class needed by ApplicationsListActivity.
 *
 * This was taken from a checkbox list tutorial at anddev.org:
 * http://www.anddev.org/extended_checkbox_list__extension_of_checkbox_text_list_tu-t5734.html
 */
public class ExtendedCheckBox implements Comparable<ExtendedCheckBox>
{	   
    private String mText = "";
    private boolean mChecked;
    
    public ExtendedCheckBox(String text, boolean checked) 
    {
    	/* constructor */ 
        mText = text;
        mChecked = checked;
    }
    public void setChecked(boolean value)
    {
    	this.mChecked = value;
    }
    public boolean getChecked()
    {
    	return this.mChecked;
    }
    
    public String getText() {
         return mText;
    }
    
    public void setText(String text) {
         mText = text;
    }

    /** Make CheckBoxifiedText comparable by its name */
    //@Override
    public int compareTo(ExtendedCheckBox other) 
    {
         if(this.mText != null)
              return this.mText.compareTo(other.getText());
         else
              throw new IllegalArgumentException();
    }
}
