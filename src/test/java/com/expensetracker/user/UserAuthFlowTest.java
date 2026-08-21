package com.expensetracker.user;

import com.expensetracker.common.email.EmailService;
import com.expensetracker.common.redis.RedisService;
import com.expensetracker.user.repository.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserAuthFlowTest {

    private static final String EMAIL = "authflow-probe@example.com";
    private static final String PASSWORD = "supersecret123";
    private static final int OTP = 123456;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisService redisService;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoSpyBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        reset();
        doReturn(OTP).when(emailService).generateOTP();
        doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
    }

    @AfterEach
    void tearDown() {
        reset();
    }

    // Redis is a shared instance, so clear this probe's keys rather than
    // letting rate-limit counters leak between runs.
    private void reset() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
        redisService.delete(redisService.maxPasswordKey(EMAIL));
        redisService.delete(redisService.blockPasswordKey(EMAIL));
        for (String type : new String[] {"confirm_email", "forget_password"}) {
            redisService.delete(redisService.otpKey(EMAIL, type));
            redisService.delete(redisService.maxOtpKey(EMAIL, type));
            redisService.delete(redisService.blockOtpKey(EMAIL, type));
        }
    }

    private String json(String body) {
        return body.replace('\'', '"');
    }

    @Test
    void fullSignupConfirmLoginRefreshLogoutFlow() throws Exception {

        // 1. sign up
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'username':'Probe','email':'" + EMAIL
                                + "','password':'" + PASSWORD + "'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.isConfirmed").value(false));

        // password must be stored hashed, never in plain text
        String stored = userRepository.findByEmail(EMAIL).orElseThrow().getPassword();
        assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");

        // 2. confirm e-mail with the OTP
        mockMvc.perform(post("/users/confirmEmail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'" + EMAIL + "','otp':'" + OTP + "'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getIsConfirmed()).isTrue();

        // 3. wrong password is rejected
        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'" + EMAIL + "','password':'wrongpassword'}")))
                .andExpect(status().isUnauthorized());

        // 4. log in
        MvcResult login = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'" + EMAIL + "','password':'" + PASSWORD + "'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        String body = login.getResponse().getContentAsString();
        String accessToken = extract(body, "accessToken");
        String refreshToken = extract(body, "refreshToken");

        // 5. access token works on a protected route
        mockMvc.perform(patch("/users/updateInfo")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'username':'ProbeRenamed'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ProbeRenamed"));

        // 6. missing Authorization header -> 401, not a 500
        mockMvc.perform(patch("/users/updateInfo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'username':'Nope'}")))
                .andExpect(status().isUnauthorized());

        // 7. garbage token -> 401
        mockMvc.perform(patch("/users/updateInfo")
                        .header("Authorization", "Bearer not.a.jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'username':'Nope'}")))
                .andExpect(status().isUnauthorized());

        // 8. an access token is NOT accepted as a refresh token (secrets differ)
        mockMvc.perform(post("/users/refresh")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        // 9. refresh token mints a new access token
        mockMvc.perform(post("/users/refresh")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // 10. logout stamps changeCredential
        Thread.sleep(1100); // JWT iat has 1-second resolution
        mockMvc.perform(post("/users/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 11. the old access token is now revoked
        mockMvc.perform(patch("/users/updateInfo")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'username':'AfterLogout'}")))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateSignupIsRejected() throws Exception {

        String payload = json("{'username':'Probe','email':'" + EMAIL
                + "','password':'" + PASSWORD + "'}");

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void validationErrorsAreReported() throws Exception {

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'username':'x','email':'nope','password':'short'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    private String extract(String json, String field) {
        int i = json.indexOf("\"" + field + "\"");
        int start = json.indexOf('"', json.indexOf(':', i)) + 1;
        return json.substring(start, json.indexOf('"', start));
    }
}
