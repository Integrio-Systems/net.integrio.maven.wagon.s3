

package net.integrio.maven.ibm;

import com.ibm.cloud.objectstorage.AmazonServiceException;
import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;
import com.ibm.cloud.objectstorage.services.s3.internal.Mimetypes;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsRequest;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectListing;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.S3Object;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;
import net.integrio.maven.common.data.TransferProgress;
import net.integrio.maven.common.data.transfer.TransferProgressFileInputStream;
import net.integrio.maven.common.data.transfer.TransferProgressFileOutputStream;
import net.integrio.maven.common.util.IOUtils;
import net.integrio.maven.common.util.RepositoryUtils;
import net.integrio.maven.common.wagon.AbstractWagon;
import net.integrio.maven.ibm.credentials.IbmCosCredentialsProvider;
import net.integrio.maven.ibm.util.CosUtils;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wagon for {@code cos://} urls backed by IBM Cloud Object Storage.
 * <p>
 * The bucket is the host of the url and the endpoint can be appended to it, for example
 * {@code cos://my-bucket.s3.us-south.cloud-object-storage.appdomain.cloud/release}. Alternatively
 * the endpoint (or just the region) is taken from the {@code configuration} of the matching server
 * in {@code settings.xml}, from a system property or from an environment variable.
 */
public final class IbmCosWagon extends AbstractWagon {

    private static final Logger logger = LoggerFactory.getLogger(IbmCosWagon.class);

    private static final String KEY_FORMAT = "%s%s";

    private static final String RESOURCE_FORMAT = "%s(.*)";

    /*
     * Injected from <server><configuration> in settings.xml by the wagon configurator of Maven.
     */
    private String endpoint;

    private String region;

    private String serviceInstanceId;

    private String iamEndpoint;

    private boolean pathStyleAccess = true;

    private volatile AmazonS3 cos;

    private volatile String bucketName;

    private volatile String baseDirectory;

    /**
     * Creates a new instance of the wagon
     */
    public IbmCosWagon() {
        super(true);
    }

    IbmCosWagon(AmazonS3 cos, String bucketName, String baseDirectory) {
        super(true);
        this.cos = cos;
        this.bucketName = bucketName;
        this.baseDirectory = baseDirectory;
    }

    private static ObjectMetadata getObjectMetadata(AmazonS3 cos, String bucketName, String baseDirectory, String resourceName) {
        return cos.getObjectMetadata(bucketName, getKey(baseDirectory, resourceName));
    }

    private static String getKey(String baseDirectory, String resourceName) {
        return String.format(KEY_FORMAT, baseDirectory, resourceName);
    }

    private static List<String> getResourceNames(ObjectListing objectListing, Pattern pattern) {
        List<String> resourceNames = new ArrayList<>();

        for (String commonPrefix : objectListing.getCommonPrefixes()) {
            resourceNames.add(getResourceName(commonPrefix, pattern));
        }

        for (S3ObjectSummary s3ObjectSummary : objectListing.getObjectSummaries()) {
            resourceNames.add(getResourceName(s3ObjectSummary.getKey(), pattern));
        }

        return resourceNames;
    }

    private static String getResourceName(String key, Pattern pattern) {
        Matcher matcher = pattern.matcher(key);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return key;
    }

    @Override
    protected void connectToRepository(Repository repository, AuthenticationInfo authenticationInfo,
                                       ProxyInfoProvider proxyInfoProvider) throws ConnectionException {
        if (this.cos == null) {
            String serviceEndpoint = CosUtils.getEndpoint(repository, this.endpoint, this.region);

            this.bucketName = CosUtils.getBucketName(repository);
            this.baseDirectory = RepositoryUtils.getBaseDirectory(repository);

            if (serviceEndpoint == null) {
                throw new ConnectionException(String.format("No IBM Cloud Object Storage endpoint configured for '%s'. "
                                + "Append it to the host of the url ('cos://%s.s3.<region>.cloud-object-storage"
                                + ".appdomain.cloud/...'), add an <endpoint> or <region> to the <configuration> of the "
                                + "server in settings.xml or set the '%s' or '%s' system property",
                        repository.getUrl(), this.bucketName, CosUtils.ENDPOINT_PROPERTY, CosUtils.REGION_PROPERTY));
            }

            String signingRegion = CosUtils.getSigningRegion(serviceEndpoint, this.region);
            logger.debug("Connecting to bucket '{}' of endpoint '{}' in region '{}'", this.bucketName, serviceEndpoint,
                    signingRegion);

            ClientConfiguration clientConfiguration = CosUtils.getClientConfiguration(proxyInfoProvider);

            this.cos = AmazonS3ClientBuilder.standard()
                    .withCredentials(new IbmCosCredentialsProvider(authenticationInfo, this.serviceInstanceId,
                            this.iamEndpoint, clientConfiguration))
                    .withClientConfiguration(clientConfiguration)
                    .withPathStyleAccessEnabled(this.pathStyleAccess)
                    .withEndpointConfiguration(
                            new EndpointConfiguration(CosUtils.getEndpointUrl(serviceEndpoint), signingRegion))
                    .build();
        }
    }

    @Override
    protected void disconnectFromRepository() {
        if (this.cos != null) {
            this.cos.shutdown();
        }
        this.cos = null;
        this.bucketName = null;
        this.baseDirectory = null;
    }

    @Override
    protected boolean doesRemoteResourceExist(String resourceName) {
        try {
            getObjectMetadata(this.cos, this.bucketName, this.baseDirectory, resourceName);
            return true;
        } catch (AmazonServiceException e) {
            return false;
        }
    }

    @Override
    protected boolean isRemoteResourceNewer(String resourceName, long timestamp) throws ResourceDoesNotExistException {
        try {
            Date lastModified = getObjectMetadata(this.cos, this.bucketName, this.baseDirectory, resourceName).getLastModified();
            return lastModified == null || lastModified.getTime() > timestamp;
        } catch (AmazonServiceException e) {
            throw new ResourceDoesNotExistException(String.format("'%s' does not exist", resourceName), e);
        }
    }

    @Override
    protected List<String> listDirectory(String directory) throws ResourceDoesNotExistException {
        List<String> directoryContents = new ArrayList<>();

        try {
            String prefix = getKey(this.baseDirectory, directory);
            Pattern pattern = Pattern.compile(String.format(RESOURCE_FORMAT, prefix));

            ListObjectsRequest listObjectsRequest = new ListObjectsRequest() //
                    .withBucketName(this.bucketName) //
                    .withPrefix(prefix) //
                    .withDelimiter("/");

            ObjectListing objectListing = this.cos.listObjects(listObjectsRequest);
            directoryContents.addAll(getResourceNames(objectListing, pattern));

            while (objectListing.isTruncated()) {
                objectListing = this.cos.listNextBatchOfObjects(objectListing);
                directoryContents.addAll(getResourceNames(objectListing, pattern));
            }

            return directoryContents;
        } catch (AmazonServiceException e) {
            throw new ResourceDoesNotExistException(String.format("'%s' does not exist", directory), e);
        }
    }

    @Override
    protected void getResource(String resourceName, File destination, TransferProgress transferProgress)
            throws TransferFailedException, ResourceDoesNotExistException {
        InputStream in = null;
        OutputStream out = null;
        try {
            S3Object cosObject = this.cos.getObject(this.bucketName, getKey(this.baseDirectory, resourceName));

            in = cosObject.getObjectContent();
            out = new TransferProgressFileOutputStream(destination, transferProgress);

            IOUtils.copy(in, out);
        } catch (AmazonServiceException e) {
            throw new ResourceDoesNotExistException(String.format("'%s' does not exist", resourceName), e);
        } catch (FileNotFoundException e) {
            throw new TransferFailedException(String.format("Cannot write file to '%s'", destination), e);
        } catch (IOException e) {
            throw new TransferFailedException(String.format("Cannot read from '%s' and write to '%s'", resourceName, destination), e);
        } finally {
            IOUtils.closeQuietly(in, out);
        }
    }

    @Override
    protected void putResource(File source, String destination, TransferProgress transferProgress) throws TransferFailedException,
            ResourceDoesNotExistException {
        String key = getKey(this.baseDirectory, destination);
        InputStream in = null;
        try {
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(source.length());
            objectMetadata.setContentType(Mimetypes.getInstance().getMimetype(source));

            in = new TransferProgressFileInputStream(source, transferProgress);

            this.cos.putObject(new PutObjectRequest(this.bucketName, key, in, objectMetadata));
        } catch (AmazonServiceException e) {
            logger.error("IBM COS", e);
            throw new TransferFailedException(String.format("Cannot write file to '%s'", destination), e);
        } catch (FileNotFoundException e) {
            logger.error("FNF", e);
            throw new ResourceDoesNotExistException(String.format("Cannot read file from '%s'", source), e);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }
}
