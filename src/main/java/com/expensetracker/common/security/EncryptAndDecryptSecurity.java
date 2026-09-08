package com.expensetracker.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class EncryptAndDecryptSecurity {

    private static final String ENCRYPTION_KEY = "1829819wjherf8812yuqy8uhr891892y";
    private static final int IV_LENGTH = 16;

    public static String encrypt(String text) {

        try {

            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            SecretKeySpec key = new SecretKeySpec(
                    ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8),
                    "AES"
            );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    new IvParameterSpec(iv)
            );

            byte[] encrypted = cipher.doFinal(
                    text.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(iv)
                    + ":"
                    + HexFormat.of().formatHex(encrypted);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String decrypt(String text) {

        try {

            String[] parts = text.split(":");

            byte[] iv = HexFormat.of().parseHex(parts[0]);
            byte[] encrypted = HexFormat.of().parseHex(parts[1]);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            SecretKeySpec key = new SecretKeySpec(
                    ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8),
                    "AES"
            );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new IvParameterSpec(iv)
            );

            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(
                    decrypted,
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
