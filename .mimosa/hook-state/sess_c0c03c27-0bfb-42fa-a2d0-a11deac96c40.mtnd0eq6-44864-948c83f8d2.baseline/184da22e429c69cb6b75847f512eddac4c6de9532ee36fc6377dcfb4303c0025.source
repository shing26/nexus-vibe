package com.nexus.campus.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserProfileSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String bio;

    private String role;

    private Stats stats = new Stats();

    private List<ActivityItem> recentActivity = new ArrayList<>();

    @Data
    public static class Stats implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Active posts (status = 1) created by the user. */
        private int posts;

        /** Active comments (status = 1) created by the user. */
        private int comments;

        /** Sum of like_count over the user's active posts. */
        private int likesReceived;

        /** Average of the latest code-review score per active post; null when no reviewed posts. */
        private Double avgAiScore;

        /** Active posts created by the user with forked_from_id set. */
        private int forks;

        /** Prompt versions where created_by is the user. */
        private int versions;
    }

    @Data
    public static class ActivityItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;

        private String type;

        private Long postId;

        private String title;

        private LocalDateTime createdAt;
    }
}
