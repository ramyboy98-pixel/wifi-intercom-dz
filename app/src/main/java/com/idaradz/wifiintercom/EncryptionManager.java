package com.idaradz.wifiintercom;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionManager {

    private static final String KEY =
            "1234567890123456";

    public static byte[] encrypt(
            byte[] data
    ){

        try{

            SecretKeySpec key =
                    new SecretKeySpec(
                            KEY.getBytes(),
                            "AES"
                    );

            Cipher cipher =
                    Cipher.getInstance(
                            "AES"
                    );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key
            );

            return cipher.doFinal(data);

        }catch (Exception e){

            return data;
        }
    }

    public static byte[] decrypt(
            byte[] data
    ){

        try{

            SecretKeySpec key =
                    new SecretKeySpec(
                            KEY.getBytes(),
                            "AES"
                    );

            Cipher cipher =
                    Cipher.getInstance(
                            "AES"
                    );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key
            );

            return cipher.doFinal(data);

        }catch (Exception e){

            return data;
        }
    }

    public static String encryptText(
            String text
    ){

        try{

            return Base64.encodeToString(
                    encrypt(
                            text.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    ),
                    Base64.DEFAULT
            );

        }catch (Exception e){

            return text;
        }
    }

    public static String decryptText(
            String text
    ){

        try{

            byte[] decoded =
                    Base64.decode(
                            text,
                            Base64.DEFAULT
                    );

            return new String(
                    decrypt(decoded),
                    StandardCharsets.UTF_8
            );

        }catch (Exception e){

            return text;
        }
    }
}
