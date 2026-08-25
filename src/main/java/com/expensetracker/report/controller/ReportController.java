package com.expensetracker.report.controller;

import com.expensetracker.common.exception.AppException;
import com.expensetracker.report.dto.CreateReportDto;
import com.expensetracker.report.dto.ReportResponse;
import com.expensetracker.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

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
    // Create Report
    // =========================================================

    @PostMapping
    public ResponseEntity<ReportResponse> createReport(

            @RequestBody
            CreateReportDto dto,

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        String token =
                getAccessToken(accessToken);

        ReportResponse response =
                reportService.createReport(
                        dto,
                        token
                );

        return ResponseEntity.ok(response);
    }
}