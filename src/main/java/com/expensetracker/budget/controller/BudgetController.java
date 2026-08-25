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

    private final BudgetService budgetService;

    public BudgetController(
            BudgetService budgetService
    ) {
        this.budgetService = budgetService;
    }

    // =========================================================
    // Get Access Token from Cookie
    // =========================================================

    private String getAccessToken(String accessToken) {

        if (accessToken == null || accessToken.isBlank()) {

            throw new AppException(
                    "Access token is required",
                    HttpStatus.UNAUTHORIZED
            );
        }

        return accessToken;
    }

    // =========================================================
    // Create Budget
    // =========================================================

    @PostMapping
    public Budget createBudget(

            @Valid
            @RequestBody
            CreateBudgetDto dto,

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        return budgetService.createBudget(
                dto,
                getAccessToken(accessToken)
        );
    }

    // =========================================================
    // Budget Summary
    // =========================================================

    @GetMapping("/summary")
    public BudgetSummaryDto getSummary(

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        return budgetService.getSummary(
                getAccessToken(accessToken)
        );
    }

    // =========================================================
    // Get Budget By ID
    // =========================================================

    @GetMapping("/{id}")
    public Budget getBudgetById(

            @PathVariable
            Long id,

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        return budgetService.getBudgetById(
                id,
                getAccessToken(accessToken)
        );
    }

    // =========================================================
    // Get All Budgets
    // =========================================================

    @GetMapping
    public List<Budget> getBudgets(

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        return budgetService.getBudgets(
                getAccessToken(accessToken)
        );
    }

    // =========================================================
    // Update Budget
    // =========================================================

    @PatchMapping("/{id}")
    public Budget updateBudget(

            @PathVariable
            Long id,

            @Valid
            @RequestBody
            UpdateBudgetDto dto,

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        return budgetService.updateBudget(
                id,
                dto,
                getAccessToken(accessToken)
        );
    }

    // =========================================================
    // Delete Budget
    // =========================================================

    @DeleteMapping("/{id}")
    public Map<String, String> deleteBudget(

            @PathVariable
            Long id,

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        return budgetService.deleteBudget(
                id,
                getAccessToken(accessToken)
        );
    }
}