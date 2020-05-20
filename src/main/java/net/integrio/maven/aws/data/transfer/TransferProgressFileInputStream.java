

package net.integrio.maven.aws.data.transfer;

import net.integrio.maven.aws.data.TransferProgress;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class TransferProgressFileInputStream extends FileInputStream {

    private final TransferProgress transferProgress;

    public TransferProgressFileInputStream(File file, TransferProgress transferProgress) throws FileNotFoundException {
        super(file);
        this.transferProgress = transferProgress;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        this.transferProgress.notify(new byte[]{(byte) b}, 1);
        return b;
    }

    @Override
    public int read(byte b[]) throws IOException {
        int count = super.read(b);
        this.transferProgress.notify(b, b.length);
        return count;
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        int count = super.read(b, off, len);
        if (off == 0) {
            this.transferProgress.notify(b, len);
        } else {
            byte[] bytes = new byte[len];
            System.arraycopy(b, off, bytes, 0, len);
            this.transferProgress.notify(bytes, len);
        }
        return count;
    }
}
