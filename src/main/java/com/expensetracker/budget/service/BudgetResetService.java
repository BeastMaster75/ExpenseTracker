package com.expensetracker.budget.service;

import com.expensetracker.budget.repository.BudgetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class BudgetResetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetResetService.class);

    private final BudgetRepository budgetRepository;

    public BudgetResetService(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    // Midnight on the 1st of every month.
    @Scheduled(cron = "${app.budget.reset-cron:0 0 0 1 * *}", zone = "${app.timezone:Africa/Cairo}")
    @Transactional
    public void resetOnSchedule() {
        resetMonthlyFigures();
    }

    // The scheduled run is missed entirely if the app is down at midnight on the
    // 1st, so catch up on startup. resetMonthlyFigures is idempotent within a
    // month. Failing here must not stop the app from booting.
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void resetOnStartup() {
        try {
            resetMonthlyFigures();
        } catch (Exception e) {
            log.error("Budget monthly reset on startup failed", e);
        }
    }

    // Deliberately not @Transactional and not public: the entry points above
    // carry the transaction. Calling this from inside the class would bypass
    // the Spring proxy, leaving the @Modifying query without a transaction.
    void resetMonthlyFigures() {

        LocalDate periodMonth = LocalDate.now().withDayOfMonth(1);

        int reset = budgetRepository.resetForPeriod(
                BigDecimal.ZERO,
                periodMonth
        );

        if (reset > 0) {
            log.info("Budget spending and available-to-use reset for {} - budgets affected: {}",
                    periodMonth, reset);
        }
    }
}
