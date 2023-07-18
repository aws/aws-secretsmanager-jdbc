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

import com.amazonaws.secretsmanager.caching.SecretCache;
import com.amazonaws.secretsmanager.caching.SecretCacheConfiguration;
import com.amazonaws.secretsmanager.util.SQLExceptionUtils;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.utils.StringUtils;

/**
 * <p>
 * Provides support for accessing MariaDB databases using credentials stored within AWS Secrets Manager.
 * </p>
 *
 * <p>
 * This will also work for MariaDB, as the error codes are the same.
 * </p>
 *
 * <p>
 * Configuration properties are specified using the "mariadb" subprefix (e.g drivers.mariadb.realDriverClass).
 * </p>
 */
public final class AWSSecretsManagerMariaDBDriver extends AWSSecretsManagerDriver {

    /**
     * MariaDB shares error codes with MySQL, as well as adding a number of new error codes specific to MariaDB.
     *
     * See <a href="https://mariadb.com/kb/en/library/mariadb-error-codes/">MariaDB error codes</a>.
     */
    public static final int ACCESS_DENIED_FOR_USER_USING_PASSWORD_TO_DATABASE = 1045;

    /**
     * Set to mariadb.
     */
    public static final String SUBPREFIX = "mariadb";

    static {
        AWSSecretsManagerDriver.register(new AWSSecretsManagerMariaDBDriver());
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with default options.
     */
    public AWSSecretsManagerMariaDBDriver() {
        super();
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Uses the passed in SecretCache.
     *
     * @param cache                                             Secret cache to use to retrieve secrets
     */
    public AWSSecretsManagerMariaDBDriver(SecretCache cache) {
        super(cache);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the passed in client builder.
     *
     * @param builder                                           Builder used to instantiate cache
     */
    public AWSSecretsManagerMariaDBDriver(SecretsManagerClientBuilder builder) {
        super(builder);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided AWS Secrets Manager client.
     *
     * @param client                                            AWS Secrets Manager client to instantiate cache
     */
    public AWSSecretsManagerMariaDBDriver(SecretsManagerClient client) {
        super(client);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided cache configuration.
     *
     * @param cacheConfig                                       Cache configuration to instantiate cache
     */
    public AWSSecretsManagerMariaDBDriver(SecretCacheConfiguration cacheConfig) {
        super(cacheConfig);
    }

    @Override
    public String getPropertySubprefix() {
        return SUBPREFIX;
    }

    @Override
    public boolean isExceptionDueToAuthenticationError(Exception e) {
        return SQLExceptionUtils.unwrapAndCheckForCode(e, ACCESS_DENIED_FOR_USER_USING_PASSWORD_TO_DATABASE);
    }

    @Override
    public String constructUrlFromEndpointPortDatabase(String endpoint, String port, String dbname) {
        String url = "jdbc:mariadb://" + endpoint;
        if (StringUtils.isNotBlank(port)) {
            url += ":" + port;
        }
        if (StringUtils.isNotBlank(dbname)) {
            url += "/" + dbname;
        }
        return url;
    }

    @Override
    public String getDefaultDriverClass() {
        return "org.mariadb.jdbc.Driver";
    }
}
