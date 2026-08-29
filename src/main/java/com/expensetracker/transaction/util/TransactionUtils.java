package com.expensetracker.transaction.util;

import com.expensetracker.budget.entity.Budget;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.common.util.MoneyUtils;
import com.expensetracker.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.Date;

public final class TransactionUtils {

    private static final Logger log = LoggerFactory.getLogger(TransactionUtils.class);

    public static final String INCOME = "income";
    public static final String EXPENSE = "expense";

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private TransactionUtils() {
    }

    public static String normaliseType(String type) {

        if (INCOME.equalsIgnoreCase(type)) {
            return INCOME;
        }

        if (EXPENSE.equalsIgnoreCase(type)) {
            return EXPENSE;
        }

        throw new AppException("Invalid transaction type", HttpStatus.BAD_REQUEST);
    }

    public static boolean isIncome(String type) {
        return INCOME.equals(type);
    }

    public static boolean isExpense(String type) {
        return EXPENSE.equals(type);
    }

    public static String requireBudgetName(String budgetName) {

        if (budgetName == null || budgetName.isBlank()) {
            throw new AppException("Budget must be selected", HttpStatus.BAD_REQUEST);
        }

        return budgetName;
    }

    public static void assertBalanceNotNegative(User user) {

        if (MoneyUtils.isNegative(user.getBalance())) {

            log.warn("Rejected - balance would go negative - userId: {}, balance: {}",
                    user.getId(), user.getBalance());

            throw new AppException("Insufficient balance", HttpStatus.BAD_REQUEST);
        }
    }

    public static void assertBudgetLimitNotNegative(Budget budget) {

        if (budget != null && MoneyUtils.isNegative(budget.getAmountLimit())) {

            log.warn("Rejected - budget limit would go negative - id: {}, name: {}, limit: {}",
                    budget.getId(), budget.getName(), budget.getAmountLimit());

            throw new AppException(
                    "Budget limit cannot go negative -- raise the budget limit first",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    public static Date rangeStart(String range, Date now) {

        if (range == null) {
            return null;
        }

        if (range.equalsIgnoreCase("last_day")) {
            return new Date(now.getTime() - DAY_MS);
        }

        if (range.equalsIgnoreCase("last_week")) {
            return new Date(now.getTime() - (7 * DAY_MS));
        }

        if (range.equalsIgnoreCase("last_month")) {
            return new Date(now.getTime() - (30 * DAY_MS));
        }

        return null;
    }
}
