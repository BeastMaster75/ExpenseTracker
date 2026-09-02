package com.expensetracker.report.repository;

import com.expensetracker.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByUserIdAndMonth(Long userId, YearMonth month);
}