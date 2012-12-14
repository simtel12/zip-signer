/*
 * Copyright (C) 2010 Ken Ellinwood
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
package kellinwood.zipsigner.cmdline;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.X509Certificate;

import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Parser;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.PropertyConfigurator;

import kellinwood.logging.log4j.Log4jLoggerFactory;
import kellinwood.logging.LoggerInterface;
import kellinwood.logging.LoggerManager;
import kellinwood.security.zipsigner.ZipSigner;



/**
 * Sign files from the command line using zipsigner-lib.
 */
public class Main 
{

    static void usage( Options options)
    {
        HelpFormatter formatter = new HelpFormatter();

        formatter.printHelp(140,
                "ZipSignerCmdline [options] <input.zip> <output.zip>",
                "Sign the input file and write the result to the given output file\n\n"+
            "Examples:\n\n"+
            "java -jar zipsigner-cmdline-<version>.jar input.zip output-signed.zip (signs in auto-testkey mode)\n\n"+
            "java -jar zipsigner-cmdline-<version>.jar -m <keyMode> input.zip output-signed.zip (signs in specified mode)\n\n"+
            "java -jar zipsigner-cmdline-<version>.jar -s <keystore file> input.zip output-signed.zip (signs with first key in the keystore)\n\n"+
            "java -jar zipsigner-cmdline-<version>.jar -s <keystore file> -a <key alias> input.zip output-signed.zip (signs with specified key in keystore)",
                options, "");

        System.exit(1);
    }

    static char[] readPassword( String prompt)  {
        System.out.print(prompt + ": ");
        System.out.flush();
        return System.console().readPassword();
    }
    
    public static void main( String[] args) {
        try {

            Options options = new Options();
            CommandLine cmdLine = null;
            Option helpOption =  new Option("h", "help", false, "Display usage information");

            Option providerOption = new Option("p", "provider", false, "Alternate security provider class - e.g., 'org.bouncycastle.jce.provider.BouncyCastleProvider'");
            providerOption.setArgs( 1);

            Option bcprovOption = new Option("b", "bcprov", false, "Use BouncyCastle as the security provider (shorthand for -p org...BouncyCastleProvider)");
            bcprovOption.setArgs( 0);

            Option modeOption = new Option("m", "keymode", false, "Keymode one of: auto, auto-testkey, auto-none, media, platform, shared, testkey, none");
            modeOption.setArgs( 1);
            
            Option keyOption = new Option("k", "key", false, "PCKS#8 encoded private key file");
            keyOption.setArgs( 1);

            Option pwOption = new Option("w", "keypass", false, "Private key password");
            pwOption.setArgs( 1);

            Option certOption = new Option("c", "cert", false, "X.509 public key certificate file");
            certOption.setArgs( 1);

            Option sbtOption = new Option("t", "template", false, "Signature block template file");
            sbtOption.setArgs( 1);
            
            Option keystoreOption = new Option("s", "keystore", false, "Keystore file");
            keystoreOption.setArgs(1);
            
            Option aliasOption = new Option("a", "alias", false, "Alias for key/cert in the keystore");
            aliasOption.setArgs(1);
            
            Option storepassOption = new Option("x", "storepass", false, "Keystore password");
            storepassOption.setArgs(1);

            Option storeTypeOption = new Option("y", "storetype", false, "Keystore type (default JKS, or BKS when using BouncyCastle provider");
            storeTypeOption.setArgs(1);

            options.addOption( helpOption);
            options.addOption( providerOption);
            options.addOption( bcprovOption);
            options.addOption( modeOption);
            options.addOption( keyOption);
            options.addOption( certOption);
            options.addOption( sbtOption); 
            options.addOption( pwOption);
            options.addOption( keystoreOption);
            options.addOption( aliasOption);
            options.addOption( storepassOption);
            options.addOption( storeTypeOption);

            Parser parser = new BasicParser();

            try {
                cmdLine = parser.parse(options, args);
            }
            catch (MissingOptionException x)
            {
                System.out.println("One or more required options are missing: " + x.getMessage());
                usage( options);
            }
            catch (ParseException x) {
                System.out.println( x.getClass().getName() + ": " + x.getMessage());
                usage( options);
            }

            if (cmdLine.hasOption( helpOption.getOpt())) usage(options);

            Properties log4jProperties = new Properties();
            log4jProperties.load ( new FileReader( "log4j.properties" ));
            PropertyConfigurator.configure( log4jProperties);
            LoggerManager.setLoggerFactory( new Log4jLoggerFactory());

            List<String> argList = cmdLine.getArgList();
            if (argList.size() != 2) usage(options);

            // LoggerInterface logger = LoggerManager.getLogger(Main.class.getName());

            String securityProvider = null;
            if (cmdLine.hasOption( providerOption.getOpt())) securityProvider = providerOption.getValue();
            else if (cmdLine.hasOption( bcprovOption.getOpt()) ||
                (cmdLine.hasOption(keystoreOption.getOpt()) && keystoreOption.getValue().toLowerCase().endsWith(".bks"))) {
                securityProvider = "org.bouncycastle.jce.provider.BouncyCastleProvider";
            }

            ZipSigner signer = new ZipSigner();

            if (securityProvider != null) {
                signer.loadProvider( securityProvider);
            }

            PrivateKey privateKey = null;            
            if (cmdLine.hasOption( keyOption.getOpt())) {
                if (!cmdLine.hasOption( certOption.getOpt())) {
                    System.out.println("Certificate file is required when specifying a private key");
                    usage( options);
                }

                String keypw = null;
                if (cmdLine.hasOption( pwOption.getOpt())) keypw = pwOption.getValue();
                else {
                    keypw = new String(readPassword("Key password"));
                    if (keypw.equals("")) keypw = null;
                }
                URL privateKeyUrl = new File( keyOption.getValue()).toURI().toURL();
                
                privateKey = signer.readPrivateKey( privateKeyUrl, keypw);
            }

            X509Certificate cert = null;
            if (cmdLine.hasOption( certOption.getOpt())) {

                if (!cmdLine.hasOption( keyOption.getOpt())) {
                    System.out.println("Private key file is required when specifying a certificate");
                    usage( options);
                }

                URL certUrl = new File( certOption.getValue()).toURI().toURL();
                cert = signer.readPublicKey( certUrl);
            }

            byte[] sigBlockTemplate = null;
            if (cmdLine.hasOption( sbtOption.getOpt())) {
                URL sbtUrl = new File( sbtOption.getValue()).toURI().toURL();
                sigBlockTemplate = signer.readContentAsBytes( sbtUrl);
            }

            if (cmdLine.hasOption( keyOption.getOpt())) {
                signer.setKeys( "custom", cert, privateKey, sigBlockTemplate);
                signer.signZip( argList.get(0), argList.get(1));
            }
            else if (cmdLine.hasOption( modeOption.getOpt())) {
                signer.setKeymode(modeOption.getValue());
                signer.signZip( argList.get(0), argList.get(1));
            }
            else if (cmdLine.hasOption(( keystoreOption.getOpt()))) {
                String storepw = null;
                if (cmdLine.hasOption( storepassOption.getOpt())) storepw = storepassOption.getValue();
                else {
                    storepw = new String(readPassword("Keystore password"));
                    if (storepw.equals("")) storepw = null;
                }
                
                String keystoreType = null;
                String alias = null;

                if (cmdLine.hasOption( storeTypeOption.getOpt())) {
                    keystoreType = storeTypeOption.getValue();
                }

                if (!cmdLine.hasOption( aliasOption.getOpt())) {
                    
                    Provider provider = null;
                    if (securityProvider != null) {
                        Class providerClass = Class.forName(securityProvider);
                        provider = (Provider)providerClass.newInstance();
                        if (keystoreType == null && securityProvider.equals("org.bouncycastle.jce.provider.BouncyCastleProvider"))
                            keystoreType = "bks";
                    }
                    if (keystoreType == null) keystoreType = "jks";
                    KeyStore keystore = null;
                    if (provider != null) keystore = KeyStore.getInstance(keystoreType, provider);
                    else keystore = KeyStore.getInstance(keystoreType);
                    keystore.load(new FileInputStream( keystoreOption.getValue()), storepw.toCharArray());
                    for (Enumeration<String> e = keystore.aliases(); e.hasMoreElements(); ) {
                        alias = e.nextElement();
                        break;
                    }
                }
                else alias = aliasOption.getValue();

                
                String keypw = null;
                if (cmdLine.hasOption( pwOption.getOpt())) keypw = pwOption.getValue();
                else {
                    keypw = new String(readPassword("Key password"));
                    if (keypw.equals("")) keypw = null;
                }
                
                signer.signZip( new File( keystoreOption.getValue()).toURI().toURL(),
                        keystoreType,
                        storepw, 
                        alias,
                        keypw, 
                        argList.get(0), 
                        argList.get(1));
            }
            else {
                signer.setKeymode("auto-testkey");
                signer.signZip( argList.get(0), argList.get(1));
            }
            
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
    }


}
