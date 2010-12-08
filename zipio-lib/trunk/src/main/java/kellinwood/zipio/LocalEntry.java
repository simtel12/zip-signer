package kellinwood.zipio;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.Inflater;

import kellinwood.logging.LoggerInterface;
import kellinwood.logging.LoggerManager;

public class LocalEntry {

    public ZipInput zipInput;

    public int signature = 0x04034b50;
    public short versionRequired;
    public short generalPurposeBits;
    public short compression;
    public short modificationTime;
    public short modificationDate;
    public int crc32;
    public int compressedSize;
    public int size;
    public String filename;
    public byte[] extraData;
    public long dataPosition;
    public byte[] data = null;
    private EntryOutputStream entryOut = null;

    private static LoggerInterface log;

    public LocalEntry( ZipInput input) {
        zipInput = input;
    }

    public LocalEntry( String name) {
        filename = name;
        compression = 8;
        data = extraData = new byte[0];
    }
    
    public static LocalEntry read(ZipInput input) throws IOException
    {

        if (getLogger().isDebugEnabled())
            getLogger().debug( String.format("FILE POSITION: 0x%08x", input.getFilePointer()));

        // 0 	4 	Local file header signature = 0x04034b50
        int signature = input.readInt();
        if (signature != 0x04034b50) {
            // back up to the signature
            input.seek( input.getFilePointer() - 4);
            return null;
        }

        LocalEntry entry = new LocalEntry( input);

        entry.doRead( input);
        return entry;
    }

    public static LoggerInterface getLogger() {
        if (log == null) log = LoggerManager.getLogger( LocalEntry.class.getName());
        return log;
    }


    private void doRead( ZipInput input) throws IOException
    {

        boolean debug = getLogger().isDebugEnabled();

        // 4 	2 	Version needed to extract (minimum)
        versionRequired = input.readShort();
        if (debug) log.debug(String.format("Version required: 0x%04x", versionRequired));

        // 6 	2 	General purpose bit flag
        generalPurposeBits = input.readShort();
        if (debug) log.debug(String.format("General purpose bits: 0x%04x", generalPurposeBits));
        
        // 8 	2 	Compression method
        compression = input.readShort();
        if (debug) log.debug(String.format("Compression: 0x%04x", compression));

        // 10 	2 	File last modification time
        modificationTime = input.readShort();
        if (debug) log.debug(String.format("Modification time: 0x%04x", modificationTime));

        // 12 	2 	File last modification date
        modificationDate = input.readShort();
        if (debug) log.debug(String.format("Modification date: 0x%04x", modificationDate));

        // 14 	4 	CRC-32
        crc32 = input.readInt();
        if (debug) log.debug(String.format("CRC-32: 0x%04x", crc32));

        // 18 	4 	Compressed size
        compressedSize = input.readInt();
        if (debug) log.debug(String.format("Compressed size: 0x%04x", compressedSize));

        // 22 	4 	Uncompressed size
        size = input.readInt();
        if (debug) log.debug(String.format("Size: 0x%04x", size));

        // 26 	2 	File name length (n)
        short fileNameLen = input.readShort();
        if (debug) log.debug(String.format("File name length: 0x%04x", fileNameLen));

        // 28 	2 	Extra field length (m)
        short extraLen = input.readShort();
        if (debug) log.debug(String.format("Extra length: 0x%04x", extraLen));

        // 30 	n 	File name      
        filename = input.readString(fileNameLen);
        if (debug) log.debug("Filename: " + filename);

        extraData = input.readBytes( extraLen);

        dataPosition = input.getFilePointer();
        if (debug) log.debug(String.format("Data position: 0x%08x",dataPosition));

        

        if (generalPurposeBits != 0) {
            if (generalPurposeBits == 0x0008)
            {
                // Scan for a signature we recognize...
                // data descriptor 0x0x08074b50
                // local header 0x04034b50, or
                // central directory entry 0x02014b50;
                byte[] scanBuf = input.readBytes(4);
                while (true) {
                    if (scanBuf[0] == 0x50 && scanBuf[1] == 0x4b) {
                        if (scanBuf[2] == 0x07 && scanBuf[3] == 0x08) {
                            // data descriptor header
                            break;
                        }
                        else if (scanBuf[2] == 0x03 && scanBuf[3] == 0x04) {
                            // local header signature
                            input.seek( input.getFilePointer() - 16);
                            break;

                        }
                        else if (scanBuf[2] == 0x01 && scanBuf[3] == 0x02) {
                            // central directory signature
                            input.seek( input.getFilePointer() - 16);
                            break;
                        }
                    }

                    scanBuf[0] = scanBuf[1];
                    scanBuf[1] = scanBuf[2];
                    scanBuf[2] = scanBuf[3];
                    scanBuf[3] = input.readByte();
                        
                }
                
                crc32 = input.readInt();
                if (debug) log.debug(String.format("CRC-32: 0x%04x", crc32));
                compressedSize = input.readInt();
                if (debug) log.debug(String.format("Compressed size: 0x%04x", compressedSize));
                size = input.readInt();
                if (debug) log.debug(String.format("Size: 0x%04x", size));

                // Don't generate the data descriptor when the zip file is re-written
                generalPurposeBits = 0;
                
            }
            else throw new IllegalStateException("Can't handle general purpose bits != 0x0000 && bits != 0x0008");
        }
        else input.seek( dataPosition + compressedSize);

        // Don't write zero-length entries with compression.
        if (size == 0) {
            compressedSize = 0;
            compression = 0;
            crc32 = 0;
        }

        // byte[] data = readBytes( compressedSize);


    }

    public void write( ZipOutput output) throws IOException
    {

        boolean debug = getLogger().isDebugEnabled();

        if (entryOut != null) {
            entryOut.close();
            crc32 = entryOut.getCRC();
            size = entryOut.getSize();
            compressedSize = entryOut.getCompressedSize();
            data = entryOut.getData();
        }
        
        output.writeInt( signature);
        output.writeShort( versionRequired);
        output.writeShort( generalPurposeBits);
        output.writeShort( compression);
        output.writeShort( modificationTime);
        output.writeShort( modificationDate);
        output.writeInt( crc32);
        output.writeInt( compressedSize);
        output.writeInt( size);
        output.writeShort( (short)filename.length());

        short numAlignBytes = 0;

        // Zipalign if the file is uncompressed, i.e., "Stored", and file size is not zero.
        if (compression == 0 && size > 0) {

            long dataPos = output.getFilePointer() + // current position
            2 +                                  // plus size of extra data length
            filename.length() +                  // plus filename
            extraData.length;                    // plus extra data

            short dataPosMod4 = (short)(dataPos % 4);

            if (dataPosMod4 > 0) {
                numAlignBytes = (short)(4 - dataPosMod4);
            }
        }

        // 28 	2 	Extra field length (m)
        output.writeShort( (short)(extraData.length + numAlignBytes));

        // 30 	n 	File name
        output.writeString( filename);

        // Extra data
        output.writeBytes( extraData);

        // Zipalign bytes
        if (numAlignBytes > 0) {
            byte[] alignBytes = new byte[numAlignBytes];
            output.writeBytes( alignBytes);
        }

        if (data != null) {
            output.writeBytes( data);
        }
        else {
            zipInput.seek( dataPosition);

            byte[] tmpData = zipInput.readBytes( compressedSize);

            output.writeBytes( tmpData);
        }
    }		

    public byte[] getData() throws IOException
    {
        
        byte[] data = new byte[size];
        
        InputStream din = getDataStream();
        
        int count = din.read( data);
        if (count != size) 
            throw new IllegalStateException(String.format("Read failed, expecting %d bytes, got %%d instead", size, count));
        return data;
    }

    // Returns an input stream for reading the entry's data. 
    public InputStream getDataStream() throws IOException {
        InputStream result;
        result = new EntryStream(this);
        if (compression != 0)  result = new InflaterInputStream( result, new Inflater( true));

        /*
        result = new FilterInputStream( result)
        {
            public int read() throws IOException
            {
                int b = super.read();
                if (b >= 0) System.out.println( b);
                return b;
            }
            public int read(byte[] b) throws IOException
            {
                return read( b, 0, b.length);
            }
            
            public int read(byte[] b, int off, int len)
                 throws IOException
            {
                int n = super.read( b, off, len);
                if (n > 0) System.out.write( b, off, n);
                return n;
            }
        };
        */
        
        return result;
    }

    public OutputStream getDataOutputStream() {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (compression == 8) { // Deflate
            DeflaterOutputStream dos = new DeflaterOutputStream( baos, new Deflater( Deflater.BEST_COMPRESSION, true));
            entryOut = new EntryOutputStream( dos, baos);
        }
        else entryOut = new EntryOutputStream( baos, baos);

        return entryOut;
    }

    class EntryOutputStream extends FilterOutputStream {
        int size = 0;
        ByteArrayOutputStream baos;
        CRC32 crc = new CRC32();
        
        public EntryOutputStream( OutputStream next, ByteArrayOutputStream baos) {
            super( next);
            this.baos = baos;
        }

        public void close() throws IOException {
            super.close();
        }

        public void flush() throws IOException {
            super.flush();
        }

        public void write(byte[] b) throws IOException {
            write( b, 0, b.length);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            super.write( b, off, len);
            crc.update( b, off, len);
            size += len;
        }

        public void write(int b) throws IOException {
            super.write( b);
            crc.update( b);
            size += 1;
        }

        public int getCRC() {
            return (int)crc.getValue();
        }

        public int getSize() {
            return size;
        }

        public byte[] getData() {
            return baos.toByteArray();
        }

        public int getCompressedSize() {
            return baos.toByteArray().length;
        }
    }
}
