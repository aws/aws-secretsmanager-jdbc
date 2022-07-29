package com.amazonaws.secretsmanager.util;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.secretsmanager.sql.AWSSecretsManagerDriver;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.util.StringUtils;

import static com.amazonaws.util.StringUtils.isNullOrEmpty;

/**
 * <p>
 *  A class for providing JDBC driver the secrets cache builder.
 *
 * Checks the config file and environment variables for overrides to the default
 * region and applies those changes to the provided secret cache builder.
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

    static final String REGION_ENVIRONMENT_VARIABLE = "AWS_SECRET_JDBC_REGION";


    private Config configFile;


    public JDBCSecretCacheBuilderProvider() {
        this(Config.loadMainConfig());
    }

    public JDBCSecretCacheBuilderProvider(Config config) {
        configFile = config;
    }

    /**
     * Provides the secrets cache builder.
     *
     * 1) If a PrivateLink DNS endpoint URL and region are given in the Config, then they are used to configure the endpoint.
     * 2) The AWS_SECRET_JDBC_REGION environment variable is checked. If set, it is used to configure the region.
     * 3) The region variable file is checked in the provided Config and, if set, used to configure the region.
     * 4) Finally, if none of these are not found, the default region provider chain is used.
     *
     * @return the built secret cache.
     */
    public AWSSecretsManagerClientBuilder build() {

        AWSSecretsManagerClientBuilder builder = AWSSecretsManagerClientBuilder.standard();

        //Retrieve data from information sources.
        String vpcEndpointUrl = configFile.getStringPropertyWithDefault(AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_URL, null);
        String vpcEndpointRegion = configFile.getStringPropertyWithDefault(AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_VPC_ENDPOINT_REGION, null);
        String envRegion = System.getenv(REGION_ENVIRONMENT_VARIABLE);
        String configRegion = configFile.getStringPropertyWithDefault(AWSSecretsManagerDriver.PROPERTY_PREFIX+"."+PROPERTY_REGION, null);


        //Apply settings to our builder configuration.
        if ( !isNullOrEmpty(vpcEndpointUrl) && !isNullOrEmpty(vpcEndpointRegion) ) {
            builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(vpcEndpointUrl, vpcEndpointRegion));
        } else if ( !isNullOrEmpty(envRegion) ) {
            builder.withRegion(envRegion);
        } else if ( !isNullOrEmpty(configRegion) ) {
            builder.withRegion(configRegion);
        }

        return builder;
    }
}
