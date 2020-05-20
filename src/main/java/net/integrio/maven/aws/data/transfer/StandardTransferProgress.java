

package net.integrio.maven.aws.data.transfer;

import net.integrio.maven.aws.data.TransferListenerSupport;
import net.integrio.maven.aws.data.TransferProgress;
import org.apache.maven.wagon.resource.Resource;

public final class StandardTransferProgress implements TransferProgress {

    private final Resource resource;

    private final int requestType;

    private final TransferListenerSupport transferListenerSupport;

    public StandardTransferProgress(Resource resource, int requestType, TransferListenerSupport transferListenerSupport) {
        this.resource = resource;
        this.requestType = requestType;
        this.transferListenerSupport = transferListenerSupport;
    }

    @Override
    public void notify(byte[] buffer, int length) {
        this.transferListenerSupport.fireTransferProgress(this.resource, this.requestType, buffer, length);
    }

}
