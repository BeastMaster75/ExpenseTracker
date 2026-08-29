package com.expensetracker.transaction.service;

import com.expensetracker.budget.entity.Budget;
import com.expensetracker.common.util.AuthUtils;
import com.expensetracker.transaction.dto.CreateTransactionDto;
import com.expensetracker.transaction.dto.UpdateTransactionDto;
import com.expensetracker.transaction.entity.Transaction;
import com.expensetracker.transaction.repository.TransactionRepository;
import com.expensetracker.transaction.util.TransactionLedger;
import com.expensetracker.transaction.util.TransactionLookup;
import com.expensetracker.transaction.util.TransactionUtils;
import com.expensetracker.user.entity.User;
import com.expensetracker.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuthUtils authUtils;
    private final TransactionLookup lookup;
    private final TransactionLedger ledger;

    public TransactionService(
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            AuthUtils authUtils,
            TransactionLookup lookup,
            TransactionLedger ledger
    ) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.authUtils = authUtils;
        this.lookup = lookup;
        this.ledger = ledger;
    }

    @Transactional
    public Transaction createTransaction(CreateTransactionDto dto, String token) {

        Long userId = authUtils.getUserId(token);

        String type = TransactionUtils.normaliseType(dto.getTransactionType());

        log.info("Creating {} transaction - userId: {}, amount: {}, budget: {}",
                type, userId, dto.getAmount(), dto.getBudgetName());

        User user = authUtils.currentUser(userId);

        Transaction tx = new Transaction();

        tx.setUser(user);
        tx.setAmount(dto.getAmount());
        tx.setType(type);
        tx.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
        tx.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : new Date());
        tx.setUpdatedAt(new Date());
        tx.setBudget(lookup.ownedBudget(userId, dto.getBudgetName()));

        ledger.apply(tx, user);

        TransactionUtils.assertBalanceNotNegative(user);
        TransactionUtils.assertBudgetLimitNotNegative(tx.getBudget());

        userRepository.save(user);

        Transaction saved = transactionRepository.save(tx);

        log.info("Transaction created - id: {}, userId: {}, type: {}, amount: {}",
                saved.getId(), userId, type, saved.getAmount());

        return saved;
    }

    public Transaction getTransactionById(Long id, String token) {

        Long userId = authUtils.getUserId(token);

        log.info("Getting transaction - id: {}, userId: {}", id, userId);

        return lookup.ownedTransaction(id, userId);
    }

    public Page<Transaction> getTransactions(Long userId, String range, int page, int size) {

        log.info("Getting transactions - userId: {}, range: {}, page: {}, size: {}",
                userId, range, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Date now = new Date();

        Date from = TransactionUtils.rangeStart(range, now);

        if (from == null) {
            return transactionRepository.findByUserId(userId, pageable);
        }

        return transactionRepository.findByUserIdAndCreatedAtBetween(userId, from, now, pageable);
    }

    @Transactional
    public Transaction updateTransaction(Long id, UpdateTransactionDto dto, String token) {

        Long userId = authUtils.getUserId(token);

        log.info("Updating transaction - id: {}, userId: {}", id, userId);

        User user = authUtils.currentUser(userId);

        Transaction tx = lookup.ownedTransaction(id, userId);

        // Held so the old budget can be checked too -- moving a transaction
        // away from a budget can leave that budget short.
        Budget previousBudget = tx.getBudget();

        // Unwind the old figures first, against the old budget and old type,
        // before any reassignment below.
        ledger.reverse(tx, user);

        applyChanges(tx, dto, userId);

        ledger.apply(tx, user);

        TransactionUtils.assertBalanceNotNegative(user);
        TransactionUtils.assertBudgetLimitNotNegative(previousBudget);
        TransactionUtils.assertBudgetLimitNotNegative(tx.getBudget());

        tx.setUpdatedAt(new Date());

        userRepository.save(user);

        Transaction updated = transactionRepository.save(tx);

        log.info("Transaction updated - id: {}, userId: {}", id, userId);

        return updated;
    }

    // Only the fields actually sent are touched, and each change is logged.
    private void applyChanges(Transaction tx, UpdateTransactionDto dto, Long userId) {

        Long id = tx.getId();

        if (dto.getAmount() != null && dto.getAmount().compareTo(tx.getAmount()) != 0) {
            log.info("Transaction {} amount: {} -> {}", id, tx.getAmount(), dto.getAmount());
            tx.setAmount(dto.getAmount());
        }

        if (dto.getDescription() != null && !dto.getDescription().equals(tx.getDescription())) {
            log.info("Transaction {} description changed", id);
            tx.setDescription(dto.getDescription());
        }

        if (dto.getCreatedAt() != null && !dto.getCreatedAt().equals(tx.getCreatedAt())) {
            log.info("Transaction {} createdAt: {} -> {}", id, tx.getCreatedAt(), dto.getCreatedAt());
            tx.setCreatedAt(dto.getCreatedAt());
        }

        if (dto.getTransactionType() != null) {

            String type = TransactionUtils.normaliseType(dto.getTransactionType());

            if (!type.equals(tx.getType())) {
                log.info("Transaction {} type: {} -> {}", id, tx.getType(), type);
                tx.setType(type);
            }
        }

        retargetBudget(tx, dto, userId);
    }

    private void retargetBudget(Transaction tx, UpdateTransactionDto dto, Long userId) {

        Long id = tx.getId();

        if (dto.getBudgetName() == null) {

            // Every transaction needs one; a row predating that rule would
            // otherwise slip through an edit still detached.
            TransactionUtils.requireBudgetName(
                    tx.getBudget() != null ? tx.getBudget().getName() : null
            );

            return;
        }

        Budget budget = lookup.ownedBudget(userId, dto.getBudgetName());

        Budget previous = tx.getBudget();

        if (previous == null || !previous.getId().equals(budget.getId())) {
            log.info("Transaction {} budget: {} -> {}", id,
                    previous != null ? previous.getName() : "none", budget.getName());
        }

        tx.setBudget(budget);
    }

    @Transactional
    public void deleteTransaction(Long id, String token) {

        Long userId = authUtils.getUserId(token);

        log.info("Deleting transaction - id: {}, userId: {}", id, userId);

        User user = authUtils.currentUser(userId);

        Transaction tx = lookup.ownedTransaction(id, userId);

        // Removing a transaction has to give back whatever it took.
        ledger.reverse(tx, user);

        TransactionUtils.assertBalanceNotNegative(user);
        TransactionUtils.assertBudgetLimitNotNegative(tx.getBudget());

        userRepository.save(user);

        transactionRepository.delete(tx);

        log.info("Transaction deleted - id: {}, userId: {}, type: {}, amount: {}",
                id, userId, tx.getType(), tx.getAmount());
    }
}
