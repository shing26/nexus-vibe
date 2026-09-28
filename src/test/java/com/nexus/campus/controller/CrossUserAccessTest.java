package com.nexus.campus.controller;

import com.nexus.campus.dto.PostUpdateRequest;
import com.nexus.campus.entity.SysMessage;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.SysMessageMapper;
import com.nexus.campus.mapper.VibeCommentMapper;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The horizontal-access matrix: user B holds a valid token and aims it at user
 * A's resources.
 *
 * <p>Each row keeps the endpoint's existing semantics rather than a new one -
 * posts and comments answer 403 because the owner is public information, the
 * message endpoint answers 404 so it never confirms that somebody else's
 * message id exists. Every row also asserts the victim row is untouched, so a
 * 403 that still mutated the database cannot pass.</p>
 *
 * <p>Vertical (role) coverage is deliberately not duplicated here:
 * {@code PostControllerIntegrationTest} and {@code CommentControllerIntegrationTest}
 * already assert the admin-can / non-admin-cannot sides of the same handlers.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/data.sql", "/test-users.sql"})
class CrossUserAccessTest {

    /** Post 1 and message 1 belong to shing (2); comment 1 belongs to alice (3). */
    private static final long OWNER_ID = 2L;
    private static final long INTRUDER_ID = 4L;
    private static final long OTHER_OWNER_COMMENT_ID = 1L;
    private static final long OTHER_OWNER_MESSAGE_ID = 1L;
    private static final long OTHER_OWNER_POST_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private VibeCommentMapper vibeCommentMapper;

    @Autowired
    private SysMessageMapper sysMessageMapper;

    private String ownerToken;
    private String intruderToken;

    @BeforeEach
    void setUp() {
        ownerToken = jwtUtil.generateToken(OWNER_ID, "shing", "USER");
        intruderToken = jwtUtil.generateToken(INTRUDER_ID, "bob", "USER");
    }

    @Test
    @DisplayName("PUT /api/v1/posts/{id}: another user's token is refused and the post is unchanged")
    void updatePost_otherUsersToken_isForbiddenAndLeavesRowAlone() throws Exception {
        VibePost before = vibePostMapper.selectById(OTHER_OWNER_POST_ID);

        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle("Hijacked title");
        request.setContent("Hijacked content written by a user who does not own this post.");

        mockMvc.perform(put("/api/v1/posts/" + OTHER_OWNER_POST_ID)
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is(403)))
                .andExpect(jsonPath("$.message", containsString("author")));

        VibePost after = vibePostMapper.selectById(OTHER_OWNER_POST_ID);
        assertThat(after.getTitle()).isEqualTo(before.getTitle());
        assertThat(after.getContent()).isEqualTo(before.getContent());
        assertThat(after.getUserId()).isEqualTo(OWNER_ID);
    }

    @Test
    @DisplayName("PUT /api/v1/posts/{id}: the owner's own token still succeeds (control)")
    void updatePost_ownersOwnToken_succeeds() throws Exception {
        PostUpdateRequest request = new PostUpdateRequest();
        request.setTitle("Renamed by the owner");

        mockMvc.perform(put("/api/v1/posts/" + OTHER_OWNER_POST_ID)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is(200)));

        assertThat(vibePostMapper.selectById(OTHER_OWNER_POST_ID).getTitle())
                .isEqualTo("Renamed by the owner");
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/{id}: another user's token is refused and the post survives")
    void deletePost_otherUsersToken_isForbiddenAndRowSurvives() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/" + OTHER_OWNER_POST_ID)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is(403)))
                .andExpect(jsonPath("$.message", containsString("author")));

        assertThat(vibePostMapper.selectById(OTHER_OWNER_POST_ID)).isNotNull();
    }

    @Test
    @DisplayName("DELETE /api/v1/comments/{id}: another user's token is refused and the comment survives")
    void deleteComment_otherUsersToken_isForbiddenAndRowSurvives() throws Exception {
        VibeComment before = vibeCommentMapper.selectById(OTHER_OWNER_COMMENT_ID);
        assertThat(before).as("fixture comment 1 must exist").isNotNull();

        mockMvc.perform(delete("/api/v1/comments/" + OTHER_OWNER_COMMENT_ID)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is(403)))
                .andExpect(jsonPath("$.message", containsString("author or an admin")));

        assertThat(vibeCommentMapper.selectById(OTHER_OWNER_COMMENT_ID)).isNotNull();
    }

    @Test
    @DisplayName("POST /api/v1/messages/{id}/read: another user's token gets 404, not a hint, and nothing is marked read")
    void markMessageRead_otherUsersToken_isNotFoundAndLeavesUnread() throws Exception {
        SysMessage before = sysMessageMapper.selectById(OTHER_OWNER_MESSAGE_ID);
        assertThat(before).as("fixture message 1 must exist").isNotNull();
        assertThat(before.getIsRead()).as("fixture message 1 starts unread").isZero();

        mockMvc.perform(post("/api/v1/messages/" + OTHER_OWNER_MESSAGE_ID + "/read")
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is(404)))
                .andExpect(jsonPath("$.message", containsString("Message not found")))
                // The refusal must not read as an ownership verdict; that would confirm the id exists.
                .andExpect(jsonPath("$.message", not(containsString("forbidden"))));

        SysMessage after = sysMessageMapper.selectById(OTHER_OWNER_MESSAGE_ID);
        assertThat(after.getIsRead()).as("the message must still be unread").isZero();
    }

    @Test
    @DisplayName("There is no cross-user surface for uploads: DELETE /api/v1/upload/image is not mapped")
    void uploadHasNoDeleteSurface() throws Exception {
        // The controller only exposes POST /api/v1/upload/image, so an uploaded
        // file cannot be deleted by its uploader, let alone by somebody else.
        // 405 is the proof that the delete surface does not exist.
        mockMvc.perform(delete("/api/v1/upload/image")
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isMethodNotAllowed());
    }

    private String toJson(Object value) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
    }
}
