package com.expensetracker.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResetPasswordDto {

    @NotBlank(message = "OTP token is required")
    private String otpToken;

    @NotBlank(message = "New password is required")
    private String newPassword;
}

