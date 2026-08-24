package com.expensetracker.budget.controller;


import com.expensetracker.budget.dto.BudgetRequestDTO;
import com.expensetracker.budget.dto.BudgetResponseDTO;
import com.expensetracker.budget.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController


@RequestMapping("/budgets")
public class BudgetController {

    private final BudgetService budgetService;



    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }


    // =========================================================
    // 1) CREATE BUDGET

    @PostMapping
    public ResponseEntity<BudgetResponseDTO> createBudget(
            @Valid @RequestBody BudgetRequestDTO request) {


        BudgetResponseDTO response =
                budgetService.createBudget(request);


        return ResponseEntity.ok(response);
    }


    // =========================================================
    // 2) GET BUDGET BY ID

    @GetMapping("/{id}")
    public ResponseEntity<BudgetResponseDTO> getBudgetById(
            @PathVariable Long id) {


        BudgetResponseDTO response =
                budgetService.getBudgetById(id);

        return ResponseEntity.ok(response);
    }


    // =========================================================
    // 3) GET ALL BUDGETS

    @GetMapping
    public ResponseEntity<List<BudgetResponseDTO>> getAllBudgets() {

        List<BudgetResponseDTO> response =
                budgetService.getAllBudgets();

        return ResponseEntity.ok(response);
    }


    // =========================================================
    // 4) UPDATE BUDGET
    @PutMapping("/{id}")
    public ResponseEntity<BudgetResponseDTO> updateBudget(
            @PathVariable Long id,
            @Valid @RequestBody BudgetRequestDTO request) {


        BudgetResponseDTO response =
                budgetService.updateBudget(id, request);

        return ResponseEntity.ok(response);
    }


    // =========================================================
    // 5) DELETE BUDGET

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(
            @PathVariable Long id) {

        budgetService.deleteBudget(id);


        return ResponseEntity.noContent().build();
    }
}