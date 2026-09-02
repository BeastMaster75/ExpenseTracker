package com.expensetracker.transaction.util;

import com.expensetracker.budget.entity.Budget;
import com.expensetracker.budget.repository.BudgetRepository;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.transaction.entity.Transaction;
import com.expensetracker.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransactionLedger {

    private static final Logger log = LoggerFactory.getLogger(TransactionLedger.class);

    private final BudgetRepository budgetRepository;

    public TransactionLedger(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    public void apply(Transaction tx, User user) {

        BigDecimal before = user.getBalance();

        if (TransactionUtils.isIncome(tx.getType())) {

            user.setBalance(before.add(tx.getAmount()));
            user.setTotalIncome(user.getTotalIncome().add(tx.getAmount()));

            log.info("Applied income {} - userId: {}, balance: {} -> {}, totalIncome: {}",
                    tx.getAmount(), user.getId(), before, user.getBalance(), user.getTotalIncome());

            // Income funds the budget it was booked against. It raises that
            // budget's available-to-use, never its configured limit, so the
            // top-up expires with the monthly reset.
            addToAvailable(tx.getBudget(), tx.getAmount());

            return;
        }

        user.setBalance(before.subtract(tx.getAmount()));
        user.setTotalExpense(user.getTotalExpense().add(tx.getAmount()));

        log.info("Applied expense {} - userId: {}, balance: {} -> {}, totalExpense: {}",
                tx.getAmount(), user.getId(), before, user.getBalance(), user.getTotalExpense());

        addToSpending(tx.getBudget(), tx.getAmount() , tx);


    }

    public void reverse(Transaction tx, User user) {

        BigDecimal before = user.getBalance();

        if (TransactionUtils.isIncome(tx.getType())) {

            user.setBalance(before.subtract(tx.getAmount()));
            user.setTotalIncome(user.getTotalIncome().subtract(tx.getAmount()));

            log.info("Reversed income {} - userId: {}, balance: {} -> {}, totalIncome: {}",
                    tx.getAmount(), user.getId(), before, user.getBalance(), user.getTotalIncome());

            addToAvailable(tx.getBudget(), tx.getAmount().negate());

            return;
        }

        user.setBalance(before.add(tx.getAmount()));
        user.setTotalExpense(user.getTotalExpense().subtract(tx.getAmount()));

        log.info("Reversed expense {} - userId: {}, balance: {} -> {}, totalExpense: {}",
                tx.getAmount(), user.getId(), before, user.getBalance(), user.getTotalExpense());

        addToSpending(tx.getBudget(), tx.getAmount().negate() , tx);
    }

    private void addToAvailable(Budget budget, BigDecimal delta) {

        if (budget == null) {
            return;
        }

        BigDecimal before = budget.getAvailableToUse();

        budget.setAvailableToUse(before.add(delta));

        budgetRepository.save(budget);

        log.info("Budget available-to-use changed - id: {}, name: {}, {} -> {} (delta {})",
                budget.getId(), budget.getName(), before, budget.getAvailableToUse(), delta);
    }

    private void addToSpending(Budget budget, BigDecimal delta , Transaction tx) {

        if (budget == null) {
            return;
        }
        if (budget.getSpending()
                .add(tx.getAmount())
                .compareTo(budget.getAmountLimit()) > 0) {
       throw new AppException("amount limit exceeded " , HttpStatus.BAD_REQUEST);
        }

        BigDecimal before = budget.getSpending();

        budget.setSpending(before.add(delta));

        budgetRepository.save(budget);

        log.info("Budget spending changed - id: {}, name: {}, {} -> {} (delta {})",
                budget.getId(), budget.getName(), before, budget.getSpending(), delta);
    }
}
