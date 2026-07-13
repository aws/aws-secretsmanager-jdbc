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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.amazonaws.secretsmanager.caching.SecretCache;
import com.amazonaws.secretsmanager.util.TestClass;

/**
 * Tests for the MariaDB Driver.
 */
public class AWSSecretsManagerMariaDBDriverTest extends TestClass {

    private AWSSecretsManagerMariaDBDriver sut;

    @Mock
    private SecretCache cache;

    @BeforeEach
    public void setup() {
        System.setProperty("drivers.mariadb.realDriverClass", "com.amazonaws.secretsmanager.sql.DummyDriver");
        MockitoAnnotations.openMocks(this);
        try {
            sut = new AWSSecretsManagerMariaDBDriver(cache);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test_getPropertySubprefix() {
        assertEquals("mariadb", sut.getPropertySubprefix());
    }

    @Test
    public void test_isExceptionDueToAuthenticationError_returnsTrue_correctException() {
        SQLException e = new SQLException("", "", 1045);

        assertTrue(sut.isExceptionDueToAuthenticationError(e));
    }

    @Test
    public void test_isExceptionDueToAuthenticationError_returnsFalse_wrongSQLException() {
        SQLException e = new SQLException("", "", 1046);

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
        assertEquals(url, "jdbc:mariadb://test-endpoint:1234/dev");
    }

    @Test
    public void test_constructUrlNullPort() {
        String url = sut.constructUrlFromEndpointPortDatabase("test-endpoint", null, "dev");
        assertEquals(url, "jdbc:mariadb://test-endpoint/dev");
    }

    @Test
    public void test_constructUrlNullDatabase() {
        String url = sut.constructUrlFromEndpointPortDatabase("test-endpoint", "1234", null);
        assertEquals(url, "jdbc:mariadb://test-endpoint:1234");
    }

    @Test
    public void test_enforceSSL_WithDisableMode() {
        String url = sut.enforceSSL("jdbc:mariadb://test-endpoint:1234/dev", "disable");
        assertEquals(url, "jdbc:mariadb://test-endpoint:1234/dev");
    }

    @Test
    public void test_enforceSSLNullPort_withVerifyFullMode() {
        String url = sut.enforceSSL("jdbc:mariadb://test-endpoint/dev", "verify-full");
        assertEquals(url, "jdbc:mariadb://test-endpoint/dev?sslMode=verify-full");
    }

    @Test
    public void test_enforceSSLNullDatabase_withVerifyCaMode() {
        String url = sut.enforceSSL("jdbc:mariadb://test-endpoint:1234", "verify-ca");
        assertEquals(url, "jdbc:mariadb://test-endpoint:1234?sslMode=verify-ca");
    }

    @Test
    public void test_enforceSSL_WithDefaultSSLMode() {
        String url = sut.enforceSSL("jdbc:mariadb://test-endpoint:1234/dev", "true");
        assertEquals(url, "jdbc:mariadb://test-endpoint:1234/dev?sslMode=trust");
    }

    @Test
    public void test_getDefaultDriverClass() {
        System.clearProperty("drivers.mariadb.realDriverClass");
        AWSSecretsManagerMariaDBDriver sut2 = new AWSSecretsManagerMariaDBDriver(cache);
        assertEquals(getFieldFrom(sut2, "realDriverClass"), sut2.getDefaultDriverClass());
    }
}
