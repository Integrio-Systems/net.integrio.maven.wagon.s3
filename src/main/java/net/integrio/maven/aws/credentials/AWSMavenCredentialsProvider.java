
package net.integrio.maven.aws.credentials;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import org.apache.maven.wagon.authentication.AuthenticationInfo;

public class AWSMavenCredentialsProvider implements AWSCredentialsProvider {

    private final AuthenticationInfo authenticationInfo;

    public AWSMavenCredentialsProvider(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
    }

    @Override
    public AWSCredentials getCredentials() {
        return this.authenticationInfo != null ? new AWSMavenCredentials(this.authenticationInfo) : null;
    }

    @Override
    public void refresh() {
    }
}
