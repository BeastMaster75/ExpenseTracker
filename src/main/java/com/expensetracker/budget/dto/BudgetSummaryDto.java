package com.expensetracker.budget.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class BudgetSummaryDto {

    // Totals across every live budget the caller owns -- the figures behind the
    // "Total Monthly Budget" card.
    private BigDecimal totalLimit;
    private BigDecimal totalSpending;

    // totalLimit - totalSpending. Goes negative once the caller overspends,
    // which the card needs in order to show an over-budget state.
    private BigDecimal remaining;

    // totalSpending as a percentage of totalLimit, two decimal places.
    // Zero when there is nothing budgeted, and free to exceed 100.
    private BigDecimal percentageUsed;

    private long budgetCount;
    private LocalDate periodMonth;
}
