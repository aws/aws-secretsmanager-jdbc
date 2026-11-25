package com.amazonaws.secretsmanager.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;

public class RegionResolverTest {

    private static final ProfileFile PROFILE_FILE = configFile(
            "[default]\n"
            + "region = us-west-1\n"
            + "[profile with-region]\n"
            + "region = eu-west-1\n"
            + "[profile ENGG/DevAdmin-Dev]\n"
            + "region = ap-southeast-2\n"
            + "[profile granted]\n"
            + "granted_sso_region = us-east-2\n"
            + "credential_process = granted credential-process --profile granted\n");

    private static ProfileFile configFile(String content) {
        return ProfileFile.builder()
                .content(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
                .type(ProfileFile.Type.CONFIGURATION)
                .build();
    }

    @ParameterizedTest
    @CsvSource({
            "arn:aws:secretsmanager:us-east-1:123456789012:secret:my-secret-AbCdEf, us-east-1",
            "arn:aws-cn:secretsmanager:cn-north-1:123456789012:secret:my-secret-AbCdEf, cn-north-1",
            "arn:aws-us-gov:secretsmanager:us-gov-west-1:123456789012:secret:my-secret-AbCdEf, us-gov-west-1"
    })
    public void test_regionFromArn_returnsArnRegion(String arn, String expectedRegion) {
        assertEquals(expectedRegion, RegionResolver.regionFromArn(arn));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"my-secret", "prod/db/credentials", "arn:aws:secretsmanager", "arn:aws:secretsmanager::1:x"})
    public void test_regionFromArn_returnsNull_whenNoArnRegion(String secretId) {
        assertNull(RegionResolver.regionFromArn(secretId));
    }

    @Test
    public void test_profileRegion_returnsProfileRegion() {
        assertEquals(Region.EU_WEST_1, RegionResolver.profileRegion(PROFILE_FILE, "with-region"));
    }

    @Test
    public void test_profileRegion_supportsSlashInProfileName() {
        assertEquals(Region.AP_SOUTHEAST_2, RegionResolver.profileRegion(PROFILE_FILE, "ENGG/DevAdmin-Dev"));
    }

    @Test
    public void test_profileRegion_returnsNull_forGrantedProfileWithoutRegion() {
        assertNull(RegionResolver.profileRegion(PROFILE_FILE, "granted"));
    }

    @Test
    public void test_profileRegion_returnsNull_forUnknownProfile() {
        assertNull(RegionResolver.profileRegion(PROFILE_FILE, "unknown"));
    }

    @Test
    public void test_profileRegion_returnsNull_forNullProfile() {
        assertNull(RegionResolver.profileRegion(PROFILE_FILE, null));
    }
}
