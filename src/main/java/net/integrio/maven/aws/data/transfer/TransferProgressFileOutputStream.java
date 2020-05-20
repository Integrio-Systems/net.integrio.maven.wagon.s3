
package net.integrio.maven.aws.data.transfer;

import net.integrio.maven.aws.data.TransferProgress;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public final class TransferProgressFileOutputStream extends FileOutputStream {

    private final TransferProgress transferProgress;

    public TransferProgressFileOutputStream(File file, TransferProgress transferProgress) throws FileNotFoundException {
        super(file);
        this.transferProgress = transferProgress;
    }

    @Override
    public void write(int b) throws IOException {
        super.write(b);
        this.transferProgress.notify(new byte[]{(byte) b}, 1);
    }

    @Override
    public void write(byte b[]) throws IOException {
        super.write(b);
        this.transferProgress.notify(b, b.length);
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        super.write(b, off, len);
        if (off == 0) {
            this.transferProgress.notify(b, len);
        } else {
            byte[] bytes = new byte[len];
            System.arraycopy(b, off, bytes, 0, len);
            this.transferProgress.notify(bytes, len);
        }
    }
}
