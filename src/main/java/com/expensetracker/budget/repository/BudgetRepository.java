package com.expensetracker.budget.repository;

import com.expensetracker.budget.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    // Every lookup is scoped to the owner so one user can never reach
    // another's budgets.
    Optional<Budget> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    List<Budget> findAllByUserIdAndDeletedFalseOrderByNameAsc(Long userId);

    Optional<Budget> findByUserIdAndNameAndDeletedFalse(Long userId, String name);

    // Includes soft-deleted rows. uk_budget_user_name does not exclude them, so
    // name collisions must be resolved against every row, not just live ones.
    Optional<Budget> findByUserIdAndName(Long userId, String name);

    boolean existsByUserIdAndName(Long userId, String name);

    List<Budget> findAllByUserId(Long userId);

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
