/*
 * Copyright (C) 2010 Ken Ellinwood.
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


package kellinwood.security.zipsigner;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import kellinwood.logging.LoggerInterface;
import kellinwood.logging.LoggerManager;

/*
 * This class provides Base64 encoding services using one of several possible 
 * implementations available elsewhere in the classpath. Supported implementations 
 * are android.util.Base64 and org.bouncycastle.util.encoders.Base64Encoder.  
 * These APIs are accessed via reflection, and as long as at least one is available
 * Base64 encoding is possible.  This technique provides compatibility across different
 * Android OS versions, and also allows zipsigner-lib to operate in desktop environments
 * as long as the BouncyCastle provider jar is in the classpath.
 * 
 * android.util.Base64 was added in API level 8 (Android 2.2, Froyo)
 * org.bouncycastle.util.encoders.Base64Encoder was removed in API level 11 (Android 3.0, Honeycomb)
 * 
 */
@SuppressWarnings("unchecked")
public class Base64 {

    static Method aMethod = null;  // Reference to the android.util.Base64.encode() method, if available
    
    static Object bEncoder = null; // Reference to an org.bouncycastle.util.encoders.Base64Encoder instance, if available
    static Method bMethod = null;  // Reference to the bEncoder.encode() method, if available
    
    static LoggerInterface logger = null;
    
    static {
        
        Class<Object> clazz;
    
        logger = LoggerManager.getLogger( Base64.class.getName());
        
        try {
            clazz = (Class<Object>) Class.forName("android.util.Base64");
            // Looking for encode( byte[] input, int flags)
            aMethod = clazz.getMethod("encode", byte[].class, Integer.TYPE);
            logger.info( clazz.getName() + " is available.");
        }
        catch (ClassNotFoundException x) {} // Ignore
        catch (Exception x) {
            logger.error("Failed to initialize use of android.util.Base64", x);
        }
        
        try {
            clazz = (Class<Object>) Class.forName("org.bouncycastle.util.encoders.Base64Encoder");
            bEncoder = clazz.newInstance();
            // Looking for encode( byte[] input, int offset, int length, OutputStream output)
            bMethod = clazz.getMethod("encode", byte[].class, Integer.TYPE, Integer.TYPE, OutputStream.class);
            logger.info( clazz.getName() + " is available.");
        }
        catch (ClassNotFoundException x) {} // Ignore
        catch (Exception x) {
            logger.error("Failed to initialize use of org.bouncycastle.util.encoders.Base64Encoder", x);
        }
        
        if (aMethod == null && bMethod == null)
            throw new IllegalStateException("No base64 encoder implementation is available.");
    }
    

    public static String encode( byte[] data) {
        try {
            if (aMethod != null) {
                // Invoking a static method call, using null for the instance value
                byte[] encodedBytes = (byte[])aMethod.invoke(null, data, 2);
                return new String( encodedBytes);
            }
            else if (bMethod != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bMethod.invoke(bEncoder, data, 0, data.length, baos);
                return new String( baos.toByteArray());
            }
        }
        catch (Exception x) {
            throw new IllegalStateException( x.getClass().getName() + ": " + x.getMessage());
        }

        
        throw new IllegalStateException("No base64 encoder implementation is available.");
    }
}