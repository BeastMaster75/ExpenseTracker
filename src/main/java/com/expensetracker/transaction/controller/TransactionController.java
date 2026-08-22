package com.expensetracker.transaction.controller;

import com.expensetracker.auth.Authentication;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.transaction.dto.CreateTransactionDto;
import com.expensetracker.transaction.dto.UpdateTransactionDto;
import com.expensetracker.transaction.entity.Transaction;
import com.expensetracker.transaction.service.TransactionService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService, Authentication authentication) {
        this.transactionService = transactionService;
    }

    private String bearerToken(String authorization) {

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AppException(
                    "Authorization header is required",
                    HttpStatus.UNAUTHORIZED
            );
        }

        return authorization.substring(BEARER_PREFIX.length());
    }

    @PostMapping
    public Transaction createTransaction(
            @RequestBody CreateTransactionDto transaction,
            @RequestHeader("Authorization") String authorization
    ) {
        String token = bearerToken(authorization);
        return transactionService.createTransaction(transaction, token);
    }

    @PatchMapping("/{id}")
    public Transaction updateTransaction(
            @PathVariable Long id,
            @RequestBody UpdateTransactionDto dto,
            @RequestHeader("Authorization") String authorization
    ) {
        String token = bearerToken(authorization);
        return transactionService.updateTransaction(id, dto, token);
    }

    @GetMapping("/{id}")
    public  Transaction getTransactionById(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id
    ) {
        String token = bearerToken(authorization);
        return transactionService.getTransactionById(id,token);
    }

    @DeleteMapping("/{id}")
    public void deleteTransaction(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authorization
    ) {
        String token = bearerToken(authorization);
        transactionService.deleteTransaction(id, token);
    }
}
