package kellinwood.logging.log4j;

import org.apache.log4j.Logger;


import kellinwood.logging.LoggerInterface;

public class Log4jLogger implements LoggerInterface {

    Logger log;
    
    public Log4jLogger(String category) {
    
        log = Logger.getLogger( category);
    }

    @Override
    public void debug(String message, Throwable t) {
        log.debug( message, t);
    }

    @Override
    public void debug(String message) {
        log.debug( message);
    }

    @Override
    public void error(String message, Throwable t) {
        log.error( message, t);
    }

    @Override
    public void error(String message) {
        log.error( message);
    }

    @Override
    public void info(String message, Throwable t) {
        log.info( message, t);
    }

    @Override
    public void info(String message) {
        log.info( message);
    }

    @Override
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return log.isInfoEnabled();
    }

    @Override
    public boolean isWarningEnabled() {
        return true;
    }

    @Override
    public void warning(String message, Throwable t) {
        log.warn(message, t);
    }

    @Override
    public void warning(String message) {
        log.warn(message);
    }

}
