INTRO

zipsigner-lib is a open source Java API for signing Zip, Apk, and Jar
files.  It was developed for use onboard Android devices, but will
work to sign files in other environments that support the Java
programming language.

The source to this library is licensed under Apache 2.0.

The primary class is kellinwood.security.zipsigner.ZipSigner.  It is
is a heavily modified version of Google's SignApk. Modifications
include the addition of convienience methods, a progress listener API,
default keys/certificates built into the classpath, and a signature
implementation that generates signatures recognized by the "install
update.zip file" feature of the recovery programs.

DEPENDENCIES

This project currently depends on other libraries:

- android-sun-jarsign-support, which contains the Sun code required to
  create a properly formated PKCS#7 signature block file. It also
  contains Base64 and HexDump utilities.  The code in this library was
  obtained from the OpenJDK project and is licensed under GPL version
  2.

- kellinwood-logging-lib, a small platform-independent logging
  framework.  In order to troubleshoot during development I needed to
  have zipsigner-lib run on both Android and my desktop JRE so I could
  compare results.  This meant having a portable logging API but I
  couldn't figure out how to make java.util.logging work on Android so
  I wrote this. The source to this library is licensed under Apache 2.0.

- kellinwood-logging-android, the android adapter for
  kellinwood-logging-lib.  This is not required, but if you want to
  see any loggging output from zipsigner-lib on Android you'll need to
  include this library and activate it via a few API calls (see below).
  The source to this library is licensed under Apache 2.0.

SOURCE

All source is 100% Java and there are no dependencies on other
installable components such as busybox, openssl, etc.  Root privileges
are not required but you probably need root in order to do something
meaningful with the results.  To use this library you'll probably
need to give your app write privileges to the sdcard.

For a demonstration of this API in use, please refer to the source
code of the ZipSigner app.

All code is available from http://code.google.com/p/zip-signer.  This
includes the above mentioned dependencies, this project's code, and
the ZipSigner Android app.

BASIC USAGE:

import kellinwood.security.zipsigner.ZipSigner;

try {
    // Sign with the built-in default test key/certificate.
    ZipSigner zipSigner = new ZipSigner();
    zipSigner.zipSigner.signZip( inputFile, outputFile);
}
catch (Throwable t) {
    // log, display toast, etc.
}


GETTING PROGRESS UPDATES:

import kellinwood.security.zipsigner.ProgressListener;

ZipSigner zipSigner = new ZipSigner();
zipSigner.zipSigner.addProgressListener( new ProgressListener() {
   public void onProgress( String currentItem, int percentDone)
   {
       // Current item is the basename of a file in the zip being signed.
       // percentDone is a value between 0 and 100
   }
});
zipSigner.zipSigner.signZip( inputFile, outputFile);


ENABLING LOG OUTPUT:

import kellinwood.logging.LoggerManager;
import kellinwood.logging.android.AndroidLogger;
import kellinwood.logging.android.AndroidLoggerFactory;

// In Activity.onCreate()...
LoggerManager.setLoggerFactory( new AndroidLoggerFactory());

// Optional, use this logging API in your own code
AndroidLogger logger = (AndroidLogger)LoggerManager.getLogger(this.getClass().getName());

// Optional, enable toasts.  If enabled, they are shown by default for
// error and warning level log output.
logger.setToastContext(getBaseContext());

// Maybe also show toasts for debug output.
// logger.setDebugToastEnabled(true);

// Optional, log something
logger.debug("Hello, world!");


Use the following adb commands to enable logcat output from zipsigner-lib:

adb shell setprop log.tag.ZipSigner VERBOSE
adb logcat ZipSigner:*


SIGNING WITH OTHER CERTIFICATES

    // Load an x509.pem public key certificate.  E.g., "file:///sdcard/mycert.x509.pem"
    public X509Certificate readPublicKey(URL publicKeyUrl);

    // Load a pkcs8 encoded private key.  Password is only required if the key is encrypted.
    public PrivateKey readPrivateKey(URL privateKeyUrl, String keyPassword);
        
    // Sign the zip using the given public/private key pair.
    public void signZip( X509Certificate publicKey, PrivateKey privateKey, 
    		String inputZipFilename, String outputZipFilename);

    // Sign the zip using a cert/key pair from the given keystore.  Keystore type on Android is "BKS".
    // See below for information on creating an Android compatible keystore.
    public void signZip( URL keystoreURL, 
                         String keystoreType,
                         String keystorePw, 
                         String certAlias,
                         String certPw, 
                         String inputZipFilename, 
                         String outputZipFilename);


KEYSTORE CREATION

An Android compatible keystore can be created on your desktop system.
Here are some brief instructions:

* Download Bouncy Castle Provider, e.g., bcprov-jdk16-145.jar, from
  http://www.bouncycastle.org/latest_releases.html

* Copy bcprov-jdk16-145.jar to $JDK_HOME/jre/lib/ext/.

* Create the key...

keytool -genkey \
        -alias CERT \
        -keystore assets/keystore.ks \
        -storetype BKS \
        -provider org.bouncycastle.jce.provider.BouncyCastleProvider \
        -storepass android \
        -keyalg RSA \
        -keysize 1024 \
        -keypass android \
        -sigalg SHA1withRSA \
        -dname "C=US,ST=California,L=Mountain View,O=Android,OU=Android,CN=Android"

* Create the cert...

keytool -selfcert -validity 9125 -alias CERT \
    -keystore assets/keystore.ks \
    -storepass android -keypass android \
    -storetype BKS \
    -provider org.bouncycastle.jce.provider.BouncyCastleProvider


* List the contents of the keystore:

keytool -list -v -keystore assets/keystore.ks \
    -storepass android -keypass android -storetype BKS \
    -provider org.bouncycastle.jce.provider.BouncyCastleProvider

* When signing files using zipsigner-lib or the ZipSigner app and you
  get and "UnrecoverableKeyException: no match" error message, it
  means that you are providing a bad key password.
