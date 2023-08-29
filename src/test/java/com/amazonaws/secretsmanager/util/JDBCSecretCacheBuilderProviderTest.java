package com.amazonaws.secretsmanager.util;

import static com.amazonaws.secretsmanager.util.JDBCSecretCacheBuilderProvider.PROPERTY_VPC_ENDPOINT_REGION;
import static com.amazonaws.secretsmanager.util.JDBCSecretCacheBuilderProvider.PROPERTY_VPC_ENDPOINT_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.amazonaws.secretsmanager.sql.AWSSecretsManagerDriver;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

public class JDBCSecretCacheBuilderProviderTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    /**
     * SetRegion Tests.
     */
    @Test
    public void test_setRegion_configFileProperty() {
        Config configProvider = mock(Config.class);
        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("us-west-2");

        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();

        assertEquals(client.serviceClientConfiguration().region(), Region.US_WEST_2);
    }

    @Test
    public void test_setRegion_environmentVariable() {
        Config configProvider = mock(Config.class);

        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;
        environmentVariables.set(environmentRegionName, "us-east-1");
        assertEquals("us-east-1", System.getenv(environmentRegionName));

        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();
        assertEquals(client.serviceClientConfiguration().region(), Region.US_EAST_1);
    }

    @Test
    public void test_setRegion_vpcEndpoint() {
        Config configProvider = mock(Config.class);
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
        Config configProvider = mock(Config.class);

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
        Config configProvider = mock(Config.class);

        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "."
                + JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;

        environmentVariables.set(environmentRegionName, "eu-west-3");
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("us-east-2");

        SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();

        assertNotEquals(client.serviceClientConfiguration().region(), Region.US_EAST_2);
        assertEquals(client.serviceClientConfiguration().region(), Region.EU_WEST_3);
    }

    /**
     * Variables must be correctly set
     */
    @Test
    public void test_settingValidation_emptyConfigPropertyIgnored() {

        Config configProvider = mock(Config.class);
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

        Config configProvider = mock(Config.class);
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

        Config configProvider = mock(Config.class);

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

        Config configProvider = mock(Config.class);

        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;
        environmentVariables.clear(environmentRegionName);

        try {
            new JDBCSecretCacheBuilderProvider(configProvider).build().build();
        } catch (SdkClientException e) {
            assertTrue(e.getMessage().startsWith("Unable to load region from any of the providers in the chain"));
        }
    }

    @Test
    public void test_settingValidation_emptyVpcIgnored() {

        Config configProvider = mock(Config.class);
        String vpcEndpointUrlName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_URL;
        String vpcEndpointRegion = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_REGION;
        when(configProvider.getStringPropertyWithDefault(vpcEndpointUrlName, null)).thenReturn("");
        when(configProvider.getStringPropertyWithDefault(vpcEndpointRegion, null)).thenReturn("");

        try {
            SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();
            assertTrue(client.serviceClientConfiguration().endpointOverride().isEmpty());
        } catch (SdkClientException e) {
            assertTrue(e.getMessage().startsWith("Unable to load region from any of the providers in the chain"));
        }
    }

    @Test
    public void test_settingValidation_nullVpcIgnored() {

        Config configProvider = mock(Config.class);
        String vpcEndpointUrlName = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_URL;
        String vpcEndpointRegion = AWSSecretsManagerDriver.PROPERTY_PREFIX + "." + PROPERTY_VPC_ENDPOINT_REGION;
        when(configProvider.getStringPropertyWithDefault(vpcEndpointUrlName, null)).thenReturn(null);
        when(configProvider.getStringPropertyWithDefault(vpcEndpointRegion, null)).thenReturn(null);

        try {
            SecretsManagerClient client = new JDBCSecretCacheBuilderProvider(configProvider).build().build();
            assertTrue(client.serviceClientConfiguration().endpointOverride().isEmpty());
        } catch (SdkClientException e) {
            assertTrue(e.getMessage().startsWith("Unable to load region from any of the providers in the chain"));
        }
    }

}
