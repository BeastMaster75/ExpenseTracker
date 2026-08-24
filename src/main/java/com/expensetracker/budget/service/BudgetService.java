package com.expensetracker.budget.service;

import com.expensetracker.auth.Authentication;
import com.expensetracker.budget.dto.BudgetRequestDTO;
import com.expensetracker.budget.dto.BudgetResponseDTO;
import com.expensetracker.budget.entity.Budget;
import com.expensetracker.budget.repository.BudgetRepository;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.user.entity.User;
import com.expensetracker.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class BudgetService {

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

    public BudgetResponseDTO createBudget(BudgetRequestDTO request) {

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        Budget budget = new Budget();

        budget.setUser(user);
        budget.setBudgetName(request.getBudgetName());
        budget.setSpending(
                request.getSpending() != null
                        ? request.getSpending()
                        : BigDecimal.ZERO
        );
        budget.setAmountLimit(request.getAmountLimit());

        // Automatically set the budget month
        budget.setPeriodMonth(
                LocalDate.now().withDayOfMonth(1)
        );

        budget.setDeleted(false);

        Budget savedBudget = budgetRepository.save(budget);

        return convertToResponse(savedBudget);
    }

    public BudgetResponseDTO getBudgetById(
            Long id,
            String token
    ) {

        Long userId = getUserId(token);

        Budget budget = budgetRepository
                .findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() ->
                        new AppException(
                                "Budget not found",
                                HttpStatus.NOT_FOUND
                        )
                );

        return convertToResponse(budget);
    }

    public List<BudgetResponseDTO> getAllBudgets(
            String token
    ) {

        Long userId = getUserId(token);

        return budgetRepository
                .findAllByUserIdAndDeletedFalse(userId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    public BudgetResponseDTO updateBudget(
            Long id,
            BudgetRequestDTO request,
            String token
    ) {

        Long userId = getUserId(token);

        Budget budget = budgetRepository
                .findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() ->
                        new AppException(
                                "Budget not found",
                                HttpStatus.NOT_FOUND
                        )
                );

        budget.setBudgetName(request.getBudgetName());
        budget.setAmountLimit(request.getAmountLimit());
       // budget.setPeriodMonth(request.getPeriodMonth());

        if (request.getSpending() != null) {
            budget.setSpending(request.getSpending());
        }

        Budget updatedBudget = budgetRepository.save(budget);

        return convertToResponse(updatedBudget);
    }

    public void deleteBudget(
            Long id,
            String token
    ) {

        Long userId = getUserId(token);

        Budget budget = budgetRepository
                .findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() ->
                        new AppException(
                                "Budget not found",
                                HttpStatus.NOT_FOUND
                        )
                );

        budget.setDeleted(true);

        budgetRepository.save(budget);
    }

    private Long getUserId(String token) {

        Claims claims = authentication.auth(token, false);

        return claims.get("id", Long.class);
    }

    private BudgetResponseDTO convertToResponse(Budget budget) {

        BudgetResponseDTO response = new BudgetResponseDTO();

        response.setId(budget.getId());
        response.setUserId(budget.getUser().getId());
        response.setBudgetName(budget.getBudgetName());
        response.setSpending(budget.getSpending());
        response.setAmountLimit(budget.getAmountLimit());
        response.setPeriodMonth(budget.getPeriodMonth());
        response.setCreatedAt(budget.getCreatedAt());
        response.setUpdatedAt(budget.getUpdatedAt());

        return response;
    }
}