package com.expensetracker.budget.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class UpdateBudgetDto {

    // All optional -- only the fields sent are applied, same as
    // UpdateTransactionDto. Spending is server-owned and never accepted here.
    private String name;

    @PositiveOrZero(message = "amountLimit must be zero or greater")
    private BigDecimal amountLimit;

    private LocalDate periodMonth;
}
