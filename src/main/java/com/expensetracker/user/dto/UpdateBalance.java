package com.expensetracker.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class UpdateBalance {

//    @NotBlank
    private BigDecimal newBalance;

}
