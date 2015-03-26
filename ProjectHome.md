API and app for signing Zip, Apk, and/or Jar files onboard Android devices.


# Details #

This project is divided into several sub-projects.
  * zipio-lib, an alternate API to java.util.zip for reading and writing zip files.  This library allows entries to be copied directly from the input to output without de-compressing and re-compressing.  It also zip-aligns to 4 byte boundaries by default.
  * zipsigner-lib - a file signing library with a simple API and built-in default certificate.  As Java code it should work anywhere, but its specifically targeted at Android application developers. Licensed under Apache 2.0.
  * zipsigner-lib-optional - which contains the code required to create a properly formatted CMS/PKCS#7 signature block file from scratch. This JAR is typically required in order to sign with private keys or create self-signed certificates for publishing apps.  If you include this file in your project, then you will also need to include the SpongyCastle JARS (sc-light, scprov, and scpkix from http://rtyley.github.com/spongycastle/). SpongyCastle is an Android-friendly version of BouncyCastle.
  * [ZipSigner](http://sites.google.com/site/zipsigner) - an Android app that integrates zipsigner-lib and signs files onboard the device.  Look for it in the [Android Market](http://market.android.com/details?id=kellinwood.zipsigner2).  Licensed under Apache 2.0.
  * kellinwood-logging-lib - a lightweight, platform-independent logging framework.  Licensed under Apache 2.0.
  * kellinwood-logging-android - android adapter for the above logging framework. Licensed under Apache 2.0.

There are detailed README files in several of the projects
  * [zipsigner-lib README file](http://code.google.com/p/zip-signer/source/browse/zipsigner-lib/trunk/README.txt)
  * [zipsigner-lib CHANGELOG file](http://code.google.com/p/zip-signer/source/browse/zipsigner-lib/trunk/CHANGELOG.txt)
  * [ZipSigner app README file](http://code.google.com/p/zip-signer/source/browse/ZipSigner/trunk/README.txt)

The latest release binaries are available for download in a folder on Google Drive [here](https://drive.google.com/folderview?id=0BwYIoogZdbVnZ3ZMd21VVndhUU0&usp=sharing#list).

## Subversion Checkouts ##

There are currently many sub-projects.  Check out the source from Subversion using the following commands.  For read-only subversion checkouts, use 'http' protocol instead of 'https'.

```
svn co https://zip-signer.googlecode.com/svn/zipio-lib/trunk zipio-lib
svn co https://zip-signer.googlecode.com/svn/zipsigner-lib/trunk zipsigner-lib
svn co https://zip-signer.googlecode.com/svn/ZipSigner/trunk ZipSigner
svn co https://zip-signer.googlecode.com/svn/kellinwood-logging-lib/trunk kellinwood-logging-lib
svn co https://zip-signer.googlecode.com/svn/kellinwood-logging-android/trunk kellinwood-logging-android
```
Optional checkouts:
```
svn co https://zip-signer.googlecode.com/svn/zipsigner-cmdline/trunk zipsigner-cmdline
svn co https://zip-signer.googlecode.com/svn/kellinwood-logging-log4j/trunk kellinwood-logging-log4j
svn co https://zip-signer.googlecode.com/svn/zipsigner-lib-optional/trunk zipsigner-lib-optional
```

## Building the Code ##

Check out the code using the Subversion commands above.

Building ZipSigner requires Maven.  If you haven't done so already,
[download](http://maven.apache.org/download.html) and
[install Maven](http://maven.apache.org/guides/getting-started/maven-in-five-minutes.html).

The zip-signer artifacts are not in any public Maven repositories so a few
manual steps are required to prime your local repository.  If you haven't used Maven
before, begin by building kellinwood-logging-lib to force the creation
of your local repository, typically ${user.home}/.m2/repository or
`C:\Documents and Settings\<user>\.m2\repository` on Windows.

```
cd kellinwood-logging-lib
mvn install
```

If this is your first time using Maven, the 'mvn install' command above will cause a bunch of stuff to be downloaded and will take a few minutes.

Next, download and extract the zipsigner-mvn-artifacts-1.x.zip into your local repository.
This will make the released artifacts available to Maven, allowing you
to skip the builds for projects you don't care about.

Now you can compile and build the main parts of the project...


```
cd zipio-lib
mvn install

cd ../zipsigner-lib
mvn install
```


