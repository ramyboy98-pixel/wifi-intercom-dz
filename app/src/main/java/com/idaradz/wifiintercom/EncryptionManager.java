package com.idaradz.wifiintercom;

import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionManager {

    private static final String SECRET =
            "WIFI_INTERCOM_PRO_AES_KEY";

    private static SecretKeySpec getKey()
            throws Exception{

        byte[] key =
                SECRET.getBytes("UTF-8");

        MessageDigest sha =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        key = sha.digest(key);

        return new SecretKeySpec(
                key,
                "AES"
        );
    }

    public static byte[] encrypt(
            byte[] data
    ){

        try{

            SecretKeySpec key =
                    getKey();

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
                    getKey();

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
}
