package com.expensetracker.user.dto;

import com.expensetracker.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdatePasswordDto {

    // Deliberately not @StrongPassword -- an existing password may predate the
    // policy, and rejecting it here would lock those users out of changing it.
    @NotBlank(message = "oldPassword is required")
    private String oldPassword;

    @NotBlank(message = "newPassword is required")
    @StrongPassword
    private String newPassword;
}
