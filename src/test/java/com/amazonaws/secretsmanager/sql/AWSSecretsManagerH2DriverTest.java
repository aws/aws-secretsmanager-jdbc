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

import java.sql.SQLException;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.junit.Before;

import com.amazonaws.secretsmanager.caching.SecretCache;
import com.amazonaws.secretsmanager.util.TestClass;

/**
 * Tests for the H2 Driver.
 */
@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor("com.amazonaws.secretsmanager.sql.AWSSecretsManagerH2Driver")
@PowerMockIgnore("jdk.internal.reflect.*")
public class AWSSecretsManagerH2DriverTest extends TestClass {

    private AWSSecretsManagerH2Driver sut;

    @Mock
    private SecretCache cache;

    @Before
    public void setup() {
        System.setProperty("drivers.h2.realDriverClass", "com.amazonaws.secretsmanager.sql.DummyDriver");
        MockitoAnnotations.initMocks(this);
        try {
            sut = new AWSSecretsManagerH2Driver(cache);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test_getPropertySubprefix() {
        assertEquals("h2", sut.getPropertySubprefix());
    }

    @Test
    public void test_isExceptionDueToAuthenticationError_returnsTrue_correctException() {
        SQLException e = new SQLException("", "28000");

        assertTrue(sut.isExceptionDueToAuthenticationError(e));
    }

    @Test
    public void test_isExceptionDueToAuthenticationError_returnsFalse_wrongSQLException() {
        SQLException e = new SQLException("", "28001");

        assertFalse(sut.isExceptionDueToAuthenticationError(e));
    }

    @Test
    public void test_isExceptionDueToAuthenticationError_returnsFalse_runtimeException() {
        RuntimeException e = new RuntimeException("asdf");

        assertFalse(sut.isExceptionDueToAuthenticationError(e));
    }

    @Test
    public void test_constructUrl() {
        String url = sut.constructUrlFromEndpointPortDatabase("test-endpoint", "1234", "dev");
        assertEquals(url, "jdbc:h2:tcp://test-endpoint:1234/dev");
    }

    @Test
    public void test_constructUrlNullPort() {
        String url = sut.constructUrlFromEndpointPortDatabase("test-endpoint", null, "dev");
        assertEquals(url, "jdbc:h2:tcp://test-endpoint/dev");
    }

    @Test
    public void test_constructUrlNullDatabase() {
        String url = sut.constructUrlFromEndpointPortDatabase("test-endpoint", "1234", null);
        assertEquals(url, "jdbc:h2:tcp://test-endpoint:1234");
    }

    @Test
    public void test_getDefaultDriverClass() {
        System.clearProperty("drivers.h2.realDriverClass");
        AWSSecretsManagerH2Driver sut2 = new AWSSecretsManagerH2Driver(cache);
        assertEquals(getFieldFrom(sut2, "realDriverClass"), sut2.getDefaultDriverClass());
    }
}