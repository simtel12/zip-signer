/*
 * Copyright (C) 2008 The Android Open Source Project
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

/* This file is a modified version of
 * com.android.signapk.SignApk.java.  The primary changes include
 * addition of the signZip() convinience methods, addition of a
 * progress listener interface, removal of main(), and the updates to
 * generate a signature that is verifiable by the Android recovery
 * programs. */

package kellinwood.security.zipsigner;

import sun.misc.BASE64Encoder;
import sun.misc.HexDumpEncoder;
import sun.security.pkcs.ContentInfo;
import sun.security.pkcs.PKCS7;
import sun.security.pkcs.SignerInfo;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X500Name;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.security.auth.x500.X500Principal;

import kellinwood.logging.LoggerInterface;
import kellinwood.logging.LoggerManager;

import sys.util.jar.Attributes;
import sys.util.jar.Manifest;

/**
 * This is a modified copy of com.android.signapk.SignApk.java.  It provides an
 * API to sign JAR files (including APKs and Zip/OTA updates) in
 * a way compatible with the mincrypt verifier, using SHA1 and RSA keys.
 */
@SuppressWarnings("restriction")
public class ZipSigner 
{

	private boolean canceled = false;
	
    private int progressTotalItems = 0;
    private int progressCurrentItem = 0;
    
	static LoggerInterface log = null;
	
    private static final String CERT_SF_NAME = "META-INF/CERT.SF";
    private static final String CERT_RSA_NAME = "META-INF/CERT.RSA";

    // Files matching this pattern are not copied to the output.
    private static Pattern stripPattern =
            Pattern.compile("^META-INF/(.*)[.](SF|RSA|DSA)$");



    // Allow the operation to be canceled.
    public void cancel() {
    	canceled = true;
    }
    
    public boolean isCanceled() {
    	return canceled;
    }
    
    @SuppressWarnings("unchecked")
	public void loadProvider( String providerClassName)
        throws ClassNotFoundException, IllegalAccessException, InstantiationException
    {
		Class providerClass = Class.forName(providerClassName);
		Provider provider = (Provider)providerClass.newInstance();
		Security.insertProviderAt(provider, 1);
    }
    
    
    public X509Certificate readPublicKey(URL publicKeyUrl)
            throws IOException, GeneralSecurityException {
        InputStream input = publicKeyUrl.openStream();
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(input);
        } finally {
            input.close();
        }
    }

    /**
     * Decrypt an encrypted PKCS 8 format private key.
     *
     * Based on ghstark's post on Aug 6, 2006 at
     * http://forums.sun.com/thread.jspa?threadID=758133&messageID=4330949
     *
     * @param encryptedPrivateKey The raw data of the private key
     * @param keyFile The file containing the private key
     */
    private KeySpec decryptPrivateKey(byte[] encryptedPrivateKey, String keyPassword)
            throws GeneralSecurityException {
        EncryptedPrivateKeyInfo epkInfo;
        try {
            epkInfo = new EncryptedPrivateKeyInfo(encryptedPrivateKey);
        } catch (IOException ex) {
            // Probably not an encrypted key.
            return null;
        }

        char[] keyPasswd = keyPassword.toCharArray();

        SecretKeyFactory skFactory = SecretKeyFactory.getInstance(epkInfo.getAlgName());
        Key key = skFactory.generateSecret(new PBEKeySpec(keyPasswd));

        Cipher cipher = Cipher.getInstance(epkInfo.getAlgName());
        cipher.init(Cipher.DECRYPT_MODE, key, epkInfo.getAlgParameters());

        try {
            return epkInfo.getKeySpec(cipher);
        } catch (InvalidKeySpecException ex) {
            getLogger().error("signapk: Password for private key may be bad.");
            throw ex;
        }
    }

    /** Read a PKCS 8 format private key. */
    public PrivateKey readPrivateKey(URL privateKeyUrl, String keyPassword)
            throws IOException, GeneralSecurityException {
        DataInputStream input = new DataInputStream( privateKeyUrl.openStream());
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            byte[] buffer = new byte[2048];

            int numRead = input.read( buffer);
            while (numRead != -1) {
                baos.write( buffer, 0, numRead);
                numRead = input.read( buffer);
            }
                
            byte[] bytes = baos.toByteArray();

            KeySpec spec = decryptPrivateKey(bytes, keyPassword);
            if (spec == null) {
                spec = new PKCS8EncodedKeySpec(bytes);
            }

            try {
                return KeyFactory.getInstance("RSA").generatePrivate(spec);
            } catch (InvalidKeySpecException ex) {
                return KeyFactory.getInstance("DSA").generatePrivate(spec);
            }
        } finally {
            input.close();
        }
    }

    /** Add the SHA1 of every file to the manifest, creating it if necessary. */
    private Manifest addDigestsToManifest(JarFile jar)
            throws IOException, GeneralSecurityException {
        java.util.jar.Manifest input = jar.getManifest();
        Manifest output = new Manifest();
        Attributes main = output.getMainAttributes();
        if (input != null) {
            main.putAll(input.getMainAttributes());
        } else {
            main.putValue("Manifest-Version", "1.0");
            main.putValue("Created-By", "1.0 (Android SignApk)");
        }

        BASE64Encoder base64 = new BASE64Encoder();
        MessageDigest md = MessageDigest.getInstance("SHA1");
        byte[] buffer = new byte[4096];
        int num;

        // We sort the input entries by name, and add them to the
        // output manifest in sorted order.  We expect that the output
        // map will be deterministic.

        TreeMap<String, JarEntry> byName = new TreeMap<String, JarEntry>();

        for (Enumeration<JarEntry> e = jar.entries(); !canceled && e.hasMoreElements(); ) {
            JarEntry entry = e.nextElement();
            byName.put(entry.getName(), entry);
        }

        getLogger().debug("Manifest entries:");
        for (JarEntry entry: byName.values()) {
        	if (canceled) break;
            String name = entry.getName();
            getLogger().debug(name);
            if (!entry.isDirectory() && !name.equals(JarFile.MANIFEST_NAME) &&
                !name.equals(CERT_SF_NAME) && !name.equals(CERT_RSA_NAME) &&
                (stripPattern == null ||
                 !stripPattern.matcher(name).matches())) {
                InputStream data = jar.getInputStream(entry);
                while ((num = data.read(buffer)) > 0) {
                    md.update(buffer, 0, num);
                }

                Attributes attr = null;
                if (input != null) {
                	java.util.jar.Attributes inAttr = input.getAttributes(name); 
                	if (inAttr != null) attr = new Attributes( inAttr);
                }
                if (attr == null) attr = new Attributes();
                attr.putValue("SHA1-Digest", base64.encode(md.digest()));
                output.getEntries().put(name, attr);
            }
        }

        return output;
    }

    
    /** Write the signature file to the given output stream. */
    private void generateSignatureFile(Manifest manifest, OutputStream out)
            throws IOException, GeneralSecurityException {
        out.write( ("Signature-Version: 1.0\r\n").getBytes());
        out.write( ("Created-By: 1.0 (Android SignApk)\r\n").getBytes());


        BASE64Encoder base64 = new BASE64Encoder();
        MessageDigest md = MessageDigest.getInstance("SHA1");
        PrintStream print = new PrintStream(
                new DigestOutputStream(new ByteArrayOutputStream(), md),
                true, "UTF-8");

        // Digest of the entire manifest
        manifest.write(print);
        print.flush();

        out.write( ("SHA1-Digest-Manifest: "+ base64.encode(md.digest()) + "\r\n\r\n").getBytes());

        Map<String, Attributes> entries = manifest.getEntries();
        for (Map.Entry<String, Attributes> entry : entries.entrySet()) {
        	if (canceled) break;
            // Digest of the manifest stanza for this entry.
        	String nameEntry = "Name: " + entry.getKey() + "\r\n"; 
            print.print( nameEntry);
            for (Map.Entry<Object, Object> att : entry.getValue().entrySet()) {
                print.print(att.getKey() + ": " + att.getValue() + "\r\n");
            }
            print.print("\r\n");
            print.flush();

            out.write( nameEntry.getBytes());
            out.write( ("SHA1-Digest: " +  base64.encode(md.digest()) + "\r\n\r\n").getBytes());
        }

    }

    /** Write a .RSA file with a digital signature. */
    private void writeSignatureBlock(
            byte[] signatureBytes, X509Certificate publicKey, OutputStream out)
            throws IOException, GeneralSecurityException 
    {
    	X500Principal x500Principal = publicKey.getIssuerX500Principal();
    	String x500PrincipalName = x500Principal.getName();
    	X500Name x500Name = null;
    	x500Name = new X500Name( x500PrincipalName);
    	
    	BigInteger serialNumber = publicKey.getSerialNumber();
    	
    	AlgorithmId SHA1_Id = AlgorithmId.get("SHA1");
    	AlgorithmId RSA_Id = AlgorithmId.get("RSA");
    	
        SignerInfo signerInfo = new SignerInfo(
                x500Name,
                serialNumber,
                SHA1_Id,
                RSA_Id,
                signatureBytes);

        PKCS7 pkcs7 = new PKCS7(
                new AlgorithmId[] { AlgorithmId.get("SHA1") },
                new ContentInfo(ContentInfo.DATA_OID, null),
                new X509Certificate[] { publicKey },
                new SignerInfo[] { signerInfo });

        pkcs7.encodeSignedData(out);
    }

    /**
     * Copy all the files in a manifest from input to output.  We set
     * the modification times in the output to a fixed time, so as to
     * reduce variation in the output file and make incremental OTAs
     * more efficient.
     */
    private void copyFiles(Manifest manifest,
        JarFile in, JarOutputStream out, long timestamp) throws IOException {
        byte[] buffer = new byte[4096];
        int num;

        Map<String, Attributes> entries = manifest.getEntries();
        List<String> names = new ArrayList<String>(entries.keySet());
        Collections.sort(names);
        for (String name : names) {
        	if (canceled) break;
            progress( name);
            JarEntry inEntry = in.getJarEntry(name);
            JarEntry outEntry = null;
            if (inEntry.getMethod() == JarEntry.STORED) {
                // Preserve the STORED method of the input entry.
                outEntry = new JarEntry(inEntry);
            } else {
                // Create a new entry so that the compressed len is recomputed.
                outEntry = new JarEntry(name);
            }
            outEntry.setTime(timestamp);
            out.putNextEntry(outEntry);

            InputStream data = in.getInputStream(inEntry);
            while ((num = data.read(buffer)) > 0) {
                out.write(buffer, 0, num);
            }
            out.flush();
        }
    }
    
    public LoggerInterface getLogger() {
    	if (log == null) log = LoggerManager.getLogger( this.getClass().getName());
    	return log;
    }
    
    public void signZip( URL keystoreURL, 
    							String keystoreType,
    			   				String keystorePw, 
    			   				String certAlias,
    			   				String certPw, 
    			   				String inputZipFilename, 
    			   				String outputZipFilename)
    	throws ClassNotFoundException, IllegalAccessException, InstantiationException, IOException, GeneralSecurityException
    {
    	InputStream keystoreStream = null;
    	
    	
    	try {
    		KeyStore keystore = null;
    		if (keystoreType == null) keystoreType = KeyStore.getDefaultType();
    		keystore = KeyStore.getInstance(keystoreType);
    			
    		keystoreStream = keystoreURL.openStream();
    		keystore.load(keystoreStream, keystorePw.toCharArray());
    		Certificate cert = keystore.getCertificate(certAlias);
    		X509Certificate publicKey = (X509Certificate)cert;
    		Key key = keystore.getKey(certAlias, certPw.toCharArray());
    		PrivateKey privateKey = (PrivateKey)key;

    		signZip( publicKey, privateKey, inputZipFilename, outputZipFilename);
    	}
    	finally {
    		if (keystoreStream != null) keystoreStream.close();
    	}
    }
    
    // Sign the input with the default test key and certificate.  
    // Save result to output file.
    public void signZip( String inputZipFilename, String outputZipFilename)
    	throws IOException, GeneralSecurityException
    {
        URL privateKeyUrl = getClass().getResource("/keys/testkey.pk8");
        PrivateKey privateKey = readPrivateKey(privateKeyUrl, null);
        
        URL publicKeyUrl = getClass().getResource("/keys/testkey.x509.pem");
        X509Certificate publicKey = readPublicKey(publicKeyUrl);
        
        signZip( publicKey, privateKey, inputZipFilename, outputZipFilename);
    }
    
    public void signZip( X509Certificate publicKey, PrivateKey privateKey, 
    		String inputZipFilename, String outputZipFilename)
    throws IOException, GeneralSecurityException
    {
    	
    	canceled = false;
    
    	if (inputZipFilename.equals( outputZipFilename)) {
    		throw new IllegalArgumentException("Input and output filenames are the same.  Specify a different name for the output.");
    	}
    	
        JarFile inputJar = null;
        JarOutputStream outputJar = null;
        log = LoggerManager.getLogger( ZipSigner.class.getName());

        try {
        	// Assume the certificate is valid for at least an hour.
        	long timestamp = publicKey.getNotBefore().getTime() + 3600L * 1000;
        	
        	inputJar = new JarFile(new File(inputZipFilename), false);  // Don't verify.
        	outputJar = new JarOutputStream(new FileOutputStream(outputZipFilename));
        	outputJar.setLevel(9);

            initProgress( inputJar.size()+3); // num entries + MANIFEST.MF, CERT.SF, CERT.RSA
            
        	JarEntry je;
        	
        	// MANIFEST.MF
            progress(JarFile.MANIFEST_NAME);
        	Manifest manifest = addDigestsToManifest(inputJar);
        	if (canceled) return;
        	je = new JarEntry(JarFile.MANIFEST_NAME);
        	je.setTime(timestamp);
        	outputJar.putNextEntry(je);
        	manifest.write(outputJar);
        	
        	// CERT.SF
            progress( CERT_SF_NAME);
        	
            // Can't use default Signature on Android.  Although it generates a signature that can be verified by jarsigner,
            // the recovery program appears to require a specific algorithm/mode/padding.  So we use the custom ZipSignature instead.
        	// Signature signature = Signature.getInstance("SHA1withRSA"); 
        	ZipSignature signature = new ZipSignature();
        	signature.initSign(privateKey);
        	
//        	if (getLogger().isDebugEnabled()) {
//        		getLogger().debug(String.format("Signature provider=%s, alg=%s, class=%s",
//        				signature.getProvider().getName(),
//        				signature.getAlgorithm(),
//        				signature.getClass().getName()));
//        	}

        	
        	je = new JarEntry(CERT_SF_NAME);
        	je.setTime(timestamp);
        	outputJar.putNextEntry(je);
        	ByteArrayOutputStream out = new ByteArrayOutputStream();
        	generateSignatureFile(manifest, out);
        	if (canceled) return;
        	byte[] sfBytes = out.toByteArray();
        	if (getLogger().isDebugEnabled()) {
        		getLogger().debug( "Signature File: \n" + new String( sfBytes) + "\n" + 
        				new HexDumpEncoder().encode( sfBytes));
        	}
        	outputJar.write(sfBytes);
        	signature.update(sfBytes);
        	byte[] signatureBytes = signature.sign();
        	
        	if (getLogger().isDebugEnabled()) {

                MessageDigest md = MessageDigest.getInstance("SHA1");
                md.update( sfBytes);
                byte[] sfDigest = md.digest();
                getLogger().debug( "Sig File SHA1: \n" + new HexDumpEncoder().encode( sfDigest));
                
        		getLogger().debug( "Signature: \n" + new HexDumpEncoder().encode(signatureBytes));

                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.DECRYPT_MODE, publicKey);

        		byte[] tmpData = cipher.doFinal( signatureBytes);
        		getLogger().debug( "Signature Decrypted: \n" + new HexDumpEncoder().encode(tmpData));

        		getLogger().debug( "SHA1 ID: \n" + new HexDumpEncoder().encode(AlgorithmId.get("SHA1").encode()));
        		
//        		// Compute signature using low-level APIs. 
//                byte[] beforeAlgorithmIdBytes =  { 0x30, 0x21 };
//                // byte[] algorithmIdBytes = {0x30, 0x09, 0x06, 0x05, 0x2B, 0x0E, 0x03, 0x02, 0x1A, 0x05, 0x00 }; 
//                byte[] algorithmIdBytes =  AlgorithmId.get("SHA1").encode();
//                byte[] afterAlgorithmIdBytes = { 0x04, 0x14 };
//                cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
//                cipher.init(Cipher.ENCRYPT_MODE, privateKey);
//                getLogger().debug( "Cipher: " + cipher.getAlgorithm() + ", blockSize = " + cipher.getBlockSize());
//                
//                cipher.update( beforeAlgorithmIdBytes);
//                cipher.update( algorithmIdBytes);
//                cipher.update( afterAlgorithmIdBytes);
//                cipher.update( sfDigest);
//                byte[] tmpData2 = cipher.doFinal();
//                getLogger().debug( "Signature : \n" + new HexDumpEncoder().encode(tmpData2));
        		
       		
        	}
        	
        	// CERT.RSA
            progress( CERT_RSA_NAME);
        	je = new JarEntry(CERT_RSA_NAME);
        	je.setTime(timestamp);
        	outputJar.putNextEntry(je);
        	writeSignatureBlock(signatureBytes, publicKey, outputJar);
        	if (canceled) return;
        	
        	// Everything else
        	copyFiles(manifest, inputJar, outputJar, timestamp);
        	if (canceled) return;
        	outputJar.flush();
        }
        finally {
            try {
                if (inputJar != null) inputJar.close();
                if (outputJar != null) outputJar.close();
            } catch (IOException e) {
                e.printStackTrace();
            } 
            
            if (canceled) {
            	try {
            		new File( outputZipFilename).delete();
            	}
            	catch (Throwable t) {
            		getLogger().warning( t.getClass().getName() + ":" + t.getMessage());
            	}
            }
        }
    }


    private void initProgress( int totalItems) {
        progressTotalItems = totalItems;
        progressCurrentItem = 0;
    }
    
    private void progress( String itemName) {

        // Create short version of item name
        int pos = itemName.lastIndexOf('/');
        if (pos >= 0) itemName = itemName.substring( pos+1);

        progressCurrentItem += 1;

        int percentDone = (100 * progressCurrentItem) / progressTotalItems;

        // Notify listeners here
        for (ProgressListener listener : listeners) {
            listener.onProgress( itemName, percentDone);
        }
    }

    private ArrayList<ProgressListener> listeners = new ArrayList<ProgressListener>();

    public synchronized void addProgressListener( ProgressListener l)
    {
        ArrayList<ProgressListener> list = (ArrayList<ProgressListener>)listeners.clone();
        list.add(l);
        listeners = list;
    }
    
    public synchronized void removeProgressListener( ProgressListener l)
    {
        ArrayList<ProgressListener> list = (ArrayList<ProgressListener>)listeners.clone();
        list.remove(l);
        listeners = list;
    }    
        
}