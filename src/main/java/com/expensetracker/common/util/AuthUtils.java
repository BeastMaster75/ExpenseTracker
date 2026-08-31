package com.expensetracker.common.util;

import com.expensetracker.auth.Authentication;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.user.entity.User;
import com.expensetracker.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthUtils {

    private static final Logger log = LoggerFactory.getLogger(AuthUtils.class);

    private final Authentication authentication;
    private final UserRepository userRepository;

    public AuthUtils(Authentication authentication, UserRepository userRepository) {
        this.authentication = authentication;
        this.userRepository = userRepository;
    }

    public Long getUserId(String token) {

        Claims claims = authentication.auth(token, false);

        return claims.get("id", Long.class);
    }

    public User currentUser(Long userId) {

        return userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> {
                    log.warn("Request failed - user not found: {}", userId);
                    return new AppException("User not exist", HttpStatus.NOT_FOUND);
                });
    }

    public User currentUser(String token) {
        return currentUser(getUserId(token));
    }
}
