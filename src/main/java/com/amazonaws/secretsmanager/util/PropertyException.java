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

/**
 * <p>
 * Thrown when there is an issue related to a property set in the configuration for this library.
 * </p>
 */
public class PropertyException extends RuntimeException {

    /**
     * Public constructor.
     *
     * @param message                                               The reason for this exception.
     * @param cause                                                 The exception that caused this one.
     */
    public PropertyException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Public constructor.
     *
     * @param message                                               The reason for this exception.
     */
    public PropertyException(String message) {
        super(message);
    }
}

