package com.amazonaws.secretsmanager.util;

import static com.amazonaws.secretsmanager.util.JDBCSecretCacheBuilderProvider.PROPERTY_VPC_ENDPOINT_REGION;
import static com.amazonaws.secretsmanager.util.JDBCSecretCacheBuilderProvider.PROPERTY_VPC_ENDPOINT_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.amazonaws.secretsmanager.caching.SecretCache;
import com.amazonaws.secretsmanager.sql.AWSSecretsManagerDriver;

import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
public class JDBCSecretCacheBuilderProviderTest {

    @SystemStub
    private EnvironmentVariables environmentVariables = new EnvironmentVariables();

    /**
     * A mock config that returns the default for every long property, like a real
     * config with none of them set.
     */
    private static Config mockConfig() {
        Config configProvider = mock(Config.class);
        when(configProvider.getLongPropertyWithDefault(anyString(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        return configProvider;
    }

    /**
     * SetRegion Tests.
     */
    @Test
    public void test_setRegion_configFileProperty() {
        Config configProvider = mockConfig();
        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("us-west-2");

        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();

        assertEquals(client.serviceClientConfiguration().region(), Region.US_WEST_2);
    }

    @Test
    public void test_setRegion_environmentVariable() {
        Config configProvider = mockConfig();

        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;
        environmentVariables.set(environmentRegionName, "us-east-1");
        assertEquals("us-east-1", System.getenv(environmentRegionName));

        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();
        assertEquals(client.serviceClientConfiguration().region(), Region.US_EAST_1);
    }

    @Test
    public void test_setRegion_vpcEndpoint() {
        Config configProvider = mockConfig();
        String vpcEndpointUrlName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_URL;
        String vpcEndpointRegion = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_REGION;
        String vpcEndpointUrlString = "https://asdf.us-west-2.amazonaws.com";
        when(configProvider.getStringPropertyWithDefault(vpcEndpointUrlName, null)).thenReturn(vpcEndpointUrlString);
        when(configProvider.getStringPropertyWithDefault(vpcEndpointRegion, null)).thenReturn("ap-southeast-3");

        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();

        assertEquals(client.serviceClientConfiguration().endpointOverride().get().toString(), vpcEndpointUrlString);
        assertEquals(client.serviceClientConfiguration().region(), Region.AP_SOUTHEAST_3);
    }

    @Test
    public void test_setRegion_defaultsToEnv() {
        try {
            new JDBCSecretCacheBuilderProvider().build().build();
        } catch (SdkClientException e) {
            assertTrue(e.getMessage().startsWith("Unable to load region from any of the providers in the chain"));
        }
    }

    /**
     * SetRegion priority tests.
     */

    @Test
    public void test_regionSelectionOrder_prefersVpcEndpointOverEverything() {
        Config configProvider = mockConfig();

        // Arrange so all properties return something valid.
        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        String vpcEndpointUrlName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_URL;
        String vpcEndpointRegion = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_REGION;
        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;
        String vpcEndpointUrlString = "https://1234.secretsmanager.amazonaws.com";

        // Arrange the return values when the properties are requested.
        environmentVariables.set(environmentRegionName, "us-east-2");
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("us-east-1");
        when(configProvider.getStringPropertyWithDefault(vpcEndpointUrlName, null))
                .thenReturn(vpcEndpointUrlString);
        when(configProvider.getStringPropertyWithDefault(vpcEndpointRegion, null)).thenReturn("us-west-2");

        // Act: Build our client
        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();

        // Assert: Make sure the endpoint was configured properly.
        assertNotEquals(client.serviceClientConfiguration().region(), Region.US_EAST_2);
        assertNotEquals(client.serviceClientConfiguration().region(), Region.US_EAST_1);
        assertEquals(client.serviceClientConfiguration().region(), Region.US_WEST_2);
        assertEquals(client.serviceClientConfiguration().endpointOverride().get().toString(),
                vpcEndpointUrlString);
    }

    @Test
    public void test_regionSelectionOrder_prefersEnvironmentVarOverConfig() {
        Config configProvider = mockConfig();

        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;

        environmentVariables.set(environmentRegionName, "eu-west-3");
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("us-east-2");

        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();

        assertNotEquals(Region.US_EAST_2, client.serviceClientConfiguration().region());
        assertEquals(Region.EU_WEST_3, client.serviceClientConfiguration().region());
    }

    /**
     * Variables must be correctly set
     */
    @Test
    public void test_settingValidation_emptyConfigPropertyIgnored() {

        Config configProvider = mockConfig();
        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("");

        try {
            new JDBCSecretCacheBuilderProvider(configProvider).build().build();
        } catch (SdkClientException e) {
            assertTrue(e.getMessage().startsWith("Unable to load region from any of the providers in the chain"));
        }
    }

    @Test
    public void test_settingValidation_nullConfigPropertyIgnored() {

        Config configProvider = mockConfig();
        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("");

        try {
            new JDBCSecretCacheBuilderProvider(configProvider).build().build();
        } catch (SdkClientException e) {
            assertTrue(e.getMessage().startsWith("Unable to load region from any of the providers in the chain"));
        }
    }

    @Test
    public void test_settingValidation_emptyEnvironmentVariableIgnored() {

        Config configProvider = mockConfig();

        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;
        environmentVariables.set(environmentRegionName, "");

        try {
            new JDBCSecretCacheBuilderProvider(configProvider).build().build();
        } catch (SdkClientException e) {
            assertTrue(e.getMessage().startsWith("Unable to load region from any of the providers in the chain"));
        }
    }

    @Test
    public void test_settingValidation_nullEnvironmentVariableIgnored() {

        Config configProvider = mockConfig();

        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;
        environmentVariables.remove(environmentRegionName);

        try {
            new JDBCSecretCacheBuilderProvider(configProvider).build().build();
        } catch (SdkClientException e) {
            assertTrue(e.getMessage().startsWith("Unable to load region from any of the providers in the chain"));
        }
    }

    @Test
    public void test_settingValidation_emptyVpcIgnored() {

        Config configProvider = mockConfig();
        String vpcEndpointUrlName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_URL;
        String vpcEndpointRegion = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_REGION;
        when(configProvider.getStringPropertyWithDefault(vpcEndpointUrlName, null)).thenReturn("");
        when(configProvider.getStringPropertyWithDefault(vpcEndpointRegion, null)).thenReturn("");

        try {
            SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();
            assertFalse(client.serviceClientConfiguration().endpointOverride().isPresent());
        } catch (SdkClientException e) {
            assertTrue(e.getMessage().startsWith("Unable to load region from any of the providers in the chain"));
        }
    }

    @Test
    public void test_settingValidation_nullVpcIgnored() {

        Config configProvider = mockConfig();
        String vpcEndpointUrlName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_URL;
        String vpcEndpointRegion = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_REGION;
        when(configProvider.getStringPropertyWithDefault(vpcEndpointUrlName, null)).thenReturn(null);
        when(configProvider.getStringPropertyWithDefault(vpcEndpointRegion, null)).thenReturn(null);

        try {
            SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();
            assertFalse(client.serviceClientConfiguration().endpointOverride().isPresent());
        } catch (SdkClientException e) {
            assertTrue(e.getMessage().startsWith("Unable to load region from any of the providers in the chain"));
        }
    }

    /**
     * Post-Quantum TLS Tests
     */
    @Test
    public void test_postQuantumTls_enabledViaConfig() {
        Config configProvider = mockConfig();
        String pqtlsPropertyName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_POST_QUANTUM_TLS_ENABLED;
        when(configProvider.getBooleanPropertyWithDefault(pqtlsPropertyName, false)).thenReturn(true);

        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();

        // Verify client was built successfully with PQTLS enabled
        verify(configProvider).getBooleanPropertyWithDefault(pqtlsPropertyName, false);
        assertNotNull(client);
    }

    @Test
    public void test_postQuantumTls_disabledByDefault() {
        Config configProvider = mockConfig();
        String pqtlsPropertyName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_POST_QUANTUM_TLS_ENABLED;
        when(configProvider.getBooleanPropertyWithDefault(pqtlsPropertyName, false)).thenReturn(false);

        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();

        // Verify client was built successfully with PQTLS disabled (default)
        verify(configProvider).getBooleanPropertyWithDefault(pqtlsPropertyName, false);
        assertNotNull(client);
    }

    @Test
    public void test_postQuantumTls_withRegionConfig() {
        Config configProvider = mockConfig();
        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        String pqtlsPropertyName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_POST_QUANTUM_TLS_ENABLED;
        
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("us-west-2");
        when(configProvider.getBooleanPropertyWithDefault(pqtlsPropertyName, false)).thenReturn(true);

        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();

        // Verify both region and PQTLS are configured
        assertEquals(Region.US_WEST_2, client.serviceClientConfiguration().region());
        assertNotNull(client);
    }

    @Test
    public void test_postQuantumTls_withVpcEndpoint() {
        Config configProvider = mockConfig();
        String vpcEndpointUrlName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_URL;
        String vpcEndpointRegion = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_REGION;
        String pqtlsPropertyName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_POST_QUANTUM_TLS_ENABLED;
        String vpcEndpointUrlString = "https://asdf.us-west-2.amazonaws.com";
        
        when(configProvider.getStringPropertyWithDefault(vpcEndpointUrlName, null)).thenReturn(vpcEndpointUrlString);
        when(configProvider.getStringPropertyWithDefault(vpcEndpointRegion, null)).thenReturn("ap-southeast-3");
        when(configProvider.getBooleanPropertyWithDefault(pqtlsPropertyName, false)).thenReturn(true);

        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();

        // Verify VPC endpoint, region, and PQTLS are all configured
        assertEquals(vpcEndpointUrlString, client.serviceClientConfiguration().endpointOverride().get().toString());
        assertEquals(Region.AP_SOUTHEAST_3, client.serviceClientConfiguration().region());
        assertNotNull(client);
    }

    /**
     * API call attempt timeout tests.
     */
    private static final String ATTEMPT_TIMEOUT_PROPERTY = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
            + JDBCSecretCacheBuilderProvider.PROPERTY_API_CALL_ATTEMPT_TIMEOUT_MILLIS;

    private static Config configWithRegion() {
        Config configProvider = mockConfig();
        when(configProvider.getStringPropertyWithDefault(AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_REGION, null)).thenReturn("us-west-2");
        return configProvider;
    }

    private static Optional<Duration> attemptTimeout(Config configProvider) {
        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();
        return client.serviceClientConfiguration().overrideConfiguration().apiCallAttemptTimeout();
    }

    @Test
    public void test_attemptTimeout_default() {
        assertEquals(Optional.of(Duration.ofMillis(JDBCSecretCacheBuilderProvider.DEFAULT_API_CALL_ATTEMPT_TIMEOUT_MILLIS)),
                attemptTimeout(configWithRegion()));
    }

    @Test
    public void test_attemptTimeout_configFileProperty() {
        Config configProvider = configWithRegion();
        when(configProvider.getLongPropertyWithDefault(ATTEMPT_TIMEOUT_PROPERTY,
                JDBCSecretCacheBuilderProvider.DEFAULT_API_CALL_ATTEMPT_TIMEOUT_MILLIS)).thenReturn(5000L);
        assertEquals(Optional.of(Duration.ofMillis(5000)), attemptTimeout(configProvider));
    }

    @Test
    public void test_attemptTimeout_zeroOrNegativeIsRejected() {
        for (long value : new long[] {0, -1}) {
            Config configProvider = configWithRegion();
            when(configProvider.getLongPropertyWithDefault(ATTEMPT_TIMEOUT_PROPERTY,
                    JDBCSecretCacheBuilderProvider.DEFAULT_API_CALL_ATTEMPT_TIMEOUT_MILLIS)).thenReturn(value);
            assertThrows(PropertyException.class, () -> new JDBCSecretCacheBuilderProvider(configProvider).build());
        }
    }

    /**
     * Returns how long the driver's cache takes to time out against an endpoint
     * that hangs, using the given number of attempts.
     */
    private long millisToTimeOutOnHungEndpoint(int maxAttempts, Long attemptTimeoutMillis) throws Exception {
        // A socket that is never accepted completes the TCP handshake but never
        // answers, like an endpoint that hangs after connecting.
        try (ServerSocket hung = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            environmentVariables.set("AWS_ACCESS_KEY_ID", "akid");
            environmentVariables.set("AWS_SECRET_ACCESS_KEY", "secret");
            environmentVariables.set("AWS_MAX_ATTEMPTS", String.valueOf(maxAttempts));

            Config configProvider = configWithRegion();
            when(configProvider.getStringPropertyWithDefault(AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                    + PROPERTY_VPC_ENDPOINT_URL, null)).thenReturn("http://127.0.0.1:" + hung.getLocalPort());
            when(configProvider.getStringPropertyWithDefault(AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                    + PROPERTY_VPC_ENDPOINT_REGION, null)).thenReturn("us-west-2");
            if (attemptTimeoutMillis != null) {
                when(configProvider.getLongPropertyWithDefault(ATTEMPT_TIMEOUT_PROPERTY,
                        JDBCSecretCacheBuilderProvider.DEFAULT_API_CALL_ATTEMPT_TIMEOUT_MILLIS)).thenReturn(attemptTimeoutMillis);
            }

            // Built the same way the driver builds its cache.
            try (SecretCache cache = new SecretCache(new JDBCSecretCacheBuilderProvider(configProvider).build())) {
                long start = System.nanoTime();
                assertThrows(ApiCallAttemptTimeoutException.class, () -> cache.getSecretString("test"));
                return Duration.ofNanos(System.nanoTime() - start).toMillis();
            }
        }
    }

    @Test
    public void test_attemptTimeout_givesUpOnHungEndpoint() throws Exception {
        long elapsedMillis = millisToTimeOutOnHungEndpoint(1, null);
        assertTrue(elapsedMillis >= 2000 && elapsedMillis < 5000, "Took " + elapsedMillis + " ms to give up");
    }

    @Test
    public void test_attemptTimeout_retriesTimedOutAttempt() throws Exception {
        // Two 300 ms attempts set through the property, plus a short backoff between them.
        long elapsedMillis = millisToTimeOutOnHungEndpoint(2, 300L);
        assertTrue(elapsedMillis >= 600 && elapsedMillis < 3000, "Took " + elapsedMillis + " ms to give up");
    }

    @Test
    public void test_attemptTimeout_sharesOneTimeoutExecutor() throws Exception {
        // Each client would otherwise start its own SDK timer threads, which stay
        // alive because closing the secret cache doesn't close its client.
        assertSame(new JDBCSecretCacheBuilderProvider(configWithRegion()).build()
                        .overrideConfiguration().scheduledExecutorService().get(),
                new JDBCSecretCacheBuilderProvider(configWithRegion()).build()
                        .overrideConfiguration().scheduledExecutorService().get());

        long before = threadsNamed("sdk-ScheduledExecutor").size();
        for (int i = 0; i < 3; i++) {
            millisToTimeOutOnHungEndpoint(1, 300L);
        }
        assertEquals(before, threadsNamed("sdk-ScheduledExecutor").size());

        List<Thread> driverThreads = threadsNamed("aws-secretsmanager-jdbc-timeout");
        assertEquals(1, driverThreads.size());
        assertTrue(driverThreads.get(0).isDaemon());
        assertSame(JDBCSecretCacheBuilderProvider.class.getClassLoader(), driverThreads.get(0).getContextClassLoader());
    }

    private static List<Thread> threadsNamed(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith(prefix))
                .collect(Collectors.toList());
    }
}
