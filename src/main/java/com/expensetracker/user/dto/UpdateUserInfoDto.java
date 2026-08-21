package com.expensetracker.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateUserInfoDto {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30)
    private String username;
}
