package com.expensetracker.user.controller;

import com.expensetracker.auth.Authentication;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.user.dto.*;
import com.expensetracker.user.entity.User;
import com.expensetracker.user.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final Authentication authentication;

    public UserController(
            UserService userService,
            Authentication authentication
    ) {
        this.userService = userService;
        this.authentication = authentication;
    }

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
    // Get Refresh Token from Cookie
    // =========================================================

    private String getRefreshToken(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {

            throw new AppException(
                    "Refresh token is required",
                    HttpStatus.UNAUTHORIZED
            );
        }

        return refreshToken;
    }

    // =========================================================
    // Create User
    // =========================================================

    @PostMapping
    public User createUser(
            @Valid @RequestBody CreateUserDto user
    ) {

        return userService.createUser(user);
    }

    // =========================================================
    // Confirm Email
    // =========================================================

    @GetMapping("/confirmEmail")
    public Map<String, String> confirmEmail(
            @RequestParam String token
    ) {

        return userService.confirmEmail(token);
    }

    // =========================================================
    // Resend Confirmation Link
    // =========================================================

    @PostMapping("/resendConfirmationLink")
    public Map<String, String> resendConfirmationLink(
            @Valid @RequestBody ResendConfirmationLinkDto dto
    ) {

        return userService.resendConfirmationLink(
                dto.getEmail()
        );
    }

    // =========================================================
    // Login
    // =========================================================

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> signIn(
            @Valid @RequestBody SignInDto user
    ) {

        Map<String, String> tokens =
                userService.signIn(user);


        ResponseCookie accessTokenCookie =
                ResponseCookie
                        .from(
                                "accessToken",
                                tokens.get("accessToken")
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .maxAge(5 * 60)
                        .sameSite("Lax")
                        .build();

        ResponseCookie refreshTokenCookie =
                ResponseCookie
                        .from(
                                "refreshToken",
                                tokens.get("refreshToken")
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .maxAge(7 * 24 * 60 * 60)
                        .sameSite("Lax")
                        .build();

        return ResponseEntity
                .ok()
                .header(
                        "Set-Cookie",
                        accessTokenCookie.toString()
                )
                .header(
                        "Set-Cookie",
                        refreshTokenCookie.toString()
                )
                .body(
                        Map.of(
                                "message",
                                "Login successful"
                        )
                );
    }

    // =========================================================
    // Refresh Token
    // =========================================================

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @CookieValue(
                    value = "refreshToken",
                    required = false
            )
            String refreshToken
    ) {

        Map<String, String> tokens =
                userService.refresh(
                        getRefreshToken(refreshToken)
                );

        // =========================
        // New Access Token
        // =========================

        ResponseCookie accessTokenCookie =
                ResponseCookie
                        .from(
                                "accessToken",
                                tokens.get("accessToken")
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .maxAge(3 * 60)
                        .sameSite("Lax")
                        .build();

        // =========================
        // New Refresh Token
        // =========================

        ResponseCookie refreshTokenCookie =
                ResponseCookie
                        .from(
                                "refreshToken",
                                tokens.get("refreshToken")
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .maxAge(7 * 24 * 60 * 60)
                        .sameSite("Lax")
                        .build();

        return ResponseEntity
                .ok()
                .header(
                        "Set-Cookie",
                        accessTokenCookie.toString()
                )
                .header(
                        "Set-Cookie",
                        refreshTokenCookie.toString()
                )
                .body(
                        Map.of(
                                "message",
                                "Token refreshed successfully"
                        )
                );
    }

    // =========================================================
    // Logout
    // =========================================================

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        String token =
                getAccessToken(accessToken);

        Claims claims =
                authentication.auth(
                        token,
                        false
                );

        Long id =
                claims.get(
                        "id",
                        Long.class
                );

        User user =
                userService.getUserById(id);

        userService.signOut(user);

        // =========================
        // Delete Access Token Cookie
        // =========================

        ResponseCookie deleteAccessTokenCookie =
                ResponseCookie
                        .from(
                                "accessToken",
                                ""
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .maxAge(0)
                        .sameSite("Lax")
                        .build();

        // =========================
        // Delete Refresh Token Cookie
        // =========================

        ResponseCookie deleteRefreshTokenCookie =
                ResponseCookie
                        .from(
                                "refreshToken",
                                ""
                        )
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .maxAge(0)
                        .sameSite("Lax")
                        .build();

        return ResponseEntity
                .ok()
                .header(
                        "Set-Cookie",
                        deleteAccessTokenCookie.toString()
                )
                .header(
                        "Set-Cookie",
                        deleteRefreshTokenCookie.toString()
                )
                .body(
                        Map.of(
                                "message",
                                "Logout successful"
                        )
                );
    }

    // =========================================================
    // Update User Info
    // =========================================================

    @PatchMapping("/updateInfo")
    public User updateUserInfo(

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken,

            @Valid @RequestBody UpdateUserInfoDto user
    ) {

        return userService.updateUserInfo(
                user,
                getAccessToken(accessToken)
        );
    }

    // =========================================================
    // Update Password
    // =========================================================

    @PatchMapping("/updatePassword")
    public Map<String, String> updatePassword(

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken,

            @Valid @RequestBody UpdatePasswordDto dto
    ) {

        return userService.updatePassword(
                dto,
                getAccessToken(accessToken)
        );
    }

    // =========================================================
    // Reset Password
    // =========================================================

    @PatchMapping("/resetPassword")
    public Map<String, String> forgetPassword(
            @Valid @RequestBody ForgetPasswordDto dto
    ) {

        return userService.forgetPassword(dto);
    }

    // =========================================================
    // Resend Forget Password OTP
    // =========================================================

    @PostMapping("/resendForgetPasswordOtp")
    public Map<String, String> resendForgetPasswordOtp(
            @Valid @RequestBody ResendOtpDto dto
    ) {

        return userService.resendForgetPasswordOtp(dto);
    }

    // =========================================================
    // Soft Delete User
    // =========================================================

    @DeleteMapping("/softDelete")
    public Map<String, String> softDeleteUser(

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        return userService.softDeleteUser(
                getAccessToken(accessToken)
        );
    }

    // =========================================================
    // Get Users
    // =========================================================

    @GetMapping("/getUsers")
    public Page<User> getUsers(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size
    ) {

        return userService.getUsers(
                page,
                size
        );
    }

    // =========================================================
    // Get Cached Users
    // =========================================================

    @GetMapping("/getCachedUsers")
    public List<User> getAllUsers() {

        return userService.getAllUsers();
    }

    // =========================================================
    // Get User By ID
    // =========================================================

    @GetMapping("/{id}")
    public User getUserById(
            @PathVariable long id
    ) {

        return userService.getUserById(id);
    }

    // =========================================================
    // Balance
    // =========================================================

    @GetMapping("/balance")
    public BalanceDto getBalanceAndIncomeAndExpense(

            @CookieValue(
                    value = "accessToken",
                    required = false
            )
            String accessToken
    ) {

        return userService.getBalanceAndIncomeAndExpense(
                getAccessToken(accessToken)
        );
    }
}