package com.campusfind.dto;

import com.campusfind.entity.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title cannot exceed 150 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description,

        @Size(max = 60, message = "Category cannot exceed 60 characters")
        String category,

        @Size(max = 150, message = "Location cannot exceed 150 characters")
        String location,

        @NotNull(message = "Report type (LOST or FOUND) is required")
        ReportType type,

        @Size(max = 10_000_000, message = "Image URL or data cannot exceed 10MB")
        String imageUrl
) {
}
