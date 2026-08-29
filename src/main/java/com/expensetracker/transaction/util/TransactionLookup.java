package com.expensetracker.transaction.util;

import com.expensetracker.budget.entity.Budget;
import com.expensetracker.budget.repository.BudgetRepository;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.transaction.entity.Transaction;
import com.expensetracker.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TransactionLookup {

    private static final Logger log = LoggerFactory.getLogger(TransactionLookup.class);

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;

    public TransactionLookup(
            TransactionRepository transactionRepository,
            BudgetRepository budgetRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
    }

    public Transaction ownedTransaction(Long id, Long userId) {

        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Transaction not found - id: {}, userId: {}", id, userId);
                    return new AppException("Transaction not found", HttpStatus.NOT_FOUND);
                });

        if (tx.getUser() == null || !tx.getUser().getId().equals(userId)) {

            log.warn("Transaction access denied - id: {}, userId: {}", id, userId);

            throw new AppException("Transaction not found", HttpStatus.NOT_FOUND);
        }

        return tx;
    }

    public Budget ownedBudget(Long userId, String budgetName) {

        String name = TransactionUtils.requireBudgetName(budgetName);

        return budgetRepository.findByUserIdAndNameAndDeletedFalse(userId, name)
                .orElseThrow(() -> {
                    log.warn("Budget not found - userId: {}, name: {}", userId, name);
                    return new AppException("Budget not exist", HttpStatus.NOT_FOUND);
                });
    }
}
