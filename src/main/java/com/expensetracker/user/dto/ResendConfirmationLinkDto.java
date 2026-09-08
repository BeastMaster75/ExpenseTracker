package com.expensetracker.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResendConfirmationLinkDto {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email")
    private String email;
}