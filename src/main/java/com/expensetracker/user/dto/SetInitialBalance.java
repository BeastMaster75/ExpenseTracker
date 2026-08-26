package com.expensetracker.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class SetInitialBalance {

    @NotBlank
    private BigDecimal initialBalance;

}
