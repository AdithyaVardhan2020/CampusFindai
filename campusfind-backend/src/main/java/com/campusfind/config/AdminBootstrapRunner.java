package com.campusfind.config;

import com.campusfind.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Provisions the initial administrator account at application startup
 * if and only if valid environment variables (CAMPUSFIND_ADMIN_EMAIL and
 * CAMPUSFIND_ADMIN_PASSWORD) are configured.
 * Does not hardcode any credentials or overwrite existing admin records.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserService userService;

    @Value("${campusfind.admin.email:}")
    private String adminEmail;

    @Value("${campusfind.admin.password:}")
    private String adminPassword;

    @Value("${campusfind.admin.full-name:System Administrator}")
    private String adminFullName;

    @Value("${campusfind.admin.reset-password:false}")
    private boolean adminResetPassword;

    public AdminBootstrapRunner(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            logger.info("Admin bootstrap: No CAMPUSFIND_ADMIN_EMAIL / CAMPUSFIND_ADMIN_PASSWORD configured. Skipping initial admin provisioning.");
            return;
        }

        if (adminResetPassword) {
            try {
                userService.resetAdminPassword(adminEmail.trim(), adminPassword);
                logger.warn("Admin bootstrap: Explicit password reset successfully applied for administrator '{}'.", adminEmail.trim());
            } catch (Exception ex) {
                logger.error("Admin bootstrap: Explicit password reset failed for '{}': {}", adminEmail.trim(), ex.getMessage());
            }
        } else {
            try {
                userService.createAdminIfAbsent(adminFullName.trim(), adminEmail.trim(), adminPassword);
                logger.info("Admin bootstrap: Initial administrator account verified/provisioned for '{}'.", adminEmail.trim());
            } catch (Exception ex) {
                logger.error("Admin bootstrap: Failed to initialize administrator account: {}", ex.getMessage());
            }
        }
    }
}
