package com.expensetracker.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResendOtpDto {

    @Email
    @NotBlank(message = "email is required")
    private String email;
}
