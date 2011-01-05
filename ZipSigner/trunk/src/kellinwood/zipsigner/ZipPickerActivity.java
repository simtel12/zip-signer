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

import java.io.File;
import java.net.URL;

import kellinwood.security.zipsigner.ZipSigner;
import kellinwood.zipsigner.R;
import kellinwood.logging.LoggerManager;
import kellinwood.logging.android.AndroidLogger;
import kellinwood.logging.android.AndroidLoggerFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

/** App for signing zip, apk, and/or jar files on an Android device. 
 *  This activity allows the input/output files to be selected and shows
 *  how to invoke the ZipSignerActivity to perform the actual work.
 *  
 */
public class ZipPickerActivity extends Activity {


    protected static final int REQUEST_CODE_PICK_FILE_TO_OPEN = 1;
    protected static final int REQUEST_CODE_PICK_FILE_TO_SAVE = 2;
    protected static final int REQUEST_CODE_PICK_INOUT_FILE = 3;

    protected static final int REQUEST_CODE_SIGN_FILE = 80701;

    private static final String PREFERENCE_IN_FILE = "input_file";
    private static final String PREFERENCE_OUT_FILE = "output_file";

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

        String extStorageDir = Environment.getExternalStorageDirectory().toString();
        // Strip /mnt from /sdcard
        if (extStorageDir.startsWith("/mnt/sdcard")) extStorageDir = extStorageDir.substring(4);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String inputFile = prefs.getString(PREFERENCE_IN_FILE, extStorageDir + "/unsigned.zip");
        String outputFile = prefs.getString(PREFERENCE_OUT_FILE, extStorageDir + "/signed.zip");        

        EditText inputText = (EditText)findViewById(R.id.InFileEditText);
        inputText.setText( inputFile); 

        EditText outputText = (EditText)findViewById(R.id.OutFileEditText);
        outputText.setText( outputFile);

        Button button = (Button) findViewById(R.id.OpenPickButton);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                pickInputFile();
            }
        });

        button = (Button) findViewById(R.id.SaveAsPickButton);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                pickOutputFile();
            }
        });      
        
        button = (Button) findViewById(R.id.InOutPickButton);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                pickInputOutputFiles();
            }
        });        
        
        Spinner spinner = (Spinner) findViewById(R.id.KeyModeSpinner);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>( this, android.R.layout.simple_spinner_item);
        for (String mode : ZipSigner.SUPPORTED_KEY_MODES) {
            adapter.add(mode);
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        
    }

    private String getInputFilename() {
        return ((EditText)findViewById(R.id.InFileEditText)).getText().toString();
    }

    private String getOutputFilename() {
        return ((EditText)findViewById(R.id.OutFileEditText)).getText().toString();
    }

    private void invokeZipSignerActivity() {
        try {

            String inputFile = getInputFilename();
            String outputFile = getOutputFilename();

            // Save the input,output file names to preferences
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PREFERENCE_IN_FILE, inputFile);
            editor.putString(PREFERENCE_OUT_FILE, outputFile);            
            editor.commit();            

            // Refuse to do anything if the external storage device is not writable (external storage = /sdcard).
            if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState())) {
                logger.error("ERROR: External storage is mounted read-only");
                return;
            }

            // Launch the ZipSignerActivity to perform the signature operation.
            Intent i = new Intent("kellinwood.zipsigner.action.SIGN_FILE");

            // Required parameters - input and output files.  The filenames must be different (e.g., 
            // you can't sign the file and save the output to itself).
            i.putExtra("inputFile", inputFile);
            i.putExtra("outputFile", outputFile);

            String keyMode = (String)((Spinner)this.findViewById(R.id.KeyModeSpinner)).getSelectedItem();
            logger.debug(keyMode);
            i.putExtra("keyMode", keyMode);

            // If "showProgressItems" is true, then the ZipSignerActivity displays the names of files in the 
            // zip as they are generated/copied during the signature process.
            i.putExtra("showProgressItems", "true"); 

            // Set the result code used to indicate that auto-key selection failed.  This will default to
            // RESULT_FIRST_USER if not set (same code used to signal an error).
            i.putExtra("autoKeyFailRC", RESULT_FIRST_USER+1);
            
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

    protected void alertDialog( String title, String message) {
        final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        // TODO: move text into resources                    
        alertDialog.setTitle( title);
        alertDialog.setMessage( message);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", (Message)null);
        alertDialog.show();
        Button button = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        button.setOnClickListener( new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                alertDialog.dismiss();
            }
            
        });        
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
            case REQUEST_CODE_PICK_INOUT_FILE:
                // obtain the filename
                uri = data == null ? null : data.getData();
                if (uri != null) {
                    String filename = uri.getPath();
                    ((EditText)findViewById(R.id.InFileEditText)).setText(filename);
                    // auto set output file ... "input.zip" becomes "input-signed.zip"
                    int pos = filename.lastIndexOf('.');
                    if (pos > 0) {
                        filename = filename.substring(0, pos) + "-signed" + filename.substring(pos); 
                    }
                    ((EditText)findViewById(R.id.OutFileEditText)).setText(filename);
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
            case REQUEST_CODE_PICK_FILE_TO_OPEN:
                break;
            case REQUEST_CODE_PICK_FILE_TO_SAVE:
                break;
            case REQUEST_CODE_PICK_INOUT_FILE:
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
        case RESULT_FIRST_USER+1: // error with auto-key selection
            switch (requestCode) {
            case REQUEST_CODE_SIGN_FILE:
                // TODO display alert dialog?
                String errorMessage = data.getStringExtra("errorMessage");
                alertDialog("Key Selection Error", errorMessage);
                break;
            default:
                logger.error("onActivityResult, RESULT_FIRST_USER+1, unknown requestCode " + requestCode);
                break;
            }
            break;            
        default:
            logger.error("onActivityResult, unknown resultCode " + resultCode + ", requestCode = " + requestCode);
        }

    }

    private void launchFileBrowser( String reason, int requestCode, String samplePath)
    {
        try
        {
            String startPath = "/";
            String inf = samplePath;
            if (inf != null && inf.length() > 0) {
                File f = new File( samplePath);
                startPath = f.getParent();
            }

            Intent intent = new Intent("kellinwood.zipsigner.action.BROWSE_FILE");
            intent.putExtra("startPath", startPath);
            intent.putExtra("reason", reason);
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, e.getMessage(), 0).show();
        }
    }


    private void pickInputFile(){
        launchFileBrowser( "select input", REQUEST_CODE_PICK_FILE_TO_OPEN, getInputFilename());
    }


    private void pickOutputFile() {
        launchFileBrowser( "select output", REQUEST_CODE_PICK_FILE_TO_SAVE, getOutputFilename());
    }

    private void pickInputOutputFiles() {
        launchFileBrowser( "select input", REQUEST_CODE_PICK_INOUT_FILE, getOutputFilename());
    }


}