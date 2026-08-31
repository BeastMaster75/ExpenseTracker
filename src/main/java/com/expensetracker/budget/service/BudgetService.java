package com.expensetracker.budget.service;

import com.expensetracker.auth.Authentication;
import com.expensetracker.budget.dto.BudgetSummaryDto;
import com.expensetracker.budget.dto.CreateBudgetDto;
import com.expensetracker.budget.dto.UpdateBudgetDto;
import com.expensetracker.budget.entity.Budget;
import com.expensetracker.budget.repository.BudgetRepository;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.common.util.MoneyUtils;
import com.expensetracker.user.entity.User;
import com.expensetracker.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final Authentication authentication;

    public BudgetService(
            BudgetRepository budgetRepository,
            UserRepository userRepository,
            Authentication authentication
    ) {
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.authentication = authentication;
    }

    private Long getUserId(String token) {
        Claims claims = authentication.auth(token, false);
        return claims.get("id", Long.class);
    }

    private Budget ownedBudget(Long id, Long userId) {
        return budgetRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> new AppException("Budget not found", HttpStatus.NOT_FOUND));
    }

    private User ownedUser(Long userId) {
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
    }

    // How much the caller is allowed to have budgeted in total: the balance
    // they opened the account with, plus every income they have booked since.
    //
    // initialBalance itself is never rewritten -- income raises the allowance
    // by being added here, not by mutating the stored figure.
    private BigDecimal allowance(User user) {
        return MoneyUtils.orZero(user.getBalance());

    }

    // A budget may not push the user's total budgeted amount past their
    // allowance -- otherwise the same money could be promised to several
    // budgets at once.
    //
    // excludeBudgetId keeps the budget being edited out of the running total,
    // so its own current limit is not counted twice against the new one.
    private void assertWithinAllowance(User user, Long excludeBudgetId, BigDecimal newLimit) {

        BigDecimal others = budgetRepository
                .findAllByUserIdAndDeletedFalseOrderByNameAsc(user.getId())
                .stream()
                .filter(budget -> excludeBudgetId == null || !excludeBudgetId.equals(budget.getId()))
                .map(Budget::getAmountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = others.add(MoneyUtils.orZero(newLimit));
        BigDecimal allowance = allowance(user);

        if (total.compareTo(allowance) > 0) {

            log.warn("Budget rejected - allowance exceeded - userId: {}, total: {}, allowance: {}",
                    user.getId(), total, allowance);

            throw new AppException(
                    "Budgets would total " + total + ", which exceeds your available "
                            + allowance + ". Lower the limit or add income first.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    @Transactional
    public Budget createBudget(CreateBudgetDto dto, String token) {

        Long userId = getUserId(token);

        log.info("Creating budget for userId: {}", userId);

        User user = ownedUser(userId);

        Budget existing = budgetRepository
                .findByUserIdAndName(userId, dto.getName())
                .orElse(null);

        if (existing != null && !existing.isDeleted()) {

            log.warn("Budget creation failed - duplicate name - userId: {}, name: {}", userId, dto.getName());

            throw new AppException("Budget with this name already exists", HttpStatus.CONFLICT);
        }

        // A revived budget is soft-deleted, so it is not in the live total the
        // check sums -- passing its id keeps that true either way.
        assertWithinAllowance(user, existing != null ? existing.getId() : null, dto.getAmountLimit());

        Budget budget = existing != null ? existing : new Budget();

        budget.setUser(user);
        budget.setName(dto.getName());
        budget.setAmountLimit(dto.getAmountLimit());
        budget.setDeleted(false);

        // Both are server-owned -- a new or revived budget starts empty.
        budget.setSpending(BigDecimal.ZERO);
        budget.setAvailableToUse(BigDecimal.ZERO);

        budget.setPeriodMonth(dto.getPeriodMonth() != null
                ? dto.getPeriodMonth()
                : LocalDate.now().withDayOfMonth(1));

        Budget saved = budgetRepository.save(budget);

        log.info("Budget {} successfully - id: {}, userId: {}",
                existing != null ? "revived" : "created", saved.getId(), userId);

        return saved;
    }

    public Budget getBudgetById(Long id, String token) {

        Long userId = getUserId(token);

        log.info("Getting budget - id: {}, userId: {}", id, userId);

        return ownedBudget(id, userId);
    }

    public List<Budget> getBudgets(String token) {

        Long userId = getUserId(token);

        log.info("Getting budgets for userId: {}", userId);

        return budgetRepository.findAllByUserIdAndDeletedFalseOrderByNameAsc(userId);
    }

    // Totals for the "Total Monthly Budget" card. Summed here rather than in
    // the client so every caller gets the same percentage, and rather than in
    // SQL because a user's budget list is small and already scoped.
    public BudgetSummaryDto getSummary(String token) {

        Long userId = getUserId(token);

        log.info("Getting budget summary for userId: {}", userId);

        List<Budget> budgets = budgetRepository.findAllByUserIdAndDeletedFalseOrderByNameAsc(userId);

        BigDecimal totalLimit = budgets.stream()
                .map(Budget::getAmountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAvailableToUse = budgets.stream()
                .map(Budget::getAvailableToUse)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSpending = budgets.stream()
                .map(Budget::getSpending)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Measured against the same ceiling the per-budget figures use, so the
        // card and the rows below it can never disagree.
        BigDecimal totalCeiling = totalLimit.add(totalAvailableToUse);

        BudgetSummaryDto summary = new BudgetSummaryDto();

        summary.setTotalLimit(totalLimit);
        summary.setTotalAvailableToUse(totalAvailableToUse);
        summary.setTotalSpending(totalSpending);
        summary.setRemaining(totalCeiling.subtract(totalSpending));
        summary.setBudgetCount(budgets.size());
        summary.setPeriodMonth(LocalDate.now().withDayOfMonth(1));

        summary.setPercentageUsed(MoneyUtils.percentage(totalSpending, totalCeiling));

        return summary;
    }

    @Transactional
    public Budget updateBudget(Long id, UpdateBudgetDto dto, String token) {

        Long userId = getUserId(token);

        log.info("Updating budget - id: {}, userId: {}", id, userId);

        Budget budget = ownedBudget(id, userId);

        if (dto.getName() != null && !dto.getName().equals(budget.getName())) {

            // A soft-deleted namesake still occupies uk_budget_user_name.
            if (budgetRepository.existsByUserIdAndName(userId, dto.getName())) {

                log.warn("Budget update failed - duplicate name - userId: {}, name: {}", userId, dto.getName());

                throw new AppException("Budget with this name already exists", HttpStatus.CONFLICT);
            }

            budget.setName(dto.getName());
        }

        // Only re-checked when the limit actually moves: a rename should not
        // fail just because older data already sits above the allowance.
        if (dto.getAmountLimit() != null) {
            assertWithinAllowance(ownedUser(userId), budget.getId(), dto.getAmountLimit());
            budget.setAmountLimit(dto.getAmountLimit());
        }

        if (dto.getPeriodMonth() != null) {
            budget.setPeriodMonth(dto.getPeriodMonth());
        }

        // Spending and availableToUse are never taken from the request -- see
        // createBudget.
        Budget updated = budgetRepository.save(budget);

        log.info("Budget updated successfully - id: {}, userId: {}", id, userId);

        return updated;
    }

    @Transactional
    public Map<String, String> deleteBudget(Long id, String token) {

        Long userId = getUserId(token);

        log.info("Deleting budget - id: {}, userId: {}", id, userId);

        Budget budget = ownedBudget(id, userId);

        // Soft delete: transactions keep pointing at the row via budget_id.
        budget.setDeleted(true);

        budgetRepository.save(budget);

        log.info("Budget deleted successfully - id: {}, userId: {}", id, userId);

        return Map.of("message", "Budget deleted successfully");
    }
}
