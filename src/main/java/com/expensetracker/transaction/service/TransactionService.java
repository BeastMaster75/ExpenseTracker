package com.expensetracker.transaction.service;

import com.expensetracker.auth.Authentication;
import com.expensetracker.budget.entity.Budget;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.transaction.dto.CreateTransactionDto;
import com.expensetracker.transaction.dto.UpdateTransactionDto;
import com.expensetracker.transaction.entity.Transaction;
import com.expensetracker.transaction.repository.TransactionRepository;
import com.expensetracker.user.entity.User;
import com.expensetracker.user.repository.UserRepository;
import com.expensetracker.user.service.UserService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import com.expensetracker.common.email.ConfirmEmailTemplate;
import java.util.Optional;
import java.util.Date;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort;
import com.expensetracker.budget.repository.BudgetRepository;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final Authentication authentication;
    private final UserRepository userRepository;

    public TransactionService(TransactionRepository transactionRepository, BudgetRepository budgetRepository, Authentication authentication, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.authentication = authentication;
        this.userRepository = userRepository;
    }

    @Transactional
    public Transaction createTransaction(CreateTransactionDto createTransactionDto, String token) {
        Claims claims = authentication.auth(token, false);
        Long userId = claims.get("id", Long.class);

        log.info("Creating transaction for userId: {}", userId);

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new AppException(
                        "User not exist",
                        HttpStatus.NOT_FOUND
                ));

        Transaction tx = new Transaction();

        tx.setUser(user);

        tx.setAmount(createTransactionDto.getAmount());

        tx.setType(createTransactionDto.getTransactionType() != null ? createTransactionDto.getTransactionType() : "income");

        tx.setDescription(createTransactionDto.getDescription() != null ? createTransactionDto.getDescription() : "");

        tx.setCreatedAt(new Date());
        tx.setUpdatedAt(new Date());

        if ("income".equalsIgnoreCase(tx.getType())) {

            user.setBalance(user.getBalance().add(tx.getAmount()));

            user.setTotalIncome(user.getTotalIncome().add(tx.getAmount()));

        } else if ("expense".equalsIgnoreCase(tx.getType())) {

            String budgetName = createTransactionDto.getBudgetName();

            if (budgetName == null || budgetName.isBlank()) {
                throw new AppException(
                        "Budget must be selected for an expense",
                        HttpStatus.BAD_REQUEST
                );
            }

            // Scoped to the caller so one user cannot spend against another's budget.
            Budget budget = budgetRepository
                    .findByUserIdAndNameAndDeletedFalse(userId, budgetName)
                    .orElseThrow(() -> new AppException(
                            "Budget not exist",
                            HttpStatus.NOT_FOUND
                    ));

            user.setBalance(user.getBalance().subtract(tx.getAmount()));

            user.setTotalExpense(user.getTotalExpense().add(tx.getAmount()));

            budget.setSpending(budget.getSpending().add(tx.getAmount()));

            budgetRepository.save(budget);

            tx.setBudget(budget);

        } else {

            throw new AppException(
                    "Invalid transaction type",
                    HttpStatus.BAD_REQUEST
            );
        }

        userRepository.save(user);

        return transactionRepository.save(tx);
    }

    public Transaction updateTransaction(Long id, UpdateTransactionDto updateTransaction, String token) {
        Claims claims = authentication.auth(token, false);
        Long userId = claims.get("id", Long.class);

        log.info("Updating transaction for userId: {}", userId);

        Transaction tx = transactionRepository.findTransactionById(id);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        if (updateTransaction.getAmount() != null) {
            tx.setAmount(updateTransaction.getAmount());
        }

        if (updateTransaction.getTransactionType() != null) {
            tx.setType(updateTransaction.getTransactionType());
        }

        if (updateTransaction.getDescription() != null) {
            tx.setDescription(updateTransaction.getDescription());
        }

        if (updateTransaction.getCreatedAt() != null) {
            tx.setCreatedAt(updateTransaction.getCreatedAt());
        }

        tx.setUpdatedAt(new Date());
        return transactionRepository.save(tx);
    }

    public Transaction getTransactionById(Long id, String token) {
        Claims claims = authentication.auth(token, false);
        Long userId = claims.get("id", Long.class);

        log.info("Getting transaction for userId: {}", userId);
        Transaction tx = transactionRepository.findTransactionById(id);
        if (!tx.getUser().getId().equals(userId)) {
            throw new AppException("Unauthorized", HttpStatus.FORBIDDEN);
        }
        return tx;

    }

    public void deleteTransaction(Long id, String token) {
        Claims claims = authentication.auth(token, false);
        Long userId = claims.get("id", Long.class);

        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new AppException("Transaction not found", HttpStatus.NOT_FOUND));

        if (!tx.getUser().getId().equals(userId)) {
            throw new AppException("Unauthorized", HttpStatus.FORBIDDEN);
        }

        transactionRepository.delete(tx);
    }

    public Page<Transaction> getTransactions(
            Long userId,
            String range,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Date now = new Date();

        Date from = null;

        if (range.equalsIgnoreCase("last_day")) {
            from = new Date(now.getTime() - (24L * 60 * 60 * 1000));
        } else if (range.equalsIgnoreCase("last_week")) {
            from = new Date(now.getTime() - (7L * 24 * 60 * 60 * 1000));
        } else if (range.equalsIgnoreCase("last_month")) {
            from = new Date(now.getTime() - (30L * 24 * 60 * 60 * 1000));
        }

        if (from == null) {
            return transactionRepository.findByUserId(userId, pageable);
        }

        return transactionRepository
                .findByUserIdAndCreatedAtBetween(
                        userId,
                        from,
                        now,
                        pageable
                );
    }
}
