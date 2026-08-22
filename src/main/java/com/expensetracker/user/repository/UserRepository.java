package com.expensetracker.user.repository;

import com.expensetracker.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByIdAndIsDeletedFalse(Long id);
    Optional<User> findByEmailAndIsDeletedFalse(String email);
}
