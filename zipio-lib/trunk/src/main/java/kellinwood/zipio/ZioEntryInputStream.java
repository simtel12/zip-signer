/*
 * Copyright (C) 2010 Ken Ellinwood
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kellinwood.zipio;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/** Input stream used to read just the data from a zip file entry. */
public class ZioEntryInputStream extends InputStream {

    RandomAccessFile raf;
    int size;
    int offset;

    public ZioEntryInputStream( ZioEntry entry) throws IOException {
        offset = 0;
        size = entry.getCompressedSize();
        raf = entry.getZipInput().in;
        raf.seek( entry.getDataPosition());
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


