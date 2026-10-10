package com.campusfind.controller;

import com.campusfind.dto.RegisterRequest;
import com.campusfind.entity.Role;
import com.campusfind.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.campusfind.repository.ItemReportRepository reportRepository;

    @Autowired
    private com.campusfind.service.UserService userService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper();
        reportRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Public user registration creates user and returns 201 with sanitized metadata")
    void testRegisterUserSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Alice Johnson",
                "alice@campus.edu",
                "Password123!",
                Role.ROLE_STUDENT
        );

        String responseJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.fullName").value("Alice Johnson"))
                .andExpect(jsonPath("$.email").value("alice@campus.edu"))
                .andExpect(jsonPath("$.role").value("ROLE_STUDENT"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Security check: password or passwordHash must NEVER be in response
        assertThat(responseJson.toLowerCase()).doesNotContain("password");
        assertThat(responseJson.toLowerCase()).doesNotContain("hash");
    }

    @Test
    @DisplayName("Registration fails with 400 Bad Request when email is already registered")
    void testRegisterUserDuplicateEmailFails() throws Exception {
        RegisterRequest request1 = new RegisterRequest(
                "Bob Smith",
                "bob@campus.edu",
                "Password123!",
                Role.ROLE_STUDENT
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        RegisterRequest requestDuplicate = new RegisterRequest(
                "Bob Duplicate",
                "bob@campus.edu",
                "AnotherPassword!",
                Role.ROLE_STUDENT
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDuplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("An account with email bob@campus.edu already exists."));
    }

    @Test
    @DisplayName("Registration fails with 400 when validation constraints are violated")
    void testRegisterUserValidationFails() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest(
                "", // Blank full name
                "not-an-email", // Invalid email
                "123", // Too short password (< 6 chars)
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me returns current user profile when authenticated")
    void testGetCurrentUserProfileWhenAuthenticated() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Carol Davis",
                "carol@campus.edu",
                "CarolSecurePass123",
                Role.ROLE_STUDENT
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(httpBasic("carol@campus.edu", "CarolSecurePass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Carol Davis"))
                .andExpect(jsonPath("$.email").value("carol@campus.edu"))
                .andExpect(jsonPath("$.role").value("ROLE_STUDENT"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me returns 401 without WWW-Authenticate header when unauthenticated")
    void testGetCurrentUserProfileFailsWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me returns 401 without WWW-Authenticate header on invalid password")
    void testLoginWithInvalidPasswordFailsWithoutBrowserPrompt() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "David Miller",
                "david@campus.edu",
                "CorrectPassword123!",
                Role.ROLE_STUDENT
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(httpBasic("david@campus.edu", "WrongPassword!")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me returns 401 without WWW-Authenticate header for non-existent user")
    void testLoginWithNonExistentUserFailsWithoutBrowserPrompt() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(httpBasic("nonexistent@campus.edu", "SomePassword123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Public registration cannot escalate privileges: requesting ROLE_ADMIN is forced to ROLE_STUDENT")
    void testPublicRegistrationCannotSelectAdminRole() throws Exception {
        RegisterRequest maliciousRequest = new RegisterRequest(
                "Attacker",
                "attacker@campus.edu",
                "HackerPass123!",
                Role.ROLE_ADMIN
        );

        // 1. Verify API response forces ROLE_STUDENT
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_STUDENT"));

        // 2. Verify Database entity actually has ROLE_STUDENT
        com.campusfind.entity.User savedUser = userRepository.findByEmailIgnoreCase("attacker@campus.edu").orElseThrow();
        assertThat(savedUser.getRole()).isEqualTo(Role.ROLE_STUDENT);

        // 3. Verify user cannot access admin endpoints
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(httpBasic("attacker@campus.edu", "HackerPass123!")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin authentication succeeds with literal password containing '$' and has admin privileges")
    void testAdminLoginWithDollarSignPasswordSucceeds() throws Exception {
        String dollarPassword = "123@$Admin";
        userService.createAdminUser(
                "Super Admin",
                "admin-dollar@campus.edu",
                dollarPassword
        );

        // 1. Verify /auth/me returns 200 with ROLE_ADMIN
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(httpBasic("admin-dollar@campus.edu", dollarPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin-dollar@campus.edu"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));

        // 2. Verify admin endpoint /api/v1/admin/users is accessible
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(httpBasic("admin-dollar@campus.edu", dollarPassword)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin authentication fails with 401 when password is truncated or missing '$' portion")
    void testAdminLoginWithTruncatedPasswordFails() throws Exception {
        String fullDollarPassword = "123@$Admin";
        userService.createAdminUser(
                "Super Admin",
                "admin-truncated@campus.edu",
                fullDollarPassword
        );

        // Truncated password (mimicking PowerShell variable expansion bug) must be rejected
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(httpBasic("admin-truncated@campus.edu", "123@")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }
}
