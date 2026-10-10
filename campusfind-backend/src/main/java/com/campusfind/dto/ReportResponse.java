package com.campusfind.dto;

import com.campusfind.entity.ItemReport;
import com.campusfind.entity.ReportStatus;
import com.campusfind.entity.ReportType;

import java.time.Instant;

public record ReportResponse(
        Long id,
        String title,
        String description,
        String category,
        String location,
        ReportType type,
        ReportStatus status,
        String imageUrl,
        Long reporterId,
        String reporterName,
        String reporterEmail,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReportResponse fromEntity(ItemReport report) {
        return new ReportResponse(
                report.getId(),
                report.getTitle(),
                report.getDescription(),
                report.getCategory(),
                report.getLocation(),
                report.getType(),
                report.getStatus(),
                report.getImageUrl(),
                report.getUser() != null ? report.getUser().getId() : null,
                report.getUser() != null ? report.getUser().getFullName() : null,
                report.getUser() != null ? report.getUser().getEmail() : null,
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }
}
