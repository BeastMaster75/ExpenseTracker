package com.expensetracker.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class BudgetResponseDTO {

    private Long id;
    private Long userId;
    private String name;
    private BigDecimal spending;
    private BigDecimal amountLimit;
    private LocalDate periodMonth;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BudgetResponseDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getSpending() {
        return spending;
    }

    public void setSpending(BigDecimal spending) {
        this.spending = spending;
    }

    public BigDecimal getAmountLimit() {
        return amountLimit;
    }

    public void setAmountLimit(BigDecimal amountLimit) {
        this.amountLimit = amountLimit;
    }

    public LocalDate getPeriodMonth() {
        return periodMonth;
    }

    public void setPeriodMonth(LocalDate periodMonth) {
        this.periodMonth = periodMonth;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}