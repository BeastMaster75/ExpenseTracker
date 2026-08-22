package com.expensetracker.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    // BCrypt ignores everything past the 72nd byte, so a longer password would
    // hash identically to its own 72-byte prefix. Reject rather than truncate.
    private static final int MAX_LENGTH = 72;

    private int min;

    @Override
    public void initialize(StrongPassword annotation) {
        this.min = annotation.min();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {

        // Null/blank belongs to @NotBlank. Reporting it here as well would only
        // overwrite that message in GlobalExceptionHandler's error map.
        if (password == null || password.isBlank()) {
            return true;
        }

        List<String> missing = new ArrayList<>();

        if (password.length() < min) {
            missing.add("at least " + min + " characters");
        }

        if (password.length() > MAX_LENGTH) {
            missing.add("at most " + MAX_LENGTH + " characters");
        }

        if (password.chars().noneMatch(Character::isUpperCase)) {
            missing.add("an uppercase letter");
        }

        if (password.chars().noneMatch(Character::isLowerCase)) {
            missing.add("a lowercase letter");
        }

        if (password.chars().noneMatch(Character::isDigit)) {
            missing.add("a digit");
        }

        if (password.chars().noneMatch(StrongPasswordValidator::isSpecial)) {
            missing.add("a special character");
        }

        if (missing.isEmpty()) {
            return true;
        }

        // Report every unmet rule in a single violation. The handler stores one
        // message per field, so separate violations would hide each other.
        context.disableDefaultConstraintViolation();

        context.buildConstraintViolationWithTemplate(
                "Password must contain: " + String.join(", ", missing)
        ).addConstraintViolation();

        return false;
    }

    private static boolean isSpecial(int character) {
        return !Character.isLetterOrDigit(character)
                && !Character.isWhitespace(character);
    }
}
