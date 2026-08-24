package com.expensetracker.budget.controller;

import com.expensetracker.budget.dto.BudgetSummaryDto;
import com.expensetracker.budget.dto.CreateBudgetDto;
import com.expensetracker.budget.dto.UpdateBudgetDto;
import com.expensetracker.budget.entity.Budget;
import com.expensetracker.budget.service.BudgetService;
import com.expensetracker.common.exception.AppException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/budgets")
public class BudgetController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    private String bearerToken(String authorization) {

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AppException("Authorization header is required", HttpStatus.UNAUTHORIZED);
        }

        return authorization.substring(BEARER_PREFIX.length());
    }

    @PostMapping
    public Budget createBudget(
            @Valid @RequestBody CreateBudgetDto dto,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return budgetService.createBudget(dto, bearerToken(authorization));
    }

    // Declared before /{id} for readability -- Spring already prefers the
    // literal path, but keeping them adjacent makes the pairing obvious.
    @GetMapping("/summary")
    public BudgetSummaryDto getSummary(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return budgetService.getSummary(bearerToken(authorization));
    }

    @GetMapping("/{id}")
    public Budget getBudgetById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return budgetService.getBudgetById(id, bearerToken(authorization));
    }

    @GetMapping
    public List<Budget> getBudgets(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return budgetService.getBudgets(bearerToken(authorization));
    }

    @PatchMapping("/{id}")
    public Budget updateBudget(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBudgetDto dto,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return budgetService.updateBudget(id, dto, bearerToken(authorization));
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deleteBudget(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return budgetService.deleteBudget(id, bearerToken(authorization));
    }
}
