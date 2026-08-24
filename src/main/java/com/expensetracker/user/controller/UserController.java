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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserService userService;
    private final Authentication authentication;

    public UserController(UserService userService, Authentication authentication) {
        this.userService = userService;
        this.authentication = authentication;
    }

    private String bearerToken(String authorization) {

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AppException(
                    "Authorization header is required",
                    HttpStatus.UNAUTHORIZED
            );
        }

        return authorization.substring(BEARER_PREFIX.length());
    }

    @PostMapping
    public User createUser(@Valid @RequestBody CreateUserDto user) {
        return userService.createUser(user);
    }

//    @PostMapping("/confirmEmail")
//    public Map<String, String> confirmEmail(@Valid @RequestBody ConfirmEmailDto dto) {
//        return userService.confirmEmail(dto);
//    }

    @GetMapping("/confirmEmail")
    public Map<String, String> confirmEmail(
            @RequestParam String token
    ) {
        return userService.confirmEmail(token);
    }

    @PostMapping("/resendConfirmationLink")
    public Map<String, String> resendConfirmationLink(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization
    ) {
        return userService.resendConfirmationLink(
                authorization
        );
    }

    @PostMapping("/login")
    public Map<String, String> signIn(@Valid @RequestBody SignInDto user) {
        return userService.signIn(user);
    }

    @PostMapping("/refresh")
    public Map<String, String> refresh(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return userService.refresh(bearerToken(authorization));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {

        Claims claims = authentication.auth(bearerToken(authorization), false);

        Long id = claims.get("id", Long.class);

        User user = userService.getUserById(id);

        return userService.signOut(user);
    }

    @PatchMapping("/updateInfo")
    public User updateUserInfo(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateUserInfoDto user
    ) {
        return userService.updateUserInfo(user, bearerToken(authorization));
    }

    @PatchMapping("/updatePassword")
    public Map<String, String> updatePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdatePasswordDto dto
    ) {
        return userService.updatePassword(dto, bearerToken(authorization));
    }

    @PatchMapping("/resetPassword")
    public Map<String, String> forgetPassword(
            @Valid @RequestBody ForgetPasswordDto dto
    ) {
        return userService.forgetPassword(dto);
    }
//    @PostMapping("/resendConfirmationLink")
//    public Map<String, String> resendConfirmationLink(
//            @RequestHeader("Authorization") String authorization
//    ) {
//        return userService.resendConfirmationLink(
//                authorization.replace("Bearer ", "")
//        );
//    }

    @DeleteMapping("/softDelete")
    public Map<String, String> softDeleteUser(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return userService.softDeleteUser(bearerToken(authorization));
    }

    @GetMapping("/getUsers")
    public Page<User> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return userService.getUsers(page, size);
    }

    @GetMapping("/getCachedUsers")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable long id) {
        return userService.getUserById(id);
    }


    @GetMapping("/balance")
    public BalanceDto getBalanceAndIncomeAndExpense(
            @RequestHeader("Authorization") String authorization
    ) {
        return userService.getBalanceAndIncomeAndExpense(authorization);
    }
}




