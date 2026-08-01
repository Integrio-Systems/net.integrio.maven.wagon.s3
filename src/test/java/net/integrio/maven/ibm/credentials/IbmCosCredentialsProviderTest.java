package net.integrio.maven.ibm.credentials;

import com.ibm.cloud.objectstorage.SDKGlobalConfiguration;
import com.ibm.cloud.objectstorage.SdkClientException;
import com.ibm.cloud.objectstorage.auth.AWSCredentials;
import com.ibm.cloud.objectstorage.oauth.BasicIBMOAuthCredentials;
import net.integrio.maven.ibm.util.CosUtils;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Covers the resolution order of the credentials. The prefixed names are set as system properties
 * because the environment of a running jvm cannot be changed; both are read by the same
 * {@link CosUtils#getSetting} and the property is only ever preferred over the variable.
 */
final class IbmCosCredentialsProviderTest {

    private static final String ACCESS_KEY = "0123456789abcdef0123456789abcdef";

    private static final String SECRET_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef";

    @AfterEach
    void clearCredentials() {
        System.clearProperty(CosUtils.ACCESS_KEY_PROPERTY);
        System.clearProperty(CosUtils.SECRET_KEY_PROPERTY);
        System.clearProperty(CosUtils.API_KEY_PROPERTY);
        System.clearProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY);
        System.clearProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY);
    }

    @Test
    void hmacCredentialsOfThePrefixedProperties() {
        System.setProperty(CosUtils.ACCESS_KEY_PROPERTY, ACCESS_KEY);
        System.setProperty(CosUtils.SECRET_KEY_PROPERTY, SECRET_KEY);

        AWSCredentials credentials = provider(null).getCredentials();

        assertEquals(ACCESS_KEY, credentials.getAWSAccessKeyId());
        assertEquals(SECRET_KEY, credentials.getAWSSecretKey());
    }

    @Test
    void prefixedHmacCredentialsWinOverTheUnprefixedAwsOnes() {
        System.setProperty(CosUtils.ACCESS_KEY_PROPERTY, ACCESS_KEY);
        System.setProperty(CosUtils.SECRET_KEY_PROPERTY, SECRET_KEY);
        System.setProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, "AKIAIOSFODNN7EXAMPLE");
        System.setProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY, "aws-secret");

        assertEquals(ACCESS_KEY, provider(null).getCredentials().getAWSAccessKeyId());
    }

    @Test
    void unprefixedAwsCredentialsStayASupportedFallback() {
        System.setProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, "AKIAIOSFODNN7EXAMPLE");
        System.setProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY, "aws-secret");

        assertEquals("AKIAIOSFODNN7EXAMPLE", provider(null).getCredentials().getAWSAccessKeyId());
    }

    @Test
    void halfAPrefixedHmacPairNamesTheMissingHalf() {
        System.setProperty(CosUtils.ACCESS_KEY_PROPERTY, ACCESS_KEY);

        SdkClientException e = assertThrows(SdkClientException.class, () -> provider(null).getCredentials());

        assertTrue(e.getMessage().contains(CosUtils.SECRET_KEY_PROPERTY), e.getMessage());
        assertTrue(e.getMessage().contains(CosUtils.SECRET_KEY_VARIABLE), e.getMessage());
    }

    @Test
    void halfAPrefixedHmacPairDoesNotFallBackToTheAwsKeys() {
        System.setProperty(CosUtils.SECRET_KEY_PROPERTY, SECRET_KEY);
        System.setProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, "AKIAIOSFODNN7EXAMPLE");
        System.setProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY, "aws-secret");

        SdkClientException e = assertThrows(SdkClientException.class, () -> provider(null).getCredentials());

        assertTrue(e.getMessage().contains(CosUtils.ACCESS_KEY_PROPERTY), e.getMessage());
    }

    @Test
    void theApiKeyWinsOverPrefixedHmacCredentials() {
        System.setProperty(CosUtils.API_KEY_PROPERTY, "my-api-key");
        System.setProperty(CosUtils.ACCESS_KEY_PROPERTY, ACCESS_KEY);
        System.setProperty(CosUtils.SECRET_KEY_PROPERTY, SECRET_KEY);

        assertInstanceOf(BasicIBMOAuthCredentials.class, provider(null).getCredentials());
    }

    @Test
    void settingsXmlWinsOverThePrefixedProperties() {
        System.setProperty(CosUtils.ACCESS_KEY_PROPERTY, ACCESS_KEY);
        System.setProperty(CosUtils.SECRET_KEY_PROPERTY, SECRET_KEY);

        AWSCredentials credentials = provider(authentication("0123456789ABCDEFGHIJ", "secret-access-key"))
                .getCredentials();

        assertEquals("0123456789ABCDEFGHIJ", credentials.getAWSAccessKeyId());
        assertEquals("secret-access-key", credentials.getAWSSecretKey());
    }

    @Test
    void withoutAnyCredentialsTheErrorListsThePrefixedNames() {
        assumeTrue(System.getenv(CosUtils.ACCESS_KEY_VARIABLE) == null
                        && System.getenv(SDKGlobalConfiguration.ACCESS_KEY_ENV_VAR) == null
                        && System.getenv(SDKGlobalConfiguration.ALTERNATE_ACCESS_KEY_ENV_VAR) == null
                        && System.getenv(CosUtils.API_KEY_VARIABLE) == null,
                "the credentials of the environment would be picked up");

        SdkClientException e = assertThrows(SdkClientException.class, () -> provider(null).getCredentials());

        assertTrue(e.getMessage().contains(CosUtils.ACCESS_KEY_PROPERTY), e.getMessage());
        assertTrue(e.getMessage().contains(CosUtils.SECRET_KEY_VARIABLE), e.getMessage());
        assertTrue(e.getMessage().contains(CosUtils.API_KEY_PROPERTY), e.getMessage());
    }

    private static IbmCosCredentialsProvider provider(AuthenticationInfo authenticationInfo) {
        return new IbmCosCredentialsProvider(authenticationInfo, null, null, null);
    }

    private static AuthenticationInfo authentication(String userName, String password) {
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setUserName(userName);
        authenticationInfo.setPassword(password);

        return authenticationInfo;
    }
}
