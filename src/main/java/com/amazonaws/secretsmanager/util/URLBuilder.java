/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.amazonaws.secretsmanager.util;

public class URLBuilder {
    private StringBuilder url; 

    /**
     * Initializes the URL Builder with base URL.
     *
     * @param baseUrl The base URL to use.
     */
    public URLBuilder(String baseUrl) {
        this.url = new StringBuilder(baseUrl);
    }

    /**
     * Appends a parameter to the URL in the form of <code>&#63;key=value</code> or <code>&amp;key=value</code>.
     * 
     * @param key               The key of the parameter.
     * @param value             The value of the parameter.
     * @param isFirstParameter  Indicates whether this is the first parameter in the URL.
     * @return                  The URL Builder object.
     */
    public URLBuilder appendParameter(String key, String value, boolean isFirstParameter) {
        if (isFirstParameter) {
            url.append("?");
        } else {
            url.append("&");
        }
        url.append(key).append("=").append(value);
        return this;
    }

    /**
     * Appends a property to the URL in the form of ;key=value;
     * 
     * @param key               The key of the property.
     * @param value             The value of the property.
     * @param isSemiColon       Indicates whether to append a semicolon or colon.
     * @return                  The URL Builder object.
     */
    public URLBuilder appendProperty(String key, String value, boolean isSemiColon) {
        if(isSemiColon && url.charAt(url.length() - 1) != ';') {
            url.append(";");
        } else if(!isSemiColon && url.charAt(url.length() - 1) != ':') {
            url.append(":");
        }
        url.append(key).append("=").append(value).append(";");
        return this;
    }

    /**
     * Returns the constructed URL.
     *
     * @return String           The built URL.
     */
    public String build() {
        return url.toString();
    }
}