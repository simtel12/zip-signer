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

public class BasicTest {

    static LoggerInterface log = null;
    private static LoggerInterface getLogger() {
        if (log != null) return log;
        log = LoggerManager.getLogger(BasicTest.class.getName());
        return log;
    }    

    
    @Test
    public void firstTest() {
        
        try {
            Properties log4jProperties = new Properties();
            log4jProperties.load ( new FileReader( "log4j.properties" ));
            PropertyConfigurator.configure( log4jProperties);
            
            LoggerManager.setLoggerFactory( new Log4jLoggerFactory());
            
            String inputFile = getClass().getResource("/simple_test.zip").getFile(); 
            getLogger().info("Loading " + inputFile);
            
            ZipInput zipInput = ZipInput.read( inputFile);
            getLogger().info("Entry count: " + zipInput.getEntries().size());
            
            CentralEntry entry = zipInput.getEntries().values().iterator().next();
            
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            String inputDate = "2010-12-25 02:59:42";
            Date date = dateFormat.parse( inputDate);
            
            entry.setTime(date.getTime());
            
            date = new Date( entry.getTime());
            
            String testDate = dateFormat.format( date);
            
            log.info( String.format("Input date: %s, test date: %s", inputDate, testDate));
            
            assertEquals( inputDate, testDate);
            
            getLogger().info("File "+entry.getName()+", date: " + dateFormat.format(date));
            
            
            
            assertTrue(true);    
        }
        catch (Exception x) {
            getLogger().error( x.getMessage(), x);
        }      
        
    }
}