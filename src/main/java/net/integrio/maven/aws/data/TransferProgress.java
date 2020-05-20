

package net.integrio.maven.aws.data;

public interface TransferProgress {

    /**
     * Notify that transfer progress has occurred
     *
     * @param buffer The bytes transferred
     * @param length The length of the bytes transferred
     */
    void notify(byte[] buffer, int length);
}
