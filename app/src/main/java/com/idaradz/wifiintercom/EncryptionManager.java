package com.idaradz.wifiintercom;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoManager {

    private static final String SECRET = "WIFI_INTERCOM_PRO_LOCAL_AES_KEY";
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE = 128;

    private static SecretKeySpec getKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(SECRET.getBytes("UTF-8"));
        return new SecretKeySpec(key, "AES");
    }

    public static byte[] encrypt(byte[] data) {
        try {
            byte[] iv = new byte[IV_SIZE];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    getKey(),
                    new GCMParameterSpec(TAG_SIZE, iv)
            );

            byte[] encrypted = cipher.doFinal(data);
            byte[] output = new byte[iv.length + encrypted.length];

            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);

            return output;

        } catch (Exception e) {
            return data;
        }
    }

    public static byte[] decrypt(byte[] data) {
        try {
            byte[] iv = new byte[IV_SIZE];
            byte[] encrypted = new byte[data.length - IV_SIZE];

            System.arraycopy(data, 0, iv, 0, IV_SIZE);
            System.arraycopy(data, IV_SIZE, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getKey(),
                    new GCMParameterSpec(TAG_SIZE, iv)
            );

            return cipher.doFinal(encrypted);

        } catch (Exception e) {
            return data;
        }
    }
}
