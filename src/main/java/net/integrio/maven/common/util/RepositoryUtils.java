

package net.integrio.maven.common.util;

import org.apache.maven.wagon.repository.Repository;

import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.appendIfMissing;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringAfter;

/**
 * Parts of a repository url that every object storage behind a wagon is addressed with.
 */
public abstract class RepositoryUtils {

    /**
     * The prefix all keys of the repository share: the path of the url, with a trailing slash and
     * empty for a repository that sits in the root of the bucket.
     */
    public static String getBaseDirectory(Repository repository) {
        Objects.requireNonNull(repository, "repository must not be null");
        String basedir = substringAfter(repository.getBasedir(), "/");

        return isNotBlank(basedir) ? appendIfMissing(basedir, "/", "/") : basedir;
    }
}
