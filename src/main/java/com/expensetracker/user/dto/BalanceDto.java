package com.expensetracker.user.dto;

import java.math.BigDecimal;

public class BalanceDto {

    private BigDecimal balance;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;

    public BalanceDto(
            BigDecimal balance,
            BigDecimal totalIncome,
            BigDecimal totalExpense
    ) {
        this.balance = balance;
        this.totalIncome = totalIncome;
        this.totalExpense = totalExpense;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public BigDecimal getTotalExpense() {
        return totalExpense;
    }
}