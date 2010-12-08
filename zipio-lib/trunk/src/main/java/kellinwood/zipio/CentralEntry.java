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
import java.io.OutputStream;
import java.util.Date;

import kellinwood.logging.LoggerInterface;
import kellinwood.logging.LoggerManager;

public class CentralEntry {

    public ZipInput zipInput;

    public int signature = 0x02014b50;
    public short versionMadeBy;
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
    public String fileComment;
    public short diskNumberStart;
    public short internalAttributes;
    public int externalAttributes;
    public int localHeaderOffset;
    public LocalEntry localEntry;

    private static LoggerInterface log;

    public CentralEntry( ZipInput input) {
        zipInput = input;
    }

    public static LoggerInterface getLogger() {
        if (log == null) log = LoggerManager.getLogger( CentralEntry.class.getName());
        return log;
    }

    public CentralEntry( String name) {
        filename = name;
        fileComment = "";
        compression = 8;
        extraData = new byte[0];
        localEntry = new LocalEntry( name);
        setTime( System.currentTimeMillis());
    }
    

    public static CentralEntry read(ZipInput input) throws IOException
    {

        // 0    4   Central directory header signature = 0x04034b50
        int signature = input.readInt();
        if (signature != 0x02014b50) {
            // back up to the signature
            input.seek( input.getFilePointer() - 4);
            return null;
        }

        CentralEntry entry = new CentralEntry( input);

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


    }

    public void writeLocalEntry( ZipOutput output) throws IOException
    {
        localHeaderOffset = (int)output.getFilePointer();
        localEntry.write( output);

        // Migrate changes to local entry up to central header
        generalPurposeBits = localEntry.generalPurposeBits;
        compression = localEntry.compression;
        crc32 = localEntry.crc32;
        compressedSize = localEntry.compressedSize;
        size = localEntry.size;
        
    }

    public boolean isDirectory() {
        return filename.endsWith("/");
    }
    
    public String getName() {
        return filename;
    }
    
    public void setName( String filename) {
        localEntry.filename = filename;
        this.filename = filename;
    }
    
    public void write( ZipOutput output) throws IOException {
        boolean debug = getLogger().isDebugEnabled();


        output.writeInt( signature);
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
        output.writeShort( (short)extraData.length);
        output.writeShort( (short)fileComment.length());
        output.writeShort( diskNumberStart);
        output.writeShort( internalAttributes);
        output.writeInt( externalAttributes);
        output.writeInt( localHeaderOffset);

        output.writeString( filename);
        output.writeBytes( extraData);
        output.writeString( fileComment);


    }

    public byte[] getData() throws IOException
    {
        return localEntry.getData();
    }

    // Returns an input stream for reading the entry's data. 
    public InputStream getDataStream() throws IOException {
        return localEntry.getDataStream();
    }
    
    public OutputStream getDataOutputStream() {
        return localEntry.getDataOutputStream();
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

        localEntry.modificationDate = modificationDate = (short)(dtime >> 16);
        localEntry.modificationTime = modificationTime = (short)(dtime & 0xFFFF);
    }

    
    // 0 (STORED), or 8 (DEFLATE)
    public void setCompression( int compression) {
        this.localEntry.compression = this.compression = (short)compression;
    }

}
