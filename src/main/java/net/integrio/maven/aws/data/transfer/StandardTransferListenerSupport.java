

package net.integrio.maven.aws.data.transfer;

import net.integrio.maven.aws.data.TransferListenerSupport;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.resource.Resource;

import java.util.HashSet;
import java.util.Set;

public final class StandardTransferListenerSupport implements TransferListenerSupport {

    private final Wagon wagon;

    private final Set<TransferListener> transferListeners = new HashSet<TransferListener>();

    public StandardTransferListenerSupport(Wagon wagon) {
        this.wagon = wagon;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        if (transferListener != null) {
            this.transferListeners.add(transferListener);
        }
    }

    @Override
    public void removeTransferListener(TransferListener transferListener) {
        this.transferListeners.remove(transferListener);
    }

    @Override
    public boolean hasTransferListener(TransferListener transferListener) {
        return this.transferListeners.contains(transferListener);
    }

    @Override
    public void fireTransferInitiated(Resource resource, int requestType) {
        TransferEvent event = new TransferEvent(this.wagon, resource, TransferEvent.TRANSFER_INITIATED, requestType);
        for (TransferListener transferListener : this.transferListeners) {
            transferListener.transferInitiated(event);
        }
    }

    @Override
    public void fireTransferStarted(Resource resource, int requestType) {
        TransferEvent event = new TransferEvent(this.wagon, resource, TransferEvent.TRANSFER_STARTED, requestType);
        for (TransferListener transferListener : this.transferListeners) {
            transferListener.transferStarted(event);
        }
    }

    @Override
    public void fireTransferProgress(Resource resource, int requestType, byte[] buffer, int length) {
        TransferEvent event = new TransferEvent(this.wagon, resource, TransferEvent.TRANSFER_PROGRESS, requestType);
        for (TransferListener transferListener : this.transferListeners) {
            transferListener.transferProgress(event, buffer, length);
        }
    }

    @Override
    public void fireTransferCompleted(Resource resource, int requestType) {
        TransferEvent event = new TransferEvent(this.wagon, resource, TransferEvent.TRANSFER_COMPLETED, requestType);
        for (TransferListener transferListener : this.transferListeners) {
            transferListener.transferCompleted(event);
        }
    }

    @Override
    public void fireTransferError(Resource resource, int requestType, Exception exception) {
        TransferEvent event = new TransferEvent(this.wagon, resource, exception, requestType);
        for (TransferListener transferListener : this.transferListeners) {
            transferListener.transferError(event);
        }
    }
}
