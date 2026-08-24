package com.expensetracker.report.controller;

import com.expensetracker.report.dto.CreateReportDto;
import com.expensetracker.report.dto.ReportResponse;
import com.expensetracker.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportResponse> createReport(
            @RequestBody CreateReportDto dto,
            @RequestHeader("Authorization") String authorization
    ) {

        String token = authorization.replace("Bearer ", "");

        ReportResponse response =
                reportService.createReport(dto, token);

        return ResponseEntity.ok(response);
    }
}