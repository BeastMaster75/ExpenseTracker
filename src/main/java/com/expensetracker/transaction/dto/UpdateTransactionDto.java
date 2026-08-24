package com.expensetracker.transaction.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
public class UpdateTransactionDto {

    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String transactionType;

    // Only needed when turning a transaction into an expense, or moving an
    // existing expense to a different budget.
    private String budgetName;

    private String description;

    private Date createdAt;
}
