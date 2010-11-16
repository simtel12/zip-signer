package kellinwood.logging;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public abstract class AbstractLogger implements LoggerInterface
{

	protected String category;
	
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
	
	public AbstractLogger( String category) {
		this.category = category;
	}
	
	protected String format( String level, String message) {
		return String.format( "%s %s %s: %s\n", dateFormat.format(new Date()), level, category, message);
	}
	
	protected abstract void write( String level, String message, Throwable t);
	
	public void debug(String message, Throwable t) {
		write( DEBUG, message, t);
	}

	public void debug(String message) {
		write( DEBUG, message, null);
	}

	public void error(String message, Throwable t) {
		write( ERROR, message, t);
	}

	public void error(String message) {
		write( ERROR, message, null);
	}

	public void info(String message, Throwable t) {
		write( INFO, message, t);
	}

	public void info(String message) {
		write( INFO, message, null);
	}

	public void warning(String message, Throwable t) {
		write( WARNING, message, t);
	}

	public void warning(String message) {
		write( WARNING, message, null);
	}

	public boolean isDebugEnabled() {
		return true;
	}

	public boolean isErrorEnabled() {
		return true;
	}

	public boolean isInfoEnabled() {
		return true;
	}

	public boolean isWarningEnabled() {
		return true;
	}


}
