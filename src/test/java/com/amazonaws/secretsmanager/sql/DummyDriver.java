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
package com.amazonaws.secretsmanager.sql;

import java.sql.Driver;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Dummy driver.
 */
public class DummyDriver implements Driver {

    public static DummyDriver instance;

    static {
        instance = new DummyDriver();
        try {
            DriverManager.registerDriver(instance);
        } catch (SQLException e) {
            throw new RuntimeException("Driver could not be registered.", e);
        }
    }

    public static int acceptsURLCallCount;
    @Override
    public boolean acceptsURL(String url) throws SQLException {
        acceptsURLCallCount++;
        return "jdbc:expectedUrl".equals(url);
    }

    public static final String SQL_ERROR_USERNAME = "SQL_ERROR_USERNAME";
    public static final String RUNTIME_ERROR_USERNAME = "RUNTIME_ERROR_USERNAME";
    public static int connectCallCount;
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        connectCallCount++;
        if (info != null && SQL_ERROR_USERNAME.equals(info.getProperty("user"))) {
            throw new SQLException("Invalid SQL Exception!");
        } else if (info != null && RUNTIME_ERROR_USERNAME.equals(info.getProperty("user"))) {
            throw new RuntimeException("Invalid Runtime Exception!");
        }
        return null;
    }

    public static int getMajorVersionCallCount;
    public static final int GET_MAJOR_VERSION_RETURN_VALUE = 87;
    @Override
    public int getMajorVersion() {
        getMajorVersionCallCount++;
        return GET_MAJOR_VERSION_RETURN_VALUE;
    }

    public static int getMinorVersionCallCount;
    public static final int GET_MINOR_VERSION_RETURN_VALUE = 75;
    @Override
    public int getMinorVersion() {
        getMinorVersionCallCount++;
        return GET_MINOR_VERSION_RETURN_VALUE;
    }

    public static int getParentLoggerCallCount;
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        getParentLoggerCallCount++;
        return null;
    }

    public static int getPropertyInfoCallCount;
    public static String getPropertyInfoParam1;
    public static Properties getPropertyInfoParam2;
    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        getPropertyInfoCallCount++;
        getPropertyInfoParam1 = url;
        getPropertyInfoParam2 = info;
        return null;
    }

    public static int jdbcCompliantCallCount;
    @Override
    public boolean jdbcCompliant() {
        jdbcCompliantCallCount++;
        return true;
    }

    public static void reset() {
        acceptsURLCallCount = 0;
        connectCallCount = 0;
        getMajorVersionCallCount = 0;
        getMinorVersionCallCount = 0;
        getParentLoggerCallCount = 0;
        jdbcCompliantCallCount = 0;
        getPropertyInfoParam1 = null;
        getPropertyInfoParam2 = null;
    }
}

