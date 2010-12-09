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
import java.io.FileReader;
import java.io.OutputStream;
import java.util.Properties;

import kellinwood.logging.LoggerInterface;
import kellinwood.logging.LoggerManager;
import kellinwood.logging.log4j.Log4jLoggerFactory;


import org.apache.log4j.PropertyConfigurator;
import org.junit.* ;
import static org.junit.Assert.* ;

public class CreateZipFileTest {

    static LoggerInterface log = null;
    private static LoggerInterface getLogger() {
        if (log != null) return log;
        log = LoggerManager.getLogger(CreateZipFileTest.class.getName());
        return log;
    }    

    
    @Test
    public void createZipTest() {
        
        try {
            Properties log4jProperties = new Properties();
            log4jProperties.load ( new FileReader( "log4j.properties" ));
            PropertyConfigurator.configure( log4jProperties);
            
            LoggerManager.setLoggerFactory( new Log4jLoggerFactory());
            
            boolean debug = getLogger().isDebugEnabled();
            
            String siblingFile = getClass().getResource("/simple_test.zip").getFile(); 
            File sfile = new File(siblingFile);
            File outputFile = new File(sfile.getParent(), "test_create.zip");
            
            ZipOutput zipOutput = new ZipOutput( outputFile);
            
            ZioEntry entry = new ZioEntry( "B.txt");
            OutputStream entryOut = entry.getOutputStream();
            entryOut.write( "The answer to the ultimate question of life, the universe, and everything is 42.".getBytes());
            zipOutput.write(entry);
            
            entry = new ZioEntry( "A.txt");
            entry.setCompression(0);
            entryOut = entry.getOutputStream();
            entryOut.write( "The name of the computer used to calculate the answer to the ultimate question is \"Earth\".".getBytes());
            zipOutput.write(entry);
            
            zipOutput.close();
            
        }
        catch (Exception x) {
            getLogger().error( x.getMessage(), x);
            fail( x.getClass().getName() + ": " + x.getMessage());
        }      
    }
    
    @Test
    public void mergeZipTest() {
        
        try {
            Properties log4jProperties = new Properties();
            log4jProperties.load ( new FileReader( "log4j.properties" ));
            PropertyConfigurator.configure( log4jProperties);
            
            LoggerManager.setLoggerFactory( new Log4jLoggerFactory());
            
            boolean debug = getLogger().isDebugEnabled();
            
            String siblingFile = getClass().getResource("/simple_test.zip").getFile();
            ZipInput zipInput = ZipInput.read( siblingFile);
            
            File sfile = new File(siblingFile);
            File outputFile = new File(sfile.getParent(), "test_merged.zip");
            
            ZipOutput zipOutput = new ZipOutput( outputFile);
            
            ZioEntry entry = new ZioEntry( "answer.txt");
            OutputStream entryOut = entry.getOutputStream();
            entryOut.write( "The answer to the ultimate question of life, the universe, and everything is 42.".getBytes());
            zipOutput.write(entry);
            
            entry = new ZioEntry( "A.txt");
            entry.setCompression(0);
            entryOut = entry.getOutputStream();
            entryOut.write( "The name of the computer used to calculate the answer to the ultimate question is \"Earth\".".getBytes());
            zipOutput.write(entry);
            
            for (ZioEntry e : zipInput.zioEntries.values()) {
                zipOutput.write(e);
            }
            
            zipOutput.close();
            
        }
        catch (Exception x) {
            getLogger().error( x.getMessage(), x);
            fail( x.getClass().getName() + ": " + x.getMessage());
        }      
    }    
}