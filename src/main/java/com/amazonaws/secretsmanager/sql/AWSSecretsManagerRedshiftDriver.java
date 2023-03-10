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
 * Provides support for accessing Redshift databases using credentials stored
 * within AWS Secrets Manager.
 * </p>
 *
 * <p>
 * Configuration properties are specified using the "redshift" subprefix (e.g
 * drivers.redshift.realDriverClass).
 * </p>
 */
public final class AWSSecretsManagerRedshiftDriver extends AWSSecretsManagerDriver {

    /**
     * The Redshift error code for when a user logs in using an invalid password.
     *
     * See <a href=
     * "https://www.postgresql.org/docs/9.6/static/errcodes-appendix.html">Postgres documentation</a> (Redshift is built on Postgres).
     */
    public static final String ACCESS_DENIED_FOR_USER_USING_PASSWORD_TO_DATABASE = "28P01";

    public static final String SUBPREFIX = "redshift";

    /**
     * Default driver class to use.
     */
    public static final String DEFAULT_DRIVER = "com.amazon.redshift.jdbc42.Driver";

    static {
        AWSSecretsManagerDriver.register(new AWSSecretsManagerRedshiftDriver());
    }

    /**
     * Constructs the driver setting the properties from the properties file using
     * system properties as defaults.
     * Instantiates the secret cache with default options.
     */
    public AWSSecretsManagerRedshiftDriver() {
        super();
    }

    /**
     * Constructs the driver setting the properties from the properties file using
     * system properties as defaults.
     * Uses the passed in SecretCache.
     *
     * @param cache Secret cache to use to retrieve secrets
     */
    public AWSSecretsManagerRedshiftDriver(SecretCache cache) {
        super(cache);
    }

    /**
     * Constructs the driver setting the properties from the properties file using
     * system properties as defaults.
     * Instantiates the secret cache with the passed in client builder.
     *
     * @param builder Builder used to instantiate cache
     */
    public AWSSecretsManagerRedshiftDriver(AWSSecretsManagerClientBuilder builder) {
        super(builder);
    }

    /**
     * Constructs the driver setting the properties from the properties file using
     * system properties as defaults.
     * Instantiates the secret cache with the provided AWS Secrets Manager client.
     *
     * @param client AWS Secrets Manager client to instantiate cache
     */
    public AWSSecretsManagerRedshiftDriver(AWSSecretsManager client) {
        super(client);
    }

    /**
     * Constructs the driver setting the properties from the properties file using
     * system properties as defaults.
     * Instantiates the secret cache with the provided cache configuration.
     *
     * @param cacheConfig Cache configuration to instantiate cache
     */
    public AWSSecretsManagerRedshiftDriver(SecretCacheConfiguration cacheConfig) {
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
            return sqlState.equals(ACCESS_DENIED_FOR_USER_USING_PASSWORD_TO_DATABASE);
        }
        return false;
    }

    @Override
    public String constructUrlFromEndpointPortDatabase(String endpoint, String port, String dbname) {
        String url = "jdbc:redshift://" + endpoint;
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
