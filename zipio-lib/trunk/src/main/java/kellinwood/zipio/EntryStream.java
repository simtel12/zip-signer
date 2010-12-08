package kellinwood.zipio;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class EntryStream extends InputStream {

    RandomAccessFile raf;
    int size;
    int offset;

    public EntryStream( LocalEntry entry) throws IOException {
        offset = 0;
        size = entry.compressedSize;
        raf = entry.zipInput.in;
        raf.seek( entry.dataPosition);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int available() throws IOException {
        return size - offset;
    }

    @Override
    public int read() throws IOException {
        if (available() == 0) return -1;
        int b = raf.read();
        if (b >= 0) {
            offset += 1;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (available() == 0) return -1;
        int numToRead = Math.min( len, available());
        // TODO Auto-generated method stub
        int numRead = raf.read(b, off, numToRead);
        if (numRead > 0) {
            offset += numRead;
        }
        return numRead;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read( b, 0, b.length);
    }

    @Override
    public long skip(long n) throws IOException {
        long numToSkip = Math.min( n, available());

        raf.seek( raf.getFilePointer() + numToSkip);
        return numToSkip;
    }
}


