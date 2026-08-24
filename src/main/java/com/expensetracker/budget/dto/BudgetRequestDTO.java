package ExpenseTracker.SCB.DTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BudgetRequestDTO {

    @NotNull
    private Long userId;

    @NotNull
    @PositiveOrZero
    private BigDecimal spending;

    @NotNull
    @PositiveOrZero
    private BigDecimal amountLimit;

    @NotNull
    private LocalDate periodMonth;

    public BudgetRequestDTO() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getSpending() {
        return spending;
    }

    public void setSpending(BigDecimal spending) {
        this.spending = spending;
    }

    public BigDecimal getAmountLimit() {
        return amountLimit;
    }

    public void setAmountLimit(BigDecimal amountLimit) {
        this.amountLimit = amountLimit;
    }

    public LocalDate getPeriodMonth() {
        return periodMonth;
    }

    public void setPeriodMonth(LocalDate periodMonth) {
        this.periodMonth = periodMonth;
    }
}
