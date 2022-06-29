package com.amazonaws.secretsmanager.util;


import com.amazonaws.secretsmanager.sql.AWSSecretsManagerDriver;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;


import static org.mockito.Mockito.when;
import static org.junit.Assert.*;
import org.powermock.api.mockito.PowerMockito;


import static com.amazonaws.secretsmanager.util.JDBCSecretCacheBuilderProvider.*;


@RunWith(PowerMockRunner.class)
@PrepareForTest({Config.class, System.class, JDBCSecretCacheBuilderProvider.class})
public class JDBCSecretCacheBuilderProviderTest {

    /**
     * SetRegion Tests.
     */
    @Test
    public void test_setRegion_configFileProperty() {
        Config configProvider = PowerMockito.mock(Config.class);
        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+ JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("asdf");

        AWSSecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider(configProvider).build();

        assertEquals("asdf", builder.getRegion());
    }


    @Test
    public void test_setRegion_environmentVariable() {
        Config configProvider = PowerMockito.mock(Config.class);
        PowerMockito.mockStatic(System.class);

        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;
        when(System.getenv(environmentRegionName)).thenReturn("asdf");
        assertEquals("asdf", System.getenv(environmentRegionName));

        AWSSecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider(configProvider).build();
        assertEquals("asdf", builder.getRegion());
    }


    @Test
    public void test_setRegion_vpcEndpoint() {
        Config configProvider = PowerMockito.mock(Config.class);
        String vpcEndpointUrlName = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_URL;
        String vpcEndpointRegion = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_REGION;
        when(configProvider.getStringPropertyWithDefault(vpcEndpointUrlName, null)).thenReturn("asdf");
        when(configProvider.getStringPropertyWithDefault(vpcEndpointRegion, null)).thenReturn("qwerty");

        AWSSecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider(configProvider).build();

        assertEquals("asdf", builder.getEndpoint().getServiceEndpoint());
        assertEquals("qwerty", builder.getEndpoint().getSigningRegion());
    }


    @Test
    public void test_setRegion_defaultsToNull() {
        AWSSecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider().build();
        assertNull(builder.getRegion());
    }

    /**
     * SetRegion priority tests.
     */

    @Test
    public void test_regionSelectionOrder_prefersVpcEndpointOverEverything() {
        Config configProvider = PowerMockito.mock(Config.class);
        PowerMockito.mockStatic(System.class);

        //Arrange so all properties return something valid.
        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+ JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        String vpcEndpointUrlName = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_URL;
        String vpcEndpointRegion = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_REGION;
        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;

        //Arrange the return values when the properties are requested.
        when(System.getenv(environmentRegionName)).thenReturn("0");
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("1");
        when(configProvider.getStringPropertyWithDefault(vpcEndpointUrlName, null)).thenReturn("2");
        when(configProvider.getStringPropertyWithDefault(vpcEndpointRegion, null)).thenReturn("3");

        //Act: Build our clientbuilder.
        AWSSecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider(configProvider).build();

        //Assert: Make sure the endpoint was configured properly.
        assertEquals("3", builder.getEndpoint().getSigningRegion());
        assertEquals("2", builder.getEndpoint().getServiceEndpoint());
        assertNotEquals("1", builder.getRegion());
        assertNotEquals("0", builder.getRegion());
    }



    @Test
    public void test_regionSelectionOrder_prefersEnvironmentVarOverConfig() {
        Config configProvider = PowerMockito.mock(Config.class);
        PowerMockito.mockStatic(System.class);

        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+ JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;

        when(System.getenv(environmentRegionName)).thenReturn("0");
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("1");

        AWSSecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider(configProvider).build();

        assertNotEquals("1", builder.getRegion());
        assertEquals("0", builder.getRegion());
    }


    /**
     * Variables must be correctly set
     */
    @Test
    public void test_settingValidation_emptyConfigPropertyIgnored() {

        Config configProvider = PowerMockito.mock(Config.class);
        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+ JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("");

        AWSSecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider(configProvider).build();

        assertNull(builder.getRegion());
    }

    @Test
    public void test_settingValidation_nullConfigPropertyIgnored() {

        Config configProvider = PowerMockito.mock(Config.class);
        String regionName = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+ JDBCSecretCacheBuilderProvider.PROPERTY_REGION;
        when(configProvider.getStringPropertyWithDefault(regionName, null)).thenReturn("");

        AWSSecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider(configProvider).build();

        assertNull(builder.getRegion());
    }

    @Test
    public void test_settingValidation_emptyEnvironmentVariableIgnored() {

        Config configProvider = PowerMockito.mock(Config.class);
        PowerMockito.mockStatic(System.class);

        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;
        when(System.getenv(environmentRegionName)).thenReturn("");

        AWSSecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider(configProvider).build();

        assertNull(builder.getRegion());
    }


    @Test
    public void test_settingValidation_nullEnvironmentVariableIgnored() {

        Config configProvider = PowerMockito.mock(Config.class);
        PowerMockito.mockStatic(System.class);

        String environmentRegionName = JDBCSecretCacheBuilderProvider.REGION_ENVIRONMENT_VARIABLE;
        when(System.getenv(environmentRegionName)).thenReturn(null);

        AWSSecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider(configProvider).build();

        assertNull(builder.getRegion());
    }




    @Test
    public void test_settingValidation_emptyVpcIgnored() {

        Config configProvider = PowerMockito.mock(Config.class);
        String vpcEndpointUrlName = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_URL;
        String vpcEndpointRegion = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_REGION;
        when(configProvider.getStringPropertyWithDefault(vpcEndpointUrlName, null)).thenReturn("");
        when(configProvider.getStringPropertyWithDefault(vpcEndpointRegion, null)).thenReturn("");

        AWSSecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider(configProvider).build();

        assertNull(builder.getEndpoint());
    }


    @Test
    public void test_settingValidation_nullVpcIgnored() {

        Config configProvider = PowerMockito.mock(Config.class);
        String vpcEndpointUrlName = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_URL;
        String vpcEndpointRegion = AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_REGION;
        when(configProvider.getStringPropertyWithDefault(vpcEndpointUrlName, null)).thenReturn("");
        when(configProvider.getStringPropertyWithDefault(vpcEndpointRegion, null)).thenReturn("");

        AWSSecretsManagerClientBuilder builder = new JDBCSecretCacheBuilderProvider(configProvider).build();

        assertNull(builder.getEndpoint());
    }

}
