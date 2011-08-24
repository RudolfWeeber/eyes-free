/*
 * Copyright (C) 2008 Google Inc.
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

package com.googlecode.eyesfree.walkytalky;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class for representing a street address. For details, see:
 * http://code.google.com/apis/maps/documentation/geocoding/
 * 
 * @author clchen@google.com (Charles L. Chen)
 */

public class Address {

    private String streetNumber = "";

    private String route = "";

    private String city = "";

    private String postalCode = "";

    private boolean isValid = false;

    public Address(String mapsJsonResponse) {
        try {
            JSONObject jsonObj = new JSONObject(mapsJsonResponse);
            String status = jsonObj.getString("status");
            if (status.equals("OK")) {
                JSONArray addressComponents = jsonObj.getJSONArray("results").getJSONObject(0)
                        .getJSONArray("address_components");
                for (int i = 0; i < addressComponents.length(); i++) {
                    JSONObject obj = addressComponents.getJSONObject(i);
                    JSONArray types = obj.getJSONArray("types");
                    for (int j = 0; j < types.length(); j++) {
                        String typeStr = types.getString(j);
                        if (typeStr.equals("street_number")) {
                            streetNumber = obj.getString("long_name");
                            break;
                        }
                        if (typeStr.equals("route")) {
                            route = obj.getString("long_name");
                            if (route.length() > 0){
                                route = ReverseGeocoder.extendShorts(route);
                            }
                            break;
                        }
                        if (typeStr.equals("locality")) {
                            city = obj.getString("long_name");
                            break;
                        }
                        if (typeStr.equals("postal_code")) {
                            postalCode = obj.getString("long_name");
                            break;
                        }
                    }
                }
                isValid = true;
            }
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public String getStreetNumber() {
        return streetNumber;
    }

    public String getRoute() {
        return route;
    }

    public String getCity() {
        return city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public boolean isValid() {
        return isValid;
    }

}
