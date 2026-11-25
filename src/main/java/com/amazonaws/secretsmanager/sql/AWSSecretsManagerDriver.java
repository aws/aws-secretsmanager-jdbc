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

import java.io.IOException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.amazonaws.secretsmanager.caching.SecretCache;
import com.amazonaws.secretsmanager.caching.SecretCacheConfiguration;
import com.amazonaws.secretsmanager.util.Config;
import com.amazonaws.secretsmanager.util.JDBCSecretCacheBuilderProvider;
import com.amazonaws.secretsmanager.util.RegionResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.utils.StringUtils;

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
 *
 * <p>
 * The connection properties <code>aws.profile</code> and <code>aws.region</code> select the AWS profile and region
 * used to read secrets for that connection. They are never forwarded to the real driver. The region is taken from,
 * in order: the secret ARN, <code>aws.region</code>, the profile's <code>region</code>, then the driver-wide
 * defaults (<code>AWS_SECRET_JDBC_REGION</code>, <code>drivers.region</code>, the SDK default region chain).
 * </p>
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
     * Connection property naming the AWS profile used to read secrets.
     */
    public static final String PROPERTY_AWS_PROFILE = "aws.profile";

    /**
     * Connection property naming the AWS region used to read secrets.
     */
    public static final String PROPERTY_AWS_REGION = "aws.region";

    private static final Pattern INVALID_HOST_PATTERN = Pattern.compile(".*[?#&;/@\\\\\\s].*");
    private static final Pattern INVALID_DBNAME_PATTERN = Pattern.compile(".*[?#&;\\\\\\s].*");
    private static final Pattern DIGITS_ONLY_PATTERN = Pattern.compile("\\d+");

    private SecretCache secretCache;

    /**
     * Caches for connections using <code>aws.profile</code> / <code>aws.region</code>.
     */
    private final ConcurrentHashMap<SecretCacheKey, SecretCache> profileCaches = new ConcurrentHashMap<>();

    /**
     * Identifies a per-connection secret cache. Either field may be null (default credentials / default region).
     */
    private static final class SecretCacheKey {
        private final String profile;
        private final String region;

        SecretCacheKey(String profile, String region) {
            this.profile = profile;
            this.region = region;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SecretCacheKey)) {
                return false;
            }
            SecretCacheKey other = (SecretCacheKey) o;
            return Objects.equals(profile, other.profile) && Objects.equals(region, other.region);
        }

        @Override
        public int hashCode() {
            return Objects.hash(profile, region);
        }
    }

    private String realDriverClass;

    private Config config;

    private ObjectMapper mapper = new ObjectMapper();



    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with default options.
     */
    protected AWSSecretsManagerDriver() {
        this(new JDBCSecretCacheBuilderProvider().build());
    }


    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Sets the secret cache to the cache that was passed in.
     *
     * @param cache                                             Secret cache to use to retrieve secrets
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    protected AWSSecretsManagerDriver(SecretCache cache) {
        this.secretCache = cache;

        setProperties();
        AWSSecretsManagerDriver.register(this);
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the passed in client builder.
     *
     * @param builder                                           Builder used to instantiate cache
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    protected AWSSecretsManagerDriver(SecretsManagerClientBuilder builder) {
        this(new SecretCache(builder));
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided AWS Secrets Manager client.
     *
     * @param client                                            AWS Secrets Manager client to instantiate cache
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    protected AWSSecretsManagerDriver(SecretsManagerClient client) {
        this(new SecretCache(client));
    }

    /**
     * Constructs the driver setting the properties from the properties file using system properties as defaults.
     * Instantiates the secret cache with the provided cache configuration.
     *
     * @param cacheConfig                                       Cache configuration to instantiate cache
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    protected AWSSecretsManagerDriver(SecretCacheConfiguration cacheConfig) {
        this(new SecretCache(cacheConfig));
    }

    /**
     * Sets general configuration properties that are unrelated to the API client.
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
     * Loads the real driver.
     *
     * @throws IllegalStateException                            When there is no class with the name
     *                                                          <code>realDriverClass</code>
     */
    private void loadRealDriver() {
        try {
            Class.forName(this.realDriverClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not load real driver with name, \"" + this.realDriverClass + "\".", e);
        }
    }

    /**
     * Called when the driver is deregistered to cleanup resources.
     */
    private static void shutdown(AWSSecretsManagerDriver driver) {
        driver.secretCache.close();
        driver.profileCaches.values().forEach(SecretCache::close);
    }

    /**
     * Returns the secret cache to use for a secret, given the connection's <code>aws.profile</code> and
     * <code>aws.region</code> values. Without either value, the driver's default cache is returned.
     *
     * @param awsProfile                                        The AWS profile name, or null.
     * @param awsRegion                                         The AWS region, or null.
     * @param secretId                                          The secret name or ARN to read.
     *
     * @return SecretCache                                      The cache for the resolved profile and region.
     *
     * @throws SQLException                                     If the Secrets Manager client cannot be created.
     */
    private SecretCache resolveCache(String awsProfile, String awsRegion, String secretId) throws SQLException {
        if (awsProfile == null && awsRegion == null) {
            return this.secretCache;
        }
        String arnRegion = RegionResolver.regionFromArn(secretId);
        String regionOverride = arnRegion != null ? arnRegion : awsRegion;
        try {
            return profileCaches.computeIfAbsent(new SecretCacheKey(awsProfile, regionOverride), key -> {
                Region region = regionOverride != null
                        ? Region.of(regionOverride)
                        : RegionResolver.profileRegion(ProfileFile.defaultProfileFile(), awsProfile);
                return createSecretCache(awsProfile, region);
            });
        } catch (SdkClientException e) {
            throw new SQLException("Could not create the AWS Secrets Manager client for " + PROPERTY_AWS_PROFILE + "="
                    + awsProfile + ", " + PROPERTY_AWS_REGION + "=" + awsRegion + ". Set " + PROPERTY_AWS_REGION
                    + ", add a region to the profile, or use the secret ARN.", e);
        }
    }

    /**
     * Creates the secret cache for a profile and region.
     *
     * @param awsProfile                                        The AWS profile name, or null for default credentials.
     * @param region                                            The region, or null for the driver-wide default region.
     *
     * @return SecretCache                                      A new secret cache.
     */
    protected SecretCache createSecretCache(String awsProfile, Region region) {
        return new SecretCache(createClientBuilder(awsProfile, region).build());
    }

    /**
     * Creates a client builder from the driver-wide configuration, overriding credentials and region.
     *
     * @param awsProfile                                        The AWS profile name, or null for default credentials.
     * @param region                                            The region, or null for the driver-wide default region.
     *
     * @return SecretsManagerClientBuilder                      The configured client builder.
     */
    SecretsManagerClientBuilder createClientBuilder(String awsProfile, Region region) {
        SecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider().build();
        if (awsProfile != null) {
            builder.credentialsProvider(ProfileCredentialsProvider.builder().profileName(awsProfile).build());
        }
        if (region != null) {
            builder.region(region);
        }
        return builder;
    }

    /**
     * Returns the properties without <code>aws.profile</code> and <code>aws.region</code>. The input is returned
     * unchanged when it has neither property.
     */
    private static Properties withoutAwsProperties(Properties info) {
        if (info == null
                || (info.getProperty(PROPERTY_AWS_PROFILE) == null && info.getProperty(PROPERTY_AWS_REGION) == null)) {
            return info;
        }
        Properties stripped = new Properties();
        for (String name : info.stringPropertyNames()) {
            if (!PROPERTY_AWS_PROFILE.equals(name) && !PROPERTY_AWS_REGION.equals(name)) {
                stripped.setProperty(name, info.getProperty(name));
            }
        }
        return stripped;
    }

    private static String trimmedProperty(Properties info, String name) {
        return info == null ? null : StringUtils.trimToNull(info.getProperty(name));
    }

    /**
     * Registers a driver along with the <code>DriverAction</code> implementation.
     *
     * @param driver                                            The driver to register.
     *
     * @throws RuntimeException                                 If the driver could not be registered.
     */
    @SuppressFBWarnings("THROWS_METHOD_THROWS_RUNTIMEEXCEPTION")
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
        loadRealDriver();
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
     * @param cache                                             The secret cache to read the credentials from.
     *
     * @return Connection                                       A database connection.
     *
     * @throws SQLException                                     If there is an error from the driver or underlying
     *                                                          database.
     * @throws InterruptedException                             If there was an interruption during secret refresh.
     */
    @SuppressFBWarnings("THROWS_METHOD_THROWS_RUNTIMEEXCEPTION")
    private Connection connectWithSecret(String unwrappedUrl, Properties info, String credentialsSecretId,
            SecretCache cache) throws SQLException, InterruptedException {
        int retryCount = 0;
        while (retryCount++ <= MAX_RETRY) {
            String secretString = cache.getSecretString(credentialsSecretId);
            Properties updatedInfo = new Properties();
            for (String name : info.stringPropertyNames()) {
                updatedInfo.setProperty(name, info.getProperty(name));
            }
            try {
                JsonNode jsonObject = mapper.readTree(secretString);
                updatedInfo.setProperty("user", jsonObject.get("username").asText());
                updatedInfo.setProperty("password", jsonObject.get("password").asText());
            } catch (IOException e) {
                // Most likely to occur in the event that the data is not JSON.
                // Or the secret's username and/or password fields have been
                // removed entirely. Either scenario is most often a user error.
                throw new RuntimeException(INVALID_SECRET_STRING_JSON);
            }

            try {
                return getWrappedDriver().connect(unwrappedUrl, updatedInfo);
            } catch (Exception e) {
                if (isExceptionDueToAuthenticationError(e)) {
                    boolean refreshSuccess = cache.refreshNow(credentialsSecretId);
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

    /**
     * Validates that secret fields do not contain characters that could be used for URL injection.
     * Prevents CWE-610 (Externally Controlled Reference to a Resource in Another Sphere).
     */
    private void validateSecretFields(String endpoint, String port, String dbname) throws SQLException {
        if (endpoint == null || endpoint.isEmpty()) {
            throw new SQLException("Secret 'host' field must not be null or empty.");
        }
        if (INVALID_HOST_PATTERN.matcher(endpoint).matches()) {
            throw new SQLException("Secret 'host' field contains invalid characters. "
                    + "A valid host must be a hostname or IP address without URL special characters.");
        }
        if (port != null && !port.isEmpty() && !DIGITS_ONLY_PATTERN.matcher(port).matches()) {
            throw new SQLException("Secret 'port' field must contain only digits.");
        }
        if (dbname != null && !dbname.isEmpty() && INVALID_DBNAME_PATTERN.matcher(dbname).matches()) {
            throw new SQLException("Secret 'dbname' field contains invalid characters.");
        }
    }

    @Override
    @SuppressFBWarnings("THROWS_METHOD_THROWS_RUNTIMEEXCEPTION")
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }

        String awsProfile = trimmedProperty(info, PROPERTY_AWS_PROFILE);
        String awsRegion = trimmedProperty(info, PROPERTY_AWS_REGION);
        Properties forwardInfo = withoutAwsProperties(info);

        String unwrappedUrl = "";
        if (url.startsWith(SCHEME)) { // If this is a URL in the correct scheme, unwrap it
            unwrappedUrl = unwrapUrl(url);
        } else { // Else, assume this is a secret ID and try to retrieve it
            try {
                String secretString = resolveCache(awsProfile, awsRegion, url).getSecretString(url);
                if (StringUtils.isBlank(secretString)) {
                    throw new IllegalArgumentException("URL " + url + " is not a valid URL starting with scheme " +
                            SCHEME + " or a valid retrievable secret ID ");
                }
                JsonNode jsonObject = mapper.readTree(secretString);
                String endpoint = jsonObject.get("host").asText();
                JsonNode portNode = jsonObject.get("port");
                String port = portNode == null ? null : portNode.asText();
                JsonNode dbnameNode = jsonObject.get("dbname");
                String dbname = dbnameNode == null ? null : dbnameNode.asText();
                validateSecretFields(endpoint, port, dbname);
                unwrappedUrl = constructUrlFromEndpointPortDatabase(endpoint, port, dbname);
            } catch (IOException e) {
                // Most likely to occur in the event that the data is not JSON.
                // Or the secret has been modified and is no longer valid.
                // Either scenario is most often a user error.
                throw new RuntimeException(INVALID_SECRET_STRING_JSON);
            }
        }

        if (forwardInfo != null && forwardInfo.getProperty("user") != null) {
            String credentialsSecretId = forwardInfo.getProperty("user");
            try {
                return connectWithSecret(unwrappedUrl, forwardInfo, credentialsSecretId,
                        resolveCache(awsProfile, awsRegion, credentialsSecretId));
            } catch (InterruptedException e) {
                // User driven exception. Throw a runtime exception.
                throw new RuntimeException(e);
            }
        } else {
            return getWrappedDriver().connect(unwrappedUrl, forwardInfo);
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
        DriverPropertyInfo[] wrappedProperties = getWrappedDriver().getPropertyInfo(unwrapUrl(url),
                withoutAwsProperties(info));
        List<DriverPropertyInfo> properties = new ArrayList<>();
        if (wrappedProperties != null) {
            properties.addAll(Arrays.asList(wrappedProperties));
        }
        properties.add(propertyInfo(info, PROPERTY_AWS_PROFILE,
                "AWS profile used to read the secret (from the AWS config and credentials files)"));
        properties.add(propertyInfo(info, PROPERTY_AWS_REGION,
                "AWS region of the secret; overrides the profile region (a secret ARN's region takes precedence)"));
        return properties.toArray(new DriverPropertyInfo[0]);
    }

    private static DriverPropertyInfo propertyInfo(Properties info, String name, String description) {
        DriverPropertyInfo property = new DriverPropertyInfo(name, trimmedProperty(info, name));
        property.description = description;
        return property;
    }

    @Override
    public boolean jdbcCompliant() {
        return getWrappedDriver().jdbcCompliant();
    }
}
