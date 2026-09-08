package com.expensetracker.auth;

import com.expensetracker.common.exception.AppException;
import com.expensetracker.user.entity.User;
import com.expensetracker.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;

@Component
public class Authentication {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    public Authentication(TokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Value("${jwt.access-secret}")
    private String accessSecret;

    @Value("${jwt.refresh-secret}")
    private String refreshSecret;

    public Claims auth(String token, boolean isRefreshToken) {

        if (token == null || token.isBlank()) {
            throw new AppException("Token is required", HttpStatus.BAD_REQUEST);
        }

        String secret = isRefreshToken
                ? refreshSecret
                : accessSecret;

        Claims claims;

        try {
            claims = tokenService.verifyToken(token, secret);

        } catch (ExpiredJwtException e) {
            throw new AppException("Token expired", HttpStatus.UNAUTHORIZED);

        } catch (JwtException e) {
            throw new AppException("Invalid token", HttpStatus.UNAUTHORIZED);
        }

        Long id = ((Number) claims.get("id")).longValue();

        Optional<User> userExist = userRepository.findById(id);

        if (userExist.isEmpty()) {
            throw new AppException("User Not exist please login", HttpStatus.NOT_FOUND);
        }

        Date userChangeCredential = userExist.get().getChangeCredential();

        // Credentials changed after this token was issued -- treat it as revoked.
        if (userChangeCredential != null
                && userChangeCredential.after(claims.getIssuedAt())) {
            throw new AppException("User Not exist", HttpStatus.NOT_FOUND);
        }

        return claims;
    }
}
