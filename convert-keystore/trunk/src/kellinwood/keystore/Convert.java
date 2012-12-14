package kellinwood.keystore;

import java.io.*;
import java.security.cert.Certificate;
import java.security.Key;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.Enumeration;

public class Convert {

    static char[] readPassword( String prompt) throws java.io.IOException {
        System.out.print(prompt + ": ");
        System.out.flush();
        return System.console().readPassword();
    }
    
    @SuppressWarnings("unchecked")
    public static Provider loadProvider( String providerClassName)
    throws ClassNotFoundException, IllegalAccessException, InstantiationException
    {
        Class providerClass = Class.forName(providerClassName);
        Provider provider = (Provider)providerClass.newInstance();
        Security.insertProviderAt(provider, 1);
        return provider;
    }
    
    public static void main(String[] args) {
        
        try {

            // System.out.println( "Default keystore type is " + KeyStore.getDefaultType());
            
            if (args.length != 2) {
                System.out.println("USAGE: Convert <keystore.jks> <keystore.bks");
                System.out.println("Converts JKS formatted keystore to BKS format.");
                System.exit(1);
            }
            
            FileInputStream fis = new FileInputStream( args[0]);
            KeyStore jksKeystore = KeyStore.getInstance("jks");
            
            char[] keystorePassword = readPassword("Keystore password");
            jksKeystore.load(fis, keystorePassword);
            fis.close();

            Provider bcProvider = loadProvider("org.bouncycastle.jce.provider.BouncyCastleProvider");
            KeyStore bksKeystore = KeyStore.getInstance("bks", bcProvider);
            bksKeystore.load(null, keystorePassword);
            
            for( Enumeration<String> e = jksKeystore.aliases(); e.hasMoreElements(); ) {
                String alias = e.nextElement();
                
                System.out.println("Alias: " + alias);
                char[] keyPassword = null;
                Key key;
                try {
                    key = jksKeystore.getKey(alias, keystorePassword);
                    keyPassword = keystorePassword;
                } catch ( java.security.UnrecoverableKeyException x) {
                    keyPassword = readPassword("Password for entry " + alias);
                    key = jksKeystore.getKey(alias, keyPassword);
                }
                
                System.out.println("Key type:   " + key.getClass().getName());
                System.out.println("Key format: " + key.getFormat());
                
                Certificate cert = jksKeystore.getCertificate(alias);

                bksKeystore.setKeyEntry(alias, key, keyPassword, new Certificate[] { cert });
            }
            
            FileOutputStream fos = new FileOutputStream( args[1]);
            bksKeystore.store(fos, keystorePassword);
            fos.close();

        } catch (Exception x) {
            x.printStackTrace();
        }

    }
}
