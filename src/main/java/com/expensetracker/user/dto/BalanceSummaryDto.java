package com.expensetracker.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class BalanceSummaryDto {



    private BigDecimal balance;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
}