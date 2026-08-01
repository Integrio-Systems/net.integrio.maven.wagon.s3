
package net.integrio.maven.ibm.credentials;

import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.SdkClientException;
import com.ibm.cloud.objectstorage.auth.AWSCredentials;
import com.ibm.cloud.objectstorage.auth.AWSCredentialsProvider;
import com.ibm.cloud.objectstorage.auth.BasicAWSCredentials;
import com.ibm.cloud.objectstorage.auth.EnvironmentVariableCredentialsProvider;
import com.ibm.cloud.objectstorage.auth.SystemPropertiesCredentialsProvider;
import com.ibm.cloud.objectstorage.oauth.BasicIBMOAuthCredentials;
import com.ibm.cloud.objectstorage.oauth.DefaultTokenManager;
import com.ibm.cloud.objectstorage.oauth.TokenManager;
import net.integrio.maven.ibm.util.CosUtils;
import org.apache.maven.wagon.authentication.AuthenticationInfo;

import static org.apache.commons.lang3.StringUtils.equalsAnyIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Resolves either IAM (API key) or HMAC credentials for IBM Cloud Object Storage, looking at the
 * {@code server} entry of {@code settings.xml} first and falling back to system properties and
 * environment variables.
 * <p>
 * HMAC credentials carry the {@code ibm.cos} prefix ({@value CosUtils#ACCESS_KEY_PROPERTY} and
 * {@value CosUtils#SECRET_KEY_PROPERTY}, or {@value CosUtils#ACCESS_KEY_VARIABLE} and
 * {@value CosUtils#SECRET_KEY_VARIABLE}), so a build that deploys to both S3 and COS keeps the two
 * sets of keys apart. The unprefixed {@code aws.*} names stay supported as a fallback.
 * <p>
 * The chain of the SDK is deliberately not reused: it keeps a credential only when it exposes an
 * access key id, which IAM credentials never do.
 */
public class IbmCosCredentialsProvider implements AWSCredentialsProvider {

    /** user names in {@code settings.xml} that mark the password as an IAM API key */
    private static final String[] API_KEY_USER_NAMES = {"apikey", "iam", "iam-apikey", "ibm-apikey"};

    private final AuthenticationInfo authenticationInfo;

    private final String serviceInstanceId;

    private final String iamEndpoint;

    private final ClientConfiguration clientConfiguration;

    /**
     * IAM credentials carry the token manager that caches the bearer token, so the credentials have
     * to be resolved once instead of on every request.
     */
    private volatile AWSCredentials credentials;

    public IbmCosCredentialsProvider(AuthenticationInfo authenticationInfo, String serviceInstanceId,
                                     String iamEndpoint, ClientConfiguration clientConfiguration) {
        this.authenticationInfo = authenticationInfo;
        this.serviceInstanceId = serviceInstanceId;
        this.iamEndpoint = iamEndpoint;
        this.clientConfiguration = clientConfiguration;
    }

    @Override
    public AWSCredentials getCredentials() {
        AWSCredentials resolved = this.credentials;

        if (resolved == null) {
            synchronized (this) {
                resolved = this.credentials;
                if (resolved == null) {
                    resolved = resolveCredentials();
                    this.credentials = resolved;
                }
            }
        }

        return resolved;
    }

    @Override
    public void refresh() {
        this.credentials = null;
    }

    private AWSCredentials resolveCredentials() {
        AWSCredentials credentials = getServerCredentials();

        if (credentials == null) {
            credentials = getApiKeyCredentials();
        }

        if (credentials == null) {
            credentials = getHmacCredentials();
        }

        if (credentials == null) {
            throw new SdkClientException(String.format("Unable to load IBM Cloud Object Storage credentials from the "
                            + "server entry in settings.xml (an IAM API key as the password of the 'apikey' user, or "
                            + "an HMAC access key id and secret access key as user name and password), from an API key "
                            + "in %s or from HMAC keys in %s and %s (or in the unprefixed 'aws.accessKeyId' and "
                            + "'aws.secretKey' system properties or 'AWS_ACCESS_KEY_ID' and 'AWS_SECRET_ACCESS_KEY' "
                            + "environment variables)",
                    describe(CosUtils.API_KEY_PROPERTY, CosUtils.API_KEY_VARIABLE),
                    describe(CosUtils.ACCESS_KEY_PROPERTY, CosUtils.ACCESS_KEY_VARIABLE),
                    describe(CosUtils.SECRET_KEY_PROPERTY, CosUtils.SECRET_KEY_VARIABLE)));
        }

        return credentials;
    }

    private AWSCredentials getServerCredentials() {
        if (this.authenticationInfo == null) {
            return null;
        }

        String userName = this.authenticationInfo.getUserName();
        String password = this.authenticationInfo.getPassword();

        if (isBlank(password)) {
            return null;
        }

        if (isBlank(userName) || equalsAnyIgnoreCase(userName, API_KEY_USER_NAMES)) {
            return getOAuthCredentials(password);
        }

        return new BasicAWSCredentials(userName, password);
    }

    private AWSCredentials getApiKeyCredentials() {
        String apiKey = CosUtils.getSetting(null, CosUtils.API_KEY_PROPERTY, CosUtils.API_KEY_VARIABLE);

        return isNotBlank(apiKey) ? getOAuthCredentials(apiKey) : null;
    }

    private AWSCredentials getOAuthCredentials(String apiKey) {
        BasicIBMOAuthCredentials credentials = new BasicIBMOAuthCredentials(apiKey,
                CosUtils.getSetting(this.serviceInstanceId, CosUtils.SERVICE_INSTANCE_ID_PROPERTY,
                        CosUtils.SERVICE_INSTANCE_ID_VARIABLE));

        TokenManager tokenManager = credentials.getTokenManager();
        if (tokenManager instanceof DefaultTokenManager) {
            DefaultTokenManager defaultTokenManager = (DefaultTokenManager) tokenManager;

            // let the token requests use the proxy and the timeouts of the repository
            if (this.clientConfiguration != null) {
                defaultTokenManager.setClientConfiguration(this.clientConfiguration);
            }

            String endpoint = CosUtils.getSetting(this.iamEndpoint, CosUtils.IAM_ENDPOINT_PROPERTY,
                    CosUtils.IAM_ENDPOINT_VARIABLE);
            if (isNotBlank(endpoint)) {
                defaultTokenManager.setIamEndpoint(endpoint);
            }
        }

        return credentials;
    }

    private static AWSCredentials getHmacCredentials() {
        AWSCredentials credentials = getPrefixedHmacCredentials();

        if (credentials == null) {
            credentials = getCredentialsQuietly(new SystemPropertiesCredentialsProvider());
        }

        return credentials != null ? credentials : getCredentialsQuietly(new EnvironmentVariableCredentialsProvider());
    }

    /**
     * HMAC credentials under the {@code ibm.cos} prefix, which never collide with the keys of an AWS
     * account. Half a pair is always a mistake, so it is reported instead of silently falling
     * through to the unprefixed names.
     */
    private static AWSCredentials getPrefixedHmacCredentials() {
        String accessKey = CosUtils.getSetting(null, CosUtils.ACCESS_KEY_PROPERTY, CosUtils.ACCESS_KEY_VARIABLE);
        String secretKey = CosUtils.getSetting(null, CosUtils.SECRET_KEY_PROPERTY, CosUtils.SECRET_KEY_VARIABLE);

        if (isBlank(accessKey) && isBlank(secretKey)) {
            return null;
        }

        if (isBlank(accessKey) || isBlank(secretKey)) {
            String present = isBlank(accessKey)
                    ? describe(CosUtils.SECRET_KEY_PROPERTY, CosUtils.SECRET_KEY_VARIABLE)
                    : describe(CosUtils.ACCESS_KEY_PROPERTY, CosUtils.ACCESS_KEY_VARIABLE);
            String missing = isBlank(accessKey)
                    ? describe(CosUtils.ACCESS_KEY_PROPERTY, CosUtils.ACCESS_KEY_VARIABLE)
                    : describe(CosUtils.SECRET_KEY_PROPERTY, CosUtils.SECRET_KEY_VARIABLE);

            throw new SdkClientException(String.format("Incomplete HMAC credentials for IBM Cloud Object Storage: %s "
                    + "is set without %s", present, missing));
        }

        return new BasicAWSCredentials(accessKey, secretKey);
    }

    private static String describe(String systemProperty, String environmentVariable) {
        return String.format("the '%s' system property (or the '%s' environment variable)", systemProperty,
                environmentVariable);
    }

    private static AWSCredentials getCredentialsQuietly(AWSCredentialsProvider credentialsProvider) {
        try {
            AWSCredentials credentials = credentialsProvider.getCredentials();

            return credentials != null && isNotBlank(credentials.getAWSAccessKeyId()) ? credentials : null;
        } catch (RuntimeException e) {
            // nothing configured for this provider, the next one in line gets a chance
            return null;
        }
    }
}
