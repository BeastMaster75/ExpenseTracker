package com.expensetracker.budget.controller;

import com.expensetracker.budget.dto.BudgetRequestDTO;
import com.expensetracker.budget.dto.BudgetResponseDTO;
import com.expensetracker.budget.service.BudgetService;
import com.expensetracker.common.exception.AppException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/budgets")
public class BudgetController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    private String bearerToken(String authorization) {

        if (authorization == null ||
                !authorization.startsWith(BEARER_PREFIX)) {

            throw new AppException(
                    "Authorization header is required",
                    HttpStatus.UNAUTHORIZED
            );
        }

        return authorization.substring(BEARER_PREFIX.length());
    }

    @PostMapping
    public ResponseEntity<BudgetResponseDTO> createBudget(
            @Valid @RequestBody BudgetRequestDTO request
//            @RequestHeader("Authorization") String authorization
    ) {

//        String token = bearerToken(authorization);

        return ResponseEntity.ok(
                budgetService.createBudget(request)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<BudgetResponseDTO> getBudgetById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authorization
    ) {

        String token = bearerToken(authorization);

        return ResponseEntity.ok(
                budgetService.getBudgetById(id, token)
        );
    }

    @GetMapping
    public ResponseEntity<List<BudgetResponseDTO>> getAllBudgets(
            @RequestHeader("Authorization") String authorization
    ) {

        String token = bearerToken(authorization);

        return ResponseEntity.ok(
                budgetService.getAllBudgets(token)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<BudgetResponseDTO> updateBudget(
            @PathVariable Long id,
            @Valid @RequestBody BudgetRequestDTO request,
            @RequestHeader("Authorization") String authorization
    ) {

        String token = bearerToken(authorization);

        return ResponseEntity.ok(
                budgetService.updateBudget(id, request, token)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authorization
    ) {

        String token = bearerToken(authorization);

        budgetService.deleteBudget(id, token);

        return ResponseEntity.noContent().build();
    }
}