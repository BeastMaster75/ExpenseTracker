package com.expensetracker.transaction.repository;

import com.expensetracker.transaction.entity.Transaction;
import com.expensetracker.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdAndIsDeletedFalse(Long id);

    List<Transaction> findAllByUserAndIsDeletedFalseOrderByCreatedAtDesc(User user, Boolean isDeleted);

    Transaction findTransactionById(Long id);
}
