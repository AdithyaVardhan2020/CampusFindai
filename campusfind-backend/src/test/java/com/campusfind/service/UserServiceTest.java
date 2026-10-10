package com.campusfind.service;

import com.campusfind.dto.RegisterRequest;
import com.campusfind.dto.UserResponse;
import com.campusfind.entity.Role;
import com.campusfind.entity.User;
import com.campusfind.exception.BadRequestException;
import com.campusfind.repository.ItemReportRepository;
import com.campusfind.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItemReportRepository reportRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("User registration securely hashes raw password with BCrypt")
    void testPasswordIsHashedWithBcrypt() {
        RegisterRequest request = new RegisterRequest(
                "Secure Student",
                "secure@campus.edu",
                "SuperSecretPassword123!",
                Role.ROLE_STUDENT
        );

        UserResponse response = userService.registerUser(request);
        assertThat(response).isNotNull();

        User savedUser = userRepository.findById(response.id()).orElseThrow();

        // Password hash must NOT equal plaintext
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("SuperSecretPassword123!");
        // BCrypt hashes start with $2a$, $2b$, or $2y$
        assertThat(savedUser.getPasswordHash()).startsWith("$2");
        // Verify BCrypt match
        assertThat(passwordEncoder.matches("SuperSecretPassword123!", savedUser.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Duplicate email registration is blocked")
    void testDuplicateEmailBlocked() {
        RegisterRequest request = new RegisterRequest(
                "First Student",
                "duplicate@campus.edu",
                "Password123!",
                Role.ROLE_STUDENT
        );
        userService.registerUser(request);

        RegisterRequest duplicate = new RegisterRequest(
                "Second Student",
                "duplicate@campus.edu",
                "Password456!",
                Role.ROLE_STUDENT
        );

        assertThatThrownBy(() -> userService.registerUser(duplicate))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Public registration strictly ignores requested role and always assigns ROLE_STUDENT")
    void testRegisterUserForcesStudentRoleEvenIfAdminRequested() {
        RegisterRequest request = new RegisterRequest(
                "Attempted Admin",
                "attempted-admin@campus.edu",
                "Password123!",
                Role.ROLE_ADMIN
        );

        UserResponse response = userService.registerUser(request);
        assertThat(response.role()).isEqualTo(Role.ROLE_STUDENT);

        User savedUser = userRepository.findById(response.id()).orElseThrow();
        assertThat(savedUser.getRole()).isEqualTo(Role.ROLE_STUDENT);
    }

    @Test
    @DisplayName("Admin creation properly hashes passwords containing '$' character with BCrypt")
    void testCreateAdminUserWithDollarSignPassword() {
        String passwordWithDollar = "123@$Admin";
        UserResponse response = userService.createAdminUser(
                "System Admin",
                "admin-test@campus.edu",
                passwordWithDollar
        );

        assertThat(response.role()).isEqualTo(Role.ROLE_ADMIN);
        User savedUser = userRepository.findById(response.id()).orElseThrow();

        // Exact match with full password containing $
        assertThat(passwordEncoder.matches(passwordWithDollar, savedUser.getPasswordHash())).isTrue();
        // Truncated password (before $) must NOT match
        assertThat(passwordEncoder.matches("123@", savedUser.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("createAdminIfAbsent is strictly insert-only and preserves existing password hash")
    void testCreateAdminIfAbsentPreservesExistingPasswordHash() {
        String originalPassword = "Original$Password123";
        UserResponse initial = userService.createAdminUser(
                "Existing Admin",
                "existing-admin@campus.edu",
                originalPassword
        );

        String initialHash = userRepository.findById(initial.id()).orElseThrow().getPasswordHash();

        // Attempt to call createAdminIfAbsent with a different password
        UserResponse second = userService.createAdminIfAbsent(
                "Existing Admin",
                "existing-admin@campus.edu",
                "Different$Password456"
        );

        User existingUser = userRepository.findById(second.id()).orElseThrow();
        assertThat(existingUser.getPasswordHash()).isEqualTo(initialHash);
        assertThat(passwordEncoder.matches(originalPassword, existingUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("Different$Password456", existingUser.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("resetAdminPassword explicitly updates password hash with BCrypt for existing administrator")
    void testResetAdminPasswordExplicitlyUpdatesHash() {
        UserResponse admin = userService.createAdminUser(
                "Admin Reset",
                "reset-admin@campus.edu",
                "Old$Password123"
        );

        String newPasswordWithDollar = "123@$Admin";
        userService.resetAdminPassword("reset-admin@campus.edu", newPasswordWithDollar);

        User updatedUser = userRepository.findById(admin.id()).orElseThrow();
        assertThat(passwordEncoder.matches(newPasswordWithDollar, updatedUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("Old$Password123", updatedUser.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("resetAdminPassword rejects password reset if target user is not ROLE_ADMIN")
    void testResetAdminPasswordRejectsStudentAccounts() {
        RegisterRequest studentRequest = new RegisterRequest(
                "Regular Student",
                "student-reset@campus.edu",
                "StudentPassword123",
                Role.ROLE_STUDENT
        );
        UserResponse student = userService.registerUser(studentRequest);

        assertThatThrownBy(() -> userService.resetAdminPassword("student-reset@campus.edu", "HackedPass123!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not have ROLE_ADMIN");

        User unmodifiedStudent = userRepository.findById(student.id()).orElseThrow();
        assertThat(passwordEncoder.matches("StudentPassword123", unmodifiedStudent.getPasswordHash())).isTrue();
    }
}
