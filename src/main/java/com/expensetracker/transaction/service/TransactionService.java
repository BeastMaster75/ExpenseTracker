package com.expensetracker.transaction.service;

import com.expensetracker.auth.Authentication;
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

import java.util.Date;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);


    private final TransactionRepository transactionRepository;
    private final Authentication authentication;
    private final UserRepository userRepository;


    public TransactionService(TransactionRepository transactionRepository, Authentication authentication, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.authentication = authentication;
        this.userRepository = userRepository;
    }

    public Transaction createTransaction(CreateTransactionDto createTransactionDto , String token) {
        Claims claims = authentication.auth(token, false);
        Long userId = claims.get("id", Long.class);

        log.info("Creating transaction for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Transaction tx = new Transaction();
        tx.setUser(user);
        tx.setAmount(createTransactionDto.getAmount());
        tx.setType(createTransactionDto.getTransactionType() != null ? createTransactionDto.getTransactionType() : "income");
        tx.setDescription(createTransactionDto.getDescription() != null ? createTransactionDto.getDescription() : "");
        tx.setCreatedAt(new Date());
        tx.setUpdatedAt(new Date());

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
        return transactionRepository.save(tx);    }

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
}
