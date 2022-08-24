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
import com.amazonaws.services.secretsmanager.*;
import com.amazonaws.util.*;

import java.sql.*;

/**
 * <p>
 * Provides support for accessing Snowflake databases using credentials stored within AWS Secrets Manager.
 * </p>
 *
 * <p>
 * Configuration properties are specified using the "snowflake" subprefix (e.g drivers.snowflake.realDriverClass).
 * </p>
 */
public final class AWSSecretsManagerSnowflakeDriver extends AWSSecretsManagerDriver {

    /**
     * The PostgreSQL error code for when a user logs in using an invalid password.
     */
    public static final String INCORRECT_USERNAME_OR_PASSWORD_WAS_SPECIFIED_SQL_STATE = "08001";

    /**
     * Set to snowflake.
     */
    public static final String SUBPREFIX = "snowflake";

    static {
        AWSSecretsManagerDriver.register(new AWSSecretsManagerSnowflakeDriver());
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with default options.
     */
    public AWSSecretsManagerSnowflakeDriver() {
        super();
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Uses the passed in SecretCache.
     *
     * @param cache                                             Secret cache to use to retrieve secrets
     */
    public AWSSecretsManagerSnowflakeDriver(SecretCache cache) {
        super(cache);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the passed in client builder.
     *
     * @param builder                                           Builder used to instantiate cache
     */
    public AWSSecretsManagerSnowflakeDriver(AWSSecretsManagerClientBuilder builder) {
        super(builder);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided AWS Secrets Manager client.
     *
     * @param client                                            AWS Secrets Manager client to instantiate cache
     */
    public AWSSecretsManagerSnowflakeDriver(AWSSecretsManager client) {
        super(client);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided cache configuration.
     *
     * @param cacheConfig                                       Cache configuration to instantiate cache
     */
    public AWSSecretsManagerSnowflakeDriver(SecretCacheConfiguration cacheConfig) {
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
            String sqlState = sqle.getSQLState();
            return sqlState.equals(INCORRECT_USERNAME_OR_PASSWORD_WAS_SPECIFIED_SQL_STATE);
        }
        return false;
    }

    @Override
    public String constructUrlFromEndpointPortDatabase(String endpoint, String port, String dbname) {
        String url = "jdbc:snowflake://" + endpoint;
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
        return "net.snowflake.client.jdbc.SnowflakeDriver";
    }
}

