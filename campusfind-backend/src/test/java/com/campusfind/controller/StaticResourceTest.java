package com.campusfind.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class StaticResourceTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("GET /index.html serves index.html with HTTP 200 without authentication")
    void testIndexHtmlPublic() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CampusFind")));
    }

    @Test
    @DisplayName("GET /browse.html serves catalog with HTTP 200 without authentication")
    void testBrowseHtmlPublic() throws Exception {
        mockMvc.perform(get("/browse.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Campus Lost")));
    }

    @Test
    @DisplayName("GET /create-report.html serves form with HTTP 200 without authentication")
    void testCreateReportHtmlPublic() throws Exception {
        mockMvc.perform(get("/create-report.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Submit a Lost or Found Report")));
    }

    @Test
    @DisplayName("GET /css/style.css serves stylesheet with HTTP 200 without authentication")
    void testCssPublic() throws Exception {
        mockMvc.perform(get("/css/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--bg-primary")));
    }

    @Test
    @DisplayName("GET /js/app.js serves frontend app script with HTTP 200 without authentication")
    void testAppJsPublic() throws Exception {
        mockMvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CampusFind AI")))
                .andExpect(content().string(containsString("setupPasswordToggles")));
    }

    @Test
    @DisplayName("GET /login.html serves login page with password toggle button and accessible labels")
    void testLoginHtmlPasswordToggle() throws Exception {
        mockMvc.perform(get("/login.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("toggleLoginPassword")))
                .andExpect(content().string(containsString("btn-toggle-password")))
                .andExpect(content().string(containsString("aria-label=\"Show password\"")));
    }

    @Test
    @DisplayName("GET /register.html serves registration page with password and confirm-password toggle buttons")
    void testRegisterHtmlPasswordToggles() throws Exception {
        mockMvc.perform(get("/register.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("toggleRegPassword")))
                .andExpect(content().string(containsString("toggleRegConfirmPassword")))
                .andExpect(content().string(containsString("register-password-grid")));
    }
}

