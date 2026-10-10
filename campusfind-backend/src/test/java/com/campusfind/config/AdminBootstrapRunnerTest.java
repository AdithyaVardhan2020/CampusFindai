package com.campusfind.config;

import com.campusfind.entity.Role;
import com.campusfind.entity.User;
import com.campusfind.repository.ItemReportRepository;
import com.campusfind.repository.UserRepository;
import com.campusfind.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AdminBootstrapRunnerTest {

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
    @DisplayName("AdminBootstrapRunner successfully creates initial administrator when environment config is present")
    void testAdminBootstrapProvisionsAdministrator() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(userService);
        ReflectionTestUtils.setField(runner, "adminEmail", "env-admin@campus.edu");
        ReflectionTestUtils.setField(runner, "adminPassword", "SuperSecureEnvPass123!");
        ReflectionTestUtils.setField(runner, "adminFullName", "Environment Administrator");

        runner.run(new DefaultApplicationArguments());

        User admin = userRepository.findByEmailIgnoreCase("env-admin@campus.edu").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(admin.getFullName()).isEqualTo("Environment Administrator");
        assertThat(passwordEncoder.matches("SuperSecureEnvPass123!", admin.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("AdminBootstrapRunner is idempotent and does not fail if admin already exists")
    void testAdminBootstrapIsIdempotent() {
        // Pre-create admin
        userService.createAdminUser("Existing Admin", "existing-admin@campus.edu", "Pass1!");

        AdminBootstrapRunner runner = new AdminBootstrapRunner(userService);
        ReflectionTestUtils.setField(runner, "adminEmail", "existing-admin@campus.edu");
        ReflectionTestUtils.setField(runner, "adminPassword", "Pass1!");
        ReflectionTestUtils.setField(runner, "adminFullName", "Existing Admin");

        runner.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("AdminBootstrapRunner skips provisioning if environment variables are not supplied")
    void testAdminBootstrapSkipsWhenConfigMissing() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(userService);
        ReflectionTestUtils.setField(runner, "adminEmail", "");
        ReflectionTestUtils.setField(runner, "adminPassword", "");

        runner.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(0);
    }
}
