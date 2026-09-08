package com.expensetracker.budget.service;

import com.expensetracker.budget.repository.BudgetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class BudgetResetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetResetService.class);

    private final BudgetRepository budgetRepository;
    private final TransactionTemplate transactionTemplate;

    public BudgetResetService(
            BudgetRepository budgetRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.budgetRepository = budgetRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // Midnight on the 1st of every month.
    @Scheduled(cron = "${app.budget.reset-cron:0 0 0 1 * *}", zone = "${app.timezone:Africa/Cairo}")
    public void resetOnSchedule() {
        resetMonthlyFigures();
    }

    // The scheduled run is missed entirely if the app is down at midnight on the
    // 1st, so catch up on startup. resetMonthlyFigures is idempotent within a
    // month, and a failure here must not stop the app from booting.
    @EventListener(ApplicationReadyEvent.class)
    public void resetOnStartup() {
        try {
            resetMonthlyFigures();
        } catch (Exception e) {
            log.error("Budget monthly reset on startup failed - continuing startup", e);
        }
    }

    /**
     * Runs the update in its own transaction through a {@link TransactionTemplate}
     * rather than an {@code @Transactional} annotation.
     *
     * <p>That is deliberate. With {@code @Transactional} on the caller, the
     * commit happens after the method returns -- outside any try/catch inside
     * it. A failed statement marks the transaction rollback-only, the catch
     * swallows the original error, and the commit then throws
     * {@code UnexpectedRollbackException} from the proxy, killing startup. The
     * template keeps begin, commit and rollback inside this call, so callers
     * can actually catch a failure.
     */
    void resetMonthlyFigures() {

        LocalDate periodMonth = LocalDate.now().withDayOfMonth(1);

        Integer reset = transactionTemplate.execute(status ->
                budgetRepository.resetForPeriod(BigDecimal.ZERO, periodMonth)
        );

        if (reset != null && reset > 0) {
            log.info("Budget spending and available-to-use reset for {} - budgets affected: {}",
                    periodMonth, reset);
        }
    }
}
