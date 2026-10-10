package com.campusfind.service;

import com.campusfind.dto.AiCandidateDto;
import com.campusfind.dto.AiMatchItemDto;
import com.campusfind.dto.AiRankResponse;
import com.campusfind.dto.ReportMatchResponse;
import com.campusfind.entity.ItemReport;
import com.campusfind.entity.ReportStatus;
import com.campusfind.entity.ReportType;
import com.campusfind.entity.Role;
import com.campusfind.entity.User;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.repository.ItemReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportMatchingServiceTest {

    @Mock
    private ItemReportRepository reportRepository;

    @Mock
    private AiServiceClient aiServiceClient;

    private ObjectMapper objectMapper;
    private ReportMatchingService matchingService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        matchingService = new ReportMatchingService(reportRepository, aiServiceClient, objectMapper);
        ReflectionTestUtils.setField(matchingService, "similarityThreshold", 0.60);
        ReflectionTestUtils.setField(matchingService, "matchLimit", 6);

        sampleUser = new User("Student One", "student@campus.edu", "hashedpw", Role.ROLE_STUDENT);
        sampleUser.setId(1L);
    }

    @Test
    @DisplayName("findMatchesForReport() matches LOST report against active OPEN FOUND candidates")
    void findMatchesForReport_LostMatchesFound_Success() {
        ItemReport lostReport = new ItemReport("Lost Blue Backpack", "North campus", "Bags", "Library", ReportType.LOST, null, sampleUser);
        lostReport.setId(10L);
        lostReport.setStatus(ReportStatus.OPEN);
        lostReport.setEmbeddingJson("[0.12, 0.34, 0.56]");

        ItemReport foundReport = new ItemReport("Found Blue Backpack", "Found near study area", "Bags", "Library", ReportType.FOUND, null, sampleUser);
        foundReport.setId(20L);
        foundReport.setStatus(ReportStatus.OPEN);
        foundReport.setEmbeddingJson("[0.11, 0.35, 0.55]");

        when(reportRepository.findById(10L)).thenReturn(Optional.of(lostReport));
        when(reportRepository.findByTypeAndStatus(ReportType.FOUND, ReportStatus.OPEN))
                .thenReturn(List.of(foundReport));

        AiRankResponse rankResponse = new AiRankResponse(
                1,
                List.of(new AiMatchItemDto(20L, 0.9412, 94.1))
        );
        when(aiServiceClient.rankCandidates(anyList(), anyList(), eq(0.60), eq(6)))
                .thenReturn(rankResponse);

        List<ReportMatchResponse> matches = matchingService.findMatchesForReport(10L);

        assertThat(matches).hasSize(1);
        ReportMatchResponse match = matches.get(0);
        assertThat(match.report().id()).isEqualTo(20L);
        assertThat(match.similarityScore()).isEqualTo(0.9412);
        assertThat(match.matchPercentage()).isEqualTo(94.1);

        // Security / workflow check: ensure report status is NOT altered or auto-resolved
        assertThat(lostReport.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(foundReport.getStatus()).isEqualTo(ReportStatus.OPEN);
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("findMatchesForReport() matches FOUND report against active OPEN LOST candidates")
    void findMatchesForReport_FoundMatchesLost_Success() {
        ItemReport foundReport = new ItemReport("Found Keycard", "Near gate", "IDs", "Gate", ReportType.FOUND, null, sampleUser);
        foundReport.setId(30L);
        foundReport.setStatus(ReportStatus.OPEN);
        foundReport.setEmbeddingJson("[0.2, 0.4]");

        ItemReport lostReport = new ItemReport("Lost Student ID", "Lost by gate", "IDs", "Gate", ReportType.LOST, null, sampleUser);
        lostReport.setId(40L);
        lostReport.setStatus(ReportStatus.OPEN);
        lostReport.setEmbeddingJson("[0.21, 0.39]");

        when(reportRepository.findById(30L)).thenReturn(Optional.of(foundReport));
        when(reportRepository.findByTypeAndStatus(ReportType.LOST, ReportStatus.OPEN))
                .thenReturn(List.of(lostReport));

        AiRankResponse rankResponse = new AiRankResponse(
                1,
                List.of(new AiMatchItemDto(40L, 0.8876, 88.8))
        );
        when(aiServiceClient.rankCandidates(anyList(), anyList(), eq(0.60), eq(6)))
                .thenReturn(rankResponse);

        List<ReportMatchResponse> matches = matchingService.findMatchesForReport(30L);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).report().id()).isEqualTo(40L);
        assertThat(matches.get(0).similarityScore()).isEqualTo(0.8876);
    }

    @Test
    @DisplayName("findMatchesForReport() returns empty list when target report has no embedding and no imageUrl")
    void findMatchesForReport_NoEmbedding_ReturnsEmptyList() {
        ItemReport reportWithoutEmbedding = new ItemReport("No Photo Item", "Details", "General", "Quad", ReportType.LOST, null, sampleUser);
        reportWithoutEmbedding.setId(50L);
        reportWithoutEmbedding.setEmbeddingJson(null);

        when(reportRepository.findById(50L)).thenReturn(Optional.of(reportWithoutEmbedding));

        List<ReportMatchResponse> matches = matchingService.findMatchesForReport(50L);

        assertThat(matches).isEmpty();
        verify(aiServiceClient, never()).rankCandidates(any(), any(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("findMatchesForReport() self-heals missing embedding when report has imageUrl")
    void findMatchesForReport_SelfHealsMissingEmbeddingWhenImageUrlPresent() {
        ItemReport target = new ItemReport("Lost Camera", "Canon DSLR", "Electronics", "Auditorium", ReportType.LOST, "data:image/jpeg;base64,dGVzdA==", sampleUser);
        target.setId(80L);
        target.setStatus(ReportStatus.OPEN);
        target.setEmbeddingJson(null);

        ItemReport candidate = new ItemReport("Found Camera", "Black camera", "Electronics", "Auditorium", ReportType.FOUND, null, sampleUser);
        candidate.setId(90L);
        candidate.setStatus(ReportStatus.OPEN);
        candidate.setEmbeddingJson("[0.3, 0.7]");

        when(reportRepository.findById(80L)).thenReturn(Optional.of(target));
        when(aiServiceClient.generateEmbeddingFromBase64("data:image/jpeg;base64,dGVzdA==")).thenReturn(List.of(0.3, 0.7));
        when(reportRepository.save(any(ItemReport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reportRepository.findByTypeAndStatus(ReportType.FOUND, ReportStatus.OPEN)).thenReturn(List.of(candidate));

        AiRankResponse rankResponse = new AiRankResponse(1, List.of(new AiMatchItemDto(90L, 0.99, 99.0)));
        when(aiServiceClient.rankCandidates(anyList(), anyList(), eq(0.60), eq(6))).thenReturn(rankResponse);

        List<ReportMatchResponse> matches = matchingService.findMatchesForReport(80L);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).report().id()).isEqualTo(90L);
        assertThat(target.getEmbeddingJson()).isEqualTo("[0.3,0.7]");
    }

    @Test
    @DisplayName("findMatchesForReport() returns empty list when no candidates of opposite type exist")
    void findMatchesForReport_NoCandidates_ReturnsEmptyList() {
        ItemReport target = new ItemReport("Watch", "Silver", "Accessories", "Gym", ReportType.LOST, null, sampleUser);
        target.setId(60L);
        target.setEmbeddingJson("[0.5, 0.5]");

        when(reportRepository.findById(60L)).thenReturn(Optional.of(target));
        when(reportRepository.findByTypeAndStatus(ReportType.FOUND, ReportStatus.OPEN))
                .thenReturn(List.of());

        List<ReportMatchResponse> matches = matchingService.findMatchesForReport(60L);

        assertThat(matches).isEmpty();
        verify(aiServiceClient, never()).rankCandidates(any(), any(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("findMatchesForReport() throws ResourceNotFoundException for unknown ID")
    void findMatchesForReport_UnknownId_ThrowsNotFound() {
        when(reportRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchingService.findMatchesForReport(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Report not found with ID: 999");
    }

    @Test
    @DisplayName("attachEmbedding() serializes embedding list to JSON and updates entity")
    void attachEmbedding_Success() {
        ItemReport report = new ItemReport("Keys", "Keychain", "Keys", "Hall", ReportType.LOST, null, sampleUser);
        report.setId(70L);

        when(reportRepository.findById(70L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(ItemReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = matchingService.attachEmbedding(70L, List.of(0.1, 0.2, 0.3));

        assertThat(response).isNotNull();
        assertThat(report.getEmbeddingJson()).isEqualTo("[0.1,0.2,0.3]");
        verify(reportRepository).save(report);
    }

    @Test
    @DisplayName("findMatchesForReport() preserves match for same item model with different colours within same category")
    void findMatchesForReport_SameCategoryDifferentColour_PreservesMatch() {
        ItemReport lostIPhone = new ItemReport("iPhone 13 Pro Graphite", "Lost near library", "Phones", "Library", ReportType.LOST, null, sampleUser);
        lostIPhone.setId(101L);
        lostIPhone.setStatus(ReportStatus.OPEN);
        lostIPhone.setEmbeddingJson("[0.15, 0.45, 0.85]");

        ItemReport foundIPhone = new ItemReport("iPhone 13 Pro Blue", "Found near canteen", "Phones", "Canteen", ReportType.FOUND, null, sampleUser);
        foundIPhone.setId(102L);
        foundIPhone.setStatus(ReportStatus.OPEN);
        foundIPhone.setEmbeddingJson("[0.16, 0.44, 0.84]");

        when(reportRepository.findById(101L)).thenReturn(Optional.of(lostIPhone));
        when(reportRepository.findByTypeAndStatus(ReportType.FOUND, ReportStatus.OPEN))
                .thenReturn(List.of(foundIPhone));

        AiRankResponse rankResponse = new AiRankResponse(
                1,
                List.of(new AiMatchItemDto(102L, 0.8675, 86.8))
        );
        when(aiServiceClient.rankCandidates(anyList(), anyList(), eq(0.60), eq(6)))
                .thenReturn(rankResponse);

        List<ReportMatchResponse> matches = matchingService.findMatchesForReport(101L);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).report().id()).isEqualTo(102L);
        assertThat(matches.get(0).similarityScore()).isEqualTo(0.8675);
        assertThat(matches.get(0).matchPercentage()).isEqualTo(86.8);
    }

    @Test
    @DisplayName("findMatchesForReport() excludes candidates from incompatible item categories before AI ranking")
    void findMatchesForReport_IncompatibleCategory_FilteredOutBeforeRanking() {
        ItemReport lostIPhone = new ItemReport("iPhone 13 Pro", "Lost near library", "Phones", "Library", ReportType.LOST, null, sampleUser);
        lostIPhone.setId(201L);
        lostIPhone.setStatus(ReportStatus.OPEN);
        lostIPhone.setEmbeddingJson("[0.15, 0.45, 0.85]");

        ItemReport foundLaptop = new ItemReport("Dell Laptop", "Found near library", "Laptops", "Library", ReportType.FOUND, null, sampleUser);
        foundLaptop.setId(202L);
        foundLaptop.setStatus(ReportStatus.OPEN);
        foundLaptop.setEmbeddingJson("[0.10, 0.40, 0.70]");

        when(reportRepository.findById(201L)).thenReturn(Optional.of(lostIPhone));
        when(reportRepository.findByTypeAndStatus(ReportType.FOUND, ReportStatus.OPEN))
                .thenReturn(List.of(foundLaptop));

        List<ReportMatchResponse> matches = matchingService.findMatchesForReport(201L);

        // Phones vs Laptops must be excluded before rankCandidates is invoked
        assertThat(matches).isEmpty();
        verify(aiServiceClient, never()).rankCandidates(anyList(), anyList(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("isCategoryCompatible() is case-insensitive and ignores surrounding whitespace")
    void isCategoryCompatible_CaseInsensitiveAndWhitespaceTolerant() {
        assertThat(matchingService.isCategoryCompatible("  Phones  ", "phones")).isTrue();
        assertThat(matchingService.isCategoryCompatible("PHONES", "  Phones ")).isTrue();
        assertThat(matchingService.isCategoryCompatible("  laptops ", "LAPTOPS  ")).isTrue();
        assertThat(matchingService.isCategoryCompatible("  Phones ", "  Laptops  ")).isFalse();
    }

    @Test
    @DisplayName("isCategoryCompatible() allows broad categories Electronics, Other, and General to match other categories")
    void isCategoryCompatible_BroadCategories_CompatibleWithOthers() {
        // Electronics matches specific devices
        assertThat(matchingService.isCategoryCompatible("Electronics", "Phones")).isTrue();
        assertThat(matchingService.isCategoryCompatible("Phones", "Electronics")).isTrue();
        assertThat(matchingService.isCategoryCompatible("Electronics", "Laptops")).isTrue();
        assertThat(matchingService.isCategoryCompatible("Laptops", "Electronics")).isTrue();

        // Other matches any category
        assertThat(matchingService.isCategoryCompatible("Other", "Keys")).isTrue();
        assertThat(matchingService.isCategoryCompatible("Wallets", "Other")).isTrue();

        // General matches any category
        assertThat(matchingService.isCategoryCompatible("General", "Bags")).isTrue();
        assertThat(matchingService.isCategoryCompatible("Books", "General")).isTrue();

        // Incompatible specific categories still return false
        assertThat(matchingService.isCategoryCompatible("Bags", "Phones")).isFalse();
        assertThat(matchingService.isCategoryCompatible("Laptops", "Clothing")).isFalse();
    }

    @Test
    @DisplayName("isCategoryCompatible() safely handles null and blank categories without exceptions")
    void isCategoryCompatible_NullAndBlank_HandledSafely() {
        assertThat(matchingService.isCategoryCompatible(null, "Phones")).isTrue();
        assertThat(matchingService.isCategoryCompatible("Phones", null)).isTrue();
        assertThat(matchingService.isCategoryCompatible(null, null)).isTrue();
        assertThat(matchingService.isCategoryCompatible("", "Phones")).isTrue();
        assertThat(matchingService.isCategoryCompatible("   ", "Laptops")).isTrue();
        assertThat(matchingService.isCategoryCompatible("Phones", "   ")).isTrue();
    }

    @Test
    @DisplayName("findMatchesForReport() filters incompatible candidates while retaining compatible candidates for ranking")
    void findMatchesForReport_MixedCandidates_FiltersOnlyIncompatible() {
        ItemReport target = new ItemReport("Lost Phone", "Lost in hall", "Phones", "Hall", ReportType.LOST, null, sampleUser);
        target.setId(301L);
        target.setStatus(ReportStatus.OPEN);
        target.setEmbeddingJson("[0.2, 0.4, 0.6]");

        ItemReport compatibleCandidate = new ItemReport("Found Phone", "Found in hall", "Phones", "Hall", ReportType.FOUND, null, sampleUser);
        compatibleCandidate.setId(302L);
        compatibleCandidate.setStatus(ReportStatus.OPEN);
        compatibleCandidate.setEmbeddingJson("[0.2, 0.4, 0.6]");

        ItemReport incompatibleCandidate = new ItemReport("Found Backpack", "Found in hall", "Bags", "Hall", ReportType.FOUND, null, sampleUser);
        incompatibleCandidate.setId(303L);
        incompatibleCandidate.setStatus(ReportStatus.OPEN);
        incompatibleCandidate.setEmbeddingJson("[0.9, 0.1, 0.1]");

        when(reportRepository.findById(301L)).thenReturn(Optional.of(target));
        when(reportRepository.findByTypeAndStatus(ReportType.FOUND, ReportStatus.OPEN))
                .thenReturn(List.of(compatibleCandidate, incompatibleCandidate));

        when(aiServiceClient.rankCandidates(anyList(), argThat(list -> list.size() == 1 && list.get(0).id().equals(302L)), eq(0.60), eq(6)))
                .thenReturn(new AiRankResponse(1, List.of(new AiMatchItemDto(302L, 0.95, 95.0))));

        List<ReportMatchResponse> matches = matchingService.findMatchesForReport(301L);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).report().id()).isEqualTo(302L);
        assertThat(matches.get(0).matchPercentage()).isEqualTo(95.0);
    }
}

