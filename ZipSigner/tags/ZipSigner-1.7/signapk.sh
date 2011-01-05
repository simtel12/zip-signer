#! /bin/bash

# Script to sign ZipSigner for the Market.  Complicated because I
# accidentally published the first version signed by the Google debug
# cert and my own cert.  Now I can't update the app unless its signed
# that way every time I publish.

if [ ! -f target/ZipSigner.apk ]; then
    echo "ERROR: target/ZipSigner.apk does not exist!"
    exit 1
fi

rm -rf target/tmp
mkdir target/tmp

cd target/tmp
unzip -q ../ZipSigner.apk

zip -q -n png ../ZipSigner-unsigned.apk `find -type f | sort`
cd ..
rm -f *-signed.apk 
rm -f *-aligned.apk 
jarsigner -keystore /home/ken/projects/sandbox/android/keystore -signedjar ZipSigner-signed.apk ZipSigner-unsigned.apk kellinwood
zipalign 4 ZipSigner-signed.apk ZipSigner-signed-aligned.apk
rm ZipSigner-unsigned.apk
rm ZipSigner-signed.apk
