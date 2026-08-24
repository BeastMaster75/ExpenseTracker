package com.expensetracker.report.entity;

import com.expensetracker.report.converter.YearMonthConverter;
import com.expensetracker.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.YearMonth;

@Entity
@Table(
        name = "reports",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "month"})
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = YearMonthConverter.class)
    @Column(name = "month", nullable = false)
    private YearMonth month;

    private BigDecimal totalIncome;

    private BigDecimal totalExpenses;

    private BigDecimal netIncome;

    private String topCategory;

    private BigDecimal topCategorySpending;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}