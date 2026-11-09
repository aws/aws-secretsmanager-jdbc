package com.amazonaws.secretsmanager.util;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.amazonaws.secretsmanager.sql.AWSSecretsManagerDriver;

import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.utils.StringUtils;

/**
 * <p>
 *  A class for providing JDBC driver the secrets cache builder.
 *
 * Checks the config file and environment variables for overrides to the default
 * region and applies those changes to the provided secret cache builder.
 * Supports Post-Quantum TLS (PQTLS) configuration via AwsCrtHttpClient. 
 * </p>
 */
public class JDBCSecretCacheBuilderProvider {

    /**
     * Configuration property to override PrivateLink DNS URL for Secrets Manager
     */
    static final String PROPERTY_VPC_ENDPOINT_URL = "vpcEndpointUrl";

    static final String PROPERTY_VPC_ENDPOINT_REGION = "vpcEndpointRegion";

    /**
     * Configuration properties to override the default region
     */
    static final String PROPERTY_REGION = "region";

    /**
     * Configuration property to enable Post-Quantum TLS
     */
    static final String PROPERTY_POST_QUANTUM_TLS_ENABLED = "postQuantumTlsEnabled";

    static final String REGION_ENVIRONMENT_VARIABLE = "AWS_SECRET_JDBC_REGION";

    /**
     * Configuration property to override the timeout, in milliseconds, for each
     * Secrets Manager API call attempt. Must be positive: the SDK has no setting
     * that turns the timeout off.
     */
    static final String PROPERTY_API_CALL_ATTEMPT_TIMEOUT_MILLIS = "apiCallAttemptTimeoutMillis";

    /**
     * The default timeout for each Secrets Manager API call attempt. The SDK has no
     * attempt timeout by default, so an endpoint that accepts a connection and never
     * answers would block opening a database connection until the HTTP client's own
     * timeouts, if any, give up. A timed out attempt is retried like any other
     * network error.
     */
    static final long DEFAULT_API_CALL_ATTEMPT_TIMEOUT_MILLIS = 2000;

    /**
     * Runs the API call attempt timeouts of every client the driver builds. Without
     * it, each client would start its own pool of SDK timer threads, which only stop
     * when the client is closed, and closing the secret cache does not close its
     * client. The SDK never shuts down an executor it is given.
     */
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR = timeoutExecutor();

    private Config configFile;

    /**
     * Constructs the provider with the default configuration.
     */
    public JDBCSecretCacheBuilderProvider() {
        this(Config.loadMainConfig());
    }

    /**
     * Constructs the provider with the provided configuration.
     * @param config Config to use for provider
     */
    public JDBCSecretCacheBuilderProvider(Config config) {
        configFile = config;
    }

    /**
     * Provides the secrets cache builder.
     *
     * 1) If Post-Quantum TLS is enabled, configures AwsCrtHttpClient with PQTLS support
     * 2) If a PrivateLink DNS endpoint URL and region are given in the Config, then they are used to configure the endpoint.
     * 3) The AWS_SECRET_JDBC_REGION environment variable is checked. If set, it is used to configure the region.
     * 4) The region variable file is checked in the provided Config and, if set, used to configure the region.
     * 5) Finally, if none of these are not found, the default region provider chain is used.
     * 6) Each API call attempt times out after the apiCallAttemptTimeoutMillis property, or
     *    DEFAULT_API_CALL_ATTEMPT_TIMEOUT_MILLIS if it is not set.
     *
     * @return the built secret cache.
     */
    public SecretsManagerClientBuilder build() {

        SecretsManagerClientBuilder builder = SecretsManagerClient.builder();

        //Retrieve data from information sources.
        String vpcEndpointUrl = configFile.getStringPropertyWithDefault(AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_URL, null);
        String vpcEndpointRegion = configFile.getStringPropertyWithDefault(AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_REGION, null);
        String envRegion = System.getenv(REGION_ENVIRONMENT_VARIABLE);
        String configRegion = configFile.getStringPropertyWithDefault(AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_REGION, null);
        boolean postQuantumTlsEnabled = configFile.getBooleanPropertyWithDefault(AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_POST_QUANTUM_TLS_ENABLED, false);
        long attemptTimeoutMillis = configFile.getLongPropertyWithDefault(AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_API_CALL_ATTEMPT_TIMEOUT_MILLIS, DEFAULT_API_CALL_ATTEMPT_TIMEOUT_MILLIS);

        if (attemptTimeoutMillis <= 0) {
            throw new PropertyException(AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_API_CALL_ATTEMPT_TIMEOUT_MILLIS
                    + " must be a positive number of milliseconds. Please check " + Config.CONFIG_FILE_NAME
                    + " or your system properties.");
        }
        builder.overrideConfiguration(builder.overrideConfiguration().toBuilder()
                .apiCallAttemptTimeout(Duration.ofMillis(attemptTimeoutMillis))
                .scheduledExecutorService(TIMEOUT_EXECUTOR)
                .build());

        // Configure Post-Quantum TLS if enabled
        if (postQuantumTlsEnabled) {
            builder.httpClientBuilder(AwsCrtHttpClient.builder().postQuantumTlsEnabled(true));
        }

        // Apply settings to our builder configuration.
        if (StringUtils.isNotBlank(vpcEndpointUrl) && StringUtils.isNotBlank(vpcEndpointRegion)) {
            builder.endpointOverride(URI.create(vpcEndpointUrl)).region(Region.of(vpcEndpointRegion));
        } else if (StringUtils.isNotBlank(envRegion)) {
            builder.region(Region.of(envRegion));
        } else if (StringUtils.isNotBlank(configRegion)) {
            builder.region(Region.of(configRegion));
        }

        return builder;
    }

    /**
     * Creates the shared timeout executor. Its one daemon thread is started by the
     * first timed request and exits after a minute with nothing to do, so an
     * application server can unload the application. Cancelled timeouts are removed
     * straight away.
     *
     * @return The shared timeout executor.
     */
    private static ScheduledExecutorService timeoutExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "aws-secretsmanager-jdbc-timeout");
            thread.setDaemon(true);
            // Don't keep the class loader of whichever application thread happened to start it.
            thread.setContextClassLoader(JDBCSecretCacheBuilderProvider.class.getClassLoader());
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        // The keep-alive must be set first: it is 0 by default on Java 8, which
        // allowCoreThreadTimeOut rejects.
        executor.setKeepAliveTime(1, TimeUnit.MINUTES);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
