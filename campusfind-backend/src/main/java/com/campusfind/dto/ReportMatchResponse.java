package com.campusfind.dto;

public record ReportMatchResponse(
        ReportResponse report,
        Double similarityScore,
        Double matchPercentage
) {
    public static ReportMatchResponse of(ReportResponse report, Double similarityScore, Double matchPercentage) {
        return new ReportMatchResponse(report, similarityScore, matchPercentage);
    }
}
