
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
                            + "an HMAC access key id and secret access key as user name and password), the '%s' or "
                            + "'aws.accessKeyId' system properties or the '%s' or 'AWS_ACCESS_KEY_ID' environment "
                            + "variables", CosUtils.API_KEY_PROPERTY, CosUtils.API_KEY_VARIABLE));
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
        AWSCredentials credentials = getCredentialsQuietly(new SystemPropertiesCredentialsProvider());

        return credentials != null ? credentials : getCredentialsQuietly(new EnvironmentVariableCredentialsProvider());
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
