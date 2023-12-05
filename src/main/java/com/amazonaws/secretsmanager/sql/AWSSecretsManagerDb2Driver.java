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
 * Provides support for accessing Db2 databases using credentials stored within AWS Secrets Manager.
 * </p>
 *
 * <p>
 * Configuration properties are specified using the "db2" subprefix (e.g drivers.mysql.realDriverClass).
 * </p>
 */
public final class AWSSecretsManagerDb2Driver extends AWSSecretsManagerDriver {

    /**
     * The Db2 error code for when a user logs in using an invalid password.
     *
     * See <a href="https://www.ibm.com/docs/en/db2-for-zos/11?topic=codes-sql-error">Db2 error codes</a>.
     */
    public static final int ACCESS_DENIED_FOR_USER_USING_PASSWORD_TO_DATABASE = -1403;

    /**
     * Set to Db2.
     */
    public static final String SUBPREFIX = "db2";

    static {
        AWSSecretsManagerDriver.register(new AWSSecretsManagerDb2Driver());
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with default options.
     */
    public AWSSecretsManagerDb2Driver() {
        super();
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Uses the passed in SecretCache.
     *
     * @param cache                                             Secret cache to use to retrieve secrets
     */
    public AWSSecretsManagerDb2Driver(SecretCache cache) {
        super(cache);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the passed in client builder.
     *
     * @param builder                                           Builder used to instantiate cache
     */
    public AWSSecretsManagerDb2Driver(SecretsManagerClientBuilder builder) {
        super(builder);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided AWS Secrets Manager client.
     *
     * @param client                                            AWS Secrets Manager client to instantiate cache
     */
    public AWSSecretsManagerDb2Driver(SecretsManagerClient client) {
        super(client);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided cache configuration.
     *
     * @param cacheConfig                                       Cache configuration to instantiate cache
     */
    public AWSSecretsManagerDb2Driver(SecretCacheConfiguration cacheConfig) {
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
        String url = "jdbc:db2://" + endpoint;
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
        try {
            Class.forName("com.ibm.db2.jcc.DB2Driver", false, this.getClass().getClassLoader());
            return "com.ibm.db2.jcc.DB2Driver";
        } catch (ClassNotFoundException e) {
            return "com.ibm.db2.jcc.DB2Driver";
        }
    }
}
