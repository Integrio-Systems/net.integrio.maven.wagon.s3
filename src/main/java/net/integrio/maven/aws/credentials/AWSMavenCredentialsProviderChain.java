
package net.integrio.maven.aws.credentials;

import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import org.apache.maven.wagon.authentication.AuthenticationInfo;

public final class AWSMavenCredentialsProviderChain extends AWSCredentialsProviderChain {

    public AWSMavenCredentialsProviderChain(AuthenticationInfo authenticationInfo) {
        super(new AWSMavenCredentialsProvider(authenticationInfo),
                new SystemPropertiesCredentialsProvider(),
                new EnvironmentVariableCredentialsProvider(),
                new ProfileCredentialsProvider(),
                new EC2ContainerCredentialsProviderWrapper());
    }
}
