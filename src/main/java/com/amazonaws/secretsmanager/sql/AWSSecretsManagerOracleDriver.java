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

import com.amazonaws.secretsmanager.caching.SecretCache;
import com.amazonaws.secretsmanager.caching.SecretCacheConfiguration;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.util.StringUtils;

/**
 * <p>
 * Provides support for accessing Oracle databases using credentials stored within AWS Secrets Manager.
 * </p>
 *
 * <p>
 * Configuration properties are specified using the "oracle" subprefix (e.g drivers.oracle.realDriverClass).
 * </p>
 *
 * <p>
 * For error codes see:
 * </p>
 *
 * <ul>
 * <li><a href="https://docs.oracle.com/en/database/oracle/oracle-database/12.2/jjdbc/JDBC-error-messages.html">
 * Oracle JDBC error codes</a>.</li>
 * <li><a href="https://docs.oracle.com/cd/B10501_01/server.920/a96525/e900.htm">Oracle JDBC error codes</a>.</li>
 * </ul>
 */
public final class AWSSecretsManagerOracleDriver extends AWSSecretsManagerDriver {

    /**
     * ORA-17079. May not be necessary, but erring on the side of caution.
     */
    public static final int USER_CREDENTIALS_DO_NOT_MATCH = 17079;

    /**
     * ORA-01017. This will occur if an incorrect password is used.
     */
    public static final int INVALID_USERNAME_OR_PASSWORD = 1017;

    /**
     * ORA-09911. May not be necessary, but erring on the side of caution.
     */
    public static final int INCORRECT_USER_PASSWORD = 9911;

    /**
     * Set to oracle.
     */
    public static final String SUBPREFIX = "oracle";

    /**
     * Default driver class to use.
     */
    public static final String DEFAULT_DRIVER = "oracle.jdbc.OracleDriver";

    static {
        AWSSecretsManagerDriver.register(new AWSSecretsManagerOracleDriver());
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with default options.
     */
    public AWSSecretsManagerOracleDriver() {
        super();
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Uses the passed in SecretCache.
     *
     * @param cache                                             Secret cache to use to retrieve secrets
     */
    public AWSSecretsManagerOracleDriver(SecretCache cache) {
        super(cache);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the passed in client builder.
     *
     * @param builder                                           Builder used to instantiate cache
     */
    public AWSSecretsManagerOracleDriver(AWSSecretsManagerClientBuilder builder) {
        super(builder);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided AWS Secrets Manager client.
     *
     * @param client                                            AWS Secrets Manager client to instantiate cache
     */
    public AWSSecretsManagerOracleDriver(AWSSecretsManager client) {
        super(client);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided cache configuration.
     *
     * @param cacheConfig                                       Cache configuration to instantiate cache
     */
    public AWSSecretsManagerOracleDriver(SecretCacheConfiguration cacheConfig) {
        super(cacheConfig);
    }

    @Override
    public String getPropertySubprefix() {
        return SUBPREFIX;
    }

    @Override
    public boolean isExceptionDueToAuthenticationError(Exception e) {
        if (e instanceof SQLException) {
            SQLException sqle = (SQLException) e;
            int errorCode = sqle.getErrorCode();
            return errorCode == USER_CREDENTIALS_DO_NOT_MATCH
                || errorCode == INVALID_USERNAME_OR_PASSWORD
                || errorCode == INCORRECT_USER_PASSWORD;
        }
        return false;
    }

    @Override
    public String constructUrlFromEndpointPortDatabase(String endpoint, String port, String dbname) {
        String url = "jdbc:oracle:thin:@//" + endpoint;
        if (!StringUtils.isNullOrEmpty(port)) {
            url += ":" + port;
        }
        if (!StringUtils.isNullOrEmpty(dbname)) {
            url += "/" + dbname;
        }
        return url;
    }

    @Override
    public String getDefaultDriverClass() {
        return DEFAULT_DRIVER;
    }
}

