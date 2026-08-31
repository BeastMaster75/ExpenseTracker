package com.expensetracker.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResetPasswordDto {
    //    @StrongPassword
    @NotBlank(message = "New password is required")
    private String newPassword;
}

