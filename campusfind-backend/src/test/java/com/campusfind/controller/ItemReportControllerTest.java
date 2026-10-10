package com.campusfind.controller;

import com.campusfind.dto.CreateReportRequest;
import com.campusfind.dto.RegisterRequest;
import com.campusfind.dto.UpdateReportStatusRequest;
import com.campusfind.entity.ReportStatus;
import com.campusfind.entity.ReportType;
import com.campusfind.entity.Role;
import com.campusfind.repository.ItemReportRepository;
import com.campusfind.repository.UserRepository;
import com.campusfind.service.ItemReportService;
import com.campusfind.service.UserService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ItemReportControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ItemReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private ItemReportService reportService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper();

        reportRepository.deleteAll();
        userRepository.deleteAll();

        // Register student 1
        userService.registerUser(new RegisterRequest(
                "Student One",
                "student1@campus.edu",
                "StudentPass1!",
                Role.ROLE_STUDENT
        ));

        // Register student 2
        userService.registerUser(new RegisterRequest(
                "Student Two",
                "student2@campus.edu",
                "StudentPass2!",
                Role.ROLE_STUDENT
        ));

        // Seed admin using secure internal provisioning
        userService.createAdminUser("Admin User", "admin@campus.edu", "AdminPass1!");
    }

    @Test
    @DisplayName("Authenticated student can create a lost item report")
    void testCreateReportSuccess() throws Exception {
        CreateReportRequest request = new CreateReportRequest(
                "Lost Blue Backpack",
                "Navy blue Herschel backpack left on 3rd floor library",
                "BAGS",
                "Library 3rd Floor",
                ReportType.LOST,
                "https://images.example.com/backpack.jpg"
        );

        mockMvc.perform(post("/api/v1/reports")
                        .with(httpBasic("student1@campus.edu", "StudentPass1!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Lost Blue Backpack"))
                .andExpect(jsonPath("$.type").value("LOST"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.imageUrl").value("https://images.example.com/backpack.jpg"))
                .andExpect(jsonPath("$.reporterEmail").value("student1@campus.edu"));
    }

    @Test
    @DisplayName("Authenticated student can create a report with a base64 image data URL exceeding 500 characters")
    void testCreateReportWithLongBase64ImageDataUrlSuccess() throws Exception {
        // Base64 data URL longer than 500 characters (902 characters) to verify no truncation occurs
        String longBase64DataUrl = "data:image/png;base64," + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==".repeat(10);

        CreateReportRequest request = new CreateReportRequest(
                "Lost Laptop Sleeve",
                "Grey padded laptop sleeve with stickers",
                "ACCESSORIES",
                "Engineering Hall 204",
                ReportType.LOST,
                longBase64DataUrl
        );

        String responseBody = mockMvc.perform(post("/api/v1/reports")
                        .with(httpBasic("student1@campus.edu", "StudentPass1!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Lost Laptop Sleeve"))
                .andExpect(jsonPath("$.imageUrl").value(longBase64DataUrl))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract ID and verify retrieval via GET /api/v1/reports/{id}
        var jsonNode = objectMapper.readTree(responseBody);
        long reportId = jsonNode.get("id").asLong();

        mockMvc.perform(get("/api/v1/reports/" + reportId)
                        .with(httpBasic("student1@campus.edu", "StudentPass1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.imageUrl").value(longBase64DataUrl));
    }

    @Test
    @DisplayName("Report created without image gracefully stores and returns null imageUrl")
    void testCreateReportWithoutImageSuccess() throws Exception {
        CreateReportRequest request = new CreateReportRequest(
                "Lost Umbrella",
                "Black compact umbrella left near entrance",
                "OTHER",
                "Main Gate",
                ReportType.LOST,
                null
        );

        String responseBody = mockMvc.perform(post("/api/v1/reports")
                        .with(httpBasic("student1@campus.edu", "StudentPass1!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var jsonNode = objectMapper.readTree(responseBody);
        long reportId = jsonNode.get("id").asLong();

        mockMvc.perform(get("/api/v1/reports/" + reportId)
                        .with(httpBasic("student1@campus.edu", "StudentPass1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    @DisplayName("Unauthenticated request to create a report returns 401 Unauthorized")
    void testCreateReportUnauthenticatedFails() throws Exception {
        CreateReportRequest request = new CreateReportRequest(
                "Found Water Bottle",
                "Silver Hydroflask found in Science Hall",
                "BOTTLES",
                "Science Hall 101",
                ReportType.FOUND,
                null
        );

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Report creation validation fails when title or description is missing")
    void testCreateReportValidationFails() throws Exception {
        CreateReportRequest invalidRequest = new CreateReportRequest(
                "", // Blank title
                "", // Blank description
                null,
                null,
                null, // Missing type
                null
        );

        mockMvc.perform(post("/api/v1/reports")
                        .with(httpBasic("student1@campus.edu", "StudentPass1!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.type").exists());
    }

    @Test
    @DisplayName("Authenticated student can list and filter reports")
    void testListAndFilterReports() throws Exception {
        reportService.createReport(new CreateReportRequest(
                "Lost iPhone 14",
                "Black iPhone 14 with cracked screen protector",
                "ELECTRONICS",
                "Dining Hall",
                ReportType.LOST,
                null
        ), "student1@campus.edu");

        reportService.createReport(new CreateReportRequest(
                "Found Keys",
                "Set of brass keys with a red lanyard",
                "KEYS",
                "Gymnasium",
                ReportType.FOUND,
                null
        ), "student2@campus.edu");

        // Filter by LOST
        mockMvc.perform(get("/api/v1/reports?type=LOST")
                        .with(httpBasic("student1@campus.edu", "StudentPass1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Lost iPhone 14"));
    }

    @Test
    @DisplayName("Report owner can update status to RESOLVED")
    void testReportOwnerCanUpdateStatus() throws Exception {
        var created = reportService.createReport(new CreateReportRequest(
                "Lost AirPods Case",
                "White AirPods Pro case",
                "ELECTRONICS",
                "Student Union",
                ReportType.LOST,
                null
        ), "student1@campus.edu");

        UpdateReportStatusRequest updateRequest = new UpdateReportStatusRequest(ReportStatus.RESOLVED);

        mockMvc.perform(patch("/api/v1/reports/" + created.id() + "/status")
                        .with(httpBasic("student1@campus.edu", "StudentPass1!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("Non-owner student cannot update another student's report status")
    void testNonOwnerCannotUpdateReportStatus() throws Exception {
        var created = reportService.createReport(new CreateReportRequest(
                "Lost Watch",
                "Silver mechanical wristwatch",
                "JEWELRY",
                "Library",
                ReportType.LOST,
                null
        ), "student1@campus.edu");

        UpdateReportStatusRequest updateRequest = new UpdateReportStatusRequest(ReportStatus.CANCELLED);

        // Student 2 attempts to change Student 1's report
        mockMvc.perform(patch("/api/v1/reports/" + created.id() + "/status")
                        .with(httpBasic("student2@campus.edu", "StudentPass2!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Administrator can update status of any report")
    void testAdminCanUpdateAnyReportStatus() throws Exception {
        var created = reportService.createReport(new CreateReportRequest(
                "Lost Calculator",
                "TI-84 Plus graph calculator",
                "ELECTRONICS",
                "Math Building 204",
                ReportType.LOST,
                null
        ), "student1@campus.edu");

        UpdateReportStatusRequest updateRequest = new UpdateReportStatusRequest(ReportStatus.RESOLVED);

        mockMvc.perform(patch("/api/v1/reports/" + created.id() + "/status")
                        .with(httpBasic("admin@campus.edu", "AdminPass1!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("Report owner can delete their report")
    void testOwnerCanDeleteReport() throws Exception {
        var created = reportService.createReport(new CreateReportRequest(
                "Lost Umbrella",
                "Black compact umbrella",
                "ACCESSORIES",
                "Bus Stop",
                ReportType.LOST,
                null
        ), "student1@campus.edu");

        mockMvc.perform(delete("/api/v1/reports/" + created.id())
                        .with(httpBasic("student1@campus.edu", "StudentPass1!")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/reports/" + created.id())
                        .with(httpBasic("student1@campus.edu", "StudentPass1!")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/reports/{id}/matches returns 200 with matches list for authenticated student")
    void testGetReportMatches_Authenticated() throws Exception {
        var created = reportService.createReport(new CreateReportRequest(
                "Lost Keys",
                "Keychain with blue lanyard",
                "KEYS",
                "Quad",
                ReportType.LOST,
                null
        ), "student1@campus.edu");

        mockMvc.perform(get("/api/v1/reports/" + created.id() + "/matches")
                        .with(httpBasic("student1@campus.edu", "StudentPass1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/reports/ai-health returns 200 diagnostic status")
    void testGetAiHealth_Authenticated() throws Exception {
        mockMvc.perform(get("/api/v1/reports/ai-health")
                        .with(httpBasic("student1@campus.edu", "StudentPass1!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("campusfind-ai"));
    }

    @Test
    @DisplayName("POST /api/v1/reports/{id}/embedding attaches embedding vector to report")
    void testAttachEmbedding_Authenticated() throws Exception {
        var created = reportService.createReport(new CreateReportRequest(
                "Lost Badge",
                "Student ID badge",
                "CARDS",
                "Main Gate",
                ReportType.LOST,
                null
        ), "student1@campus.edu");

        String payload = "{\"embedding\": [0.15, -0.25, 0.35]}";

        mockMvc.perform(post("/api/v1/reports/" + created.id() + "/embedding")
                        .with(httpBasic("student1@campus.edu", "StudentPass1!"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id()));
    }
}
