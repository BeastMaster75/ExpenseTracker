package com.expensetracker.budget.entity;

import com.expensetracker.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
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
@Entity
@Table(
        name = "budgetss",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_budget_user_name",
                columnNames = {"user_id", "name"}
        )
)
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Transactions reference their budget by this name, so it must be unique
    // per user -- see the uk_budget_user_name constraint above.
    @Column(nullable = false)
    private String name;

    // Server-owned: starts at zero, expenses add to it, BudgetResetService
    // zeroes it at the start of each month. Never taken from a request.
    @Column(nullable = false)
    private BigDecimal spending = BigDecimal.ZERO;

    @Column(name = "amount_limit", nullable = false)
    private BigDecimal amountLimit;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt = LocalDateTime.now();
    }
}
