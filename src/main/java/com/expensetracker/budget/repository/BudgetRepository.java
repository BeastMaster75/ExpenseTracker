package com.expensetracker.budget.repository;

import com.expensetracker.budget.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    // Get only budgets that are not soft deleted
    List<Budget> findAllByDeletedFalse();
}