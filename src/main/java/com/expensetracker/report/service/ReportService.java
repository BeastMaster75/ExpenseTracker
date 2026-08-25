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

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final Authentication authentication;

    @Transactional
    public ReportResponse createReport(
            CreateReportDto dto,
            String token
    ) {
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


        LocalDateTime startDateTime = month
                .atDay(1)
                .atStartOfDay();

        LocalDateTime endDateTime = month
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay();

        Date from = Date.from(
                startDateTime
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
        );

        Date to = Date.from(
                endDateTime
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
        );

        List<Transaction> transactions =
                transactionRepository
                        .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(
                                userId,
                                from,
                                to
                        );

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
}