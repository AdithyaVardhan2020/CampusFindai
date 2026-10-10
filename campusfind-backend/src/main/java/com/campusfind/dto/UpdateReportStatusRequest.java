package com.campusfind.dto;

import com.campusfind.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateReportStatusRequest(
        @NotNull(message = "Report status is required")
        ReportStatus status
) {
}
