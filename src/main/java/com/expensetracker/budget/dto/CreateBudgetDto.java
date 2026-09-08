package com.expensetracker.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class CreateBudgetDto {

    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "amountLimit is required")
    @PositiveOrZero(message = "amountLimit must be zero or greater")
    private BigDecimal amountLimit;

    // Optional -- defaults to the current month. Spending is deliberately
    // absent: it is server-owned, see Budget#spending.
    private LocalDate periodMonth;
}
