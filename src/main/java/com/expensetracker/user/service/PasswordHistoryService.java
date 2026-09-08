package com.expensetracker.user.service;

import com.expensetracker.common.exception.AppException;
import com.expensetracker.common.security.HashSecurity;
import com.expensetracker.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Service
public class PasswordHistoryService {

    private static final String DELIMITER = ",";

    public static final int MAX_REMEMBERED = 5;

    private final HashSecurity hashSecurity;

    public PasswordHistoryService(HashSecurity hashSecurity) {
        this.hashSecurity = hashSecurity;
    }

    /**
     * Rejects a candidate password that matches the current one or any of the
     * remembered hashes. Call before hashing the new password.
     */
    public void assertNotReused(User user, String newPassword) {

        // The password in use is not part of the history column, so it needs
        // its own check -- this also covers accounts that predate the feature.
        if (user.getPassword() != null
                && hashSecurity.compare(newPassword, user.getPassword())) {

            throw new AppException(
                    "New password must be different from your current password",
                    HttpStatus.BAD_REQUEST
            );
        }

        // BCrypt salts every hash, so the same password encodes differently each
        // time and equality comparison is useless. Each stored hash has to be
        // verified individually.
        for (String previousHash : parse(user.getPasswordHistory())) {

            if (hashSecurity.compare(newPassword, previousHash)) {

                throw new AppException(
                        "You cannot reuse any of your last "
                                + MAX_REMEMBERED + " passwords",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
    }

    /**
     * Pushes the hash being replaced onto the front of the history and drops
     * anything past the newest {@value #MAX_REMEMBERED}.
     */
    public void record(User user, String replacedHash) {

        if (replacedHash == null || replacedHash.isBlank()) {
            return;
        }

        List<String> history = new ArrayList<>(parse(user.getPasswordHistory()));

        history.add(0, replacedHash);

        // Pruned on write, so the column never grows past the cap.
        if (history.size() > MAX_REMEMBERED) {
            history = history.subList(0, MAX_REMEMBERED);
        }

        user.setPasswordHistory(String.join(DELIMITER, history));
    }

    private List<String> parse(String raw) {

        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        return Arrays.stream(raw.split(DELIMITER))
                .filter(hash -> !hash.isBlank())
                .toList();
    }
}
