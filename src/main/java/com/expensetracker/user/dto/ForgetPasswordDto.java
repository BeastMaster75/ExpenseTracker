package com.expensetracker.user.dto;

import com.expensetracker.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ForgetPasswordDto {

    @NotBlank(message = "newPassword is required")
    @StrongPassword
    private String newPassword;

    @NotBlank(message = "otp is required")
    private String otp;
}
