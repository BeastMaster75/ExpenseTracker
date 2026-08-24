package com.expensetracker.budget.repository;

import com.expensetracker.budget.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findAllByDeletedFalse();

    Optional<Budget> findByUserIdAndNameAndDeletedFalse(Long userId, String name);

    boolean existsByUserIdAndNameAndDeletedFalse(Long userId, String name);

    // Zeroes spending for every live budget not already on the given period.
    // Bulk update so the monthly reset does not load every budget into memory.
    // The periodMonth guard makes a re-run within the same month a no-op.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Budget b
            SET b.spending = :zero,
                b.periodMonth = :periodMonth,
                b.updatedAt = CURRENT_TIMESTAMP
            WHERE b.deleted = false
              AND (b.periodMonth IS NULL OR b.periodMonth <> :periodMonth)
            """)
    int resetSpendingForPeriod(
            @Param("zero") BigDecimal zero,
            @Param("periodMonth") LocalDate periodMonth
    );
}