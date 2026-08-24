package com.expensetracker.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
public class CreateTransactionDto{

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    // Required for expenses only -- income is not tied to a budget, so the
    // check lives in the service where the type is known.
    private String budgetName;

    @NotBlank(message = "transactionType is required")
    private String transactionType;

    private Date createdAt;

    private String description;

}
