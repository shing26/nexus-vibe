package com.nexus.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.campus.entity.VibePost;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface VibePostMapper extends BaseMapper<VibePost> {

    @Select("SELECT p.*, u.nickname as authorName, c.name as categoryName " +
            "FROM vibe_post p " +
            "LEFT JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN vibe_channel c ON p.category_id = c.id " +
            "WHERE p.status = 1 " +
            "ORDER BY p.is_pinned DESC, p.create_time DESC")
    List<VibePost> selectActivePosts();

    @Select({"<script>",
            "SELECT p.*, u.nickname as authorName, c.name as categoryName ",
            "FROM vibe_post p ",
            "LEFT JOIN sys_user u ON p.user_id = u.id ",
            "LEFT JOIN vibe_channel c ON p.category_id = c.id ",
            "WHERE p.status = 1 ",
            "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', #{keyword}, '%')) ",
            "OR LOWER(p.content) LIKE LOWER(CONCAT('%', #{keyword}, '%'))) ",
            "ORDER BY p.create_time DESC",
            "</script>"})
    Page<VibePost> selectSearchPage(Page<VibePost> page, @Param("keyword") String keyword);

    @Select({"<script>",
            "SELECT p.*, u.nickname as authorName, c.name as categoryName ",
            "FROM vibe_post p ",
            "LEFT JOIN sys_user u ON p.user_id = u.id ",
            "LEFT JOIN vibe_channel c ON p.category_id = c.id ",
            "WHERE p.status = 1 ",
            "<if test='categoryId != null'> AND p.category_id = #{categoryId} </if>",
            "<if test='postType != null'> AND p.post_type = #{postType} </if>",
            "ORDER BY p.is_pinned DESC, p.create_time DESC",
            "</script>"})
    Page<VibePost> selectPostPage(Page<VibePost> page,
                                  @Param("categoryId") Integer categoryId,
                                  @Param("postType") String postType);

    @Select({"<script>",
            "SELECT p.*, u.nickname as authorName, c.name as categoryName ",
            "FROM vibe_post p ",
            "LEFT JOIN sys_user u ON p.user_id = u.id ",
            "LEFT JOIN vibe_channel c ON p.category_id = c.id ",
            "WHERE p.status = 1 ",
            "<if test='keyword != null and keyword != \"\"'>",
            " AND (LOWER(p.title) LIKE LOWER(CONCAT('%', #{keyword}, '%')) ",
            " OR LOWER(p.content) LIKE LOWER(CONCAT('%', #{keyword}, '%'))) ",
            "</if>",
            "<if test='categoryId != null'> AND p.category_id = #{categoryId} </if>",
            "<if test='postType != null'> AND p.post_type = #{postType} </if>",
            "<if test='language != null and language != \"\"'>",
            " AND (LOWER(p.content) LIKE LOWER(CONCAT('%', '```', #{language}, '%')) ",
            " OR LOWER(COALESCE(p.prompt_metadata, '')) LIKE LOWER(CONCAT('%', #{language}, '%'))) ",
            "</if>",
            "<if test='aiScoreMin != null'>",
            " AND p.ai_reviewed = 1 AND p.ai_review_score * 10 &gt;= #{aiScoreMin} ",
            "</if>",
            "<choose>",
            "<when test='sort == \"hot\"'> ORDER BY p.is_pinned DESC, p.like_count DESC, p.create_time DESC </when>",
            "<when test='sort == \"ai\"'> ORDER BY p.ai_reviewed DESC, p.ai_review_score DESC, p.create_time DESC </when>",
            "<otherwise> ORDER BY p.is_pinned DESC, p.create_time DESC </otherwise>",
            "</choose>",
            "</script>"})
    Page<VibePost> selectFilteredPage(Page<VibePost> page,
                                      @Param("keyword") String keyword,
                                      @Param("categoryId") Integer categoryId,
                                      @Param("postType") String postType,
                                      @Param("language") String language,
                                      @Param("aiScoreMin") Integer aiScoreMin,
                                      @Param("sort") String sort);

    @Select("SELECT p.*, u.nickname as authorName, c.name as categoryName " +
            "FROM vibe_post p " +
            "LEFT JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN vibe_channel c ON p.category_id = c.id " +
            "WHERE p.status = 1 AND p.category_id = #{categoryId} " +
            "ORDER BY p.create_time DESC")
    List<VibePost> selectPostsByCategory(@Param("categoryId") Integer categoryId);

    @Select("SELECT p.*, u.nickname as authorName, c.name as categoryName " +
            "FROM vibe_post p " +
            "LEFT JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN vibe_channel c ON p.category_id = c.id " +
            "WHERE p.id = #{id}")
    VibePost selectPostWithDetails(@Param("id") Long id);

    @Select("SELECT p.*, u.nickname as authorName, c.name as categoryName " +
            "FROM vibe_post p " +
            "LEFT JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN vibe_channel c ON p.category_id = c.id " +
            "WHERE p.status = 1 AND (p.title LIKE CONCAT('%', #{keyword}, '%') OR p.content LIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY p.create_time DESC")
    List<VibePost> searchPosts(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM vibe_post WHERE status = 1 AND create_time >= CURDATE()")
    int countTodayPosts();

    @Update("UPDATE vibe_post SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);

    @Update("UPDATE vibe_post SET like_count = like_count + 1 WHERE id = #{id}")
    int incrementLikeCount(@Param("id") Long id);

    @Select("SELECT p.*, u.nickname as authorName, c.name as categoryName " +
            "FROM vibe_post p " +
            "LEFT JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN vibe_channel c ON p.category_id = c.id " +
            "WHERE p.user_id = #{userId} " +
            "ORDER BY p.create_time DESC")
    List<VibePost> selectPostsByUserId(@Param("userId") Long userId);

    @Select("SELECT p.*, u.nickname as authorName, c.name as categoryName " +
            "FROM vibe_post p " +
            "LEFT JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN vibe_channel c ON p.category_id = c.id " +
            "WHERE p.status = 2 " +
            "ORDER BY p.create_time DESC")
    List<VibePost> selectPendingAuditPosts();

    @Update("UPDATE vibe_post SET like_count = #{count} WHERE id = #{id}")
    int updateLikeCount(@Param("id") Long id, @Param("count") Integer count);

    @Update("UPDATE vibe_post SET like_count = GREATEST(like_count + #{delta}, 0) WHERE id = #{postId}")
    int updateLikeCountDelta(@Param("postId") Long postId, @Param("delta") int delta);

    @Update("UPDATE vibe_post SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = #{postId}")
    int decrementCommentCount(@Param("postId") Long postId);

    @Insert("INSERT INTO vibe_post_like (post_id, user_id) VALUES (#{postId}, #{userId})")
    int insertPostLike(@Param("postId") Long postId, @Param("userId") Long userId);

    @Delete("DELETE FROM vibe_post_like WHERE post_id = #{postId} AND user_id = #{userId}")
    int deletePostLike(@Param("postId") Long postId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM vibe_post_like WHERE post_id = #{postId} AND user_id = #{userId}")
    int countPostLike(@Param("postId") Long postId, @Param("userId") Long userId);

    @Select("SELECT p.*, u.nickname as authorName, c.name as categoryName " +
            "FROM vibe_post p " +
            "LEFT JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN vibe_channel c ON p.category_id = c.id " +
            "WHERE p.status = 1 " +
            "ORDER BY p.like_count DESC, p.create_time DESC " +
            "LIMIT #{limit}")
    List<VibePost> selectTopLikedPosts(@Param("limit") int limit);

    @Select({"<script>",
            "SELECT p.*, u.nickname as authorName, c.name as categoryName ",
            "FROM vibe_post p ",
            "LEFT JOIN sys_user u ON p.user_id = u.id ",
            "LEFT JOIN vibe_channel c ON p.category_id = c.id ",
            "WHERE p.id IN ",
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach> ",
            "</script>"})
    List<VibePost> selectByIdsOrdered(@Param("ids") List<Long> ids);

    @Select("SELECT p.*, u.nickname as authorName, c.name as categoryName " +
            "FROM vibe_post p " +
            "LEFT JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN vibe_channel c ON p.category_id = c.id " +
            "WHERE p.status = 1 AND p.category_id = #{categoryId} " +
            "ORDER BY p.create_time DESC")
    List<VibePost> selectActivePostsByCategory(@Param("categoryId") Integer categoryId);

    @Select("SELECT p.*, u.nickname as authorName, c.name as categoryName " +
            "FROM vibe_post p " +
            "LEFT JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN vibe_channel c ON p.category_id = c.id " +
            "WHERE p.status = 1 " +
            "ORDER BY p.is_pinned DESC, p.create_time DESC")
    List<VibePost> selectActivePostsOrdered();

    @Select("SELECT p.*, u.nickname as authorName, c.name as categoryName " +
            "FROM vibe_post p " +
            "LEFT JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN vibe_channel c ON p.category_id = c.id " +
            "WHERE p.status = 1 AND p.is_pinned = 1 " +
            "ORDER BY p.create_time DESC")
    List<VibePost> selectPinnedPosts();

    @Select("SELECT p.*, u.nickname as authorName, c.name as categoryName " +
            "FROM vibe_post p " +
            "LEFT JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN vibe_channel c ON p.category_id = c.id " +
            "WHERE p.status = 1 " +
            "ORDER BY p.is_pinned DESC, p.create_time DESC")
    List<VibePost> selectActivePostsPinnedFirst();

    @Select("SELECT COUNT(*) FROM vibe_post WHERE user_id = #{userId} AND status = 1")
    long countActivePostsByUserId(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(like_count), 0) FROM vibe_post WHERE user_id = #{userId} AND status = 1")
    long sumLikeCountByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM vibe_post WHERE user_id = #{userId} AND status = 1 AND forked_from_id IS NOT NULL")
    long countForksByUserId(@Param("userId") Long userId);

    @Select("SELECT p.*, u.nickname as authorName, c.name as categoryName " +
            "FROM vibe_post p " +
            "LEFT JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN vibe_channel c ON p.category_id = c.id " +
            "WHERE p.user_id = #{userId} AND p.status = 1 " +
            "ORDER BY p.create_time DESC, p.id DESC LIMIT 10")
    List<VibePost> selectLatestActivePostsByUserId(@Param("userId") Long userId);

    @Update("UPDATE vibe_post SET is_pinned = 1 WHERE id = #{id}")
    int pinPost(@Param("id") Long id);

    @Update("UPDATE vibe_post SET is_pinned = 0 WHERE id = #{id}")
    int unpinPost(@Param("id") Long id);
    @Update("UPDATE vibe_post SET status = #{status} WHERE id = #{id}")
    int updatePostStatus(@Param("id") Long id, @Param("status") Integer status);
}
