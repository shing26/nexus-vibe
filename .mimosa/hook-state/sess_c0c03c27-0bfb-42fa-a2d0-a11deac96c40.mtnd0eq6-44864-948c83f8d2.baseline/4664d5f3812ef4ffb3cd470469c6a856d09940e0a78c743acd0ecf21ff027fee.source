package com.nexus.campus.service;

import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.dto.UserProfileSummary;
import com.nexus.campus.entity.PromptVersion;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.entity.VibeComment;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.PromptVersionMapper;
import com.nexus.campus.mapper.SysUserMapper;
import com.nexus.campus.mapper.VibeCommentMapper;
import com.nexus.campus.mapper.VibePostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserProfileSummaryService {

    private static final int ACTIVITY_LIMIT = 10;
    private static final int COMMENT_TITLE_LIMIT = 60;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private VibeCommentMapper vibeCommentMapper;

    @Autowired
    private PromptVersionMapper promptVersionMapper;

    @Autowired
    private AiReviewLogMapper aiReviewLogMapper;

    @Autowired
    private AiReviewDetailService aiReviewDetailService;

    public UserProfileSummary getSummary(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        UserProfileSummary summary = new UserProfileSummary();
        summary.setId(user.getId());
        summary.setUsername(user.getUsername());
        summary.setNickname(user.getNickname());
        summary.setAvatar(user.getAvatar());
        summary.setBio(user.getBio());
        summary.setRole(user.getRole());

        UserProfileSummary.Stats stats = summary.getStats();
        stats.setPosts((int) vibePostMapper.countActivePostsByUserId(userId));
        stats.setComments((int) vibeCommentMapper.countCommentsByUserId(userId));
        stats.setLikesReceived((int) vibePostMapper.sumLikeCountByUserId(userId));
        stats.setForks((int) vibePostMapper.countForksByUserId(userId));
        stats.setVersions((int) promptVersionMapper.countVersionsByCreatedBy(userId));
        stats.setAvgAiScore(calculateAverageAiScore(userId));

        summary.setRecentActivity(buildRecentActivity(userId));
        return summary;
    }

    private Double calculateAverageAiScore(Long userId) {
        List<AiReviewLog> logs = aiReviewLogMapper.selectLatestCodeReviewLogsByUserId(userId);
        Set<Long> countedPosts = new HashSet<>();
        double total = 0;
        int count = 0;
        for (AiReviewLog log : logs) {
            if (!countedPosts.add(log.getPostId())) {
                continue;
            }
            Integer score = aiReviewDetailService.score(log);
            if (score != null) {
                total += score;
                count++;
            }
        }
        return count == 0 ? null : total / count;
    }

    private List<UserProfileSummary.ActivityItem> buildRecentActivity(Long userId) {
        List<UserProfileSummary.ActivityItem> items = new ArrayList<>();
        for (VibePost post : vibePostMapper.selectLatestActivePostsByUserId(userId)) {
            items.add(activityItem(post.getId(), "POST", post.getId(), post.getTitle(), post.getCreateTime()));
        }
        for (VibeComment comment : vibeCommentMapper.selectLatestActiveCommentsByUserId(userId)) {
            items.add(activityItem(comment.getId(), "COMMENT", comment.getPostId(),
                    commentTitle(comment.getContent()), comment.getCreateTime()));
        }
        for (PromptVersion version : promptVersionMapper.selectLatestVersionsByCreatedBy(userId)) {
            items.add(activityItem(version.getId(), "VERSION", version.getPostId(), version.getTitle(),
                    version.getCreateTime()));
        }

        items.sort(Comparator.comparing(UserProfileSummary.ActivityItem::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(UserProfileSummary.ActivityItem::getId,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return items.size() > ACTIVITY_LIMIT
                ? new ArrayList<>(items.subList(0, ACTIVITY_LIMIT))
                : items;
    }

    private UserProfileSummary.ActivityItem activityItem(Long id, String type, Long postId,
                                                         String title, LocalDateTime createdAt) {
        UserProfileSummary.ActivityItem item = new UserProfileSummary.ActivityItem();
        item.setId(id);
        item.setType(type);
        item.setPostId(postId);
        item.setTitle(title);
        item.setCreatedAt(createdAt);
        return item;
    }

    private String commentTitle(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= COMMENT_TITLE_LIMIT
                ? normalized
                : normalized.substring(0, COMMENT_TITLE_LIMIT) + "...";
    }
}
