package com.expensetracker.budget.service;

import com.expensetracker.budget.dto.BudgetRequestDTO;
import com.expensetracker.budget.dto.BudgetResponseDTO;
import com.expensetracker.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.expensetracker.budget.repository.BudgetRepository;
import com.expensetracker.user.repository.UserRepository;
import com.expensetracker.user.entity.User;
import com.expensetracker.budget.entity.Budget;
import java.util.List;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;

    public BudgetService(
            BudgetRepository budgetRepository,
            UserRepository userRepository) {

        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
    }

    public BudgetResponseDTO createBudget(BudgetRequestDTO request) {

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        // Transactions look budgets up by name, so a user cannot have two.
        if (budgetRepository.existsByUserIdAndNameAndDeletedFalse(
                user.getId(), request.getName())) {

            throw new AppException(
                    "Budget with this name already exists",
                    HttpStatus.CONFLICT
            );
        }

        Budget budget = new Budget();

        budget.setUser(user);
        budget.setName(request.getName());
        budget.setSpending(request.getSpending());
        budget.setAmountLimit(request.getAmountLimit());
        budget.setPeriodMonth(request.getPeriodMonth());

        // New budgets are active by default
        budget.setDeleted(false);

        Budget savedBudget = budgetRepository.save(budget);

        return convertToResponse(savedBudget);
    }

    // Get budget by ID
    public BudgetResponseDTO getBudgetById(Long id) {

        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Budget not found"));

        if (budget.isDeleted()) {
            throw new RuntimeException("Budget not found");
        }

        return convertToResponse(budget);
    }

    // Get all budgets
    public List<BudgetResponseDTO> getAllBudgets() {

        List<Budget> budgets =
                budgetRepository.findAllByDeletedFalse();

        return budgets.stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Update an existing budget
    public BudgetResponseDTO updateBudget(
            Long id,
            BudgetRequestDTO request) {

        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Budget not found"));

        if (budget.isDeleted()) {
            throw new RuntimeException("Budget not found");
        }

        // Renaming is allowed, but must not collide with another live budget.
        if (!budget.getName().equals(request.getName())
                && budgetRepository.existsByUserIdAndNameAndDeletedFalse(
                        budget.getUser().getId(), request.getName())) {

            throw new AppException(
                    "Budget with this name already exists",
                    HttpStatus.CONFLICT
            );
        }

        budget.setName(request.getName());
        budget.setSpending(request.getSpending());
        budget.setAmountLimit(request.getAmountLimit());
        budget.setPeriodMonth(request.getPeriodMonth());

        Budget updatedBudget = budgetRepository.save(budget);

        return convertToResponse(updatedBudget);
    }

    // Soft delete the budget
    public void deleteBudget(Long id) {

        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Budget not found"));

        if (budget.isDeleted()) {
            throw new RuntimeException("Budget already deleted");
        }

        budget.setDeleted(true);

        budgetRepository.save(budget);
    }

    // Convert Budget entity to response DTO
    private BudgetResponseDTO convertToResponse(Budget budget) {

        BudgetResponseDTO response = new BudgetResponseDTO();

        response.setId(budget.getId());
        response.setUserId(budget.getUser().getId());
        response.setName(budget.getName());
        response.setSpending(budget.getSpending());
        response.setAmountLimit(budget.getAmountLimit());
        response.setPeriodMonth(budget.getPeriodMonth());
        response.setCreatedAt(budget.getCreatedAt());
        response.setUpdatedAt(budget.getUpdatedAt());

        return response;
    }
}