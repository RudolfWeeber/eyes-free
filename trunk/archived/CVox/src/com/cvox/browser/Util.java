/*
	Copyright (C) 2008 Jeffrey Sharkey, http://jsharkey.org/
	
	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	
	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.
	
	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.cvox.browser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import android.content.res.Resources;
import android.os.Process;
import android.util.Log;

public class Util {
	
	public final static String TAG = Util.class.toString();
	
	/**
	 * Return a specific raw resource contents as a String value.
	 */
	public static String getRawString(Resources res, int id) throws Exception {
		String result = null;
		InputStream is = null;
		try {
			is = res.openRawResource(id);
			byte[] raw = new byte[is.available()];
			is.read(raw);
			result = new String(raw);
		} catch(Exception e) {
			throw new Exception("Problem while trying to read raw", e);
		} finally {
			try {
				is.close();
			} catch(Exception e) {
			}
		}
		return result;

	}
	
	/**
	 * Return a specific file contents as a String value.
	 */
	public static String getFileString(File file) throws Exception {
		String result = null;
		InputStream is = null;
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			is = new FileInputStream(file);
			
			int bytesRead;
			byte[] buffer = new byte[1024];
			while ((bytesRead = is.read(buffer)) != -1) {
				os.write(buffer, 0, bytesRead);
			}
	
			os.flush();
			result = new String(os.toByteArray());
		} catch(Exception e) {
			throw new Exception("Problem while trying to read file", e);
		} finally {
			try {
				os.close();
				is.close();
			} catch(Exception e) {
			}
		}
		return result;
		
	}
	
	/**
	 * Return a specific url contents as a String value.
	 */
	public static String getUrlString(String remoteUrl) throws Exception {
		String result = null;
		InputStream is = null;
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			URL url = new URL(remoteUrl);
			URLConnection connection = url.openConnection();
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			connection.connect();
			is = connection.getInputStream();
			
			int bytesRead;
			byte[] buffer = new byte[1024];
			while ((bytesRead = is.read(buffer)) != -1) {
				os.write(buffer, 0, bytesRead);
			}
	
			os.flush();
			result = new String(os.toByteArray());
		} catch(Exception e) {
			throw new Exception("Problem while trying to read url", e);
		} finally {
			try {
				os.close();
				is.close();
			} catch(Exception e) {
			}
		}
		return result;

	}
}
