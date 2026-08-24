package com.expensetracker.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BudgetResponseDTO {

    private Long id;

    private Long userId;

    private String budgetName;

    private BigDecimal spending;

    private BigDecimal amountLimit;

    private LocalDate periodMonth;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}