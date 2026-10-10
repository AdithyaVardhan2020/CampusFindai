package com.campusfind.controller;

import com.campusfind.dto.RegisterRequest;
import com.campusfind.dto.UserResponse;
import com.campusfind.entity.Role;
import com.campusfind.repository.ItemReportRepository;
import com.campusfind.repository.UserRepository;
import com.campusfind.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AdminControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItemReportRepository reportRepository;

    @Autowired
    private UserService userService;

    private MockMvc mockMvc;
    private UserResponse studentUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        reportRepository.deleteAll();
        userRepository.deleteAll();

        // Seed admin user using secure admin provisioning
        userService.createAdminUser("Super Admin", "admin@campus.edu", "AdminSecure123!");

        // Seed student user
        studentUser = userService.registerUser(new RegisterRequest(
                "John Student",
                "john@campus.edu",
                "StudentSecure123!",
                Role.ROLE_STUDENT
        ));
    }

    @Test
    @DisplayName("Administrator can list all registered users")
    void testAdminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(httpBasic("admin@campus.edu", "AdminSecure123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Student user is rejected with 403 Forbidden when accessing admin endpoints")
    void testStudentCannotAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(httpBasic("john@campus.edu", "StudentSecure123!")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to admin endpoint is rejected with 401 Unauthorized")
    void testUnauthenticatedCannotAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Administrator can delete a user account")
    void testAdminCanDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/" + studentUser.id())
                        .with(httpBasic("admin@campus.edu", "AdminSecure123!")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(httpBasic("admin@campus.edu", "AdminSecure123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Administrator can view all reports")
    void testAdminCanListReports() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports")
                        .with(httpBasic("admin@campus.edu", "AdminSecure123!")))
                .andExpect(status().isOk());
    }
}
