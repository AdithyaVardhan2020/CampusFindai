package com.campusfind.service;

import com.campusfind.dto.CreateReportRequest;
import com.campusfind.dto.ReportResponse;
import com.campusfind.dto.UpdateReportStatusRequest;
import com.campusfind.entity.ItemReport;
import com.campusfind.entity.ReportStatus;
import com.campusfind.entity.ReportType;
import com.campusfind.entity.Role;
import com.campusfind.entity.User;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.repository.ItemReportRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ItemReportService {

    private final ItemReportRepository reportRepository;
    private final UserService userService;

    public ItemReportService(ItemReportRepository reportRepository, UserService userService) {
        this.reportRepository = reportRepository;
        this.userService = userService;
    }

    public ReportResponse createReport(CreateReportRequest request, String reporterEmail) {
        User user = userService.getUserEntityByEmail(reporterEmail);

        ItemReport report = new ItemReport(
                request.title().trim(),
                request.description().trim(),
                request.category() != null ? request.category().trim() : null,
                request.location() != null ? request.location().trim() : null,
                request.type(),
                request.imageUrl() != null ? request.imageUrl().trim() : null,
                user
        );

        ItemReport saved = reportRepository.save(report);
        return ReportResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getReports(ReportType type, ReportStatus status) {
        List<ItemReport> reports;

        if (type != null && status != null) {
            reports = reportRepository.findByTypeAndStatus(type, status);
        } else if (type != null) {
            reports = reportRepository.findByType(type);
        } else if (status != null) {
            reports = reportRepository.findByStatus(status);
        } else {
            reports = reportRepository.findAllByOrderByCreatedAtDesc();
        }

        return reports.stream()
                .map(ReportResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse getReportById(Long id) {
        ItemReport report = findEntityById(id);
        return ReportResponse.fromEntity(report);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getReportsByUser(Long userId) {
        return reportRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ReportResponse::fromEntity)
                .toList();
    }

    public ReportResponse updateReportStatus(Long id, UpdateReportStatusRequest request, String currentUserEmail) {
        ItemReport report = findEntityById(id);
        User currentUser = userService.getUserEntityByEmail(currentUserEmail);

        boolean isOwner = report.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You are not authorized to update this report.");
        }

        report.setStatus(request.status());
        ItemReport updated = reportRepository.save(report);
        return ReportResponse.fromEntity(updated);
    }

    public void deleteReport(Long id, String currentUserEmail) {
        ItemReport report = findEntityById(id);
        User currentUser = userService.getUserEntityByEmail(currentUserEmail);

        boolean isOwner = report.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You are not authorized to delete this report.");
        }

        reportRepository.delete(report);
    }

    private ItemReport findEntityById(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with ID: " + id));
    }
}
