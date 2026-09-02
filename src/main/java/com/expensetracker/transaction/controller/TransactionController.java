package com.expensetracker.transaction.controller;

import com.expensetracker.auth.Authentication;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.transaction.dto.CreateTransactionDto;
import com.expensetracker.transaction.dto.UpdateTransactionDto;
import com.expensetracker.transaction.entity.Transaction;
import com.expensetracker.transaction.service.TransactionService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final Authentication authentication;

    public TransactionController(
            TransactionService transactionService,
            Authentication authentication
    ) {
        this.transactionService = transactionService;
        this.authentication = authentication;
    }

    private String getAccessToken(String accessToken) {

        if (accessToken == null || accessToken.isBlank()) {
            throw new AppException("Access token is required", HttpStatus.UNAUTHORIZED);
        }

        return accessToken;
    }

    private Long getUserId(String token) {
        Claims claims = authentication.auth(token, false);
        return claims.get("id", Long.class);
    }

    @PostMapping
    public Transaction createTransaction(
            @Valid @RequestBody CreateTransactionDto transaction,
            @CookieValue(value = "accessToken", required = false) String accessToken
    ) {
        return transactionService.createTransaction(transaction, getAccessToken(accessToken));
    }

    @PatchMapping("/{id}")
    public Transaction updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTransactionDto dto,
            @CookieValue(value = "accessToken", required = false) String accessToken
    ) {
        return transactionService.updateTransaction(id, dto, getAccessToken(accessToken));
    }

    @GetMapping("/{id}")
    public Transaction getTransactionById(
            @PathVariable Long id,
            @CookieValue(value = "accessToken", required = false) String accessToken
    ) {
        return transactionService.getTransactionById(id, getAccessToken(accessToken));
    }

    @DeleteMapping("/{id}")
    public void deleteTransaction(
            @PathVariable Long id,
            @CookieValue(value = "accessToken", required = false) String accessToken
    ) {
        transactionService.deleteTransaction(id, getAccessToken(accessToken));
    }

    @GetMapping
    public Page<Transaction> getTransactions(
            @RequestParam(defaultValue = "last_month") String range,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CookieValue(value = "accessToken", required = false) String accessToken
    ) {
        String token = getAccessToken(accessToken);

        return transactionService.getTransactions(getUserId(token), range, page, size);
    }
}
