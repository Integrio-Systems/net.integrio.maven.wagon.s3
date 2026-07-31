package net.integrio.maven.common.util;

import org.apache.maven.wagon.repository.Repository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RepositoryUtilsTest {

    @Test
    void baseDirectoryEndsWithASlash() {
        assertEquals("release/", RepositoryUtils.getBaseDirectory(repository("cos://my-bucket/release")));
        assertEquals("release/", RepositoryUtils.getBaseDirectory(repository("cos://my-bucket/release/")));
        assertEquals("snapshot/inner/", RepositoryUtils.getBaseDirectory(repository("s3://my-bucket/snapshot/inner")));
    }

    @Test
    void baseDirectoryOfTheBucketRootIsEmpty() {
        assertEquals("", RepositoryUtils.getBaseDirectory(repository("cos://my-bucket")));
        assertEquals("", RepositoryUtils.getBaseDirectory(repository("s3://my-bucket")));
    }

    private static Repository repository(String url) {
        return new Repository("test", url);
    }
}
