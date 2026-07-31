package net.integrio.maven.ibm.util;

import org.apache.maven.wagon.repository.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class CosUtilsTest {

    private static final String PUBLIC_ENDPOINT = "s3.us-south.cloud-object-storage.appdomain.cloud";

    @AfterEach
    void clearSettings() {
        System.clearProperty(CosUtils.ENDPOINT_PROPERTY);
        System.clearProperty(CosUtils.REGION_PROPERTY);
    }

    @Test
    void bucketNameIsTheHostWhenTheUrlCarriesNoEndpoint() {
        assertEquals("my-bucket", CosUtils.getBucketName(repository("cos://my-bucket/release")));
    }

    @Test
    void bucketNameIsTheHostWithoutTheEndpoint() {
        assertEquals("my-bucket", CosUtils.getBucketName(repository("cos://my-bucket." + PUBLIC_ENDPOINT + "/release")));
    }

    @Test
    void bucketNameKeepsDots() {
        assertEquals("my.bucket", CosUtils.getBucketName(repository("cos://my.bucket." + PUBLIC_ENDPOINT + "/release")));
    }

    @Test
    void bucketNameOfLegacyEndpoint() {
        assertEquals("my-bucket",
                CosUtils.getBucketName(repository("cos://my-bucket.s3-api.us-geo.objectstorage.softlayer.net/release")));
    }

    @Test
    void endpointOfTheUrlWinsOverEveryOtherSetting() {
        System.setProperty(CosUtils.ENDPOINT_PROPERTY, "s3.eu-de.cloud-object-storage.appdomain.cloud");

        assertEquals(PUBLIC_ENDPOINT,
                CosUtils.getEndpoint(repository("cos://my-bucket." + PUBLIC_ENDPOINT + "/release"), "s3.au-syd.cloud-object-storage.appdomain.cloud", "au-syd"));
    }

    @Test
    void endpointOfTheConfigurationWinsOverTheSystemProperty() {
        System.setProperty(CosUtils.ENDPOINT_PROPERTY, "s3.eu-de.cloud-object-storage.appdomain.cloud");

        assertEquals(PUBLIC_ENDPOINT, CosUtils.getEndpoint(repository("cos://my-bucket/release"), PUBLIC_ENDPOINT, null));
    }

    @Test
    void endpointFallsBackToTheSystemProperty() {
        System.setProperty(CosUtils.ENDPOINT_PROPERTY, PUBLIC_ENDPOINT);

        assertEquals(PUBLIC_ENDPOINT, CosUtils.getEndpoint(repository("cos://my-bucket/release"), null, null));
    }

    @Test
    void endpointIsDerivedFromTheRegion() {
        assertEquals(PUBLIC_ENDPOINT, CosUtils.getEndpoint(repository("cos://my-bucket/release"), null, "us-south"));

        System.setProperty(CosUtils.REGION_PROPERTY, "eu-de");
        assertEquals("s3.eu-de.cloud-object-storage.appdomain.cloud",
                CosUtils.getEndpoint(repository("cos://my-bucket/release"), null, null));
    }

    @Test
    void endpointIsNullWhenNothingConfiguresOne() {
        assertNull(CosUtils.getEndpoint(repository("cos://my-bucket/release"), null, null));
    }

    @Test
    void endpointUrlDefaultsToHttps() {
        assertEquals("https://" + PUBLIC_ENDPOINT, CosUtils.getEndpointUrl(PUBLIC_ENDPOINT));
        assertEquals("https://" + PUBLIC_ENDPOINT, CosUtils.getEndpointUrl("https://" + PUBLIC_ENDPOINT));
        assertEquals("http://cos.internal:9000", CosUtils.getEndpointUrl("http://cos.internal:9000"));
    }

    @Test
    void signingRegionIsTakenFromTheEndpoint() {
        assertEquals("us-south", CosUtils.getSigningRegion(PUBLIC_ENDPOINT, null));
        assertEquals("us-south", CosUtils.getSigningRegion("https://" + PUBLIC_ENDPOINT, null));
        assertEquals("us-geo", CosUtils.getSigningRegion("s3-api.us-geo.objectstorage.softlayer.net", null));
    }

    @Test
    void signingRegionSkipsTheQualifierOfPrivateAndDirectEndpoints() {
        assertEquals("eu-de", CosUtils.getSigningRegion("s3.private.eu-de.cloud-object-storage.appdomain.cloud", null));
        assertEquals("eu-de", CosUtils.getSigningRegion("s3.direct.eu-de.cloud-object-storage.appdomain.cloud", null));
    }

    @Test
    void signingRegionOfTheConfigurationWins() {
        assertEquals("au-syd", CosUtils.getSigningRegion(PUBLIC_ENDPOINT, "au-syd"));
    }

    @Test
    void signingRegionFallsBackToUsStandard() {
        assertEquals("us-standard", CosUtils.getSigningRegion("cos.internal", null));
    }

    private static Repository repository(String url) {
        return new Repository("cos-test", url);
    }
}
