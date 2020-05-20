

package net.integrio.maven.aws.maven;

import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;

final class NullProtectingProxyInfoProvider implements ProxyInfoProvider {

    private final ProxyInfo proxyInfo;

    NullProtectingProxyInfoProvider(ProxyInfo proxyInfo) {
        this.proxyInfo = proxyInfo;
    }

    @Override
    public ProxyInfo getProxyInfo(String protocol) {
        if ((protocol == null) || (this.proxyInfo == null) || protocol.equalsIgnoreCase(this.proxyInfo.getType())) {
            return this.proxyInfo;
        } else {
            return null;
        }
    }
}
