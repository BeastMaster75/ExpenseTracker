package com.expensetracker.budget.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BudgetRequestDTO {

    @NotNull
    private Long userId;

    @NotNull
    private String budgetName;

    @PositiveOrZero
    private BigDecimal spending = BigDecimal.ZERO;

    @NotNull
    @PositiveOrZero
    private BigDecimal amountLimit;
}