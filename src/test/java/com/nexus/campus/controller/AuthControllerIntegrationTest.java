package com.nexus.campus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.dto.LoginRequest;
import com.nexus.campus.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Commit;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for {@link AuthController}.
 *
 * <p>Tests registration, login, duplicate-user handling, wrong credentials,
 * and unauthenticated access to a protected endpoint.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Register a new user → 200 + JWT token")
    void registerNewUser_shouldReturn200() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("freshuser_" + System.currentTimeMillis());
        request.setPassword("testPass123");
        request.setNickname("Fresh User");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.username", is(request.getUsername())))
                .andExpect(jsonPath("$.data.role", is("USER")));
    }

    @Test
    @DisplayName("Register with an existing username → 400")
    void registerDuplicateUser_shouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("admin"); // admin exists in seed data so registration should fail
        request.setPassword("testPass123");
        request.setNickname("Duplicate");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(400)));
    }

    @Test
    @DisplayName("Login with correct credentials → 200 + JWT token")
    void loginValidCredentials_shouldReturn200() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("shing");
        request.setPassword("123456");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.username", is("shing")))
                .andExpect(jsonPath("$.data.role", is("USER")));
    }

    @Test
    @DisplayName("Login with wrong password → 401")
    void loginWrongPassword_shouldReturn401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("shing");
        request.setPassword("wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(401)));
    }

    @Test
    @DisplayName("Access protected endpoint without token → 401")
    void accessProtectedEndpointWithoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)))
                .andExpect(jsonPath("$.message", containsString("Authentication required")));
    }

    @Test
    @DisplayName("GET /api/v1/users/profile without token should return 401")
    void getUserProfileWithoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)));
    }

    @Test
    @DisplayName("PUT /api/v1/users/profile without token should return 401")
    void updateUserProfileWithoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(put("/api/v1/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)));
    }

    @Test
    @DisplayName("PUT /api/v1/users/password without token should return 401")
    void changePasswordWithoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(put("/api/v1/users/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)));
    }
}
