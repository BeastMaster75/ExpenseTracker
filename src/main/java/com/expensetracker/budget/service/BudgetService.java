package com.expensetracker.budget.service;

import com.expensetracker.auth.Authentication;
import com.expensetracker.budget.dto.BudgetSummaryDto;
import com.expensetracker.budget.dto.CreateBudgetDto;
import com.expensetracker.budget.dto.UpdateBudgetDto;
import com.expensetracker.budget.entity.Budget;
import com.expensetracker.budget.repository.BudgetRepository;
import com.expensetracker.common.exception.AppException;
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

    @Transactional
    public Budget createBudget(CreateBudgetDto dto, String token) {

        Long userId = getUserId(token);

        log.info("Creating budget for userId: {}", userId);

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Budget existing = budgetRepository
                .findByUserIdAndName(userId, dto.getName())
                .orElse(null);

        if (existing != null && !existing.isDeleted()) {

            log.warn("Budget creation failed - duplicate name - userId: {}, name: {}", userId, dto.getName());

            throw new AppException("Budget with this name already exists", HttpStatus.CONFLICT);
        }

        Budget budget = existing != null ? existing : new Budget();

        budget.setUser(user);
        budget.setName(dto.getName());
        budget.setAmountLimit(dto.getAmountLimit());
        budget.setDeleted(false);

        // Spending is server-owned -- a new or revived budget starts empty.
        budget.setSpending(BigDecimal.ZERO);

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

        BigDecimal totalSpending = budgets.stream()
                .map(Budget::getSpending)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BudgetSummaryDto summary = new BudgetSummaryDto();

        summary.setTotalLimit(totalLimit);
        summary.setTotalSpending(totalSpending);
        summary.setRemaining(totalLimit.subtract(totalSpending));
        summary.setBudgetCount(budgets.size());
        summary.setPeriodMonth(LocalDate.now().withDayOfMonth(1));

        // Same helper the per-budget percentage uses, so the card and the rows
        // below it can never disagree. Handles a zero total limit.
        summary.setPercentageUsed(Budget.percentage(totalSpending, totalLimit));

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

        if (dto.getAmountLimit() != null) {
            budget.setAmountLimit(dto.getAmountLimit());
        }

        if (dto.getPeriodMonth() != null) {
            budget.setPeriodMonth(dto.getPeriodMonth());
        }

        // Spending is never taken from the request -- see createBudget.
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
