

package net.integrio.maven.aws;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.internal.Mimetypes;
import com.amazonaws.services.s3.model.*;
import net.integrio.maven.aws.credentials.AWSMavenCredentialsProviderChain;
import net.integrio.maven.aws.data.TransferProgress;
import net.integrio.maven.aws.data.transfer.TransferProgressFileInputStream;
import net.integrio.maven.aws.data.transfer.TransferProgressFileOutputStream;
import net.integrio.maven.aws.maven.AbstractWagon;
import net.integrio.maven.aws.util.IOUtils;
import net.integrio.maven.aws.util.S3Utils;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;

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


public final class AmazonS3Wagon extends AbstractWagon {

    private static final String KEY_FORMAT = "%s%s";

    private static final String RESOURCE_FORMAT = "%s(.*)";

    private volatile AmazonS3 amazonS3;

    private volatile String bucketName;

    private volatile String baseDirectory;

    /**
     * Creates a new instance of the wagon
     */
    public AmazonS3Wagon() {
        super(true);
    }

    AmazonS3Wagon(AmazonS3 amazonS3, String bucketName, String baseDirectory) {
        super(true);
        this.amazonS3 = amazonS3;
        this.bucketName = bucketName;
        this.baseDirectory = baseDirectory;
    }

    private static ObjectMetadata getObjectMetadata(AmazonS3 amazonS3, String bucketName, String baseDirectory, String resourceName) {
        return amazonS3.getObjectMetadata(bucketName, getKey(baseDirectory, resourceName));
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
                                       ProxyInfoProvider proxyInfoProvider) throws AuthenticationException {
        if (this.amazonS3 == null) {
            AWSMavenCredentialsProviderChain credentialsProvider =
                    new AWSMavenCredentialsProviderChain(authenticationInfo);
            ClientConfiguration clientConfiguration = S3Utils.getClientConfiguration(proxyInfoProvider);

            this.bucketName = S3Utils.getBucketName(repository);
            this.baseDirectory = S3Utils.getBaseDirectory(repository);


            this.amazonS3 = AmazonS3Client.builder()
                    .withCredentials(credentialsProvider)
                    .withClientConfiguration(clientConfiguration)
                    .withRegion(Regions.DEFAULT_REGION)
                    .build();


        }
    }

    @Override
    protected void disconnectFromRepository() {
        this.amazonS3 = null;
        this.bucketName = null;
        this.baseDirectory = null;
    }

    @Override
    protected boolean doesRemoteResourceExist(String resourceName) {
        try {
            getObjectMetadata(this.amazonS3, this.bucketName, this.baseDirectory, resourceName);
            return true;
        } catch (AmazonServiceException e) {
            return false;
        }
    }

    @Override
    protected boolean isRemoteResourceNewer(String resourceName, long timestamp) throws ResourceDoesNotExistException {
        try {
            Date lastModified = getObjectMetadata(this.amazonS3, this.bucketName, this.baseDirectory, resourceName).getLastModified();
            return lastModified == null || lastModified.getTime() > timestamp;
        } catch (AmazonServiceException e) {
            throw new ResourceDoesNotExistException(String.format("'%s' does not exist", resourceName), e);
        }
    }

    @Override
    protected List<String> listDirectory(String directory) throws ResourceDoesNotExistException {
        List<String> directoryContents = new ArrayList<String>();

        try {
            String prefix = getKey(this.baseDirectory, directory);
            Pattern pattern = Pattern.compile(String.format(RESOURCE_FORMAT, prefix));

            ListObjectsRequest listObjectsRequest = new ListObjectsRequest() //
                    .withBucketName(this.bucketName) //
                    .withPrefix(prefix) //
                    .withDelimiter("/");

            ObjectListing objectListing;

            objectListing = this.amazonS3.listObjects(listObjectsRequest);
            directoryContents.addAll(getResourceNames(objectListing, pattern));

            while (objectListing.isTruncated()) {
                objectListing = this.amazonS3.listObjects(listObjectsRequest);
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
            S3Object s3Object = this.amazonS3.getObject(this.bucketName, getKey(this.baseDirectory, resourceName));

            in = s3Object.getObjectContent();
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

            this.amazonS3.putObject(new PutObjectRequest(this.bucketName, key, in, objectMetadata));
        } catch (AmazonServiceException e) {
            throw new TransferFailedException(String.format("Cannot write file to '%s'", destination), e);
        } catch (FileNotFoundException e) {
            throw new ResourceDoesNotExistException(String.format("Cannot read file from '%s'", source), e);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }
}
