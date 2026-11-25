package com.amazonaws.secretsmanager.sql;

import static com.amazonaws.secretsmanager.sql.AWSSecretsManagerDriver.PROPERTY_AWS_PROFILE;
import static com.amazonaws.secretsmanager.sql.AWSSecretsManagerDriver.PROPERTY_AWS_REGION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.amazonaws.secretsmanager.caching.SecretCache;
import com.sun.net.httpserver.HttpServer;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

/**
 * Tests for the <code>aws.profile</code> and <code>aws.region</code> connection properties.
 */
@ExtendWith(SystemStubsExtension.class)
public class AWSSecretsManagerDriverProfileTest {

    private static final String JDBC_URL = "jdbc-secretsmanager:expectedUrl";
    private static final String SECRET_NAME = "my-secret";
    private static final String SECRET_ARN = "arn:aws:secretsmanager:us-west-2:123456789012:secret:my-secret-AbCdEf";
    private static final String DEV_PROFILE = "dev";
    private static final String GRANTED_PROFILE = "ENGG/DevAdmin-Dev";

    @SystemStub
    private EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @TempDir
    private Path tempDir;

    private SecretCache defaultCache;

    private ProfileAwareDriver sut;

    /**
     * Records every (profile, region) passed to <code>createSecretCache</code> and returns mock caches whose secret
     * username is the secret ID.
     */
    static class ProfileAwareDriver extends AWSSecretsManagerDummyDriver {

        final List<CreatedCache> createdCaches = Collections.synchronizedList(new ArrayList<>());
        final List<SecretCache> caches = Collections.synchronizedList(new ArrayList<>());
        boolean failCreation;

        ProfileAwareDriver(SecretCache cache) {
            super(cache);
        }

        @Override
        protected SecretCache createSecretCache(String awsProfile, Region region) {
            if (failCreation) {
                throw SdkClientException.create("Unable to load region");
            }
            createdCaches.add(created(awsProfile, region));
            SecretCache cache = mockCache();
            caches.add(cache);
            return cache;
        }
    }

    /**
     * Arguments of one <code>createSecretCache</code> call.
     */
    static final class CreatedCache {
        private final String profile;
        private final Region region;

        CreatedCache(String profile, Region region) {
            this.profile = profile;
            this.region = region;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CreatedCache)) {
                return false;
            }
            CreatedCache other = (CreatedCache) o;
            return Objects.equals(profile, other.profile) && Objects.equals(region, other.region);
        }

        @Override
        public int hashCode() {
            return Objects.hash(profile, region);
        }

        @Override
        public String toString() {
            return "(profile=" + profile + ", region=" + region + ")";
        }
    }

    private static CreatedCache created(String profile, Region region) {
        return new CreatedCache(profile, region);
    }

    private static SecretCache mockCache() {
        SecretCache cache = mock(SecretCache.class);
        when(cache.getSecretString(anyString())).thenAnswer(invocation -> String.format(
                "{\"username\": \"%s\", \"password\": \"pass\", \"host\": \"dbhost\"}",
                (String) invocation.getArgument(0)));
        return cache;
    }

    @BeforeEach
    public void setup() throws IOException {
        System.clearProperty("drivers.dummy.realDriverClass");
        Path configFile = tempDir.resolve("config");
        Files.write(configFile, ("[profile " + DEV_PROFILE + "]\n"
                + "region = eu-west-1\n"
                + "[profile " + GRANTED_PROFILE + "]\n"
                + "granted_sso_region = us-east-2\n"
                + "credential_process = granted credential-process --profile " + GRANTED_PROFILE + "\n")
                .getBytes(StandardCharsets.UTF_8));
        environmentVariables
                .set("AWS_CONFIG_FILE", configFile.toString())
                .set("AWS_SHARED_CREDENTIALS_FILE", tempDir.resolve("credentials").toString())
                .remove("AWS_SECRET_JDBC_REGION")
                .remove("AWS_PROFILE");

        defaultCache = mockCache();
        sut = new ProfileAwareDriver(defaultCache);
        DummyDriver.reset();
    }

    @AfterEach
    public void tearDown() throws SQLException {
        DriverManager.deregisterDriver(sut);
    }

    private static Properties props(String... keyValues) {
        Properties props = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            props.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return props;
    }

    private static void assertNoAwsProperties(Properties props) {
        assertFalse(props.containsKey(PROPERTY_AWS_PROFILE));
        assertFalse(props.containsKey(PROPERTY_AWS_REGION));
    }

    /*******************************************************************************************************************
     * Cache selection
     ******************************************************************************************************************/

    @Test
    public void test_connect_usesDefaultCache_withoutAwsProperties() throws SQLException {
        sut.connect(JDBC_URL, props("user", SECRET_NAME));

        verify(defaultCache).getSecretString(SECRET_NAME);
        assertTrue(sut.createdCaches.isEmpty());
    }

    @Test
    public void test_connect_usesProfileRegion_withProfileOnly() throws SQLException {
        sut.connect(JDBC_URL, props("user", SECRET_NAME, PROPERTY_AWS_PROFILE, DEV_PROFILE));

        assertEquals(Collections.singletonList(created(DEV_PROFILE, Region.EU_WEST_1)), sut.createdCaches);
        verify(sut.caches.get(0)).getSecretString(SECRET_NAME);
        verify(defaultCache, never()).getSecretString(anyString());
    }

    @Test
    public void test_connect_usesDefaultRegion_forProfileWithoutRegion() throws SQLException {
        sut.connect(JDBC_URL, props("user", SECRET_NAME, PROPERTY_AWS_PROFILE, GRANTED_PROFILE));

        assertEquals(Collections.singletonList(created(GRANTED_PROFILE, null)), sut.createdCaches);
    }

    @Test
    public void test_connect_usesDefaultCredentials_withRegionOnly() throws SQLException {
        sut.connect(JDBC_URL, props("user", SECRET_NAME, PROPERTY_AWS_REGION, "us-east-2"));

        assertEquals(Collections.singletonList(created(null, Region.US_EAST_2)), sut.createdCaches);
    }

    @Test
    public void test_connect_awsRegionOverridesProfileRegion() throws SQLException {
        sut.connect(JDBC_URL, props("user", SECRET_NAME, PROPERTY_AWS_PROFILE, DEV_PROFILE,
                PROPERTY_AWS_REGION, "us-east-2"));

        assertEquals(Collections.singletonList(created(DEV_PROFILE, Region.US_EAST_2)), sut.createdCaches);
    }

    @Test
    public void test_connect_arnRegionOverridesAwsRegion() throws SQLException {
        sut.connect(JDBC_URL, props("user", SECRET_ARN, PROPERTY_AWS_PROFILE, DEV_PROFILE,
                PROPERTY_AWS_REGION, "us-east-2"));

        assertEquals(Collections.singletonList(created(DEV_PROFILE, Region.US_WEST_2)), sut.createdCaches);
    }

    @Test
    public void test_connect_trimsValues_andIgnoresBlankValues() throws SQLException {
        sut.connect(JDBC_URL, props("user", SECRET_NAME, PROPERTY_AWS_PROFILE, "  " + DEV_PROFILE + " ",
                PROPERTY_AWS_REGION, "   "));

        assertEquals(Collections.singletonList(created(DEV_PROFILE, Region.EU_WEST_1)), sut.createdCaches);
        assertNoAwsProperties(DummyDriver.lastConnectInfo);
    }

    @Test
    public void test_connect_usesProfileCache_forSecretIdUrl() throws SQLException {
        sut.connect("my-db-secret", props("user", SECRET_NAME, PROPERTY_AWS_PROFILE, DEV_PROFILE));

        assertEquals(1, sut.createdCaches.size());
        verify(sut.caches.get(0)).getSecretString("my-db-secret");
        verify(sut.caches.get(0)).getSecretString(SECRET_NAME);
        verify(defaultCache, never()).getSecretString(anyString());
    }

    @Test
    public void test_connect_reusesCache_forSameProfileAndRegion() throws SQLException {
        sut.connect(JDBC_URL, props("user", SECRET_NAME, PROPERTY_AWS_PROFILE, DEV_PROFILE));
        sut.connect(JDBC_URL, props("user", "other-secret", PROPERTY_AWS_PROFILE, DEV_PROFILE));
        sut.connect(JDBC_URL, props("user", SECRET_NAME, PROPERTY_AWS_PROFILE, GRANTED_PROFILE));

        assertEquals(Arrays.asList(created(DEV_PROFILE, Region.EU_WEST_1), created(GRANTED_PROFILE, null)),
                sut.createdCaches);
    }

    @Test
    public void test_connect_createsOneCache_underConcurrentConnects() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Object>> tasks = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                tasks.add(() -> sut.connect(JDBC_URL, props("user", SECRET_NAME, PROPERTY_AWS_PROFILE, DEV_PROFILE)));
            }
            for (Future<Object> future : executor.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }

        assertEquals(1, sut.createdCaches.size());
    }

    @Test
    public void test_connect_refreshesProfileCache_onAuthenticationError() throws InterruptedException {
        sut.exceptionIsDueToAuth = true;

        assertThrows(SQLException.class, () -> sut.connect(JDBC_URL,
                props("user", DummyDriver.SQL_ERROR_USERNAME, PROPERTY_AWS_PROFILE, DEV_PROFILE)));

        verify(sut.caches.get(0)).refreshNow(DummyDriver.SQL_ERROR_USERNAME);
        verify(defaultCache, never()).refreshNow(anyString());
    }

    @Test
    public void test_connect_throwsSQLException_whenClientCannotBeCreated() {
        sut.failCreation = true;

        SQLException e = assertThrows(SQLException.class, () -> sut.connect(JDBC_URL,
                props("user", SECRET_NAME, PROPERTY_AWS_PROFILE, GRANTED_PROFILE)));

        assertTrue(e.getMessage().contains(PROPERTY_AWS_REGION));
        assertInstanceOf(SdkClientException.class, e.getCause());
    }

    /*******************************************************************************************************************
     * Property forwarding
     ******************************************************************************************************************/

    @Test
    public void test_connect_doesNotForwardAwsProperties_withSecretUser() throws SQLException {
        Properties info = props("user", SECRET_NAME, "xyz", "true",
                PROPERTY_AWS_PROFILE, DEV_PROFILE, PROPERTY_AWS_REGION, "us-east-2");

        sut.connect(JDBC_URL, info);

        Properties received = DummyDriver.lastConnectInfo;
        assertNoAwsProperties(received);
        assertEquals("true", received.getProperty("xyz"));
        assertEquals(SECRET_NAME, received.getProperty("user"));
        assertEquals(DEV_PROFILE, info.getProperty(PROPERTY_AWS_PROFILE));
    }

    @Test
    public void test_connect_doesNotForwardAwsProperties_withoutUser() throws SQLException {
        sut.connect(JDBC_URL, props("xyz", "true", PROPERTY_AWS_PROFILE, DEV_PROFILE));

        assertNoAwsProperties(DummyDriver.lastConnectInfo);
        assertEquals("true", DummyDriver.lastConnectInfo.getProperty("xyz"));
        assertTrue(sut.createdCaches.isEmpty());
    }

    @Test
    public void test_connect_readsAwsPropertiesFromDefaults_andKeepsOtherDefaults() throws SQLException {
        Properties info = new Properties(props(PROPERTY_AWS_PROFILE, DEV_PROFILE, "xyz", "true"));
        info.setProperty("user", SECRET_NAME);

        sut.connect(JDBC_URL, info);

        assertEquals(Collections.singletonList(created(DEV_PROFILE, Region.EU_WEST_1)), sut.createdCaches);
        assertNoAwsProperties(DummyDriver.lastConnectInfo);
        assertEquals("true", DummyDriver.lastConnectInfo.getProperty("xyz"));
    }

    @Test
    public void test_getPropertyInfo_addsAwsProperties_andStripsThemFromWrappedDriver() throws SQLException {
        DriverPropertyInfo[] result = sut.getPropertyInfo(JDBC_URL, props(PROPERTY_AWS_PROFILE, DEV_PROFILE, "xyz", "1"));

        assertEquals(2, result.length);
        assertEquals(PROPERTY_AWS_PROFILE, result[0].name);
        assertEquals(DEV_PROFILE, result[0].value);
        assertFalse(result[0].required);
        assertEquals(PROPERTY_AWS_REGION, result[1].name);
        assertNull(result[1].value);
        assertNoAwsProperties(DummyDriver.getPropertyInfoParam2);
        assertEquals("1", DummyDriver.getPropertyInfoParam2.getProperty("xyz"));
    }

    @Test
    public void test_getPropertyInfo_keepsWrappedDriverProperties() throws SQLException {
        DummyDriver.getPropertyInfoReturnValue = new DriverPropertyInfo[] {new DriverPropertyInfo("useSSL", "true")};

        DriverPropertyInfo[] result = sut.getPropertyInfo(JDBC_URL, new Properties());

        assertEquals(3, result.length);
        assertEquals("useSSL", result[0].name);
        assertEquals("true", result[0].value);
        assertEquals(PROPERTY_AWS_PROFILE, result[1].name);
        assertEquals(PROPERTY_AWS_REGION, result[2].name);
    }

    /*******************************************************************************************************************
     * Lifecycle
     ******************************************************************************************************************/

    @Test
    public void test_deregister_closesAllCaches() throws SQLException {
        sut.connect(JDBC_URL, props("user", SECRET_NAME, PROPERTY_AWS_PROFILE, DEV_PROFILE));
        sut.connect(JDBC_URL, props("user", SECRET_NAME, PROPERTY_AWS_REGION, "us-east-2"));

        DriverManager.deregisterDriver(sut);

        verify(defaultCache).close();
        verify(sut.caches.get(0)).close();
        verify(sut.caches.get(1)).close();
    }

    /*******************************************************************************************************************
     * Client configuration
     ******************************************************************************************************************/

    @Test
    public void test_createSecretCache_buildsCacheWithoutCallingAws() throws SQLException {
        AWSSecretsManagerDummyDriver driver = new AWSSecretsManagerDummyDriver(defaultCache);
        try {
            SecretCache cache = assertDoesNotThrow(() -> driver.createSecretCache(GRANTED_PROFILE, Region.EU_WEST_1));

            assertNotNull(cache);
            cache.close();
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    @Test
    public void test_createClientBuilder_usesProfileCredentialsAndRegion() {
        SecretsManagerClient client = sut.createClientBuilder(GRANTED_PROFILE, Region.EU_WEST_1).build();

        assertEquals(Region.EU_WEST_1, client.serviceClientConfiguration().region());
        assertInstanceOf(ProfileCredentialsProvider.class, client.serviceClientConfiguration().credentialsProvider());
    }

    @Test
    public void test_createClientBuilder_usesDefaultCredentials_withoutProfile() {
        SecretsManagerClient client = sut.createClientBuilder(null, Region.EU_WEST_1).build();

        assertFalse(client.serviceClientConfiguration().credentialsProvider() instanceof ProfileCredentialsProvider);
    }

    @Test
    public void test_createClientBuilder_fallsBackToDriverRegion_withoutRegion() {
        environmentVariables.set("AWS_SECRET_JDBC_REGION", "ca-central-1");

        SecretsManagerClient client = assertDoesNotThrow(() -> sut.createClientBuilder(DEV_PROFILE, null).build());

        assertEquals(Region.CA_CENTRAL_1, client.serviceClientConfiguration().region());
    }

    @Test
    public void test_createClientBuilder_resolvesContainerCredentials_withoutProfile() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/credentials", exchange -> {
            boolean authorized = "container-token".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("{\"AccessKeyId\": \"AKIDCONTAINER\", \"SecretAccessKey\": \"secret\","
                    + " \"Token\": \"session\", \"Expiration\": \"2099-01-01T00:00:00Z\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(authorized ? 200 : 401, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            environmentVariables
                    .remove("AWS_ACCESS_KEY_ID")
                    .remove("AWS_SECRET_ACCESS_KEY")
                    .remove("AWS_SESSION_TOKEN")
                    .remove("AWS_WEB_IDENTITY_TOKEN_FILE")
                    .remove("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI")
                    .set("AWS_CONTAINER_CREDENTIALS_FULL_URI",
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/credentials")
                    .set("AWS_CONTAINER_AUTHORIZATION_TOKEN", "container-token");

            SecretsManagerClient client = sut.createClientBuilder(null, Region.EU_WEST_1).build();
            AwsCredentialsIdentity credentials = client.serviceClientConfiguration().credentialsProvider()
                    .resolveIdentity().join();

            assertEquals("AKIDCONTAINER", credentials.accessKeyId());
        } finally {
            server.stop(0);
        }
    }
}
