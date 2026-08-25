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

    // =========================================================
    // Get Access Token from Cookie
    // =========================================================

    private String getAccessToken(String accessToken) {

        if (accessToken == null || accessToken.isBlank()) {

            throw new AppException(
                    "Access token is required",
                    HttpStatus.UNAUTHORIZED
            );
        }

        return accessToken;
    }

    // =========================================================
    // Get User ID from Access Token
    // =========================================================

    private Long getUserId(String token) {

        Claims claims =
                authentication.auth(
                        token,
                        false
                );

        return claims.get(
                "id",
                Long.class
        );
    }

    // =========================================================
    // Create Transaction
    // =========================================================

    @PostMapping
    public Transaction createTransaction(

            @Valid
            @RequestBody
            CreateTransactionDto transaction,

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        String token =
                getAccessToken(accessToken);

        return transactionService.createTransaction(
                transaction,
                token
        );
    }

    // =========================================================
    // Update Transaction
    // =========================================================

    @PatchMapping("/{id}")
    public Transaction updateTransaction(

            @PathVariable
            Long id,

            @Valid
            @RequestBody
            UpdateTransactionDto dto,

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        String token =
                getAccessToken(accessToken);

        return transactionService.updateTransaction(
                id,
                dto,
                token
        );
    }

    // =========================================================
    // Get Transaction By ID
    // =========================================================

    @GetMapping("/{id}")
    public Transaction getTransactionById(

            @PathVariable
            Long id,

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        String token =
                getAccessToken(accessToken);

        return transactionService.getTransactionById(
                id,
                token
        );
    }

    // =========================================================
    // Delete Transaction
    // =========================================================

    @DeleteMapping("/{id}")
    public void deleteTransaction(

            @PathVariable
            Long id,

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        String token =
                getAccessToken(accessToken);

        transactionService.deleteTransaction(
                id,
                token
        );
    }

    // =========================================================
    // Get Transactions
    // =========================================================

    @GetMapping
    public Page<Transaction> getTransactions(

            @RequestParam(
                    defaultValue = "last_month"
            )
            String range,

            @RequestParam(
                    defaultValue = "0"
            )
            int page,

            @RequestParam(
                    defaultValue = "10"
            )
            int size,

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        String token =
                getAccessToken(accessToken);

        Long userId =
                getUserId(token);

        return transactionService.getTransactions(
                userId,
                range,
                page,
                size
        );
    }
}