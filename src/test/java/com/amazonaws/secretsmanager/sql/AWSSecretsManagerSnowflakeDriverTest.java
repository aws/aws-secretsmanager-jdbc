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

import com.amazonaws.secretsmanager.caching.*;
import com.amazonaws.secretsmanager.util.*;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.Mock;
import org.mockito.*;
import org.powermock.core.classloader.annotations.*;
import org.powermock.modules.junit4.*;

import java.sql.*;

import static org.junit.Assert.*;

/**
 * Tests for the PostgreSQL Driver.
 */
@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor("com.amazonaws.secretsmanager.sql.AWSSecretsManagerSnowflakeDriver")
@PowerMockIgnore("jdk.internal.reflect.*")
public class AWSSecretsManagerSnowflakeDriverTest extends TestClass {

    private AWSSecretsManagerSnowflakeDriver sut;

    @Mock
    private SecretCache cache;

    @Before
    public void setup() {
        System.setProperty("drivers.snowflake.realDriverClass", "com.amazonaws.secretsmanager.sql.DummyDriver");
        MockitoAnnotations.initMocks(this);
        try {
            sut = new AWSSecretsManagerSnowflakeDriver(cache);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test_getPropertySubprefix() {
        assertEquals("snowflake", sut.getPropertySubprefix());
    }

    @Test
    public void test_isExceptionDueToAuthenticationError_returnsTrue_correctException() {
        SQLException e = new SQLException("", "08001");

        assertTrue(sut.isExceptionDueToAuthenticationError(e));
    }

    @Test
    public void test_isExceptionDueToAuthenticationError_returnsFalse_wrongSQLException() {
        SQLException e = new SQLException("", "08002");

        assertFalse(sut.isExceptionDueToAuthenticationError(e));
    }

    @Test
    public void test_isExceptionDueToAuthenticationError_returnsFalse_runtimeException() {
        RuntimeException e = new RuntimeException("asdf");

        assertFalse(sut.isExceptionDueToAuthenticationError(e));
    }

    @Test
    public void test_constructUrl() {
        String url = sut.constructUrlFromEndpointPortDatabase("http://test-endpoint", "1234", "dev");
        assertEquals(url, "jdbc:snowflake://http://test-endpoint:1234/dev");
    }

    @Test
    public void test_constructUrlNullPort() {
        String url = sut.constructUrlFromEndpointPortDatabase("http://test-endpoint", null, "dev");
        assertEquals(url, "jdbc:snowflake://http://test-endpoint/dev");
    }

    @Test
    public void test_constructUrlNullDatabase() {
        String url = sut.constructUrlFromEndpointPortDatabase("http://test-endpoint", "1234", null);
        assertEquals(url, "jdbc:snowflake://http://test-endpoint:1234");
    }

    @Test
    public void test_getDefaultDriverClass() {
        System.clearProperty("drivers.snowflake.realDriverClass");
        AWSSecretsManagerSnowflakeDriver sut2 = new AWSSecretsManagerSnowflakeDriver(cache);
        assertEquals(getFieldFrom(sut2, "realDriverClass"), sut2.getDefaultDriverClass());
    }
}

