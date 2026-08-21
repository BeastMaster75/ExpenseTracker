package com.expensetracker.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdatePasswordDto {

    @NotBlank(message = "oldPassword is required")
    private String oldPassword;

    @NotBlank(message = "newPassword is required")
    private String newPassword;
}
