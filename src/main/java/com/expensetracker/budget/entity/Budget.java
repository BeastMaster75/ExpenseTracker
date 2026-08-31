package com.expensetracker.budget.entity;

import com.expensetracker.common.util.MoneyUtils;
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
        name = "budgets",
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

    // The static allowance the user configured. Transactions never move it --
    // income tops up availableToUse instead.
    @Column(name = "amount_limit", nullable = false)
    private BigDecimal amountLimit;

    // Extra allowance granted by income booked against this budget. Server-
    // owned like spending, and zeroed by the same monthly reset: it is an
    // exception for the current month, not a permanent raise.
    @Column(name = "available_to_use", nullable = false)
    private BigDecimal availableToUse = BigDecimal.ZERO;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    // What this budget may actually spend this month: the static limit plus
    // whatever income topped it up. Every spending rule measures against this,
    // not against amountLimit alone.
    @Transient
    public BigDecimal getSpendingCeiling() {
        return MoneyUtils.orZero(amountLimit).add(MoneyUtils.orZero(availableToUse));
    }

    // Derived, not stored. Serialised with the budget so the client never has
    // to redo the maths -- and never has to handle the divide-by-zero itself.
    @Transient
    public BigDecimal getRemaining() {
        return getSpendingCeiling().subtract(MoneyUtils.orZero(spending));
    }

    @Transient
    public BigDecimal getPercentageUsed() {
        return MoneyUtils.percentage(spending, getSpendingCeiling());
    }

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
