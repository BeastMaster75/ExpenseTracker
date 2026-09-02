package com.expensetracker.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {

    private Long id;

    private YearMonth month;

    private BigDecimal totalIncome;

    private BigDecimal totalExpenses;

    private BigDecimal netIncome;

    private String topCategory;

    private BigDecimal topCategorySpending;

    private Map<String, BigDecimal> categorySpending;
}