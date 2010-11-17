/*
 * Copyright (C) 2010 Ken Ellinwood.
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
package kellinwood.zipsigner;

import java.net.URL;

import kellinwood.zipsigner.R;
import kellinwood.logging.LoggerManager;
import kellinwood.logging.android.AndroidLogger;
import kellinwood.logging.android.AndroidLoggerFactory;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/** Demo app for signing zip, apk, and/or jar files on an Android device. 
 *  This activity allows the input/output files to be selected and shows
 *  how to invoke the ZipSignerActivity to perform the actual work.
 *  
 *  If you have ES File Explorer installed, then you can use the 'choose' and 
 *  'save as' buttons to select the input and output files using the explorer. 
 * */
public class ZipPickerActivity extends Activity {
	

	protected static final int REQUEST_CODE_PICK_FILE_TO_OPEN = 1;
	protected static final int REQUEST_CODE_PICK_FILE_TO_SAVE = 2;
	protected static final int REQUEST_CODE_PICK_DIRECTORY = 3;
	
	protected static final int REQUEST_CODE_SIGN_FILE = 80701;
	
	AndroidLogger logger = null;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.zip_picker);
        
        LoggerManager.setLoggerFactory( new AndroidLoggerFactory());
        
        logger = (AndroidLogger)LoggerManager.getLogger(this.getClass().getName());
        // enable toasts for info level logging.  toasts are default for error and warnings.
        logger.setToastContext(getBaseContext());
        logger.setInfoToastEnabled(true);
        
        Button createButton = (Button)findViewById(R.id.SignButton);
        createButton.setOnClickListener( new OnClickListener() {
        	public void onClick( View view) {
        		invokeZipSignerActivity();
        	}
        });
        
        EditText inputText = (EditText)findViewById(R.id.InFileEditText);
        inputText.setText( Environment.getExternalStorageDirectory().toString() + "/test_unsigned.zip");
        
        EditText outputText = (EditText)findViewById(R.id.OutFileEditText);
        outputText.setText( Environment.getExternalStorageDirectory().toString() + "/test_signed.zip");
        
        Button button = (Button) findViewById(R.id.OpenPickButton);
        button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				openFile();
			}
        });

        button = (Button) findViewById(R.id.SaveAsPickButton);
        button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				saveFile();
			}
        });        
        
    }
    
    private void invokeZipSignerActivity() {
    	try {
    		
    		// Refuse to do anything of the external storage device is not writeable (external storage = /sdcard).
    		if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState())) {
    			logger.error("ERROR: External storage is mounted read-only");
    			return;
    		}
    		
    		// Launch the ZipSignerActivity to perform the signature operation.
    		Intent i = new Intent("kellinwood.zipsigner.action.SIGN_FILE");
    		
    		// Required parameters - input and output files.  The filenames must be different (e.g., 
    		// you can't sign the file and save the output to itself).
    		i.putExtra("inputFile", ((EditText)findViewById(R.id.InFileEditText)).getText().toString());
    		i.putExtra("outputFile", ((EditText)findViewById(R.id.OutFileEditText)).getText().toString());
    		
//    		// The following keystore/key parameters are optional.  
//    		// The default values are as you see them here.
//    		URL keystoreUrl = getClass().getResource("/assets/keystore.ks");
//    		if (keystoreUrl == null) {
//    			logger.error( "Unable to locate keystore.");
//    			return;
//    		}    		
//    		i.putExtra("keystoreUrl", keystoreUrl.toExternalForm());
//    		i.putExtra("keystoreType", "BKS");
//    		i.putExtra("keystorePass", "android");
//    		i.putExtra("keyAlias", "CERT");
//    		i.putExtra("keyPass", "android");
    		
    		// If "showProgressItems" is true, then the ZipSignerActivity displays the names of files in the 
    		// zip as they are generated/copied during the signature process.
    		i.putExtra("showProgressItems", "true"); 
    		
    		
    		// Activity is started and the result is returned via a call to onActivityResult(), below.
    		startActivityForResult(i, REQUEST_CODE_SIGN_FILE);
    		
    		
    	}
    	catch (Throwable x) {
    		logger.error( x.getClass().getName() + ": " + x.getMessage(), x);
    	}
    	
    }

    
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }
    
    /* Handles item selections */
    public boolean onOptionsItemSelected(MenuItem item) 
    {
        switch (item.getItemId()) {
        case R.id.MenuItemShowHelp:
        	String targetURL = getString(R.string.AboutZipSignerDocUrl);
        	Intent i = new Intent( Intent.ACTION_VIEW, Uri.parse(targetURL));
        	startActivity(i);
        	return true;
        case R.id.MenuItemAbout:
        	AboutDialog.show(this);
        	return true;
        }
        return false;
    }
    
    /**
     * Receives the result of other activities started with startActivityForResult(...)
     */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

        Uri uri;
        
        switch (resultCode)
        {
        case RESULT_OK:

            switch (requestCode) {
			case REQUEST_CODE_PICK_FILE_TO_OPEN:
				// obtain the filename
                uri = data == null ? null : data.getData();
				if (uri != null) {
					((EditText)findViewById(R.id.InFileEditText)).setText(uri.getPath());
				}				
				break;
			case REQUEST_CODE_PICK_FILE_TO_SAVE:
				// obtain the filename
                uri = data == null ? null : data.getData();
				if (uri != null) {
					((EditText)findViewById(R.id.OutFileEditText)).setText(uri.getPath());
				}				
				break;
            case REQUEST_CODE_SIGN_FILE:
                logger.info("File signing operation succeeded!");
                break;
            default:
                logger.error("onActivityResult, RESULT_OK, unknown requestCode " + requestCode);
                break;
            }
            break;
        case RESULT_CANCELED:   // signing operation canceled
            switch (requestCode) {
            case REQUEST_CODE_SIGN_FILE:
                logger.info("File signing CANCELED!");
                break;
            default:
                logger.error("onActivityResult, RESULT_CANCELED, unknown requestCode " + requestCode);
                break;
            }
            break;
        case RESULT_FIRST_USER: // error during signing operation
            switch (requestCode) {
            case REQUEST_CODE_SIGN_FILE:
            	// ZipSignerActivity displays a toast upon exiting with an error, so we probably don't need to do this.
            	String errorMessage = data.getStringExtra("errorMessage");
                logger.debug("Error during file signing: " + errorMessage);
                break;
            default:
                logger.error("onActivityResult, RESULT_FIRST_USER, unknown requestCode " + requestCode);
                break;
            }
            break;
        default:
            logger.error("onActivityResult, unknown resultCode " + resultCode + ", requestCode = " + requestCode);
        }

	}
    
	private void openFile(){
    	try{
    		Intent intent = getIntent("com.estrongs.action.PICK_FILE",getString(R.string.FileManagerOpenButtonLabel));
    		startActivityForResult(intent, REQUEST_CODE_PICK_FILE_TO_OPEN);
	    } catch (ActivityNotFoundException e) {
			Toast.makeText(this, R.string.ESNoFileManagerInstalled, 0).show();
		}
    }
   
    private void saveFile() {
		
		Intent intent = getIntent("com.estrongs.action.PICK_FILE",getString(R.string.FileManagerSaveButtonLabel));
		// Assign a path.
		intent.setData(Uri.parse("file://" + Environment.getExternalStorageDirectory().toString() + "/"));
		
		try {
			startActivityForResult(intent, REQUEST_CODE_PICK_FILE_TO_SAVE);
		} catch (ActivityNotFoundException e) {
			// No compatible file manager was found.
			Toast.makeText(this, getString(R.string.ESNoFileManagerInstalled),Toast.LENGTH_SHORT).show();
		}
	}
    
    
    private Intent getIntent(String action,String btnTitle){
    	
    	Intent intent = new Intent(action);
    	
    	if (btnTitle != null)
    		intent.putExtra("com.estrongs.intent.extra.BUTTON_TITLE", btnTitle);
		
		return intent ;
    }    
}