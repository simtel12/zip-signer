INTRO

ZipSigner is an Android app that is capable of signing Zip, Apk, and
Jar files on the device.  Its purpose is to demonstrate the use of
zipsigner-lib, a separate API that does the real work of signing the
files.  

ZipSigner contains an activity that can be launched from other apps to
sign files.  This capability is demonstrated by the ZipPickerActivity.
Take a look at the code for more details.

The source is 100% Java and there are no dependencies on other
installable components such as busybox.  Root privileges are not
required (but you probably need root in order to do something
meaningful with the results).

Files are signed in a way compatible with the OTA/Update.zip
installation method offered by the various recovery programs.


LOGCAT OUTPUT

To enable debug output from the app, execute the following adb commands:

adb shell setprop log.tag.ZipSigner VERBOSE
adb logcat ZipPickerActivity:* ZipSignerActivity:* ZipSigner:*

KEYSTORE

An Android compatible keystore can be created on you desktop system.
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
  get "UnrecoverableKeyException: no match", it means that you are
  providing a bad key password.
