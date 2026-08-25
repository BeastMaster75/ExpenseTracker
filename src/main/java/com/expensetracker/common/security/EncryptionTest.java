package com.expensetracker.common.security;

import com.expensetracker.common.security.EncryptAndDecryptSecurity;
import org.springframework.stereotype.Component;

@Component
public class EncryptionTest {

    public static void main(String[] args) {

        String password = "Mohamed123456@";

        String encrypted =
                EncryptAndDecryptSecurity.encrypt(password);

        System.out.println("Encrypted password:");
        System.out.println(encrypted);

        System.out.println("Decrypted password:");
        System.out.println(
                EncryptAndDecryptSecurity.decrypt(encrypted)
        );
    }
}