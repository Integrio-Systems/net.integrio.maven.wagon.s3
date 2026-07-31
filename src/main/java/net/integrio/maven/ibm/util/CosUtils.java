

package net.integrio.maven.ibm.util;

import com.ibm.cloud.objectstorage.ClientConfiguration;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.startsWithAny;
import static org.apache.commons.lang3.StringUtils.substringAfter;

/**
 * Turns a {@code cos://} repository and the surrounding configuration into the values needed to
 * talk to IBM Cloud Object Storage.
 */
public abstract class CosUtils {

    public static final String ENDPOINT_PROPERTY = "ibm.cos.endpoint";

    public static final String ENDPOINT_VARIABLE = "IBM_COS_ENDPOINT";

    public static final String REGION_PROPERTY = "ibm.cos.region";

    public static final String REGION_VARIABLE = "IBM_COS_REGION";

    public static final String API_KEY_PROPERTY = "ibm.cos.apiKey";

    public static final String API_KEY_VARIABLE = "IBM_API_KEY_ID";

    public static final String SERVICE_INSTANCE_ID_PROPERTY = "ibm.cos.serviceInstanceId";

    public static final String SERVICE_INSTANCE_ID_VARIABLE = "IBM_SERVICE_INSTANCE_ID";

    public static final String IAM_ENDPOINT_PROPERTY = "ibm.cos.iamEndpoint";

    public static final String IAM_ENDPOINT_VARIABLE = "IBM_AUTH_ENDPOINT";

    private static final String PROXY_PROTOCOL = "cos";

    /**
     * Splits a host into bucket and endpoint. Every IBM COS endpoint starts with an {@code s3}
     * label ({@code s3.us-south.cloud-object-storage.appdomain.cloud},
     * {@code s3-api.us-geo.objectstorage.softlayer.net}), so anything in front of it is the bucket.
     * The reluctant first group keeps buckets that contain dots intact.
     */
    private static final Pattern BUCKET_AND_ENDPOINT = Pattern.compile("^(.+?)\\.(s3[.-].+)$");

    private static final String PUBLIC_ENDPOINT_FORMAT = "s3.%s.cloud-object-storage.appdomain.cloud";

    /** endpoint labels that sit between the {@code s3} label and the region */
    private static final List<String> ENDPOINT_QUALIFIERS = Arrays.asList("private", "direct", "dias");

    private static final String DEFAULT_SIGNING_REGION = "us-standard";

    public static String getBucketName(Repository repository) {
        Objects.requireNonNull(repository, "repository must not be null");

        String host = repository.getHost();
        Matcher matcher = BUCKET_AND_ENDPOINT.matcher(host);

        return matcher.matches() ? matcher.group(1) : host;
    }

    /**
     * Resolves the service endpoint, in order of precedence, from the repository host, the wagon
     * configuration, the {@value #ENDPOINT_PROPERTY} system property, the
     * {@value #ENDPOINT_VARIABLE} environment variable and finally from the configured region.
     *
     * @return the endpoint, or {@code null} when nothing configures one
     */
    public static String getEndpoint(Repository repository, String configuredEndpoint, String configuredRegion) {
        Objects.requireNonNull(repository, "repository must not be null");

        Matcher matcher = BUCKET_AND_ENDPOINT.matcher(repository.getHost());
        if (matcher.matches()) {
            return matcher.group(2);
        }

        String endpoint = getSetting(configuredEndpoint, ENDPOINT_PROPERTY, ENDPOINT_VARIABLE);
        if (isNotBlank(endpoint)) {
            return endpoint;
        }

        String region = getSetting(configuredRegion, REGION_PROPERTY, REGION_VARIABLE);

        return isNotBlank(region) ? String.format(PUBLIC_ENDPOINT_FORMAT, region) : null;
    }

    public static String getEndpointUrl(String endpoint) {
        Objects.requireNonNull(endpoint, "endpoint must not be null");

        return startsWithAny(endpoint, "http://", "https://") ? endpoint : "https://" + endpoint;
    }

    /**
     * The region a request is signed for. IBM COS derives it from the credential scope of the
     * request, so the region of the endpoint is the right default.
     */
    public static String getSigningRegion(String endpoint, String configuredRegion) {
        String region = getSetting(configuredRegion, REGION_PROPERTY, REGION_VARIABLE);

        return isNotBlank(region) ? region : getRegionFromEndpoint(endpoint);
    }

    public static ClientConfiguration getClientConfiguration(ProxyInfoProvider proxyInfoProvider) {
        ClientConfiguration clientConfiguration = new ClientConfiguration();

        if (proxyInfoProvider != null) {
            ProxyInfo proxyInfo = proxyInfoProvider.getProxyInfo(PROXY_PROTOCOL);
            if (proxyInfo != null) {
                clientConfiguration.withProxyHost(proxyInfo.getHost()).withProxyPort(proxyInfo.getPort());

                if (isNotBlank(proxyInfo.getUserName())) {
                    clientConfiguration.withProxyUsername(proxyInfo.getUserName())
                            .withProxyPassword(proxyInfo.getPassword());
                }
            }
        }

        return clientConfiguration;
    }

    /**
     * @return the first of the configured value, the system property and the environment variable
     * that is set, or {@code null} when none of them is
     */
    public static String getSetting(String configured, String systemProperty, String environmentVariable) {
        if (isNotBlank(configured)) {
            return configured;
        }

        String property = System.getProperty(systemProperty);
        if (isNotBlank(property)) {
            return property;
        }

        String variable = System.getenv(environmentVariable);

        return isNotBlank(variable) ? variable : null;
    }

    private static String getRegionFromEndpoint(String endpoint) {
        String host = endpoint.contains("//") ? substringAfter(endpoint, "//") : endpoint;
        String[] labels = host.split("\\.");

        // only the endpoints of IBM COS carry their region, right behind the leading s3 label
        if (labels.length > 1 && (labels[0].equals("s3") || labels[0].startsWith("s3-"))) {
            for (int i = 1; i < labels.length; i++) {
                if (!ENDPOINT_QUALIFIERS.contains(labels[i])) {
                    return labels[i];
                }
            }
        }

        return DEFAULT_SIGNING_REGION;
    }
}
