package com.expensetracker.budget.repository;

import com.expensetracker.budget.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findAllByUserIdAndDeletedFalse(Long userId);

    Optional<Budget> findByIdAndUserIdAndDeletedFalse(
            Long id,
            Long userId
    );
}