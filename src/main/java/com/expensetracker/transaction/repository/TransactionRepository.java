package com.expensetracker.transaction.repository;

import com.expensetracker.transaction.entity.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdAndIsDeletedFalse(Long id);

    List<Transaction> findAllByUserAndIsDeletedFalseOrderByCreatedAtDesc(
            com.expensetracker.user.entity.User user,
            Boolean isDeleted
    );

    Transaction findTransactionById(Long id);

    Page<Transaction> findByUserId(
            Long userId,
            Pageable pageable
    );

    Page<Transaction> findByUserIdAndCreatedAtBetween(
            Long userId,
            Date from,
            Date to,
            Pageable pageable
    );

    List<Transaction> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(
            Long userId,
            Date from,
            Date to
    );
}