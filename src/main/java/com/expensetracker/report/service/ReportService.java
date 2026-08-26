package com.expensetracker.report.service;

import com.expensetracker.auth.Authentication;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.report.dto.CreateReportDto;
import com.expensetracker.report.dto.ReportResponse;
import com.expensetracker.report.entity.Report;
import com.expensetracker.report.repository.ReportRepository;
import com.expensetracker.transaction.entity.Transaction;
import com.expensetracker.transaction.repository.TransactionRepository;
import com.expensetracker.user.entity.User;
import com.expensetracker.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.netty.util.internal.StringUtil.escapeCsv;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final Authentication authentication;

    @Transactional
    public ReportResponse createReport(CreateReportDto dto,String token) {
        Claims claims = authentication.auth(token, false);
        Long userId = claims.get("id", Long.class);

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new AppException(
                        "User not found",
                        HttpStatus.NOT_FOUND
                ));

        YearMonth month = dto.getMonth();

        if (month == null) {
            throw new AppException(
                    "Month is required",
                    HttpStatus.BAD_REQUEST
            );
        }


        LocalDateTime startDateTime = month.atDay(1).atStartOfDay();

        LocalDateTime endDateTime = month.plusMonths(1).atDay(1).atStartOfDay();

        Date from = Date.from(startDateTime.atZone(ZoneId.systemDefault()).toInstant());

        Date to = Date.from(endDateTime.atZone(ZoneId.systemDefault()).toInstant());

        List<Transaction> transactions = transactionRepository.findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(userId, from, to);

        BigDecimal totalIncome = transactions.stream()
                .filter(transaction ->
                        "INCOME".equalsIgnoreCase(transaction.getType())
                )
                .map(Transaction::getAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        BigDecimal totalExpenses = transactions.stream()
                .filter(transaction ->
                        "EXPENSE".equalsIgnoreCase(transaction.getType())
                )
                .map(Transaction::getAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        BigDecimal netIncome =
                totalIncome.subtract(totalExpenses);

        Map<String, BigDecimal> spendingByCategory =
                transactions.stream()
                        .filter(transaction ->
                                "EXPENSE".equalsIgnoreCase(transaction.getType())
                        )
                        .filter(transaction ->
                                transaction.getBudget() != null
                        )
                        .collect(Collectors.groupingBy(
                                transaction ->
                                        transaction.getBudget().getName(),
                                Collectors.reducing(
                                        BigDecimal.ZERO,
                                        Transaction::getAmount,
                                        BigDecimal::add
                                )
                        ));

        String topCategory = null;
        BigDecimal topCategorySpending = BigDecimal.ZERO;

        if (!spendingByCategory.isEmpty()) {

            Map.Entry<String, BigDecimal> topEntry =
                    spendingByCategory.entrySet()
                            .stream()
                            .max(Map.Entry.comparingByValue())
                            .orElse(null);

            if (topEntry != null) {
                topCategory = topEntry.getKey();
                topCategorySpending = topEntry.getValue();
            }
        }

        Report report = reportRepository
                .findByUserIdAndMonth(userId, month)
                .orElseGet(Report::new);

        report.setUser(user);
        report.setMonth(month);
        report.setTotalIncome(totalIncome);
        report.setTotalExpenses(totalExpenses);
        report.setNetIncome(netIncome);
        report.setTopCategory(topCategory);
        report.setTopCategorySpending(topCategorySpending);

        Report savedReport = reportRepository.save(report);

        return ReportResponse.builder()
                .id(savedReport.getId())
                .month(savedReport.getMonth())
                .totalIncome(savedReport.getTotalIncome())
                .totalExpenses(savedReport.getTotalExpenses())
                .netIncome(savedReport.getNetIncome())
                .topCategory(savedReport.getTopCategory())
                .topCategorySpending(savedReport.getTopCategorySpending())
                .categorySpending(spendingByCategory)
                .build();
    }



    public byte[] downloadReport(
            String month,
            String token
    ) {

        // =========================
        // Authenticate user
        // =========================

        Claims claims =
                authentication.auth(
                        token,
                        false
                );

        Long userId =
                claims.get(
                        "id",
                        Long.class
                );

        // =========================
        // Validate month
        // =========================

        YearMonth yearMonth;

        try {

            yearMonth = YearMonth.parse(month);

        } catch (Exception e) {
            throw new AppException("Invalid month format. Use yyyy-MM", HttpStatus.BAD_REQUEST);
        }

        // =========================
        // Find user
        // =========================

        userRepository
                .findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() ->
                        new AppException(
                                "User not found",
                                HttpStatus.NOT_FOUND
                        )
                );

        // =========================
        // Month range
        // =========================

        LocalDateTime startDateTime =
                yearMonth.atDay(1).atStartOfDay();

        LocalDateTime endDateTime =
                yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        Date from =
                Date.from(startDateTime.atZone(ZoneId.systemDefault()).toInstant());

        Date to =
                Date.from(endDateTime.atZone(ZoneId.systemDefault()).toInstant());

        // =========================
        // Get transactions
        // =========================

        List<Transaction> transactions =
                transactionRepository.findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(userId, from, to);

        // =========================
        // Calculate totals
        // =========================

        BigDecimal totalIncome =
                transactions.stream()
                        .filter(transaction -> "INCOME".equalsIgnoreCase(transaction.getType()))
                        .map(Transaction::getAmount)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal totalExpenses =
                transactions.stream()
                        .filter(transaction ->
                                "EXPENSE".equalsIgnoreCase(
                                        transaction.getType()
                                )
                        )
                        .map(Transaction::getAmount)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal netIncome =
                totalIncome.subtract(totalExpenses);

        // =========================
        // Spending by category
        // =========================

        Map<String, BigDecimal> spendingByCategory =
                transactions.stream()
                        .filter(transaction ->
                                "EXPENSE".equalsIgnoreCase(
                                        transaction.getType()
                                )
                        )
                        .filter(transaction ->
                                transaction.getBudget() != null
                        )
                        .collect(
                                Collectors.groupingBy(
                                        transaction ->
                                                transaction
                                                        .getBudget()
                                                        .getName(),

                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                Transaction::getAmount,
                                                BigDecimal::add
                                        )
                                )
                        );

        // =========================
        // Top category
        // =========================

        String topCategory = null;

        BigDecimal topCategorySpending =
                BigDecimal.ZERO;

        if (!spendingByCategory.isEmpty()) {

            Map.Entry<String, BigDecimal> topEntry =
                    spendingByCategory.entrySet()
                            .stream()
                            .max(
                                    Map.Entry.comparingByValue()
                            )
                            .orElse(null);

            if (topEntry != null) {

                topCategory =
                        topEntry.getKey();

                topCategorySpending =
                        topEntry.getValue();
            }
        }

        // =========================
        // Build CSV
        // =========================

        StringBuilder csv =
                new StringBuilder();

        // =========================
        // Report Summary
        // =========================

        csv.append("Report Summary\n");

        csv.append("Month,")
                .append(yearMonth)
                .append("\n");

        csv.append("Total Income,")
                .append(totalIncome)
                .append("\n");

        csv.append("Total Expenses,")
                .append(totalExpenses)
                .append("\n");

        csv.append("Net Income,")
                .append(netIncome)
                .append("\n");

        csv.append("Top Category,")
                .append(
                        topCategory != null
                                ? escapeCsv(topCategory)
                                : ""
                )
                .append("\n");

        csv.append("Top Category Spending,")
                .append(topCategorySpending)
                .append("\n");

        csv.append("\n");

        // =========================
        // Category Spending
        // =========================

        csv.append("Category Spending\n");

        csv.append(
                "Category,Amount\n"
        );

        for (
                Map.Entry<String, BigDecimal> entry
                : spendingByCategory.entrySet()
        ) {

            csv.append(
                            escapeCsv(entry.getKey())
                    )
                    .append(",");

            csv.append(
                            entry.getValue()
                    )
                    .append("\n");
        }

        csv.append("\n");

        // =========================
        // Transactions
        // =========================

        csv.append("Transactions\n");

        csv.append(
                "Type,Amount,Budget,Created At\n"
        );

        for (Transaction transaction : transactions) {

            String type =
                    transaction.getType() != null
                            ? transaction
                            .getType()
                            .toString()
                            : "";

            String amount =
                    transaction.getAmount() != null
                            ? transaction
                            .getAmount()
                            .toString()
                            : "0";

            String budget =
                    transaction.getBudget() != null
                            ? transaction
                            .getBudget()
                            .getName()
                            : "";

            String createdAt =
                    transaction.getCreatedAt() != null
                            ? transaction
                            .getCreatedAt()
                            .toString()
                            : "";

            csv.append(
                    escapeCsv(type)
            ).append(",");

            csv.append(
                    escapeCsv(amount)
            ).append(",");

            csv.append(
                    escapeCsv(budget)
            ).append(",");

            csv.append(
                    escapeCsv(createdAt)
            ).append("\n");
        }

        // =========================
        // UTF-8 BOM
        // =========================

        String content =
                "\uFEFF" + csv;

        return content.getBytes(
                java.nio.charset.StandardCharsets.UTF_8
        );
    }



}