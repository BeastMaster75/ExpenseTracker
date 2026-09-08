package com.expensetracker.budget;

import com.expensetracker.budget.repository.BudgetRepository;
import com.expensetracker.common.email.EmailService;
import com.expensetracker.common.security.EncryptAndDecryptSecurity;
import com.expensetracker.common.redis.RedisService;
import com.expensetracker.transaction.repository.TransactionRepository;
import com.expensetracker.user.entity.User;
import com.expensetracker.user.repository.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
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

    // The API decrypts the incoming password, so the client encrypts before it
    // POSTs. Sending plaintext fails in HexFormat, not in validation.
    private static final String ENCRYPTED_PASSWORD =
            EncryptAndDecryptSecurity.encrypt(PASSWORD);

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
        fundingBudgetReady = false;
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

            // Redis is a remote Upstash instance, so a slow round-trip here is
            // a network hiccup, not a test failure -- clearing counters is
            // hygiene, never an assertion.
            forgetQuietly(redisService.maxPasswordKey(email));
            forgetQuietly(redisService.blockPasswordKey(email));

            for (String type : new String[] {"confirm_email", "forget_password"}) {
                forgetQuietly(redisService.otpKey(email, type));
                forgetQuietly(redisService.maxOtpKey(email, type));
                forgetQuietly(redisService.blockOtpKey(email, type));
            }
        }
    }

    private void forgetQuietly(String key) {
        try {
            redisService.delete(key);
        } catch (Exception ignored) {
            // best effort
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
                                + "','password':'" + ENCRYPTED_PASSWORD + "'}")))
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
                                + "','password':'" + ENCRYPTED_PASSWORD + "'}")))
                .andExpect(status().isOk())
                .andReturn();

        // Login hands the token back as an HttpOnly cookie, not in the body.
        Cookie accessToken = login.getResponse().getCookie("accessToken");

        assertThat(accessToken)
                .as("login should set an accessToken cookie")
                .isNotNull();

        setInitialBalance(email, STARTING_BALANCE);

        return accessToken.getValue();
    }

    // Budgets are now capped by initialBalance + income, so a probe user needs
    // an opening balance before any of these limits are allowed.
    //
    // Written straight to the row rather than through POST /users/initialBalance
    // because SetInitialBalance puts @NotBlank on a BigDecimal, which has no
    // validator for that type.
    private static final String STARTING_BALANCE = "100000";

    private void setInitialBalance(String email, String amount) {

        User user = userRepository.findByEmail(email).orElseThrow();

        user.setInitialBalance(new BigDecimal(amount));

        userRepository.save(user);
    }

    // Income is booked against a budget and tops up its available-to-use, so
    // funding is parked in a dedicated "Salary" budget. That keeps the figures
    // of whichever budget a test is actually asserting on untouched.
    private static final String SALARY = "Salary";

    private void fund(String token, String amount) throws Exception {

        if (!fundingBudgetReady) {
            createBudget(token, SALARY, "0");
        }

        mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':" + amount + ",'transactionType':'income',"
                                + "'budgetName':'" + SALARY + "'}")))
                .andExpect(status().isOk());

        fundingBudgetReady = true;
    }

    private boolean fundingBudgetReady;

    private String spend(String token, String amount, String budgetName) throws Exception {

        return mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':" + amount + ",'transactionType':'expense',"
                                + "'budgetName':'" + budgetName + "'}")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String createBudget(String token, String name, String limit) throws Exception {

        return mockMvc.perform(post("/budgets")
                        .cookie(new Cookie("accessToken", token))
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
                        .cookie(new Cookie("accessToken", token))
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
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'Groceries','amountLimit':9999}")))
                .andExpect(status().isConflict());
    }

    @Test
    void reportsValidationErrors() throws Exception {

        String token = signUpAndLogin(OWNER);

        mockMvc.perform(post("/budgets")
                        .cookie(new Cookie("accessToken", token))
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
                        .cookie(new Cookie("accessToken", intruderToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/budgets/" + budgetId)
                        .cookie(new Cookie("accessToken", intruderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'Hijacked','amountLimit':1}")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/budgets/" + budgetId)
                        .cookie(new Cookie("accessToken", intruderToken)))
                .andExpect(status().isNotFound());

        // the intruder's own listing stays empty
        mockMvc.perform(get("/budgets")
                        .cookie(new Cookie("accessToken", intruderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listsOnlyOwnBudgets() throws Exception {

        String ownerToken = signUpAndLogin(OWNER);

        createBudget(ownerToken, "Groceries", "4000");
        createBudget(ownerToken, "Transport", "1500");

        mockMvc.perform(get("/budgets")
                        .cookie(new Cookie("accessToken", ownerToken)))
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
        fund(token, "1000");

        mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':250,'transactionType':'expense',"
                                + "'budgetName':'Groceries','description':'weekly shop'}")))
                .andExpect(status().isOk());

        spend(token, "150", "Groceries");

        // spending accumulates across expenses
        mockMvc.perform(get("/budgets")
                        .cookie(new Cookie("accessToken", token)))
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
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':250,'transactionType':'expense',"
                                + "'budgetName':'NoSuchBudget'}")))
                .andExpect(status().isNotFound());
    }

    @Test
    void expenseWithoutBudgetNameIsRejected() throws Exception {

        String token = signUpAndLogin(OWNER);

        mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
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
                        .cookie(new Cookie("accessToken", intruderToken))
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

        fund(token, "1000");

        spend(token, "250", "Groceries");

        // renaming and re-limiting must not reset what has been spent
        mockMvc.perform(patch("/budgets/" + budgetId)
                        .cookie(new Cookie("accessToken", token))
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
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/budgets/" + budgetId)
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/budgets")
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Re-creating under the freed name revives the soft-deleted row rather
        // than inserting a second one -- uk_budget_user_name still covers it.
        String revived = createBudget(token, "Groceries", "1000");

        assertThat(extractNumber(revived, "id")).isEqualTo(budgetId);

        mockMvc.perform(get("/budgets")
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amountLimit").value(1000))
                // a revived budget starts its own period clean
                .andExpect(jsonPath("$[0].spending").value(0));
    }

    @Test
    void summaryAggregatesLimitsSpendingAndPercentage() throws Exception {

        String token = signUpAndLogin(OWNER);

        createBudget(token, "Groceries", "4000");
        createBudget(token, "Transport", "1000");

        // income tops up the Salary budget's available-to-use: 0 -> 5000. Its
        // configured limit stays at 0.
        fund(token, "5000");

        spend(token, "1000", "Groceries");
        spend(token, "250", "Transport");

        // 5000 configured + 5000 from income = 10000 ceiling, 1250 spent
        // -> 12.50% used, 8750 left
        mockMvc.perform(get("/budgets/summary")
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLimit").value(5000))
                .andExpect(jsonPath("$.totalAvailableToUse").value(5000))
                .andExpect(jsonPath("$.totalSpending").value(1250))
                .andExpect(jsonPath("$.remaining").value(8750))
                .andExpect(jsonPath("$.percentageUsed").value(12.50))
                .andExpect(jsonPath("$.budgetCount").value(3))
                .andExpect(jsonPath("$.periodMonth")
                        .value(LocalDate.now().withDayOfMonth(1).toString()));
    }

    @Test
    void incomeTopsUpTheBudgetItIsBookedAgainst() throws Exception {

        String token = signUpAndLogin(OWNER);

        String budget = createBudget(token, "Groceries", "400");
        String budgetId = extractNumber(budget, "id");

        mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':600,'transactionType':'income',"
                                + "'budgetName':'Groceries'}")))
                .andExpect(status().isOk());

        // the configured limit is static; income lands in availableToUse and
        // raises the ceiling the budget is measured against
        mockMvc.perform(get("/budgets/" + budgetId)
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountLimit").value(400))
                .andExpect(jsonPath("$.availableToUse").value(600))
                .andExpect(jsonPath("$.spending").value(0))
                .andExpect(jsonPath("$.remaining").value(1000));

        User user = userRepository.findByEmail(OWNER).orElseThrow();

        assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(user.getTotalIncome()).isEqualByComparingTo(BigDecimal.valueOf(600));
    }

    @Test
    void deletingIncomeTakesItsFundingBackOutOfTheBudget() throws Exception {

        String token = signUpAndLogin(OWNER);

        String budget = createBudget(token, "Groceries", "400");
        String budgetId = extractNumber(budget, "id");

        String income = mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':600,'transactionType':'income',"
                                + "'budgetName':'Groceries'}")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(delete("/transactions/" + extractNumber(income, "id"))
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk());

        // the top-up is gone; the configured limit never moved
        mockMvc.perform(get("/budgets/" + budgetId)
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountLimit").value(400))
                .andExpect(jsonPath("$.availableToUse").value(0))
                .andExpect(jsonPath("$.remaining").value(400));
    }

    @Test
    void summaryIsZeroWithoutBudgets() throws Exception {

        String token = signUpAndLogin(OWNER);

        // no budgets means no division by zero
        mockMvc.perform(get("/budgets/summary")
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLimit").value(0))
                .andExpect(jsonPath("$.totalSpending").value(0))
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(jsonPath("$.percentageUsed").value(0))
                .andExpect(jsonPath("$.budgetCount").value(0));
    }

    @Test
    void aBudgetCannotBeSpentPastItsCeiling() throws Exception {

        String token = signUpAndLogin(OWNER);

        String budget = createBudget(token, "Groceries", "100");

        // funding lands in Salary, so the balance covers the expense even
        // though Groceries itself has no room for it
        fund(token, "1000");

        mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':150,'transactionType':'expense',"
                                + "'budgetName':'Groceries'}")))
                .andExpect(status().isBadRequest());

        // rejected outright, so nothing was recorded against the budget
        mockMvc.perform(get("/budgets/" + extractNumber(budget, "id"))
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spending").value(0))
                .andExpect(jsonPath("$.amountLimit").value(100))
                .andExpect(jsonPath("$.remaining").value(100));

        // spending right up to 100% is still fine
        spend(token, "100", "Groceries");

        mockMvc.perform(get("/budgets/" + extractNumber(budget, "id"))
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spending").value(100))
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(jsonPath("$.percentageUsed").value(100.00));
    }

    @Test
    void incomeOnABudgetUnlocksSpendingPastItsConfiguredLimit() throws Exception {

        String token = signUpAndLogin(OWNER);

        createBudget(token, "Groceries", "100");

        // 150 is over the configured limit of 100 on its own
        mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':200,'transactionType':'income',"
                                + "'budgetName':'Groceries'}")))
                .andExpect(status().isOk());

        // but the ceiling is now 100 + 200, so it fits
        spend(token, "150", "Groceries");

        mockMvc.perform(get("/budgets")
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amountLimit").value(100))
                .andExpect(jsonPath("$[0].availableToUse").value(200))
                .andExpect(jsonPath("$[0].spending").value(150))
                .andExpect(jsonPath("$[0].remaining").value(150));
    }

    @Test
    void budgetsCannotTotalMoreThanTheOpeningBalancePlusIncome() throws Exception {

        String token = signUpAndLogin(INTRUDER);

        setInitialBalance(INTRUDER, "1000");

        createBudget(token, "Groceries", "600");

        // 600 + 500 would be 1100, past the 1000 opening balance
        mockMvc.perform(post("/budgets")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'Transport','amountLimit':500}")))
                .andExpect(status().isBadRequest());

        // 400 fits exactly
        createBudget(token, "Transport", "400");

        // income raises the allowance, so the earlier 500 now has room
        mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':500,'transactionType':'income',"
                                + "'budgetName':'Groceries'}")))
                .andExpect(status().isOk());

        createBudget(token, "Travel", "500");

        // and the opening balance itself was never rewritten
        User user = userRepository.findByEmail(INTRUDER).orElseThrow();

        assertThat(user.getInitialBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    void summaryCountsOnlyOwnLiveBudgets() throws Exception {

        String ownerToken = signUpAndLogin(OWNER);

        String budget = createBudget(ownerToken, "Groceries", "4000");
        createBudget(ownerToken, "Transport", "1000");

        // a soft-deleted budget drops out of the totals
        mockMvc.perform(delete("/budgets/" + extractNumber(budget, "id"))
                        .cookie(new Cookie("accessToken", ownerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/budgets/summary")
                        .cookie(new Cookie("accessToken", ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLimit").value(1000))
                .andExpect(jsonPath("$.budgetCount").value(1));

        // and another user's budgets never leak in
        String intruderToken = signUpAndLogin(INTRUDER);

        mockMvc.perform(get("/budgets/summary")
                        .cookie(new Cookie("accessToken", intruderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLimit").value(0))
                .andExpect(jsonPath("$.budgetCount").value(0));
    }

    @Test
    void summaryRequiresAuthentication() throws Exception {

        mockMvc.perform(get("/budgets/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void budgetCarriesItsOwnPercentageAndRemaining() throws Exception {

        String token = signUpAndLogin(OWNER);

        String budget = createBudget(token, "Groceries", "400");
        String budgetId = extractNumber(budget, "id");

        // a fresh budget is 0% used
        mockMvc.perform(get("/budgets/" + budgetId)
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentageUsed").value(0))
                .andExpect(jsonPath("$.remaining").value(400));

        fund(token, "1000");
        spend(token, "100", "Groceries");

        mockMvc.perform(get("/budgets/" + budgetId)
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spending").value(100))
                .andExpect(jsonPath("$.percentageUsed").value(25.00))
                .andExpect(jsonPath("$.remaining").value(300));

        // and the same figures come back in the list
        mockMvc.perform(get("/budgets")
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].percentageUsed").value(25.00))
                .andExpect(jsonPath("$[0].remaining").value(300));
    }

    @Test
    void percentageSurvivesAZeroLimit() throws Exception {

        String token = signUpAndLogin(OWNER);

        // a zero limit must not blow up with a division by zero
        String budget = createBudget(token, "Unbudgeted", "0");

        mockMvc.perform(get("/budgets/" + extractNumber(budget, "id"))
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentageUsed").value(0))
                .andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    void expenseCannotOverdrawTheBalance() throws Exception {

        String token = signUpAndLogin(OWNER);

        createBudget(token, "Groceries", "4000");

        fund(token, "100");

        mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':101,'transactionType':'expense',"
                                + "'budgetName':'Groceries'}")))
                .andExpect(status().isBadRequest());

        // the rejected expense must leave nothing behind
        User user = userRepository.findByEmail(OWNER).orElseThrow();

        assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(user.getTotalExpense()).isEqualByComparingTo(BigDecimal.ZERO);

        mockMvc.perform(get("/budgets")
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(jsonPath("$[0].spending").value(0));
    }

    @Test
    void rejectsInvalidTransactionInput() throws Exception {

        String token = signUpAndLogin(OWNER);

        createBudget(token, "Groceries", "4000");

        // negative amount
        mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':-5,'transactionType':'income',"
                                + "'budgetName':'Groceries'}")))
                .andExpect(status().isBadRequest());

        // zero amount
        mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':0,'transactionType':'income',"
                                + "'budgetName':'Groceries'}")))
                .andExpect(status().isBadRequest());

        // missing type
        mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':10,'budgetName':'Groceries'}")))
                .andExpect(status().isBadRequest());

        // unknown type
        mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':10,'transactionType':'refund',"
                                + "'budgetName':'Groceries'}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editingAnExpenseRestatesSpendingAndBalance() throws Exception {

        String token = signUpAndLogin(OWNER);

        createBudget(token, "Groceries", "4000");

        fund(token, "1000");

        String tx = spend(token, "250", "Groceries");

        mockMvc.perform(patch("/transactions/" + extractNumber(tx, "id"))
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':400}")))
                .andExpect(status().isOk());

        // spending follows the edit rather than double-counting
        mockMvc.perform(get("/budgets")
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(jsonPath("$[0].spending").value(400))
                .andExpect(jsonPath("$[0].percentageUsed").value(10.00));

        User user = userRepository.findByEmail(OWNER).orElseThrow();

        assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(user.getTotalExpense()).isEqualByComparingTo(BigDecimal.valueOf(400));
    }

    @Test
    void movingAnExpenseBetweenBudgetsShiftsSpending() throws Exception {

        String token = signUpAndLogin(OWNER);

        createBudget(token, "Groceries", "4000");
        createBudget(token, "Transport", "1000");

        fund(token, "1000");

        String tx = spend(token, "250", "Groceries");

        mockMvc.perform(patch("/transactions/" + extractNumber(tx, "id"))
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'budgetName':'Transport'}")))
                .andExpect(status().isOk());

        // ordered by name: Groceries, Salary, Transport
        mockMvc.perform(get("/budgets")
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(jsonPath("$[0].name").value("Groceries"))
                .andExpect(jsonPath("$[0].spending").value(0))
                .andExpect(jsonPath("$[2].name").value("Transport"))
                .andExpect(jsonPath("$[2].spending").value(250));
    }

    @Test
    void deletingAnExpenseGivesBackSpendingAndBalance() throws Exception {

        String token = signUpAndLogin(OWNER);

        createBudget(token, "Groceries", "4000");

        fund(token, "1000");

        String tx = spend(token, "250", "Groceries");

        mockMvc.perform(delete("/transactions/" + extractNumber(tx, "id"))
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/budgets")
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(jsonPath("$[0].spending").value(0))
                .andExpect(jsonPath("$[0].percentageUsed").value(0));

        User user = userRepository.findByEmail(OWNER).orElseThrow();

        assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(user.getTotalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void cannotDeleteIncomeThatHasAlreadyBeenSpent() throws Exception {

        String token = signUpAndLogin(OWNER);

        createBudget(token, "Groceries", "4000");
        createBudget(token, SALARY, "0");

        String income = mockMvc.perform(post("/transactions")
                        .cookie(new Cookie("accessToken", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'amount':1000,'transactionType':'income',"
                                + "'budgetName':'" + SALARY + "'}")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        spend(token, "800", "Groceries");

        // removing the income would leave the balance at -800
        mockMvc.perform(delete("/transactions/" + extractNumber(income, "id"))
                        .cookie(new Cookie("accessToken", token)))
                .andExpect(status().isBadRequest());

        User user = userRepository.findByEmail(OWNER).orElseThrow();

        assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(user.getTotalIncome()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    void cannotTouchAnotherUsersTransaction() throws Exception {

        String ownerToken = signUpAndLogin(OWNER);

        createBudget(ownerToken, "Groceries", "4000");
        fund(ownerToken, "1000");

        String tx = spend(ownerToken, "250", "Groceries");
        String txId = extractNumber(tx, "id");

        String intruderToken = signUpAndLogin(INTRUDER);

        mockMvc.perform(get("/transactions/" + txId)
                        .cookie(new Cookie("accessToken", intruderToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/transactions/" + txId)
                        .cookie(new Cookie("accessToken", intruderToken)))
                .andExpect(status().isNotFound());

        // the owner's figures are untouched
        mockMvc.perform(get("/budgets")
                        .cookie(new Cookie("accessToken", ownerToken)))
                .andExpect(jsonPath("$[0].spending").value(250));
    }

    private String extractNumber(String json, String field) {
        Matcher match = Pattern
                .compile("\"" + field + "\"\\s*:\\s*(\\d+)")
                .matcher(json);
        assertThat(match.find()).isTrue();
        return match.group(1);
    }
}
