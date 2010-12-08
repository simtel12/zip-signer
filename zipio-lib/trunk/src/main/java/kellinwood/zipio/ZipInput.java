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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Manifest;

import kellinwood.logging.LoggerInterface;
import kellinwood.logging.LoggerManager;

/**
 *
 */
public class ZipInput 
{

    static LoggerInterface log;

    public String inputFilename;
    RandomAccessFile in = null;
    long fileLength;

    Map<String,LocalEntry> localEntries = new LinkedHashMap<String,LocalEntry>();
    Map<String,CentralEntry> centralEntries = new LinkedHashMap<String,CentralEntry>();
    CentralEnd centralEnd;
    Manifest manifest;

    public ZipInput( String filename) throws IOException
    {
        this.inputFilename = filename;
        in = new RandomAccessFile( new File( inputFilename), "r");
        fileLength = in.length();
    }

    private static LoggerInterface getLogger() {
        if (log == null) log = LoggerManager.getLogger(LocalEntry.class.getName());
        return log;
    }

    public static ZipInput read( String filename) throws IOException {
        ZipInput zipInput = new ZipInput( filename);
        zipInput.doRead();
        return zipInput;
    }
    
    
    public CentralEntry getEntry( String filename) {
        return centralEntries.get(filename);
    }
    
    public Map<String,CentralEntry> getEntries() {
        return centralEntries;
    }
    
    public Manifest getManifest() throws IOException {
        if (manifest == null) {
            LocalEntry le = localEntries.get("META-INF/MANIFEST.MF");
            if (le != null) {
                manifest = new Manifest( le.getDataStream());
            }
        }
        return manifest; 
    }

    private void doRead()
    {
        try {

            LocalEntry localEntry = LocalEntry.read(this);
            while (localEntry != null) {
                if (localEntry.filename.equals("META-INF/MANIFEST.MF")) {

                }
                localEntries.put( localEntry.filename, localEntry);
                localEntry = LocalEntry.read(this);
            } 

            CentralEntry centralEntry = CentralEntry.read(this);
            while (centralEntry != null) {
                centralEntry.localEntry = localEntries.get( centralEntry.filename);
                centralEntries.put( centralEntry.filename, centralEntry);
                centralEntry = CentralEntry.read(this);
            } 

            centralEnd = CentralEnd.read( this);
        }
        catch (Throwable t) {
            t.printStackTrace();
        }    	
    }

    public void close() {
        if (in != null) try { in.close(); } catch( Throwable t) {}
    }

    public long getFilePointer() throws IOException {
        return in.getFilePointer(); 
    }

    public void seek( long position) throws IOException {
        in.seek(position);
    }

    public byte readByte() throws IOException {
        return in.readByte();
    }
    
    public int readInt() throws IOException{
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result |= (in.readUnsignedByte() << (8 * i));
        }
        return result;
    }

    public short readShort() throws IOException {
        short result = 0;
        for (int i = 0; i < 2; i++) {
            result |= (in.readUnsignedByte() << (8 * i));
        }
        return result;
    }

    public String readString( int length) throws IOException {

        byte[] buffer = new byte[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = in.readByte();
        }
        return new String(buffer);
    }

    public byte[] readBytes( int length) throws IOException {

        byte[] buffer = new byte[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = in.readByte();
        }
        return buffer;
    }


}


