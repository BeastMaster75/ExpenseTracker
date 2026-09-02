package com.expensetracker.auth;

import com.expensetracker.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class TokenService {

    private static final long ACCESS_TOKEN_TTL = 30 * 60 * 1000L;

    private static final long OTP_TOKEN_TTL = 20 * 60 * 1000L;

    private static final long REFRESH_TOKEN_TTL = 7L * 24 * 60 * 60 * 1000;

    @Value("${jwt.access-secret}")
    private String accessSecret;

    @Value("${jwt.refresh-secret}")
    private String refreshSecret;

    @Value("${jwt.otp-secret}")
    private String otpSecret;

    public String generateToken(
            Map<String, Object> claims,
            long expiration,
            String secret
    ) {

        SecretKey key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        return Jwts.builder()
                .claims(claims)
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .signWith(key)
                .compact();
    }

    public String generateAccessToken(User user) {

        return generateToken(
                Map.of(
                        "id", user.getId(),
                        "email", user.getEmail()
                ),
                ACCESS_TOKEN_TTL,
                accessSecret
        );
    }

    public String generateRefreshToken(User user) {

        return generateToken(
                Map.of(
                        "id", user.getId(),
                        "email", user.getEmail()
                ),
                REFRESH_TOKEN_TTL,
                refreshSecret
        );
    }

    public String generateOtpToken(User user) {

        return generateToken(
                Map.of(
                        "id", user.getId(),
                        "email", user.getEmail()
                ),
                OTP_TOKEN_TTL,
                otpSecret
        );
    }



    public Claims verifyToken(String token, String secret) {

        SecretKey key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
