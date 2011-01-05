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


import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import kellinwood.logging.LoggerInterface;
import kellinwood.logging.LoggerManager;
import kellinwood.logging.log4j.Log4jLoggerFactory;


import org.apache.log4j.PropertyConfigurator;
import org.junit.* ;
import static org.junit.Assert.* ;

public abstract class AbstractTest {

    LoggerInterface log = null;

    protected boolean debug = false;
    
    protected LoggerInterface getLogger() {
        if (log != null) return log;
        log = LoggerManager.getLogger(this.getClass().getName());
        return log;
    }    

    public void setupLogging() {
        
        try {
            Properties log4jProperties = new Properties();
            log4jProperties.load ( new FileReader( "log4j.properties" ));
            PropertyConfigurator.configure( log4jProperties);
            
            LoggerManager.setLoggerFactory( new Log4jLoggerFactory());
            
            debug = getLogger().isDebugEnabled();
        }
        catch (RuntimeException x) {
            throw x;
        }
        catch (Throwable x) {
            throw new IllegalStateException( x.getMessage(), x);
        }
    }

}