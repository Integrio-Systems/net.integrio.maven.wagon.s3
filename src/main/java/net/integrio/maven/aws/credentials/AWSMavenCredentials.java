

package net.integrio.maven.aws.credentials;

import com.amazonaws.auth.AWSCredentials;
import org.apache.maven.wagon.authentication.AuthenticationInfo;

public class AWSMavenCredentials implements AWSCredentials {

    private final AuthenticationInfo authenticationInfo;

    public AWSMavenCredentials(AuthenticationInfo authenticationInfo) {
        this.authenticationInfo = authenticationInfo;
    }

    @Override
    public String getAWSAccessKeyId() {
        return this.authenticationInfo.getUserName();
    }

    @Override
    public String getAWSSecretKey() {
        return this.authenticationInfo.getPassword();
    }
}
