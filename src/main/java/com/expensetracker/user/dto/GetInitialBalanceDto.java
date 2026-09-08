package com.expensetracker.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class GetInitialBalanceDto {



    private BigDecimal initialBalance;
}