

package net.integrio.maven.aws.util;

import com.amazonaws.ClientConfiguration;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;

import java.util.Objects;

public abstract class S3Utils {

    public static String getBucketName(Repository repository) {
        Objects.requireNonNull(repository, "repository must not be null");
        return repository.getHost();
    }

    public static ClientConfiguration getClientConfiguration(ProxyInfoProvider proxyInfoProvider) {
        ClientConfiguration clientConfiguration = new ClientConfiguration();

        if (proxyInfoProvider != null) {
            ProxyInfo proxyInfo = proxyInfoProvider.getProxyInfo("s3");
            if (proxyInfo != null) {
                clientConfiguration.withProxyHost(proxyInfo.getHost()).withProxyPort(proxyInfo.getPort());
            }
        }

        return clientConfiguration;
    }
}
