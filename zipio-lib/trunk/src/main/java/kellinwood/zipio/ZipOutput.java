package kellinwood.zipio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

import kellinwood.logging.LoggerInterface;
import kellinwood.logging.LoggerManager;

/**
 *
 */
public class ZipOutput 
{

    static LoggerInterface log;

    String outputFilename;
    RandomAccessFile out = null;

    List<CentralEntry> entriesWritten = new LinkedList<CentralEntry>();
    Set<String> namesWritten = new HashSet<String>();
    
    public ZipOutput( String filename) throws IOException
    {
        this.outputFilename = filename;
        File ofile = new File( outputFilename);
        if (ofile.exists()) ofile.delete();
        out = new RandomAccessFile( ofile, "rw");
    }

    private static LoggerInterface getLogger() {
        if (log == null) log = LoggerManager.getLogger(LocalEntry.class.getName());
        return log;
    }

    public void write( CentralEntry entry) throws IOException {
        String entryName = entry.getName();
        if (namesWritten.contains( entryName)) {
            getLogger().warning("Skipping duplicate file in output: " + entryName);
            return;
        }
        entry.writeLocalEntry( this);
        entriesWritten.add( entry);
        namesWritten.add( entryName);
    }


    
    public void close() throws IOException
    {
        CentralEnd centralEnd = new CentralEnd();
        
        centralEnd.centralStartOffset = (int)getFilePointer();
        centralEnd.numCentralEntries = centralEnd.totalCentralEntries = (short)entriesWritten.size();
        
        for (CentralEntry entry : entriesWritten) {
            entry.write( this);
        }
        
        centralEnd.centralDirectorySize = (short)(getFilePointer() - centralEnd.centralStartOffset);
        centralEnd.fileComment = "";
        
        centralEnd.write( this);
        
        if (out != null) try { out.close(); } catch( Throwable t) {}
    }

    public long getFilePointer() throws IOException {
        return out.getFilePointer(); 
    }

    public void seek( long position) throws IOException {
        out.seek(position);
    }

    public void writeInt( int value) throws IOException{
        byte[] data = new byte[4];
        for (int i = 0; i < 4; i++) {
            data[i] = (byte)(value & 0xFF);
            value = value >> 8;
        }
        out.write( data);
    }

    public void writeShort( short value) throws IOException {
        byte[] data = new byte[2];
        for (int i = 0; i < 2; i++) {
            data[i] = (byte)(value & 0xFF);
            value = (short)(value >> 8);
        }
        out.write( data);

    }

    public void writeString( String value) throws IOException {

        out.write( value.getBytes());
    }

    public void writeBytes( byte[] value) throws IOException {

        out.write( value);
    }


}


