package com.expensetracker.user.service;

import com.expensetracker.auth.Authentication;
import com.expensetracker.auth.TokenService;
import com.expensetracker.common.email.EmailService;
import com.expensetracker.common.email.ResendOtp;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.common.redis.RedisService;
import com.expensetracker.common.security.EncryptAndDecryptSecurity;
import com.expensetracker.common.security.HashSecurity;
import com.expensetracker.user.dto.*;
import com.expensetracker.user.entity.User;
import com.expensetracker.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.UUID;
import com.expensetracker.common.email.ConfirmEmailTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;


@Service
public class UserService {

    private static final String ALL_USERS_KEY = "users::all";

    private static final String CONFIRM_EMAIL_TYPE = "confirm_email";

    private static final String FORGET_PASSWORD_TYPE = "forget_password";

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final HashSecurity hashSecurity;
    private final TokenService tokenService;
    private final Authentication authentication;
    private final RedisService redisService;
    private final EmailService sendEmail;
    private final ResendOtp resendOtp;
    private final ObjectMapper objectMapper;
    private final PasswordHistoryService passwordHistoryService;

    private String generateEmailVerificationToken() {
        return UUID.randomUUID().toString();
    }
    private String hashVerificationToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm not available",
                    e
            );
        }
    }

    public UserService(
            UserRepository userRepository,
            TokenService tokenService,
            HashSecurity hashSecurity,
            Authentication authentication,
            RedisService redisService,
            EmailService sendEmail,
            ResendOtp resendOtp,
            ObjectMapper objectMapper,
            PasswordHistoryService passwordHistoryService
    ) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.hashSecurity = hashSecurity;
        this.authentication = authentication;
        this.redisService = redisService;
        this.sendEmail = sendEmail;
        this.resendOtp = resendOtp;
        this.objectMapper = objectMapper;
        this.passwordHistoryService = passwordHistoryService;
    }

    @Value("${jwt.refresh-secret}")
    private String refreshSecret;

    public User createUser(CreateUserDto user) {

        log.info("Creating user with email: {}", user.getEmail());

        Optional<User> userExist = userRepository.findByEmail(user.getEmail());

        String decryptedPassword = EncryptAndDecryptSecurity.decrypt(user.getPassword());

        if (userExist.isPresent()) {

            User existingUser = userExist.get();

            if (!existingUser.getIsDeleted()) {

                throw new AppException(
                        "User already exist",
                        HttpStatus.CONFLICT
                );
            }

            // Restore soft-deleted user

            existingUser.setUsername(user.getUsername());

            existingUser.setPassword(
                    hashSecurity.hash(decryptedPassword)
            );

            existingUser.setIsDeleted(false);
            existingUser.setIsConfirmed(false);

            existingUser.setChangeCredential(null);

            existingUser.setBalance(BigDecimal.ZERO);
            existingUser.setTotalIncome(BigDecimal.ZERO);
            existingUser.setTotalExpense(BigDecimal.ZERO);

            String verificationToken = generateEmailVerificationToken();

            existingUser.setEmailVerificationTokenHash(
                    hashVerificationToken(verificationToken)
            );

            existingUser.setEmailVerificationTokenExpiresAt(
                    new Date(
                            System.currentTimeMillis() + 15 * 60 * 1000
                    )
            );

            User restoredUser = userRepository.save(existingUser);

            String verificationLink =
                    "http://localhost:8081/users/confirmEmail?token=" + verificationToken;

            sendEmail.sendEmail(
                    restoredUser.getEmail(),
                    "Confirm your email",
                    ConfirmEmailTemplate.confirmEmail(
                            restoredUser.getUsername(),
                            verificationLink
                    )
            );

            log.info(
                    "Soft-deleted user restored - userId: {}",
                    restoredUser.getId()
            );

            return restoredUser;
        }

        // Create new user

        User newUser = new User();

        newUser.setUsername(user.getUsername());
        newUser.setEmail(user.getEmail());

        newUser.setPassword(
                hashSecurity.hash(decryptedPassword)
        );

        newUser.setIsDeleted(false);
        newUser.setIsConfirmed(false);

        newUser.setBalance(BigDecimal.ZERO);
        newUser.setTotalIncome(BigDecimal.ZERO);
        newUser.setTotalExpense(BigDecimal.ZERO);

        String verificationToken = generateEmailVerificationToken();

        newUser.setEmailVerificationTokenHash(
                hashVerificationToken(verificationToken)
        );

        newUser.setEmailVerificationTokenExpiresAt(
                new Date(
                        System.currentTimeMillis() + 15 * 60 * 1000
                )
        );

        User savedUser = userRepository.save(newUser);

        String verificationLink =
                "http://localhost:8081/users/confirmEmail?token=" + verificationToken;

        sendEmail.sendEmail(
                savedUser.getEmail(),
                "Confirm your email",
                ConfirmEmailTemplate.confirmEmail(
                        savedUser.getUsername(),
                        verificationLink
                )
        );

        log.info(
                "User created successfully - id: {}, email: {}",
                savedUser.getId(),
                savedUser.getEmail()
        );

        return savedUser;
    }

    public Map<String, String> confirmEmail(String token) {

        if (token == null || token.isBlank()) {

            throw new AppException(
                    "Verification token is required",
                    HttpStatus.BAD_REQUEST
            );
        }

        String tokenHash =
                hashVerificationToken(token);

        Optional<User> userExist =
                userRepository.findByEmailVerificationTokenHash(
                        tokenHash
                );

        if (userExist.isEmpty()) {

            throw new AppException(
                    "Invalid verification token",
                    HttpStatus.UNAUTHORIZED
            );
        }

        User user =
                userExist.get();

        if (user.getEmailVerificationTokenExpiresAt() == null
                || user.getEmailVerificationTokenExpiresAt()
                .before(new Date())) {

            throw new AppException(
                    "Verification token expired",
                    HttpStatus.UNAUTHORIZED
            );
        }

        if (user.getIsDeleted()) {

            throw new AppException(
                    "User not found",
                    HttpStatus.NOT_FOUND
            );
        }

        user.setIsConfirmed(true);

        // Token is one-time use
        user.setEmailVerificationTokenHash(null);
        user.setEmailVerificationTokenExpiresAt(null);

        userRepository.save(user);

        log.info(
                "Email confirmed successfully - userId: {}",
                user.getId()
        );

        return Map.of(
                "message",
                "Email confirmed successfully"
        );
    }

    public Map<String, String> resendConfirmationLink(String email) {

        log.info(
                "Resend confirmation link started - email: {}",
                email
        );

        User user =
                userRepository
                        .findByEmailAndIsDeletedFalse(email)
                        .orElseThrow(() ->
                                new AppException(
                                        "User not found",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (user.getIsConfirmed()) {

            throw new AppException(
                    "Email already confirmed",
                    HttpStatus.BAD_REQUEST
            );
        }

        String verificationToken =
                generateEmailVerificationToken();

        user.setEmailVerificationTokenHash(
                hashVerificationToken(verificationToken)
        );

        user.setEmailVerificationTokenExpiresAt(
                new Date(
                        System.currentTimeMillis()
                                + 15 * 60 * 1000
                )
        );

        userRepository.save(user);

        String verificationLink =
                "http://localhost:8081/users/confirmEmail?token="
                        + verificationToken;

        sendEmail.sendEmail(
                user.getEmail(),
                "Confirm Your Email",
                ConfirmEmailTemplate.confirmEmail(
                        user.getUsername(),
                        verificationLink
                )
        );

        log.info(
                "Confirmation link resent successfully - userId: {}",
                user.getId()
        );

        return Map.of(
                "message",
                "Confirmation link sent successfully"
        );
    }

    public Map<String, String> signIn(SignInDto user) {

        log.info("Login attempt - email: {}", user.getEmail());

        User existingUser = userRepository.findByEmailAndIsDeletedFalse(user.getEmail())
                .orElseThrow(() -> {

                    log.warn("Login failed - user not found: {}", user.getEmail());

                    return new AppException(
                            "User not exist",
                            HttpStatus.NOT_FOUND
                    );
                });

        if (!existingUser.getIsConfirmed()) {

            throw new AppException(
                    "Email not confirmed",
                    HttpStatus.BAD_REQUEST
            );
        }

        String decryptedPassword = EncryptAndDecryptSecurity.decrypt(user.getPassword());

        Long blockedTime = redisService.ttlTimer(redisService.blockPasswordKey(existingUser.getEmail()));

        if (blockedTime > 0) {

            log.warn("Login blocked - userId: {} - remaining seconds: {}", existingUser.getId(), blockedTime);

            throw new AppException(
                    String.format(
                            "You have executed the maximum number of tries, please try again after %s seconds",
                            blockedTime
                    ),
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!hashSecurity.compare(decryptedPassword, existingUser.getPassword())) {

            log.warn("Login failed - invalid password - userId: {}", existingUser.getId());

            long maxPassTries = 0;

            String value = redisService.get(redisService.maxPasswordKey(user.getEmail()));

            if (value != null) {
                maxPassTries = Long.parseLong(value);
            }

            if (maxPassTries >= 5) {

                redisService.setValue(redisService.blockPasswordKey(user.getEmail()), "1", 60 * 5);

                log.warn("User blocked after maximum login attempts - userId: {}", existingUser.getId());

                throw new AppException(
                        "you have executed the maximum number of tries",
                        HttpStatus.BAD_REQUEST
                );
            }

            redisService.incr(redisService.maxPasswordKey(user.getEmail()));
            redisService.expire(redisService.maxPasswordKey(user.getEmail()), 60 * 5);

            throw new AppException(
                    "Invalid password",
                    HttpStatus.UNAUTHORIZED
            );
        }

        String accessToken = tokenService.generateAccessToken(existingUser);
        String refreshToken = tokenService.generateRefreshToken(existingUser);

        log.info("Login successful - userId: {}", existingUser.getId());

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        );
    }

    public User updateUserInfo(UpdateUserInfoDto user, String token) {

        Claims claims = authentication.auth(token, false);

        Long id = claims.get("id", Long.class);

        log.info("Updating user information - userId: {}", id);

        Optional<User> userExist = userRepository.findByIdAndIsDeletedFalse(id);

        if (userExist.isEmpty()) {
            log.warn("Update user failed - user not found: {}", id);

            throw new AppException(
                    "User not found",
                    HttpStatus.NOT_FOUND
            );
        }

        User existingUser = userExist.get();

        existingUser.setUsername(user.getUsername());

        User updatedUser = userRepository.save(existingUser);

        redisService.delete(ALL_USERS_KEY);

        log.info("User information updated successfully - userId: {}", id);

        return updatedUser;
    }

    public Map<String, String> updatePassword(UpdatePasswordDto dto, String token) {

        Claims claims = authentication.auth(token, false);

        Long id = claims.get("id", Long.class);

        log.info("Password update started - userId: {}", id);

        Optional<User> userExist = userRepository.findByIdAndIsDeletedFalse(id);

        if (userExist.isEmpty()) {
            log.warn("Password update failed - user not found: {}", id);

            throw new AppException(
                    "User not found",
                    HttpStatus.NOT_FOUND
            );
        }

        String decryptedOldPassword = EncryptAndDecryptSecurity.decrypt(dto.getOldPassword());
        String decryptedNewPassword = EncryptAndDecryptSecurity.decrypt(dto.getNewPassword());

        User existingUser = userExist.get();

        if (!hashSecurity.compare(decryptedOldPassword, existingUser.getPassword())) {

            throw new AppException(
                    "Old password is incorrect",
                    HttpStatus.UNAUTHORIZED
            );
        }

        passwordHistoryService.assertNotReused(existingUser, decryptedNewPassword);

        passwordHistoryService.record(existingUser, existingUser.getPassword());

        existingUser.setPassword(hashSecurity.hash(decryptedNewPassword));

        existingUser.setChangeCredential(new Date());

        redisService.delete(ALL_USERS_KEY);

        userRepository.save(existingUser);

        log.info("Password updated successfully - userId: {}", id);

        return Map.of(
                "message",
                "Password Changed successfully"
        );
    }

//    public Map<String, String> forgetPassword(ForgetPasswordDto dto) {
//
//        log.info("Forget password started - email: {}", dto.getEmail());
//
//        Optional<User> userExist = userRepository.findByEmailAndIsDeletedFalse(dto.getEmail());
//
//        if (userExist.isEmpty()) {
//            log.warn("Forget password failed - user not found: {}", dto.getEmail());
//
//            throw new AppException(
//                    "User not found",
//                    HttpStatus.NOT_FOUND
//            );
//        }
//
//        String decryptedNewPassword = EncryptAndDecryptSecurity.decrypt(dto.getNewPassword());
//
//        User existingUser = userExist.get();
//
//        String otpExist = redisService.get(
//                redisService.otpKey(
//                        existingUser.getEmail(),
//                        FORGET_PASSWORD_TYPE
//                )
//        );
//
//        if (otpExist == null || otpExist.isEmpty()) {
//            log.warn("Forget password failed - OTP expired - email: {}", existingUser.getEmail());
//
//            throw new AppException(
//                    "OTP expired or incorrect",
//                    HttpStatus.BAD_REQUEST
//            );
//        }
//
//        if (!hashSecurity.compare(dto.getOtp(), otpExist)) {
//            log.warn("Forget password failed - invalid OTP - email: {}", existingUser.getEmail());
//
//            throw new AppException(
//                    "Invalid OTP",
//                    HttpStatus.UNAUTHORIZED
//            );
//        }
//
//        passwordHistoryService.assertNotReused(
//                existingUser,
//                decryptedNewPassword
//        );
//
//        passwordHistoryService.record(
//                existingUser,
//                existingUser.getPassword()
//        );
//
//        existingUser.setPassword(
//                hashSecurity.hash(decryptedNewPassword)
//        );
//
//        existingUser.setChangeCredential(new Date());
//
//        redisService.delete(ALL_USERS_KEY);
//
//        redisService.delete(
//                redisService.otpKey(
//                        existingUser.getEmail(),
//                        FORGET_PASSWORD_TYPE
//                )
//        );
//
//        userRepository.save(existingUser);
//
//        log.info("Password reset successfully - userId: {}", existingUser.getId());
//
//        return Map.of(
//                "message",
//                "Password changed successfully"
//        );
//    }

    @Value("${jwt.otp-secret}")
    private String otpSecret;
    public Map<String, String> verifyForgetPasswordOtp(
            VerifyOtpDto dto
    ) {

        log.info(
                "Verify forget password OTP started - email: {}",
                dto.getEmail()
        );

        User existingUser =
                userRepository
                        .findByEmailAndIsDeletedFalse(dto.getEmail())
                        .orElseThrow(() -> {

                            log.warn(
                                    "Verify OTP failed - user not found: {}",
                                    dto.getEmail()
                            );

                            return new AppException(
                                    "User not found",
                                    HttpStatus.NOT_FOUND
                            );
                        });

        String otpExist =
                redisService.get(
                        redisService.otpKey(
                                existingUser.getEmail(),
                                FORGET_PASSWORD_TYPE
                        )
                );

        if (otpExist == null || otpExist.isEmpty()) {

            log.warn(
                    "Verify OTP failed - OTP expired - email: {}",
                    existingUser.getEmail()
            );

            throw new AppException(
                    "OTP expired or incorrect",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!hashSecurity.compare(dto.getOtp(), otpExist)) {

            log.warn(
                    "Verify OTP failed - invalid OTP - email: {}",
                    existingUser.getEmail()
            );

            throw new AppException(
                    "Invalid OTP",
                    HttpStatus.UNAUTHORIZED
            );
        }


        String otpToken =
                tokenService.generateOtpToken(existingUser);

        redisService.delete(
                redisService.otpKey(
                        existingUser.getEmail(),
                        FORGET_PASSWORD_TYPE
                )
        );

        log.info(
                "Forget password OTP verified successfully - userId: {}",
                existingUser.getId()
        );

        return Map.of(
                "message",
                "OTP verified successfully",
                "otpToken",
                otpToken
        );
    }


    public Map<String, String> resetPassword(
            ResetPasswordDto dto
    ) {

        log.info("Reset password started");

        Claims claims;

        try {

//            String otpSecret = "";
            claims =
                    tokenService.verifyToken(
                            dto.getOtpToken(),
                            otpSecret
                    );

        } catch (Exception e) {

            log.warn(
                    "Reset password failed - invalid or expired OTP token"
            );

            throw new AppException(
                    "Invalid or expired OTP token",
                    HttpStatus.UNAUTHORIZED
            );
        }

        Long userId =
                claims.get(
                        "id",
                        Long.class
                );

        User existingUser =
                userRepository
                        .findByIdAndIsDeletedFalse(userId)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Reset password failed - user not found: {}",
                                    userId
                            );

                            return new AppException(
                                    "User not found",
                                    HttpStatus.NOT_FOUND
                            );
                        });

        passwordHistoryService.assertNotReused(
                existingUser,
                dto.getNewPassword()
        );

        passwordHistoryService.record(
                existingUser,
                existingUser.getPassword()
        );

        existingUser.setPassword(
                hashSecurity.hash(
                        dto.getNewPassword()
                )
        );

        existingUser.setChangeCredential(
                new Date()
        );

        redisService.delete(
                ALL_USERS_KEY
        );

        userRepository.save(existingUser);

        log.info(
                "Password reset successfully - userId: {}",
                existingUser.getId()
        );

        return Map.of(
                "message",
                "Password changed successfully"
        );
    }



    public Map<String, String> resendForgetPasswordOtp(ResendOtpDto dto) {

        log.info(
                "Resend forget password OTP started - email: {}",
                dto.getEmail()
        );

        Optional<User> userExist =
                userRepository.findByEmailAndIsDeletedFalse(dto.getEmail());

        if (userExist.isEmpty()) {
            log.warn(
                    "Resend forget password OTP failed - user not found: {}",
                    dto.getEmail()
            );

            throw new AppException(
                    "User not found",
                    HttpStatus.NOT_FOUND
            );
        }

        User user = userExist.get();

        resendOtp.sendOtp(
                user.getEmail(),
                user.getUsername(),
                "Forget Password Reset",
                FORGET_PASSWORD_TYPE
        );

        log.info(
                "Forget password OTP resent successfully - userId: {}",
                user.getId()
        );

        return Map.of(
                "message",
                "Reset forget password OTP resent successfully"
        );
    }

    public Map<String, String> softDeleteUser(String token) {

        Claims claims = authentication.auth(token, false);

        Long id = claims.get("id", Long.class);

        log.info("Soft delete user started - userId: {}", id);

        Optional<User> userExist = userRepository.findByIdAndIsDeletedFalse(id);

        if (userExist.isEmpty()) {
            log.warn("Soft delete failed - user not found: {}", id);

            throw new AppException(
                    "User not found",
                    HttpStatus.NOT_FOUND
            );
        }

        User user = userExist.get();

        user.setChangeCredential(new Date());
        user.setIsDeleted(true);

        userRepository.save(user);

        redisService.delete(ALL_USERS_KEY);

        log.info("User soft deleted successfully - userId: {}", id);

        return Map.of(
                "message", "Deleted successfully"
        );
    }

    public Page<User> getUsers(int page, int size) {

        log.info("Fetching users - page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page, size);

        Page<User> users = userRepository.findAllByIsDeletedFalseAndIsConfirmedFalse(pageable);

        log.info("Users fetched successfully - count: {}", users.getNumberOfElements());

        return users;
    }

    public List<User> getAllUsers() {

        log.info("Fetching all users");

        String cachedUsers = redisService.get(ALL_USERS_KEY);

        if (cachedUsers != null) {

            log.info("Users fetched from Redis cache");

            try {
                return objectMapper.readValue(
                        cachedUsers,
                        new TypeReference<List<User>>() {}
                );
            } catch (Exception error) {

                log.error("Failed to read users from Redis cache", error);

                throw new AppException(
                        "Failed to read users snapshot",
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
        }

        List<User> users = userRepository.findAll();

        redisService.snapshot(
                ALL_USERS_KEY,
                users,
                60 * 60 * 24
        );

        log.info("Users fetched from database and cached - count: {}", users.size());

        return users;
    }

    public User getUserById(Long id) {

        log.info("Fetching user - userId: {}", id);

        Optional<User> userExist = userRepository.findByIdAndIsDeletedFalseAndIsConfirmedFalse(id);

        if (userExist.isEmpty()) {
            log.warn("User not found - userId: {}", id);

            throw new AppException(
                    "User not found",
                    HttpStatus.NOT_FOUND
            );
        }

        log.info("User fetched successfully - userId: {}", id);

        return userExist.get();
    }

    public Map<String, String> refresh(String refreshToken) {

        log.info("Refresh token request started");

        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Refresh token request failed - token is missing");

            throw new AppException(
                    "Refresh token is required",
                    HttpStatus.BAD_REQUEST
            );
        }

        Claims claims;

        try {
            claims = tokenService.verifyToken(refreshToken, refreshSecret);

        } catch (Exception e) {
            log.warn("Refresh token validation failed");

            throw new AppException(
                    "Invalid or expired refresh token",
                    HttpStatus.UNAUTHORIZED
            );
        }

        Long id = claims.get("id", Long.class);

        Optional<User> user = userRepository.findById(id);

        if (user.isEmpty()) {
            log.warn("Refresh token failed - user not found: {}", id);

            throw new AppException(
                    "User not found",
                    HttpStatus.NOT_FOUND
            );
        }

        String accessToken = tokenService.generateAccessToken(user.get());

        log.info("Access token refreshed successfully - userId: {}", id);

        return Map.of(
                "accessToken", accessToken
        );
    }

    public Map<String, String> signOut(User user) {

        log.info("User logout started - userId: {}", user.getId());

        user.setChangeCredential(new Date());

        userRepository.save(user);

        log.info("User logged out successfully - userId: {}", user.getId());

        return Map.of(
                "message", "Logout done"
        );
    }

    public BalanceDto getBalanceAndIncomeAndExpense(String token) {

        Claims claims = authentication.auth(token, false);
        Long userId = claims.get("id", Long.class);

        Optional<User> userExist =
                userRepository.findByIdAndIsDeletedFalse(userId);

        if (userExist.isEmpty()) {
            throw new AppException(
                    "User not exist",
                    HttpStatus.NOT_FOUND
            );
        }

        User user = userExist.get();

        return new BalanceDto(
                user.getBalance(),
                user.getTotalIncome(),
                user.getTotalExpense()
        );
    }

    public Map<String, String> addInitialBalance(SetInitialBalance dto, String token) {

        Claims claims = authentication.auth(token, false);

        Long id = claims.get("id", Long.class);

        log.info("Adding initial balance - userId: {}", id);

        User existingUser = userRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(
                        "User not found",
                        HttpStatus.NOT_FOUND
                ));

        existingUser.setInitialBalance(dto.getInitialBalance());

        userRepository.save(existingUser);

        log.info("Initial balance added successfully - userId: {}", id);

        return Map.of(
                "message",
                "Initial balance added successfully"
        );
    }

    public Map<String, String> updateInitialBalance(UpdateBalance dto, String token) {

        Claims claims = authentication.auth(token, false);

        Long id = claims.get("id", Long.class);

        log.info("Updating initial balance - userId: {}", id);

        User existingUser = userRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(
                        "User not found",
                        HttpStatus.NOT_FOUND
                ));

        existingUser.setInitialBalance(dto.getNewBalance());

        userRepository.save(existingUser);

        log.info("Initial balance updated successfully - userId: {}", id);

        return Map.of(
                "message",
                "Initial balance updated successfully"
        );
    }

}
