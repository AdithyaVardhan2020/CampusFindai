package com.campusfind.controller;

import com.campusfind.dto.ReportResponse;
import com.campusfind.dto.UserResponse;
import com.campusfind.service.ItemReportService;
import com.campusfind.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;
    private final ItemReportService reportService;

    public AdminController(UserService userService, ItemReportService reportService) {
        this.userService = userService;
        this.reportService = reportService;
    }

    /**
     * GET /api/v1/admin/users
     * List all registered users (strictly requires ROLE_ADMIN).
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * DELETE /api/v1/admin/users/{id}
     * Remove a user account (strictly requires ROLE_ADMIN).
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/admin/reports
     * List all reports across all users and statuses.
     */
    @GetMapping("/reports")
    public ResponseEntity<List<ReportResponse>> getAllReports() {
        return ResponseEntity.ok(reportService.getReports(null, null));
    }
}
