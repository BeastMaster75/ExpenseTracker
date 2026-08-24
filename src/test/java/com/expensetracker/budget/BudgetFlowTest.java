package com.expensetracker.budget;

import com.expensetracker.budget.repository.BudgetRepository;
import com.expensetracker.common.email.EmailService;
import com.expensetracker.common.redis.RedisService;
import com.expensetracker.transaction.repository.TransactionRepository;
import com.expensetracker.user.entity.User;
import com.expensetracker.user.repository.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BudgetFlowTest {

    private static final String OWNER = "budget-owner-probe@example.com";
    private static final String INTRUDER = "budget-intruder-probe@example.com";
    private static final String PASSWORD = "Supersecret123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RedisService redisService;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoSpyBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        cleanUp();
        doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    // Postgres and Redis are shared between runs, so clear this probe's rows
    // and rate-limit counters rather than letting them leak.
    private void cleanUp() {

        for (String email : new String[] {OWNER, INTRUDER}) {

            userRepository.findByEmail(email).ifPresent(user -> {
                transactionRepository.deleteAll(
                        transactionRepository.findAllByUserAndIsDeletedFalseOrderByCreatedAtDesc(user, false)
                );
                // every row, including soft-deleted ones -- they still hold the
                // user_id foreign key
                budgetRepository.deleteAll(
                        budgetRepository.findAllByUserId(user.getId())
                );
                userRepository.delete(user);
            });

            redisService.delete(redisService.maxPasswordKey(email));
            redisService.delete(redisService.blockPasswordKey(email));

            for (String type : new String[] {"confirm_email", "forget_password"}) {
                redisService.delete(redisService.otpKey(email, type));
                redisService.delete(redisService.maxOtpKey(email, type));
                redisService.delete(redisService.blockOtpKey(email, type));
            }
        }
    }

    private String json(String body) {
        return body.replace('\'', '"');
    }

    // Signs up, follows the e-mailed confirmation link, logs in, and hands back
    // the access token -- budget endpoints are useless without one.
    private String signUpAndLogin(String email) throws Exception {

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'username':'Probe','email':'" + email
                                + "','password':'" + PASSWORD + "'}")))
                .andExpect(status().isOk());

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);

        verify(emailService)
                .sendEmail(eq(email), eq("Confirm your email"), html.capture());

        Matcher link = Pattern
                .compile("confirmEmail\\?token=([^\"&\\s]+)")
                .matcher(html.getValue());

        assertThat(link.find()).isTrue();

        mockMvc.perform(get("/users/confirmEmail").param("token", link.group(1)))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'" + email
                                + "','password':'" + PASSWORD + "'}")))
                .andExpect(status().isOk())
                .andReturn();

        return extract(login.getResponse().getContentAsString(), "accessToken");
    }

    private String extract(String json, String field) {
        int i = json.indexOf("\"" + field + "\"");
        int start = json.indexOf('"', json.indexOf(':', i)) + 1;
        return json.substring(start, json.indexOf('"', start));
    }

    private String createBudget(String token, String name, String limit) throws Exception {

        return mockMvc.perform(post("/budgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'" + name + "','amountLimit':" + limit + "}")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void createsBudgetWithZeroSpendingAndCurrentPeriod() throws Exception {

        String token = signUpAndLogin(OWNER);

        mockMvc.perform(post("/budgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'Groceries','amountLimit':4000}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Groceries"))
                .andExpect(jsonPath("$.amountLimit").value(4000))
                // spending is server-owned: always starts empty
                .andExpect(jsonPath("$.spending").value(0))
                // periodMonth defaults to the 1st of the current month
                .andExpect(jsonPath("$.periodMonth")
                        .value(LocalDate.now().withDayOfMonth(1).toString()));
    }

    @Test
    void rejectsDuplicateNameForSameUser() throws Exception {

        String token = signUpAndLogin(OWNER);

        createBudget(token, "Groceries", "4000");

        mockMvc.perform(post("/budgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'Groceries','amountLimit':9999}")))
                .andExpect(status().isConflict());
    }

    @Test
    void reportsValidationErrors() throws Exception {

        String token = signUpAndLogin(OWNER);

        mockMvc.perform(post("/budgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'','amountLimit':-5}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.amountLimit").exists());
    }

    @Test
    void requiresAuthenticationOnEveryEndpoint() throws Exception {

        mockMvc.perform(post("/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'Groceries','amountLimit':4000}")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/budgets"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/budgets/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/budgets/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotExposeAnotherUsersBudget() throws Exception {

        String ownerToken = signUpAndLogin(OWNER);
        String budget = createBudget(ownerToken, "Groceries", "4000");
        String budgetId = extractNumber(budget, "id");

        String intruderToken = signUpAndLogin(INTRUDER);

        // 404 rather than 403 so the endpoint does not leak which ids exist
        mockMvc.perform(get("/budgets/" + budgetId)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/budgets/" + budgetId)
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'Hijacked','amountLimit':1}")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/budgets/" + budgetId)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());

        // the intruder's own listing stays empty
        mockMvc.perform(get("/budgets")
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listsOnlyOwnBudgets() throws Exception {

        String ownerToken = signUpAndLogin(OWNER);

        createBudget(ownerToken, "Groceries", "4000");
        createBudget(ownerToken, "Transport", "1500");

        mockMvc.perform(get("/budgets")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Groceries"))
                .andExpect(jsonPath("$[1].name").value("Transport"));
    }

    @Test
    void expenseAddsToBudgetSpending() throws Exception {

        String token = signUpAndLogin(OWNER);

        createBudget(token, "Groceries", "4000");

        // fund the account first so the balance maths is observable
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':1000,'transactionType':'income'}")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':250,'transactionType':'expense',"
                                + "'budgetName':'Groceries','description':'weekly shop'}")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':150,'transactionType':'expense',"
                                + "'budgetName':'Groceries'}")))
                .andExpect(status().isOk());

        // spending accumulates across expenses
        mockMvc.perform(get("/budgets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spending").value(400));

        User user = userRepository.findByEmail(OWNER).orElseThrow();

        assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(user.getTotalIncome()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(user.getTotalExpense()).isEqualByComparingTo(BigDecimal.valueOf(400));
    }

    @Test
    void expenseAgainstUnknownBudgetIsRejected() throws Exception {

        String token = signUpAndLogin(OWNER);

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':250,'transactionType':'expense',"
                                + "'budgetName':'NoSuchBudget'}")))
                .andExpect(status().isNotFound());
    }

    @Test
    void expenseWithoutBudgetNameIsRejected() throws Exception {

        String token = signUpAndLogin(OWNER);

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':250,'transactionType':'expense'}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expenseCannotTouchAnotherUsersBudget() throws Exception {

        String ownerToken = signUpAndLogin(OWNER);
        createBudget(ownerToken, "Groceries", "4000");

        String intruderToken = signUpAndLogin(INTRUDER);

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':250,'transactionType':'expense',"
                                + "'budgetName':'Groceries'}")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatePreservesAccumulatedSpending() throws Exception {

        String token = signUpAndLogin(OWNER);

        String budget = createBudget(token, "Groceries", "4000");
        String budgetId = extractNumber(budget, "id");

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':250,'transactionType':'expense',"
                                + "'budgetName':'Groceries'}")))
                .andExpect(status().isOk());

        // renaming and re-limiting must not reset what has been spent
        mockMvc.perform(patch("/budgets/" + budgetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'Food','amountLimit':5000}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Food"))
                .andExpect(jsonPath("$.amountLimit").value(5000))
                .andExpect(jsonPath("$.spending").value(250));
    }

    @Test
    void softDeletedBudgetDisappears() throws Exception {

        String token = signUpAndLogin(OWNER);

        String budget = createBudget(token, "Groceries", "4000");
        String budgetId = extractNumber(budget, "id");

        mockMvc.perform(delete("/budgets/" + budgetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/budgets/" + budgetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/budgets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Re-creating under the freed name revives the soft-deleted row rather
        // than inserting a second one -- uk_budget_user_name still covers it.
        String revived = createBudget(token, "Groceries", "1000");

        assertThat(extractNumber(revived, "id")).isEqualTo(budgetId);

        mockMvc.perform(get("/budgets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amountLimit").value(1000))
                // a revived budget starts its own period clean
                .andExpect(jsonPath("$[0].spending").value(0));
    }

    private String extractNumber(String json, String field) {
        Matcher match = Pattern
                .compile("\"" + field + "\"\\s*:\\s*(\\d+)")
                .matcher(json);
        assertThat(match.find()).isTrue();
        return match.group(1);
    }
}
