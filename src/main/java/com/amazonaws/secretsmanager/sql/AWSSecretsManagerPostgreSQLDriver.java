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
import com.amazonaws.secretsmanager.util.URLBuilder;

import com.amazonaws.secretsmanager.caching.SecretCache;
import com.amazonaws.secretsmanager.caching.SecretCacheConfiguration;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.utils.StringUtils;

/**
 * <p>
 * Provides support for accessing PostgreSQL databases using credentials stored within AWS Secrets Manager.
 * </p>
 *
 * <p>
 * Configuration properties are specified using the "postgresql" subprefix (e.g drivers.postgresql.realDriverClass).
 * </p>
 */
public final class AWSSecretsManagerPostgreSQLDriver extends AWSSecretsManagerDriver {

    /**
     * The PostgreSQL error code for when a user logs in using an invalid password.
     *
     * See <a href="https://www.postgresql.org/docs/9.6/static/errcodes-appendix.html">PostgreSQL documentation</a>.
     */
    public static final String ACCESS_DENIED_FOR_USER_USING_PASSWORD_TO_DATABASE = "28P01";

    /**
     * The PostgreSQL error code for invalid authorization specification.
     *
     * PostgreSQL returns 28000 for general authentication failures (e.g. pg_hba.conf rejection,
     * certificate auth failure) as opposed to 28P01 which specifically indicates an invalid password.
     *
     * See <a href="https://www.postgresql.org/docs/current/errcodes-appendix.html">PostgreSQL documentation</a>.
     */
    public static final String ACCESS_DENIED_FOR_INVALID_AUTHORIZATION_SPECIFICATION = "28000";

    /**
     * The error code returned by PGBouncer when serving a cached authentication failure.
     *
     * When a server login fails, PGBouncer's check_fast_fail() rejects subsequent clients
     * with SQLSTATE 08P01 (protocol_violation) instead of the original 28P01 from PostgreSQL.
     * This is PGBouncer's default SQLSTATE for all disconnect_client() calls.
     *
     * See <a href="https://github.com/pgbouncer/pgbouncer/blob/master/src/objects.c">PGBouncer check_fast_fail()</a>.
     */
    public static final String PGBOUNCER_AUTH_FAILURE = "08P01";

    /**
     * Set to postgresql.
     */
    public static final String SUBPREFIX = "postgresql";

    static {
        AWSSecretsManagerDriver.register(new AWSSecretsManagerPostgreSQLDriver());
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with default options.
     */
    public AWSSecretsManagerPostgreSQLDriver() {
        super();
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Uses the passed in SecretCache.
     *
     * @param cache                                             Secret cache to use to retrieve secrets
     */
    public AWSSecretsManagerPostgreSQLDriver(SecretCache cache) {
        super(cache);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the passed in client builder.
     *
     * @param builder                                           Builder used to instantiate cache
     */
    public AWSSecretsManagerPostgreSQLDriver(SecretsManagerClientBuilder builder) {
        super(builder);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided AWS Secrets Manager client.
     *
     * @param client                                            AWS Secrets Manager client to instantiate cache
     */
    public AWSSecretsManagerPostgreSQLDriver(SecretsManagerClient client) {
        super(client);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided cache configuration.
     *
     * @param cacheConfig                                       Cache configuration to instantiate cache
     */
    public AWSSecretsManagerPostgreSQLDriver(SecretCacheConfiguration cacheConfig) {
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
            return sqlState.equals(ACCESS_DENIED_FOR_USER_USING_PASSWORD_TO_DATABASE) || sqlState.equals(ACCESS_DENIED_FOR_INVALID_AUTHORIZATION_SPECIFICATION) || sqlState.equals(PGBOUNCER_AUTH_FAILURE);
        }
        return false;
    }

    @Override
    public String constructUrlFromEndpointPortDatabase(String endpoint, String port, String dbname) {
        String url = "jdbc:postgresql://" + endpoint;
        if (StringUtils.isNotBlank(port)) {
            url += ":" + port;
        }

        url += "/";

        if (StringUtils.isNotBlank(dbname)) {
            url += dbname;
        }

        return url;
    }

    @Override
    public String enforceSSL(String url, String sslMode) {

        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        URLBuilder builder = new URLBuilder(url);

        switch(sslMode) {
            case "disable":
                break;
            case "allow":
            case "prefer":
            case "require":
            case "verify-ca":
            case "verify-full":
                return builder.appendParameter("sslmode", sslMode, !url.contains("?")).build();
            default:
                return builder.appendParameter("sslmode", "prefer", !url.contains("?")).build();
        }
        return url;
    }

    @Override
    public String getDefaultDriverClass() {
        return "org.postgresql.Driver";
    }
}
