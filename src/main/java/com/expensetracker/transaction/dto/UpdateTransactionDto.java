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

    private String budgetName;

    private String description;

    private Date createdAt;
}
