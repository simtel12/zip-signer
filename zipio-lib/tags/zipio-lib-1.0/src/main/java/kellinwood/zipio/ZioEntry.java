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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import kellinwood.logging.LoggerInterface;
import kellinwood.logging.LoggerManager;

public class ZioEntry {

    private ZipInput zipInput;

    // public int signature = 0x02014b50;
    private short versionMadeBy;
    private short versionRequired;
    private short generalPurposeBits;
    private short compression;
    private short modificationTime;
    private short modificationDate;
    private int crc32;
    private int compressedSize;
    private int size;
    private String filename;
    private byte[] extraData;
    private short numAlignBytes = 0;
    private String fileComment;
    private short diskNumberStart;
    private short internalAttributes;
    private int externalAttributes;
    
    private int localHeaderOffset;
    private long dataPosition = -1;
    private byte[] data = null;
    private EntryOutputStream entryOut = null;
    

    private static byte[] alignBytes = new byte[4];
    
    private static LoggerInterface log;

    public ZioEntry( ZipInput input) {
        zipInput = input;
    }

    public static LoggerInterface getLogger() {
        if (log == null) log = LoggerManager.getLogger( ZioEntry.class.getName());
        return log;
    }

    public ZioEntry( String name) {
        filename = name;
        fileComment = "";
        compression = 8;
        extraData = new byte[0];
        setTime( System.currentTimeMillis());
    }
    


    public void readLocalHeader() throws IOException
    {
        ZipInput input = zipInput;
        int tmp;
        boolean debug = getLogger().isDebugEnabled();

        input.seek( localHeaderOffset);

        if (debug) getLogger().debug( String.format("FILE POSITION: 0x%08x", input.getFilePointer()));

        // 0 	4 	Local file header signature = 0x04034b50
        int signature = input.readInt();
        if (signature != 0x04034b50) {
            throw new IllegalStateException( String.format("Local header not found at pos=0x%08x, file=%s", input.getFilePointer(), filename));
        }

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
        tmp = input.readInt();
        crc32 = (tmp != 0) ? tmp : crc32;
        if (debug) log.debug(String.format("CRC-32: 0x%04x", crc32));

        // 18 	4 	Compressed size
        tmp = input.readInt();
        compressedSize = (tmp != 0) ? tmp : compressedSize;
        if (debug) log.debug(String.format("Compressed size: 0x%04x", compressedSize));

        // 22 	4 	Uncompressed size
        tmp = input.readInt();
        size = (tmp != 0) ? tmp : size;
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

        // Throw away extra data from local header.
        byte[] extra = input.readBytes( extraLen);

        // Record the file position of this entry's data.
        dataPosition = input.getFilePointer();
        if (debug) log.debug(String.format("Data position: 0x%08x",dataPosition));

        if (generalPurposeBits != 0x0000 && generalPurposeBits != 0x0008) {
            throw new IllegalStateException("Can't handle general purpose bits != 0x0000 && bits != 0x0008");
        }

        generalPurposeBits = 0; // Don't write a data descriptor
        
        // Don't write zero-length entries with compression.
        if (size == 0) {
            compressedSize = 0;
            compression = 0;
            crc32 = 0;
        }
    }

    public void writeLocalEntry( ZipOutput output) throws IOException
    {
        if (data == null && dataPosition < 0 && zipInput != null) {
            readLocalHeader();
        }
        
        localHeaderOffset = (int)output.getFilePointer();

        boolean debug = getLogger().isDebugEnabled();
        
        if (debug) {
            getLogger().debug( String.format("Local header at 0x%08x - %s", localHeaderOffset, filename));
        }
        
        if (entryOut != null) {
            entryOut.close();
            size = entryOut.getSize();
            data = entryOut.getData();
            compressedSize = data.length;
            crc32 = entryOut.getCRC();
        }
        
        output.writeInt( 0x04034b50);
        output.writeShort( versionRequired);
        output.writeShort( generalPurposeBits);
        output.writeShort( compression);
        output.writeShort( modificationTime);
        output.writeShort( modificationDate);
        output.writeInt( crc32);
        output.writeInt( compressedSize);
        output.writeInt( size);
        output.writeShort( (short)filename.length());

        numAlignBytes = 0;

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
            output.writeBytes( alignBytes, 0, numAlignBytes);
        }

        if (debug) getLogger().debug(String.format("Data position 0x%08x", output.getFilePointer()));
        if (data != null) {
            output.writeBytes( data);
            if (debug) getLogger().debug(String.format("Wrote %d bytes", data.length));
        }
        else {

            zipInput.seek( dataPosition);
            
            int bufferSize = Math.min( compressedSize, 8096);
            byte[] buffer = new byte[bufferSize];
            long totalCount = 0;
            
            while (totalCount != compressedSize) {
                int numRead = zipInput.in.read( buffer, 0, (int)Math.min( compressedSize -  totalCount, bufferSize));  
                if (numRead > 0) {
                    output.writeBytes(buffer, 0, numRead);
                    if (debug) getLogger().debug(String.format("Wrote %d bytes", numRead));
                    totalCount += numRead;
                }
                else throw new IllegalStateException(String.format("EOF reached while copying %s with %d bytes left to go", filename, compressedSize -  totalCount));
            }
        }
    }		
    
    public static ZioEntry read(ZipInput input) throws IOException
    {

        // 0    4   Central directory header signature = 0x02014b50
        int signature = input.readInt();
        if (signature != 0x02014b50) {
            // back up to the signature
            input.seek( input.getFilePointer() - 4);
            return null;
        }

        ZioEntry entry = new ZioEntry( input);

        entry.doRead( input);
        return entry;
    }

    private void doRead( ZipInput input) throws IOException
    {

        boolean debug = getLogger().isDebugEnabled();

        // 4    2   Version needed to extract (minimum)
        versionMadeBy = input.readShort();
        if (debug) log.debug(String.format("Version made by: 0x%04x", versionMadeBy));

        // 4    2   Version required
        versionRequired = input.readShort();
        if (debug) log.debug(String.format("Version required: 0x%04x", versionRequired));

        // 6    2   General purpose bit flag
        generalPurposeBits = input.readShort();
        if (debug) log.debug(String.format("General purpose bits: 0x%04x", generalPurposeBits));
        if (generalPurposeBits != 0 && generalPurposeBits != 0x0008) {
            throw new IllegalStateException("Can't handle general purpose bits != 0x0000 && bits != 0x0008");
        }

        // 8    2   Compression method
        compression = input.readShort();
        if (debug) log.debug(String.format("Compression: 0x%04x", compression));

        // 10   2   File last modification time
        modificationTime = input.readShort();
        if (debug) log.debug(String.format("Modification time: 0x%04x", modificationTime));

        // 12   2   File last modification date
        modificationDate = input.readShort();
        if (debug) log.debug(String.format("Modification date: 0x%04x", modificationDate));

        // 14   4   CRC-32
        crc32 = input.readInt();
        if (debug) log.debug(String.format("CRC-32: 0x%04x", crc32));

        // 18   4   Compressed size
        compressedSize = input.readInt();
        if (debug) log.debug(String.format("Compressed size: 0x%04x", compressedSize));

        // 22   4   Uncompressed size
        size = input.readInt();
        if (debug) log.debug(String.format("Size: 0x%04x", size));

        // 26   2   File name length (n)
        short fileNameLen = input.readShort();
        if (debug) log.debug(String.format("File name length: 0x%04x", fileNameLen));

        // 28   2   Extra field length (m)
        short extraLen = input.readShort();
        if (debug) log.debug(String.format("Extra length: 0x%04x", extraLen));

        short fileCommentLen = input.readShort();
        if (debug) log.debug(String.format("File comment length: 0x%04x", fileCommentLen));

        diskNumberStart = input.readShort();
        if (debug) log.debug(String.format("Disk number start: 0x%04x", diskNumberStart));

        internalAttributes = input.readShort();
        if (debug) log.debug(String.format("Internal attributes: 0x%04x", internalAttributes));

        externalAttributes = input.readInt();
        if (debug) log.debug(String.format("External attributes: 0x%08x", externalAttributes));

        localHeaderOffset = input.readInt();
        if (debug) log.debug(String.format("Local header offset: 0x%08x", localHeaderOffset));

        // 30   n   File name      
        filename = input.readString(fileNameLen);
        if (debug) log.debug("Filename: " + filename);

        extraData = input.readBytes( extraLen);

        fileComment = input.readString( fileCommentLen);
        if (debug) log.debug("File comment: " + fileComment);
        
        // dataPosition = localHeaderOffset + 30 + filename.length() + extraData.length;
        // if (debug) log.debug(String.format("Data position: 0x%08x", dataPosition));

    }

    /** Returns the entry's data. */
    public byte[] getData() throws IOException
    {
        if (data != null) return data;
        
        byte[] tmpdata = new byte[size];
        
        InputStream din = getInputStream();
        
        int count = din.read( tmpdata);
        if (count != size) 
            throw new IllegalStateException(String.format("Read failed, expecting %d bytes, got %%d instead", size, count));
        return tmpdata;
    }

    // Returns an input stream for reading the entry's data. 
    public InputStream getInputStream() throws IOException {
        return getInputStream(null);
    }

    // Returns an input stream for reading the entry's data. 
    public InputStream getInputStream(OutputStream monitorStream) throws IOException {
        ZioEntryInputStream dataStream;
        dataStream = new ZioEntryInputStream(this);
        if (monitorStream != null) dataStream.setMonitorStream( monitorStream);
        if (compression != 0)  {
            // Note: When using nowrap=true with Inflater it is also necessary to provide 
            // an extra "dummy" byte as input. This is required by the ZLIB native library 
            // in order to support certain optimizations.
            dataStream.setReturnDummyByte(true);
            return new InflaterInputStream( dataStream, new Inflater( true));
        }
        else return dataStream;
    }

    // Returns an output stream for writing an entry's data.
    public OutputStream getOutputStream() 
    {
        entryOut = new EntryOutputStream( compression);
        return entryOut;
    }

    static class EntryOutputStream extends OutputStream {
        int size = 0;  // tracks uncompressed size of data
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CRC32 crc = new CRC32();
        int crcValue = 0;
        OutputStream downstream;
        
        public EntryOutputStream( int compression) {
            
            if (compression != 0)
                downstream = new DeflaterOutputStream( baos, new Deflater( Deflater.BEST_COMPRESSION, true));
            else downstream = baos;    
        }

        public void close() throws IOException {
            downstream.flush();
            downstream.close();
            crcValue = (int)crc.getValue();
        }

        public int getCRC() {
            return crcValue;
        }
        
        public void flush() throws IOException {
            downstream.flush();
        }

        public void write(byte[] b) throws IOException {
            downstream.write(b);
            crc.update(b);
            size += b.length;
        }

        public void write(byte[] b, int off, int len) throws IOException {
            downstream.write( b, off, len);
            crc.update( b, off, len);
            size += len;
        }

        public void write(int b) throws IOException {
            downstream.write( b);
            crc.update( b);
            size += 1;
        }

        public int getSize() {
            return size;
        }

        public byte[] getData() {
            return baos.toByteArray();
        }

    }
    
    public void write( ZipOutput output) throws IOException {
        boolean debug = getLogger().isDebugEnabled();


        output.writeInt( 0x02014b50);
        output.writeShort( versionMadeBy);
        output.writeShort( versionRequired);
        output.writeShort( generalPurposeBits);
        output.writeShort( compression);
        output.writeShort( modificationTime);
        output.writeShort( modificationDate);
        output.writeInt( crc32);
        output.writeInt( compressedSize);
        output.writeInt( size);
        output.writeShort( (short)filename.length());
        output.writeShort( (short)(extraData.length + numAlignBytes));
        output.writeShort( (short)fileComment.length());
        output.writeShort( diskNumberStart);
        output.writeShort( internalAttributes);
        output.writeInt( externalAttributes);
        output.writeInt( localHeaderOffset);
        
        output.writeString( filename);
        output.writeBytes( extraData);
        if (numAlignBytes > 0) output.writeBytes( alignBytes, 0, numAlignBytes);
        output.writeString( fileComment);

    }

    /*
     * Returns timetamp in Java format
     */
    public long getTime() {
        int year = (int)(((modificationDate >> 9) & 0x007f) + 80);
        int month = (int)(((modificationDate >> 5) & 0x000f) - 1);
        int day = (int)(modificationDate & 0x001f);
        int hour = (int)((modificationTime >> 11) & 0x001f);
        int minute = (int)((modificationTime >> 5) & 0x003f);
        int seconds = (int)((modificationTime << 1) & 0x003e);
        Date d = new Date( year, month, day, hour, minute, seconds);
        return d.getTime();
    }

    /*
     * Set the file timestamp (using a Java time value).
     */
    public void setTime(long time) {
        Date d = new Date(time);
        long dtime;
        int year = d.getYear() + 1900;
        if (year < 1980) {
            dtime = (1 << 21) | (1 << 16);
        }
        else {
            dtime = (year - 1980) << 25 | (d.getMonth() + 1) << 21 |
            d.getDate() << 16 | d.getHours() << 11 | d.getMinutes() << 5 |
            d.getSeconds() >> 1;
        }

        modificationDate = (short)(dtime >> 16);
        modificationTime = (short)(dtime & 0xFFFF);
    }

    public boolean isDirectory() {
        return filename.endsWith("/");
    }
    
    public String getName() {
        return filename;
    }
    
    public void setName( String filename) {
        this.filename = filename;
    }
    
    /** Use 0 (STORED), or 8 (DEFLATE). */
    public void setCompression( int compression) {
        this.compression = (short)compression;
    }

    public short getVersionMadeBy() {
        return versionMadeBy;
    }

    public short getVersionRequired() {
        return versionRequired;
    }

    public short getGeneralPurposeBits() {
        return generalPurposeBits;
    }

    public short getCompression() {
        return compression;
    }

    public int getCrc32() {
        return crc32;
    }

    public int getCompressedSize() {
        return compressedSize;
    }

    public int getSize() {
        return size;
    }

    public byte[] getExtraData() {
        return extraData;
    }

    public String getFileComment() {
        return fileComment;
    }

    public short getDiskNumberStart() {
        return diskNumberStart;
    }

    public short getInternalAttributes() {
        return internalAttributes;
    }

    public int getExternalAttributes() {
        return externalAttributes;
    }

    public int getLocalHeaderOffset() {
        return localHeaderOffset;
    }

    public long getDataPosition() {
        return dataPosition;
    }

    public EntryOutputStream getEntryOut() {
        return entryOut;
    }

    public ZipInput getZipInput() {
        return zipInput;
    }

}
