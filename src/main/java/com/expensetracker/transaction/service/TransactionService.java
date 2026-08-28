package com.expensetracker.transaction.service;

import com.expensetracker.auth.Authentication;
import com.expensetracker.budget.entity.Budget;
import com.expensetracker.budget.repository.BudgetRepository;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.transaction.dto.CreateTransactionDto;
import com.expensetracker.transaction.dto.UpdateTransactionDto;
import com.expensetracker.transaction.entity.Transaction;
import com.expensetracker.transaction.repository.TransactionRepository;
import com.expensetracker.user.entity.User;
import com.expensetracker.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Locale;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private static final String INCOME = "income";
    private static final String EXPENSE = "expense";

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final Authentication authentication;
    private final UserRepository userRepository;

    public TransactionService(
            TransactionRepository transactionRepository,
            BudgetRepository budgetRepository,
            Authentication authentication,
            UserRepository userRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.authentication = authentication;
        this.userRepository = userRepository;
    }

    private Long getUserId(String token) {
        Claims claims = authentication.auth(token, false);
        return claims.get("id", Long.class);
    }

    private User currentUser(Long userId) {
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new AppException("User not exist", HttpStatus.NOT_FOUND));
    }

    private Transaction ownedTransaction(Long id, Long userId) {

        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new AppException("Transaction not found", HttpStatus.NOT_FOUND));

        if (tx.getUser() == null || !tx.getUser().getId().equals(userId)) {
            throw new AppException("Transaction not found", HttpStatus.NOT_FOUND);
        }

        return tx;
    }

    private String normaliseType(String type) {

        if (INCOME.equalsIgnoreCase(type)) {
            return INCOME;
        }

        if (EXPENSE.equalsIgnoreCase(type)) {
            return EXPENSE;
        }

        throw new AppException("Invalid transaction type", HttpStatus.BAD_REQUEST);
    }

    private Budget resolveBudget(Long userId, String budgetName) {

        if (budgetName == null || budgetName.isBlank()) {
            throw new AppException("Budget must be selected", HttpStatus.BAD_REQUEST);
        }

        return budgetRepository.findByUserIdAndNameAndDeletedFalse(userId, budgetName)
                .orElseThrow(() -> new AppException("Budget not exist", HttpStatus.NOT_FOUND));
    }

    private void apply(Transaction tx, User user) {

        Budget budget = tx.getBudget();

        if (INCOME.equals(tx.getType())) {

            user.setBalance(user.getBalance().add(tx.getAmount()));
            user.setTotalIncome(user.getTotalIncome().add(tx.getAmount()));

            if (budget != null) {
                budget.setAmountLimit(budget.getAmountLimit().add(tx.getAmount()));
                budgetRepository.save(budget);
            }

            return;
        }

        user.setBalance(user.getBalance().subtract(tx.getAmount()));
        user.setTotalExpense(user.getTotalExpense().add(tx.getAmount()));

        if (budget != null) {
            budget.setSpending(budget.getSpending().add(tx.getAmount()));
            budgetRepository.save(budget);
        }
    }

    private void reverse(Transaction tx, User user) {

        Budget budget = tx.getBudget();

        if (INCOME.equals(tx.getType())) {

            user.setBalance(user.getBalance().subtract(tx.getAmount()));
            user.setTotalIncome(user.getTotalIncome().subtract(tx.getAmount()));

            if (budget != null) {
                budget.setAmountLimit(budget.getAmountLimit().subtract(tx.getAmount()));
                budgetRepository.save(budget);
            }

            return;
        }

        user.setBalance(user.getBalance().add(tx.getAmount()));
        user.setTotalExpense(user.getTotalExpense().subtract(tx.getAmount()));

        if (budget != null) {
            budget.setSpending(budget.getSpending().subtract(tx.getAmount()));
            budgetRepository.save(budget);
        }
    }

    private void assertBalanceNotNegative(User user) {

        if (user.getBalance().signum() < 0) {
            throw new AppException("Insufficient balance", HttpStatus.BAD_REQUEST);
        }
    }

    private void assertBudgetLimitNotNegative(Budget budget) {

        if (budget != null && budget.getAmountLimit().signum() < 0) {
            throw new AppException(
                    "Budget limit cannot go negative -- raise the budget limit first",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    @Transactional
    public Transaction createTransaction(CreateTransactionDto dto, String token) {

        Long userId = getUserId(token);

        log.info("Creating transaction for userId: {}", userId);

        User user = currentUser(userId);

        String type = normaliseType(dto.getTransactionType());

        Transaction tx = new Transaction();

        tx.setUser(user);
        tx.setAmount(dto.getAmount());
        tx.setType(type);
        tx.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
        tx.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : new Date());
        tx.setUpdatedAt(new Date());
        tx.setBudget(resolveBudget(userId, dto.getBudgetName()));

        apply(tx, user);

        assertBalanceNotNegative(user);
        assertBudgetLimitNotNegative(tx.getBudget());

        userRepository.save(user);

        log.info("Transaction created successfully - userId: {}, type: {}", userId, type);

        return transactionRepository.save(tx);
    }

    @Transactional
    public Transaction updateTransaction(Long id, UpdateTransactionDto dto, String token) {

        Long userId = getUserId(token);

        log.info("Updating transaction - id: {}, userId: {}", id, userId);

        User user = currentUser(userId);

        Transaction tx = ownedTransaction(id, userId);

        Budget previousBudget = tx.getBudget();

        reverse(tx, user);

        if (dto.getAmount() != null) {
            tx.setAmount(dto.getAmount());
        }

        if (dto.getDescription() != null) {
            tx.setDescription(dto.getDescription());
        }

        if (dto.getCreatedAt() != null) {
            tx.setCreatedAt(dto.getCreatedAt());
        }

        if (dto.getTransactionType() != null) {
            tx.setType(normaliseType(dto.getTransactionType()));
        }

        if (dto.getBudgetName() != null) {
            tx.setBudget(resolveBudget(userId, dto.getBudgetName()));

        } else if (tx.getBudget() == null) {
            throw new AppException("Budget must be selected", HttpStatus.BAD_REQUEST);
        }

        apply(tx, user);

        assertBalanceNotNegative(user);
        assertBudgetLimitNotNegative(previousBudget);
        assertBudgetLimitNotNegative(tx.getBudget());

        tx.setUpdatedAt(new Date());

        userRepository.save(user);

        log.info("Transaction updated successfully - id: {}, userId: {}", id, userId);

        return transactionRepository.save(tx);
    }

    public Transaction getTransactionById(Long id, String token) {

        Long userId = getUserId(token);

        log.info("Getting transaction - id: {}, userId: {}", id, userId);

        return ownedTransaction(id, userId);
    }

    @Transactional
    public void deleteTransaction(Long id, String token) {

        Long userId = getUserId(token);

        log.info("Deleting transaction - id: {}, userId: {}", id, userId);

        User user = currentUser(userId);

        Transaction tx = ownedTransaction(id, userId);

        reverse(tx, user);

        assertBalanceNotNegative(user);
        assertBudgetLimitNotNegative(tx.getBudget());

        userRepository.save(user);

        transactionRepository.delete(tx);

        log.info("Transaction deleted successfully - id: {}, userId: {}", id, userId);
    }

    public Page<Transaction> getTransactions(Long userId, String range, int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Date now = new Date();

        Date from = switch (range.toLowerCase(Locale.ROOT)) {
            case "last_day" -> new Date(now.getTime() - DAY_MS);
            case "last_week" -> new Date(now.getTime() - (7 * DAY_MS));
            case "last_month" -> new Date(now.getTime() - (30 * DAY_MS));
            default -> null;
        };

        if (from == null) {
            return transactionRepository.findByUserId(userId, pageable);
        }

        return transactionRepository.findByUserIdAndCreatedAtBetween(userId, from, now, pageable);
    }
}
