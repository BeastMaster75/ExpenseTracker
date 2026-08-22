package com.expensetracker.transaction.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
public class UpdateTransactionDto {

    private BigDecimal amount;

    private String transactionType;

    private String description;

    private Date createdAt;
}