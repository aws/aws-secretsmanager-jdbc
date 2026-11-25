package com.amazonaws.secretsmanager.util;

import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.profiles.ProfileProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.StringUtils;

/**
 * Resolves the Secrets Manager region from a secret ARN or from a named AWS profile.
 */
public final class RegionResolver {

    private RegionResolver() {
    }

    /**
     * Extracts the region from a secret ARN (<code>arn:&lt;partition&gt;:secretsmanager:&lt;region&gt;:...</code>).
     *
     * @param secretId                                          Secret name or ARN.
     *
     * @return String                                           The ARN region, or null if the secret ID is not an ARN.
     */
    public static String regionFromArn(String secretId) {
        if (secretId == null || !secretId.startsWith("arn:")) {
            return null;
        }
        String[] parts = secretId.split(":");
        return parts.length > 3 ? StringUtils.trimToNull(parts[3]) : null;
    }

    /**
     * Reads the <code>region</code> property of a profile.
     *
     * @param profileFile                                       The AWS profile file to read.
     * @param profileName                                       The profile name.
     *
     * @return Region                                           The profile region, or null if the profile or its region
     *                                                          is not defined.
     */
    public static Region profileRegion(ProfileFile profileFile, String profileName) {
        if (profileName == null) {
            return null;
        }
        return profileFile.profile(profileName)
                .flatMap(profile -> profile.property(ProfileProperty.REGION))
                .map(StringUtils::trimToNull)
                .map(Region::of)
                .orElse(null);
    }
}
