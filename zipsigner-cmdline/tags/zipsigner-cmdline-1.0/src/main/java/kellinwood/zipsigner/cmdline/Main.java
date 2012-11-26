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
import java.io.FileReader;
import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.security.PrivateKey;
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
                "Sign the input file and write the result to the given output file\n",
                options, "");

        System.exit(1);
    }

    public static void main( String[] args) {
        try {

            Options options = new Options();
            CommandLine cmdLine = null;
            Option helpOption =  new Option("h", "help", false, "Display usage information");

            Option providerOption = new Option("p", "provider", false, "Alternate security provider class - e.g., 'org.bouncycastle.jce.provider.BouncyCastleProvider'");
            providerOption.setArgs( 1);

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

            options.addOption( helpOption);
            options.addOption( providerOption);
            options.addOption( modeOption);
            options.addOption( keyOption);
            options.addOption( certOption);
            options.addOption( sbtOption);            

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

            ZipSigner signer = new ZipSigner();

            if (cmdLine.hasOption( providerOption.getOpt())) {
                signer.loadProvider( providerOption.getValue());
            }

            PrivateKey privateKey = null;            
            if (cmdLine.hasOption( keyOption.getOpt())) {
                if (!cmdLine.hasOption( certOption.getOpt())) {
                    System.out.println("Certificate file is required when specifying a private key");
                    usage( options);
                }

                String keypw = null;
                if (cmdLine.hasOption( pwOption.getOpt())) keypw = pwOption.getValue();
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
            }
            else if (cmdLine.hasOption( modeOption.getOpt())) {
                signer.setKeymode(modeOption.getValue());
            }
            signer.signZip( argList.get(0), argList.get(1));
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
    }


}
