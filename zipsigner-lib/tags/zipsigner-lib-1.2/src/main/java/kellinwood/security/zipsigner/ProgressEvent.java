package kellinwood.security.zipsigner;

public class ProgressEvent {

    public static final int PRORITY_NORMAL = 0;
    public static final int PRORITY_IMPORTANT = 1;
    
    private String message;
    private int percentDone;
    private int priority;
    
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public int getPercentDone() {
        return percentDone;
    }
    public void setPercentDone(int percentDone) {
        this.percentDone = percentDone;
    }
    public int getPriority() {
        return priority;
    }
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
}
