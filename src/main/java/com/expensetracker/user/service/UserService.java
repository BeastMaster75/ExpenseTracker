package com.expensetracker.user.service;

import com.expensetracker.auth.Authentication;
import com.expensetracker.auth.TokenService;
import com.expensetracker.common.email.EmailService;
import com.expensetracker.common.email.EmailTemplate;
import com.expensetracker.common.email.ResendOtp;
import com.expensetracker.common.exception.AppException;
import com.expensetracker.common.redis.RedisService;
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

        log.info("Creating new user with email: {}", user.getEmail());

        Optional<User> userExist = userRepository.findByEmail(user.getEmail());

        if (userExist.isPresent()) {
            log.warn("User creation failed - email already exists: {}", user.getEmail());

            throw new AppException(
                    "User already exist",
                    HttpStatus.CONFLICT
            );
        }

        User newUser = new User();

        newUser.setUsername(user.getUsername());
        newUser.setEmail(user.getEmail());

        newUser.setPassword(
                hashSecurity.hash(user.getPassword())
        );

        int otp = sendEmail.generateOTP();

        sendEmail.sendEmail(
                newUser.getEmail(),
                "email confirmation",
                EmailTemplate.otpEmail(
                        newUser.getUsername(),
                        otp,
                        "Confirmation Otp"
                )
        );

        redisService.setValue(
                redisService.otpKey(newUser.getEmail(), CONFIRM_EMAIL_TYPE),
                hashSecurity.hash(String.valueOf(otp)),
                60 * 5
        );

        User savedUser = userRepository.save(newUser);

        log.info(
                "User created successfully - id: {}, email: {}",
                savedUser.getId(),
                savedUser.getEmail()
        );

        return savedUser;
    }

    public Map<String, String> confirmEmail(ConfirmEmailDto dto) {

        log.info("Email confirmation started - email: {}", dto.getEmail());

        Optional<User> userExist = userRepository.findByEmail(dto.getEmail());

        if (userExist.isEmpty()) {
            log.warn("Email confirmation failed - user not found: {}", dto.getEmail());

            throw new AppException(
                    "User not found",
                    HttpStatus.NOT_FOUND
            );
        }

        User user = userExist.get();

        if (user.getIsConfirmed()) {
            log.warn("Email confirmation failed - already confirmed - userId: {}", user.getId());

            throw new AppException(
                    "User already confirmed",
                    HttpStatus.BAD_REQUEST
            );
        }

        String otpExist = redisService.get(
                redisService.otpKey(dto.getEmail(), CONFIRM_EMAIL_TYPE)
        );

        if (otpExist == null || otpExist.isEmpty()) {
            log.warn("Email confirmation failed - OTP expired - userId: {}", user.getId());

            throw new AppException(
                    "OTP expired or incorrect",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!hashSecurity.compare(dto.getOtp(), otpExist)) {
            log.warn("Email confirmation failed - invalid OTP - userId: {}", user.getId());

            throw new AppException(
                    "Invalid OTP",
                    HttpStatus.UNAUTHORIZED
            );
        }

        user.setIsConfirmed(true);

        userRepository.save(user);

        redisService.delete(
                redisService.otpKey(dto.getEmail(), CONFIRM_EMAIL_TYPE)
        );

        log.info("Email confirmed successfully - userId: {}", user.getId());

        return Map.of(
                "message", "confirmed done successfully "
        );
    }

    public Map<String, String> resendConfirmOtp(ResendOtpDto dto, String token) {

        Claims claims = authentication.auth(token, false);

        Long id = claims.get("id", Long.class);

        log.info("Resend confirmation OTP started - userId: {}", id);

        Optional<User> userExist = userRepository.findByIdAndIsDeletedFalse(id);

        if (userExist.isEmpty()) {
            log.warn("Resend confirmation OTP failed - user not found: {}", id);

            throw new AppException(
                    "User not found",
                    HttpStatus.NOT_FOUND
            );
        }

        User user = userExist.get();

        if (user.getIsConfirmed()) {
            log.warn(
                    "Resend confirmation OTP failed - user already confirmed - userId: {}",
                    id
            );

            throw new AppException(
                    "User already confirmed",
                    HttpStatus.BAD_REQUEST
            );
        }

        resendOtp.sendOtp(
                dto.getEmail(),
                user.getUsername(),
                "Confirmation Otp ",
                CONFIRM_EMAIL_TYPE
        );

        log.info("Confirmation OTP resent successfully - userId: {}", id);

        return Map.of(
                "message", "confirmed otp resent successfully "
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

        Long blockedTime = redisService.ttlTimer(
                redisService.blockPasswordKey(existingUser.getEmail())
        );

        if (!hashSecurity.compare(
                user.getPassword(),
                existingUser.getPassword()
        )) {

            log.warn("Login failed - invalid password - userId: {}", existingUser.getId());

            if (blockedTime > 0) {

                log.warn(
                        "Login blocked - userId: {} - remaining seconds: {}",
                        existingUser.getId(),
                        blockedTime
                );

                throw new AppException(
                        String.format(
                                "You have executed the maximum number of tries, please try again after %s seconds",
                                blockedTime
                        ),
                        HttpStatus.BAD_REQUEST
                );
            }

            long maxPassTries = 0;

            String value = redisService.get(
                    redisService.maxPasswordKey(user.getEmail())
            );

            if (value != null) {
                maxPassTries = Long.parseLong(value);
            }

            if (maxPassTries >= 5) {

                redisService.setValue(
                        redisService.blockPasswordKey(user.getEmail()),
                        "1",
                        60 * 5
                );

                log.warn(
                        "User blocked after maximum login attempts - userId: {}",
                        existingUser.getId()
                );

                throw new AppException(
                        "you have executed the maximum number of tries",
                        HttpStatus.BAD_REQUEST
                );
            }

            redisService.incr(
                    redisService.maxPasswordKey(user.getEmail())
            );

            redisService.expire(
                    redisService.maxPasswordKey(user.getEmail()),
                    60 * 5
            );

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

        User existingUser = userExist.get();

        if (!hashSecurity.compare(
                dto.getOldPassword(),
                existingUser.getPassword()
        )) {

            log.warn("Password update failed - invalid old password - userId: {}", id);

            throw new AppException(
                    "Invalid password",
                    HttpStatus.UNAUTHORIZED
            );
        }

        passwordHistoryService.assertNotReused(existingUser, dto.getNewPassword());

        passwordHistoryService.record(existingUser, existingUser.getPassword());

        existingUser.setPassword(
                hashSecurity.hash(dto.getNewPassword())
        );

        existingUser.setChangeCredential(new Date());

        redisService.delete(ALL_USERS_KEY);

        userRepository.save(existingUser);

        log.info("Password updated successfully - userId: {}", id);

        return Map.of(
                "message", "Password Changed successfully "
        );
    }

    public Map<String, String> forgetPassword(ForgetPasswordDto dto, String token) {

        Claims claims = authentication.auth(token, false);

        Long id = claims.get("id", Long.class);

        log.info("Forget password started - userId: {}", id);

        Optional<User> userExist = userRepository.findByIdAndIsDeletedFalse(id);

        if (userExist.isEmpty()) {
            log.warn("Forget password failed - user not found: {}", id);

            throw new AppException(
                    "User not found",
                    HttpStatus.NOT_FOUND
            );
        }

        User existingUser = userExist.get();

        String otpExist = redisService.get(
                redisService.otpKey(existingUser.getEmail(), FORGET_PASSWORD_TYPE)
        );

        if (otpExist == null || otpExist.isEmpty()) {
            log.warn("Forget password failed - OTP expired - userId: {}", id);

            throw new AppException(
                    "OTP expired or incorrect",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!hashSecurity.compare(dto.getOtp(), otpExist)) {
            log.warn("Forget password failed - invalid OTP - userId: {}", id);

            throw new AppException(
                    "Invalid OTP",
                    HttpStatus.UNAUTHORIZED
            );
        }

        passwordHistoryService.assertNotReused(existingUser, dto.getNewPassword());

        passwordHistoryService.record(existingUser, existingUser.getPassword());

        existingUser.setPassword(
                hashSecurity.hash(dto.getNewPassword())
        );

        existingUser.setChangeCredential(new Date());

        redisService.delete(ALL_USERS_KEY);

        redisService.delete(
                redisService.otpKey(existingUser.getEmail(), FORGET_PASSWORD_TYPE)
        );

        userRepository.save(existingUser);

        log.info("Password reset successfully - userId: {}", id);

        return Map.of(
                "message", "Password Changed successfully "
        );
    }

    public Map<String, String> resendForgetPasswordOtp(ResendOtpDto dto, String token) {

        Claims claims = authentication.auth(token, false);

        Long id = claims.get("id", Long.class);

        log.info("Resend forget password OTP started - userId: {}", id);

        Optional<User> userExist = userRepository.findByIdAndIsDeletedFalse(id);

        if (userExist.isEmpty()) {
            log.warn("Resend forget password OTP failed - user not found: {}", id);

            throw new AppException(
                    "User not found",
                    HttpStatus.NOT_FOUND
            );
        }

        User user = userExist.get();

        resendOtp.sendOtp(
                dto.getEmail(),
                user.getUsername(),
                "Forget Password Reset",
                FORGET_PASSWORD_TYPE
        );

        log.info("Forget password OTP resent successfully - userId: {}", id);

        return Map.of(
                "message", "reset forget password otp resent successfully "
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

        Page<User> users = userRepository.findAll(pageable);

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

        Optional<User> userExist = userRepository.findByIdAndIsDeletedFalse(id);

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
}
