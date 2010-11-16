INTRO

zipsigner-lib is a Java API for signing Zip, Apk, and Jar files.  It
was developed for use onboard Android devices, but will work to sign
files in other environments that support the Java programming
language.

The primary class is kellinwood.security.zipsigner.ZipSigner.  It is
is a heavily modified version of Google's SignApk. Modifications
include the addition of convienience methods, a progress listener API,
default keys/certificates built into the classpath, and a signature
implementation that generates signatures recognized the "install
update.zip file" feature of the recovery programs.

This library currently depends on other libraries:
- android-sun-jarsign-support, which contains the Sun's security and
  related APIs required to sign files.
- kellinwood-logging-lib, a small platform-independent logging
  framework

The source is 100% Java and there are no dependencies on other
installable components such as busybox.  Root privileges are not
required (but you probably need root in order to do something
meaningful with the results).  To use this library, you'll probably
need to give your app write privileges to the sdcard.

Files are signed in a way compatible with the OTA/Update.zip
installation method offered by the various Android recovery programs.

For a demonstration of this API in use, please refer to the source
code of the ZipSigner app.

