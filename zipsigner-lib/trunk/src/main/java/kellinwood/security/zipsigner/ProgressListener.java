package kellinwood.security.zipsigner;

public interface ProgressListener {

    /** Called to notify the listener that progress has been made during
        the zip signing operation.
        @param currentItem the name of the item being processed.
        @param percentDone a value between 0 and 100 indicating 
               percent complete.
    */
    public void onProgress( String currentItem, int percentDone);
}