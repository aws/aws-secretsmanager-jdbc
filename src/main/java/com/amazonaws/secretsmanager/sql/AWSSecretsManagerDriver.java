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

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.secretsmanager.util.Config;
import com.amazonaws.secretsmanager.caching.SecretCache;
import com.amazonaws.secretsmanager.caching.SecretCacheConfiguration;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * <p>
 * Provides support for accessing SQL databases using credentials stored within AWS Secrets Manager. If this
 * functionality is desired, then a subclass of this class should be specified as the JDBC driver for an application.
 * </p>
 *
 * <p>
 * The driver to propagate <code>connect</code> requests to should also be specified in the configuration. Doing this
 * will cause the real driver to be registered once an instance of this driver is made (which will be when this driver
 * is registered).
 * </p>
 *
 * <p>
 * This base class registers itself with the <code>java.sql.DriverManager</code> when its constructor is called. That
 * means a subclass only needs to make a new instance of itself in its static block to register.
 * </p>
 *
 * <p>
 * This does not support including the user (secret ID) and password in the jdbc url, as JDBC url formats are database
 * specific. If this functionality is desired, it must be implemented in a subclass.
 * </p>
 *
 * <p>
 * Ignores the password field, drawing a secret ID from the user field. The secret referred to by this field is
 * expected to be in the standard JSON format used by the rotation lambdas provided by Secrets Manager:
 * </p>
 *
 * <pre>
 * {@code
 * {
 *     "username": "xxxx",
 *     "password": "xxxx",
 *     ...
 * }
 * }
 * </pre>
 *
 * <p>
 * Here is a list of the configuration properties. The subprefix is an implementation specific String used to keep
 * the properties for different drivers separate. For example, the MySQL driver wrapper might use mysql as its
 * subprefix, making the full property name for the realDriverClass for the MySQL driver wrapper
 * drivers.mysql.realDriverClass (all Driver properties will be prefixed with "drivers."). This String is defined by
 * the method <code>getPropertySubprefix</code>.
 * </p>
 *
 * <ul>
 *   <li>drivers.<i>subprefix</i>.realDriverClass - (optional) The class name of the driver to propagate calls to.
 *                                                  If not specified, default for <i>subprefix</i> is used</li>
 * </ul>
 */
public abstract class AWSSecretsManagerDriver implements Driver {

    /**
     * "jdbc-secretsmanager", so the JDBC URL should start with "jdbc-secretsmanager" instead of just "jdbc".
     */
    public static final String SCHEME = "jdbc-secretsmanager";

    /**
     * Maximum number of times to retry connecting to DB on auth failures
     */
    public static final int MAX_RETRY = 5;

    /**
     * "drivers", so all configuration properties start with "drivers.".
     */
    public static final String PROPERTY_PREFIX = "drivers";

    /**
     * Message to return on the RuntimeException when secret string is invalid json
     */ 
    public static final String INVALID_SECRET_STRING_JSON = "Could not parse SecretString JSON";

    /**
     * Configuration property to override PrivateLink DNS URL for Secrets Manager
     */
    private static final String PROPERTY_VPC_ENDPOINT_URL = "vpcEndpointUrl";

    private static final String PROPERTY_VPC_ENDPOINT_REGION = "vpcEndpointRegion";

    private SecretCache secretCache;

    private String realDriverClass;

    private Config config;

    private ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with default options.
     */
    protected AWSSecretsManagerDriver() {
        this(new SecretCache());
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Sets the secret cache to the cache that was passed in.
     *
     * @param cache                                             Secret cache to use to retrieve secrets
     */
    protected AWSSecretsManagerDriver(SecretCache cache) {

        final Config config = Config.loadMainConfig();

        String vpcEndpointUrl = config.getStringPropertyWithDefault(PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_URL, null);
        String vpcEndpointRegion = config.getStringPropertyWithDefault(PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_REGION, null);

        if (vpcEndpointUrl == null || vpcEndpointUrl.isEmpty() || vpcEndpointRegion == null || vpcEndpointRegion.isEmpty()) {
            this.secretCache = cache;
        } else {
            AWSSecretsManagerClientBuilder builder = AWSSecretsManagerClientBuilder.standard();
            builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(vpcEndpointUrl, vpcEndpointRegion));

            this.secretCache = new SecretCache(builder);
        }

        setProperties();
        AWSSecretsManagerDriver.register(this);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the passed in client builder.
     *
     * @param builder                                           Builder used to instantiate cache
     */
    protected AWSSecretsManagerDriver(AWSSecretsManagerClientBuilder builder) {
        this(new SecretCache(builder));
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided AWS Secrets Manager client.
     *
     * @param client                                            AWS Secrets Manager client to instantiate cache
     */
    protected AWSSecretsManagerDriver(AWSSecretsManager client) {
        this(new SecretCache(client));
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided cache configuration.
     *
     * @param cacheConfig                                       Cache configuration to instantiate cache
     */
    protected AWSSecretsManagerDriver(SecretCacheConfiguration cacheConfig) {
        this(new SecretCache(cacheConfig));
    }

    /**
     * Sets general configuration properties that are unrelated to the API client.
     *
     * @param config                                            The main configuration for this driver.
     */
    private void setProperties() {
        this.config = Config.loadMainConfig().getSubconfig(PROPERTY_PREFIX + "." + getPropertySubprefix());
        if (this.config == null) {
            this.realDriverClass = getDefaultDriverClass();
            return;
        }
        this.realDriverClass = this.config.getStringPropertyWithDefault("realDriverClass", getDefaultDriverClass());
    }

    /**
     * Called when the driver is deregistered to cleanup resources.
     */
    private static void shutdown(AWSSecretsManagerDriver driver) {
        driver.secretCache.close();
    }

    /**
     * Registers a driver along with the <code>DriverAction</code> implementation.
     *
     * @param driver                                            The driver to register.
     *
     * @throws RuntimeException                                 If the driver could not be registered.
     */
    protected static void register(AWSSecretsManagerDriver driver) {
        try {
            DriverManager.registerDriver(driver, () -> shutdown(driver));
        } catch (SQLException e) {
            throw new RuntimeException("Driver could not be registered.", e);
        }
    }

    /**
     * Gets the "subprefix" used for configuration properties for this driver. For example, if this method returns the
     * String, "mysql", then the real driver that this will forward requests to would be set to
     * drivers.mysql.realDriverClass in the properties file or in the system properties.
     *
     * @return String                                           The subprefix to use for configuration properties.
     */
    public abstract String getPropertySubprefix();

    /**
     * Replaces <code>SCHEME</code> in a jdbc url with "jdbc" in order to pass the url to the real driver.
     *
     * @param jdbcUrl                                           The jdbc url with <code>SCHEME</code> as the scheme.
     *
     * @return String                                           The jdbc url with the scheme changed.
     *
     * @throws IllegalArgumentException                         When the url does not start with <code>SCHEME</code>.
     */
    private String unwrapUrl(String jdbcUrl) {
        if (!jdbcUrl.startsWith(SCHEME)) {
            throw new IllegalArgumentException("JDBC URL is malformed. Must use scheme, \"" + SCHEME + "\".");
        }
        return jdbcUrl.replaceFirst(SCHEME, "jdbc");
    }

    /**
     * Returns an instance of the real <code>java.sql.Driver</code> that this should propagate calls to. The real
     * driver is specified by the realDriverClass property.
     *
     * @return Driver                                           The real <code>Driver</code> that calls should be
     *                                                          propagated to.
     *
     * @throws IllegalStateException                            When there is no driver with the name
     *                                                          <code>realDriverClass</code>
     */
    public Driver getWrappedDriver() {
        Enumeration<Driver> availableDrivers = DriverManager.getDrivers();
        while (availableDrivers.hasMoreElements()) {
            Driver driver = availableDrivers.nextElement();
            if (driver.getClass().getName().equals(this.realDriverClass)) {
                return driver;
            }
        }
        throw new IllegalStateException("No Driver has been registered with name, " + this.realDriverClass
                                        + ". Please check your system properties or " + Config.CONFIG_FILE_NAME
                                        + " for typos. Also ensure that the Driver registers itself.");
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        if (url == null) {
            throw new SQLException("url cannot be null.");
        }

        if (url.startsWith(SCHEME)) {
            // If this is a URL in our SCHEME, call the acceptsURL method of the wrapped driver
            return getWrappedDriver().acceptsURL(unwrapUrl(url));
        } else if (url.startsWith("jdbc:")) {
            // For any other JDBC URL, return false
            return false;
        } else  {
            // We accept a secret ID as the URL so if the config is set, and it's not a JDBC URL, return true
            return true;
        }
    }

    /**
     * Determines whether or not an <code>Exception</code> is due to an authentication failure with the remote
     * database. This method is called during <code>connect</code> to decide if authentication needs to be attempted
     * again with refreshed credentials. A good way to implement this is to look up the error codes that
     * <code>java.sqlSQLException</code>s will have when an authentication failure occurs. These are database
     * specific.
     *
     * @param exception                                         The <code>Exception</code> to test.
     *
     * @return boolean                                          Whether or not the <code>Exception</code> indicates that
     *                                                          the credentials used for authentication are stale.
     */
    public abstract boolean isExceptionDueToAuthenticationError(Exception exception);

    /**
     * Construct a database URL from the endpoint, port and database name. This method is called when the
     * <code>connect</code> method is called with a secret ID instead of a URL. 
     *
     * @param endpoint                                          The endpoint retrieved from the secret cache
     * @param port                                              The port retrieved from the secret cache
     * @param dbname                                            The database name retrieved from the secret cache
     *
     * @return String                                           The constructed URL based on the endpoint and port
     */
    public abstract String constructUrlFromEndpointPortDatabase(String endpoint, String port, String dbname);

    /**
     * Get the default real driver class name for this driver.
     *
     * @return String                                           The default real driver class name
     */
    public abstract String getDefaultDriverClass();

    /**
     * Calls the real driver's <code>connect</code> method using credentials from a secret stored in AWS Secrets
     * Manager.
     *
     * @param unwrappedUrl                                      The jdbc url that the real driver will accept.
     * @param info                                              The information to pass along to the real driver. The
     *                                                          user and password fields will be replaced with the
     *                                                          credentials retrieved from Secrets Manager.
     * @param credentialsSecretId                               The friendly name or ARN of the secret that stores the
     *                                                          login credentials.
     *
     * @return Connection                                       A database connection.
     *
     * @throws SQLException                                     If there is an error from the driver or underlying
     *                                                          database.
     * @throws InterruptedException                             If there was an interruption during secret refresh.
     */
    private Connection connectWithSecret(String unwrappedUrl, Properties info, String credentialsSecretId)
            throws SQLException, InterruptedException {
        int retryCount = 0;
        while (retryCount++ <= MAX_RETRY) {
            String secretString = secretCache.getSecretString(credentialsSecretId);
            Properties updatedInfo = new Properties(info);
            try {
                JsonNode jsonObject = mapper.readTree(secretString);
                updatedInfo.setProperty("user", jsonObject.get("username").asText());
                updatedInfo.setProperty("password", jsonObject.get("password").asText());
            } catch (IOException e) {
                // Most likely to occur in the event that the data is not JSON. This is more of a user error.
                throw new RuntimeException(INVALID_SECRET_STRING_JSON);
            }

            try {
                return getWrappedDriver().connect(unwrappedUrl, updatedInfo);
            } catch (Exception e) {
                if (isExceptionDueToAuthenticationError(e)) {
                    boolean refreshSuccess = this.secretCache.refreshNow(credentialsSecretId);
                    if (!refreshSuccess) {
                        throw(e);
                    }
                }
                else {
                    throw(e);
                }
            }
        }

        // Max retries reached
        throw new SQLException("Connect failed to authenticate: reached max connection retries");
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }

        String unwrappedUrl = "";
        if (url.startsWith(SCHEME)) { // If this is a URL in the correct scheme, unwrap it
            unwrappedUrl = unwrapUrl(url);
        } else { // Else, assume this is a secret ID and try to retrieve it
            String secretString = secretCache.getSecretString(url);
            if (StringUtils.isNullOrEmpty(secretString)) {
                throw new IllegalArgumentException("URL " + url + " is not a valid URL starting with scheme " +
                        SCHEME + " or a valid retrievable secret ID ");
            }

            try {
                JsonNode jsonObject = mapper.readTree(secretString);
                String endpoint = jsonObject.get("host").asText();
                JsonNode portNode = jsonObject.get("port");
                String port = portNode == null ? null : portNode.asText();
                JsonNode dbnameNode = jsonObject.get("dbname");
                String dbname = dbnameNode == null ? null : dbnameNode.asText();
                unwrappedUrl = constructUrlFromEndpointPortDatabase(endpoint, port, dbname);
            } catch (IOException e) {
                // Most likely to occur in the event that the data is not JSON. This is more of a user error.
                throw new RuntimeException(INVALID_SECRET_STRING_JSON);
            }
        }

        if (info != null && info.getProperty("user") != null) {
            String credentialsSecretId = info.getProperty("user");
            try {
                return connectWithSecret(unwrappedUrl, info, credentialsSecretId);
            } catch (InterruptedException e) {
                // User driven exception. Throw a runtime exception.
                throw new RuntimeException(e);
            }
        } else {
            return getWrappedDriver().connect(unwrappedUrl, info);
        }
    }

    @Override
    public int getMajorVersion() {
        return getWrappedDriver().getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return getWrappedDriver().getMinorVersion();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return getWrappedDriver().getParentLogger();
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return getWrappedDriver().getPropertyInfo(unwrapUrl(url), info);
    }

    @Override
    public boolean jdbcCompliant() {
        return getWrappedDriver().jdbcCompliant();
    }
}

