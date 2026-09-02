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

    // Income booked against these budgets this month. Added to totalLimit to
    // get the ceiling the figures below are measured against.
    private BigDecimal totalAvailableToUse;

    // (totalLimit + totalAvailableToUse) - totalSpending.
    private BigDecimal remaining;

    // totalSpending as a percentage of the ceiling, two decimal places. Zero
    // when there is nothing budgeted; capped at 100 in practice now that
    // spending past a budget's ceiling is rejected.
    private BigDecimal percentageUsed;

    private long budgetCount;
    private LocalDate periodMonth;
}
