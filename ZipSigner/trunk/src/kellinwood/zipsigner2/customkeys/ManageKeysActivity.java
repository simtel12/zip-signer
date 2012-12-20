/*
 * Copyright (C) 2012 Ken Ellinwood.
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
package kellinwood.zipsigner2.customkeys;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.UnrecoverableKeyException;
import java.util.Enumeration;
import java.util.List;

import android.app.ProgressDialog;
import kellinwood.zipsigner2.R;
import kellinwood.zipsigner2.ZipPickerActivity;

import kellinwood.logging.LoggerInterface;
import kellinwood.logging.LoggerManager;
import kellinwood.logging.android.AndroidLogger;
import kellinwood.logging.android.AndroidLoggerFactory;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ListView;

/* Work with Keystore files, keys, and passwords.
 */
public class ManageKeysActivity extends Activity {

    // Codes used for inter-thread messaging
    static final int MESSAGE_CODE_LOAD_KEYSTORE_PASSWORD = 1;   
    static final int MESSAGE_CODE_BAD_KEYSTORE_PASSWORD = 2;   
    static final int MESSAGE_CODE_KEYSTORE_LOADED = 3;   
    static final int MESSAGE_CODE_KEYSTORE_LOAD_ERROR = 4;
    static final int MESSAGE_CODE_KEYSTORE_REMEMBER_PASSWORD = 5;
    static final int MESSAGE_CODE_ALIAS_REMEMBER_PASSWORD = 6;
    static final int MESSAGE_CODE_ALIAS_DISPLAY_NAME = 7;

    
    // codes used for inter-activity messsaging
    protected static final int REQUEST_CODE_PICK_KEYSTORE_FILE = 1;

    private static final int MENU_ITEM_REMOVE = 42;    
    private static final int MENU_ITEM_KEYSTORE_REMEMBER_PASSWORD = 43;    
    private static final int MENU_ITEM_KEYSTORE_FORGET_PASSWORD = 44;  
    private static final int MENU_ITEM_ALIAS_REMEMBER_PASSWORD = 45;
    private static final int MENU_ITEM_ALIAS_FORGET_PASSWORD = 46;
    private static final int MENU_ITEM_ALIAS_DISPLAY_NAME = 47;

    AndroidLogger logger = null;

    ExpandableListView keystoreListView = null;
    KeystoreExpandableListAdapter keystoreExpandableListAdapter = null;

    String extStorageDir = "/";

    CustomKeysDataSource customKeysDataSource = null;
    ProgressDialog keystoreLoadingDialog = null;
    
    
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_keys);

        LoggerManager.setLoggerFactory( new AndroidLoggerFactory());

        logger = (AndroidLogger)LoggerManager.getLogger(this.getClass().getName());
        // enable toasts for info level logging.  toasts are default for error and warnings.
        logger.setToastContext(getBaseContext());
        logger.setInfoToastEnabled(true);


        extStorageDir = Environment.getExternalStorageDirectory().toString();
        // Strip /mnt from /sdcard
        if (extStorageDir.startsWith("/mnt/sdcard")) extStorageDir = extStorageDir.substring(4);
        
        Button button = (Button) findViewById(R.id.AddKeystoreButton);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                launchSelectKeystoreFile();
            }
        });
    
        customKeysDataSource = new CustomKeysDataSource(getBaseContext());
        customKeysDataSource.open();
        
        keystoreListView = (ExpandableListView)findViewById(R.id.KeystoreExpandableListView);
        keystoreExpandableListAdapter = new KeystoreExpandableListAdapter(this, customKeysDataSource.getAllKeystores());
        keystoreListView.setAdapter( keystoreExpandableListAdapter);
        keystoreListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        for(int i=0; i < keystoreExpandableListAdapter.getGroupCount(); i++) {
            keystoreListView.expandGroup(i);
        }

        keystoreListView.setOnCreateContextMenuListener( new OnCreateContextMenuListener()
        {
            public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {

                ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
                int type = ExpandableListView.getPackedPositionType(info.packedPosition);
                int group = ExpandableListView.getPackedPositionGroup(info.packedPosition);
                int child = ExpandableListView.getPackedPositionChild(info.packedPosition);
                
                //Only create a context menu for group items
                if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
                    List<Keystore> keystoreList = customKeysDataSource.getAllKeystores();
                    Keystore keystore = keystoreList.get( group);
                    menu.setHeaderTitle(keystore.getPath());
                    menu.add(0, MENU_ITEM_REMOVE, 0, R.string.UnregisterMenuItemLabel);
                    if (keystore.rememberPassword())
                        menu.add( 0, MENU_ITEM_KEYSTORE_FORGET_PASSWORD, 0, R.string.ForgetPasswordMenuItemLabel);
                    else 
                        menu.add( 0, MENU_ITEM_KEYSTORE_REMEMBER_PASSWORD, 0, R.string.RememberPasswordMenuItemLabel);
                }
                else if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
                    
                    List<Keystore> keystoreList = customKeysDataSource.getAllKeystores();
                    Keystore keystore = keystoreList.get( group);
                    Alias alias = keystore.getAliases().get(child);
                    logger.debug("Adding context menu for alias " + alias.getName());
                    menu.setHeaderTitle(alias.getName());
                    if (alias.rememberPassword()) {
                        menu.add( 0, MENU_ITEM_ALIAS_FORGET_PASSWORD, 0, R.string.ForgetPasswordMenuItemLabel);
                    } else {
                        menu.add( 0, MENU_ITEM_ALIAS_REMEMBER_PASSWORD, 0, R.string.RememberPasswordMenuItemLabel);
                    }
                    menu.add( 0, MENU_ITEM_ALIAS_DISPLAY_NAME, 0, R.string.DisplayNameMenuItemLabel);
                }
            }
        });

    }

    
    @Override
    protected void onStart() {
        super.onStart();
        customKeysDataSource.open();
    }

    @Override
    protected void onStop() {
        super.onStop();
        customKeysDataSource.close();
    }

    public boolean onContextItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
        case MENU_ITEM_REMOVE:
            doExpListContextMenuOp( item, MENU_ITEM_REMOVE);
            break;
        case MENU_ITEM_KEYSTORE_REMEMBER_PASSWORD:
            doExpListContextMenuOp( item, MENU_ITEM_KEYSTORE_REMEMBER_PASSWORD);
            break;
        case MENU_ITEM_KEYSTORE_FORGET_PASSWORD:
            doExpListContextMenuOp( item, MENU_ITEM_KEYSTORE_FORGET_PASSWORD);
            break;
        case MENU_ITEM_ALIAS_REMEMBER_PASSWORD:
            doExpListContextMenuOp( item, MENU_ITEM_ALIAS_REMEMBER_PASSWORD);
            break;
        case MENU_ITEM_ALIAS_FORGET_PASSWORD:
            doExpListContextMenuOp( item, MENU_ITEM_ALIAS_FORGET_PASSWORD);
            break;
        case MENU_ITEM_ALIAS_DISPLAY_NAME:
            doExpListContextMenuOp( item, MENU_ITEM_ALIAS_DISPLAY_NAME);
            break;
        default:
            logger.error("Unknown context menu item ID: " + item.getItemId());
            break;
        }
        return false;
    }

    private void doExpListContextMenuOp( MenuItem item, int opcode) 
    {
        ExpandableListView.ExpandableListContextMenuInfo info = 
            (ExpandableListView.ExpandableListContextMenuInfo)item.getMenuInfo();
        int group = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        int child = ExpandableListView.getPackedPositionChild(info.packedPosition);
        List<Keystore> keystoreList = customKeysDataSource.getAllKeystores();
        Keystore keystore = keystoreList.get( group);
        Alias alias;
        try {
            switch (opcode) {
            case MENU_ITEM_REMOVE:
                customKeysDataSource.deleteKeystore(keystore);
                keystoreExpandableListAdapter.dataChanged(customKeysDataSource.getAllKeystores());
                break;
            case MENU_ITEM_KEYSTORE_REMEMBER_PASSWORD:
                EnterPasswordDialog.show( 
                        ManageKeysActivity.this, handler, getResources().getString( R.string.EnterKeystorePassword),
                        MESSAGE_CODE_KEYSTORE_REMEMBER_PASSWORD, keystore.getPath(), keystore.getId(), true, null);
                break;
            case MENU_ITEM_KEYSTORE_FORGET_PASSWORD:
                keystore.setRememberPassword(false);
                keystore.setPassword(null);
                customKeysDataSource.updateKeystore( keystore);
                keystoreExpandableListAdapter.dataChanged(keystoreList);
                break;
            case MENU_ITEM_ALIAS_REMEMBER_PASSWORD:
                alias = keystore.getAliases().get( child);
                EnterPasswordDialog.show(
                    ManageKeysActivity.this, handler, getResources().getString( R.string.EnterKeyPassword),
                    MESSAGE_CODE_ALIAS_REMEMBER_PASSWORD, keystore.getPath(), alias.getId(), true, alias.getName());
                break;
            case MENU_ITEM_ALIAS_FORGET_PASSWORD:
                alias = keystore.getAliases().get( child);
                alias.setRememberPassword(false);
                alias.setPassword(null);
                customKeysDataSource.updateAlias(alias);
                keystoreExpandableListAdapter.dataChanged(keystoreList);
                break;
            case MENU_ITEM_ALIAS_DISPLAY_NAME:
                alias = keystore.getAliases().get( child);
                EditDisplayNameDialog.show(this, handler, getResources().getString(R.string.DisplayNameMenuItemLabel),
                    MESSAGE_CODE_ALIAS_DISPLAY_NAME, alias.getId(), alias.getDisplayName());
                break;
            }
        }
        catch (Exception x) { logger.error(x.getMessage(), x); }        
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
            case REQUEST_CODE_PICK_KEYSTORE_FILE:
                // obtain the filename
                uri = data == null ? null : data.getData();
                if (uri != null) {
                    EnterPasswordDialog.show( 
                            ManageKeysActivity.this, handler, getResources().getString( R.string.EnterKeystorePassword),
                            MESSAGE_CODE_LOAD_KEYSTORE_PASSWORD, uri.getPath(), 0, true, null);
                }                           
                break;                
            default:
                logger.error("onActivityResult, RESULT_OK, unknown requestCode " + requestCode);
                break;
            }
            break;
        case RESULT_CANCELED:   // operation canceled
            switch (requestCode) {
            case REQUEST_CODE_PICK_KEYSTORE_FILE:
                break;                
            default:
                logger.error("onActivityResult, RESULT_CANCELED, unknown requestCode " + requestCode);
                break;
            }
            break;
        default:
            logger.error("onActivityResult, unknown resultCode " + resultCode + ", requestCode = " + requestCode);
        }

    }


    private void launchSelectKeystoreFile()
    {
        boolean debug = logger.isDebugEnabled();
        // Set sample path to the filename of the last theme loaded, or the output filename if there aren't any keystores loaded yet.
        // This will keep the starting directory of the file browser somewhat consistent.
        String samplePath = extStorageDir + "/dummy.txt";
        if (debug) logger.debug( String.format("Using sample path: %s", samplePath));
        ZipPickerActivity.launchFileBrowser(this, getResources().getString(R.string.BrowserSelectKeystore), REQUEST_CODE_PICK_KEYSTORE_FILE, samplePath);
    }

    


    // Define the Handler that receives messages from the threads and update the display
    final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            
            
            switch (msg.what) {
            case ManageKeysActivity.MESSAGE_CODE_LOAD_KEYSTORE_PASSWORD:
                String encodedPassword = msg.getData().getString(EnterPasswordDialog.MSG_DATA_PASSWORD);
                String keystorePath = msg.getData().getString(EnterPasswordDialog.MSG_DATA_PATH);
                boolean rememberPassword = msg.getData().getBoolean( EnterPasswordDialog.MSG_DATA_REMEMBER_PASSWORD);
                keystoreLoadingDialog = new ProgressDialog(ManageKeysActivity.this);
                keystoreLoadingDialog.setMessage(getResources().getString(R.string.KeystoreLoadingMessage));
                keystoreLoadingDialog.show();
                new KeystoreLoader( keystorePath, encodedPassword, rememberPassword).start();
                break;
            case ManageKeysActivity.MESSAGE_CODE_BAD_KEYSTORE_PASSWORD:
                if (keystoreLoadingDialog != null) keystoreLoadingDialog.dismiss(); keystoreLoadingDialog = null;
                encodedPassword = msg.getData().getString(EnterPasswordDialog.MSG_DATA_PASSWORD);
                keystorePath = msg.getData().getString(EnterPasswordDialog.MSG_DATA_PATH);
                rememberPassword = msg.getData().getBoolean(EnterPasswordDialog.MSG_DATA_REMEMBER_PASSWORD);
                logger.error(getResources().getString(R.string.WrongKeystorePassword));
                EnterPasswordDialog.show(
                    ManageKeysActivity.this, handler, getResources().getString(R.string.EnterKeystorePassword),
                    MESSAGE_CODE_LOAD_KEYSTORE_PASSWORD, keystorePath, 0, rememberPassword, null);
                break;
            case ManageKeysActivity.MESSAGE_CODE_KEYSTORE_LOADED:
                if (keystoreLoadingDialog != null) keystoreLoadingDialog.dismiss(); keystoreLoadingDialog = null;
                logger.debug("Keystore loaded.");
                keystoreExpandableListAdapter.dataChanged(customKeysDataSource.getAllKeystores());
                keystoreListView.expandGroup(keystoreExpandableListAdapter.getGroupCount()-1);
                break;
            case ManageKeysActivity.MESSAGE_CODE_KEYSTORE_LOAD_ERROR:
                if (keystoreLoadingDialog != null) keystoreLoadingDialog.dismiss(); keystoreLoadingDialog = null;
                logger.error( msg.getData().getString( EnterPasswordDialog.MSG_DATA_MESSAGE));
                break;
            case ManageKeysActivity.MESSAGE_CODE_KEYSTORE_REMEMBER_PASSWORD:
                long keystoreId = msg.getData().getLong(EnterPasswordDialog.MSG_DATA_ID);
                encodedPassword = msg.getData().getString(EnterPasswordDialog.MSG_DATA_PASSWORD);
                keystorePath = msg.getData().getString(EnterPasswordDialog.MSG_DATA_PATH);
                rememberPassword = msg.getData().getBoolean( EnterPasswordDialog.MSG_DATA_REMEMBER_PASSWORD);     
                handleRememberKeystorePassword( keystoreId, keystorePath, encodedPassword, rememberPassword);
                break;
            case ManageKeysActivity.MESSAGE_CODE_ALIAS_REMEMBER_PASSWORD:
                long aliasId = msg.getData().getLong(EnterPasswordDialog.MSG_DATA_ID);
                encodedPassword = msg.getData().getString(EnterPasswordDialog.MSG_DATA_PASSWORD);
                keystorePath = msg.getData().getString(EnterPasswordDialog.MSG_DATA_PATH);
                rememberPassword = msg.getData().getBoolean( EnterPasswordDialog.MSG_DATA_REMEMBER_PASSWORD);
                handleRememberAliasPassword(aliasId, keystorePath, encodedPassword, rememberPassword);
                break;
            case ManageKeysActivity.MESSAGE_CODE_ALIAS_DISPLAY_NAME:
                aliasId = msg.getData().getLong(EditDisplayNameDialog.MSG_DATA_ALIAS_ID);
                String displayName = msg.getData().getString(EditDisplayNameDialog.MSG_DATA_TEXT);
                handleAliasDisplayName(aliasId, displayName);
                break;
            case EnterPasswordDialog.MESSAGE_CODE_ENTER_PASSWORD_CANCELLED:
                break; // ignore
            default:
                logger.error("Unknown message code " + msg.what);
                break;
            }
        }
    };


    void handleRememberKeystorePassword( long keystoreId, String keystorePath, String encodedPassword, boolean rememberPassword) {
        char[] password = null;
        try {
            java.security.KeyStore ks = java.security.KeyStore.getInstance("bks");
            FileInputStream fis = new FileInputStream( keystorePath);
            password = PasswordObfuscator.getInstance().decodeKeystorePassword( keystorePath, encodedPassword);
            ks.load( fis, password);
            fis.close();     
            List<Keystore> keystoreList = customKeysDataSource.getAllKeystores();
            for (Keystore keystore : keystoreList) {
                if (keystore.getId() == keystoreId) {
                    keystore.setPassword(encodedPassword);
                    keystore.setRememberPassword(true);
                    customKeysDataSource.updateKeystore(keystore);
                    
                    for (Alias alias : keystore.getAliases()) {
                        try {
                            ks.getKey(alias.getName(), password);
                            alias.setRememberPassword(rememberPassword);
                            String keypw = PasswordObfuscator.getInstance().encodeAliasPassword( keystorePath,alias.getName(), password);
                            alias.setPassword(keypw);
                            customKeysDataSource.updateAlias(alias);
                        } catch (Exception x) {
                            logger.debug("Password for entry " + alias.getName() + " is not the same as the keystore password");
                        }                                
                    }
                    keystoreExpandableListAdapter.dataChanged(keystoreList);
                    break;
                }
            }
        } catch (Exception x) {
            if (x.getMessage().indexOf("integrity check failed") >= 0) {
                logger.error(getResources().getString(R.string.WrongKeystorePassword));
                EnterPasswordDialog.show(
                        ManageKeysActivity.this, handler, getResources().getString( R.string.EnterKeystorePassword),
                        MESSAGE_CODE_KEYSTORE_REMEMBER_PASSWORD, keystorePath, keystoreId, rememberPassword, null);
            }
            else {
                logger.error("Error opening keystore file - " + x.getMessage());                    
            }
        }
        finally {
            if (password != null) PasswordObfuscator.flush( password);
        }
    }

    void handleRememberAliasPassword( long aliasId, String keystorePath, String encodedPassword, boolean rememberPassword) {
        char[] password = null;
        Alias matchedAlias = null;
        try {
            List<Keystore> keystoreList = customKeysDataSource.getAllKeystores();
            for (Keystore keystore : keystoreList) {
                for (Alias alias : keystore.getAliases()) {
                    if (alias.getId() == aliasId) {
                        matchedAlias = alias;
                        java.security.KeyStore ks = java.security.KeyStore.getInstance("bks");
                        try {
                            FileInputStream fis = new FileInputStream( keystore.getPath());
                            if (keystore.getPassword() != null)
                                password = PasswordObfuscator.getInstance().decodeKeystorePassword(keystore.getPath(), keystore.getPassword());
                            ks.load( fis, password);
                            fis.close();
                        } catch (Exception x) {
                            logger.error("Failed to open keystore - " + x.getMessage());
                            return;
                        }
                        password = PasswordObfuscator.getInstance().decodeAliasPassword(keystore.getPath(),alias.getName(), encodedPassword);
                        ks.getKey(alias.getName(), password);
                        alias.setRememberPassword(rememberPassword);
                        alias.setPassword(encodedPassword);
                        customKeysDataSource.updateAlias(alias);
                        keystoreExpandableListAdapter.dataChanged(keystoreList);
                    }
                }
            }
        } catch (UnrecoverableKeyException x) {
            logger.error(getResources().getString(R.string.WrongKeyPassword));
            EnterPasswordDialog.show(
                ManageKeysActivity.this, handler, getResources().getString( R.string.EnterKeyPassword),
                MESSAGE_CODE_ALIAS_REMEMBER_PASSWORD, keystorePath, aliasId, rememberPassword, matchedAlias.getName());
        } catch (Exception x) {
            logger.error("Error saving password - " + x.getMessage());
        }
        finally {
            if (password != null) PasswordObfuscator.flush( password);
        }
    }

    private void handleAliasDisplayName(long aliasId, String displayName) {
        try {
            List<Keystore> keystoreList = customKeysDataSource.getAllKeystores();
            for (Keystore keystore : keystoreList) {
                for (Alias alias : keystore.getAliases()) {
                    if (alias.getId() == aliasId) {
                        alias.setDisplayName(displayName);
                        customKeysDataSource.updateAlias(alias);
                        keystoreExpandableListAdapter.dataChanged(keystoreList);
                    }
                }
            }
        } catch (Exception x) {
            logger.error("Error saving display name - " + x.getMessage());
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // TODO Auto-generated method stub
        super.onConfigurationChanged(newConfig);
    }

    class KeystoreLoader extends Thread {
        
        String keystorePath;
        String encodedPassword;
        boolean rememberPassword;
        
        LoggerInterface logger = LoggerManager.getLogger(KeystoreLoader.class.getName());
        
        public KeystoreLoader( String keystorePath, String encodedPassword, boolean rememberPassword) {
            // this.customKeysDataSource = customKeysDataSource;
            this.keystorePath = keystorePath;
            this.encodedPassword = encodedPassword;
            this.rememberPassword = rememberPassword;
        }
        
        public void run() {
            char[] password = null;
            try {
                java.security.KeyStore ks = java.security.KeyStore.getInstance("bks");
                FileInputStream fis = new FileInputStream( keystorePath);
                password = PasswordObfuscator.getInstance().decodeKeystorePassword( keystorePath, encodedPassword);
                ks.load( fis, password);
                fis.close();
                
                Keystore keystore = new Keystore();
                keystore.setPath( keystorePath);
                keystore.setRememberPassword( rememberPassword);
                keystore.setPassword( encodedPassword);
                
                for (Enumeration<String> e = ks.aliases(); e.hasMoreElements(); ) {
                    String aliasName = e.nextElement();
                    Alias alias = new Alias();
                    alias.setName(aliasName);
                    alias.setDisplayName(aliasName);
                    alias.setSelected(true);
                    try {
                        ks.getKey(aliasName, password);
                        alias.setRememberPassword(rememberPassword);
                        String keypw = PasswordObfuscator.getInstance().encodeAliasPassword( keystorePath,aliasName,password);
                        alias.setPassword(keypw);
                    } catch (Exception x) {
                        logger.debug("Password for entry " + aliasName + " is not the same as the keystore password");
                        alias.setRememberPassword(false);
                    }
                    keystore.addAlias(alias);
                }
                
                customKeysDataSource.addKeystore(keystore);
                sendMessage( MESSAGE_CODE_KEYSTORE_LOADED, null);
                
            } catch (IOException x) {
                if (x.getCause() != null)
                    logger.warning("IOException: cause="+x.getCause().getClass().getName(), x);
                else logger.warning("IOException: cause=null");
                
                if (x.getMessage().indexOf("integrity check failed") >= 0) {
                    sendMessage( MESSAGE_CODE_BAD_KEYSTORE_PASSWORD,null);
                } 
                else {
                    String msg = "Error opening keystore file - " + x.getMessage();
                    logger.error(msg, x);
                    sendMessage( MESSAGE_CODE_KEYSTORE_LOAD_ERROR, msg);
                    
                }
            } catch (Exception x) {
                String msg = "Error processing keystore file - " + x.getMessage();
                logger.error(msg, x);
                sendMessage( MESSAGE_CODE_KEYSTORE_LOAD_ERROR, msg);
            }
            finally {
                if (password != null) PasswordObfuscator.flush(password);
            }
        }
        
        void sendMessage( int msgCode, String message) {
            Message msg = new Message();
            msg.what = msgCode;
            Bundle data = new Bundle();
            data.putString( EnterPasswordDialog.MSG_DATA_MESSAGE, message);
            data.putString(EnterPasswordDialog.MSG_DATA_PASSWORD,  encodedPassword);
            data.putString(EnterPasswordDialog.MSG_DATA_PATH, keystorePath);
            data.putBoolean(EnterPasswordDialog.MSG_DATA_REMEMBER_PASSWORD, rememberPassword);
            msg.setData(data);
            handler.sendMessage(msg);            
        }
    }
}

