package com.nexus.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.campus.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE status = 1 AND (username LIKE CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%'))")
    List<SysUser> searchByKeyword(@Param("keyword") String keyword);

    /**
     * Users who published since the cutoff, newest first.
     *
     * <p>The cutoff is a parameter rather than {@code DATEADD('DAY', -7,
     * CURRENT_TIMESTAMP)}, which is what this used to say. That is H2's spelling;
     * MySQL has no {@code DATEADD}, so on the deployment this query answers 500 from
     * a public endpoint while passing every test — the tests run on H2. Both engines
     * take a bound timestamp, so the window now comes from the caller.</p>
     *
     * <p>The {@code JOIN} and {@code DISTINCT} it travelled with went the same way for
     * the same reason: {@code SELECT DISTINCT u.* … ORDER BY p.create_time} is MySQL
     * error 3065, whose {@code ONLY_FULL_GROUP_BY} refuses an ordering on a column the
     * projection does not contain. {@code EXISTS} removes the duplicates the
     * {@code DISTINCT} was compensating for, and the ranking key returns as its own
     * aliased column, which both engines accept. The response shape is untouched — the
     * extra column maps to no property on {@code SysUser}, so MyBatis drops it.</p>
     */
    @Select("SELECT u.*, " +
            "(SELECT MAX(p.create_time) FROM vibe_post p " +
            "  WHERE p.user_id = u.id AND p.status = 1 AND p.create_time >= #{since}) AS last_post_time " +
            "FROM sys_user u " +
            "WHERE EXISTS (SELECT 1 FROM vibe_post p " +
            "  WHERE p.user_id = u.id AND p.status = 1 AND p.create_time >= #{since}) " +
            "ORDER BY last_post_time DESC " +
            "LIMIT #{limit}")
    List<SysUser> selectRecentActiveUsers(@Param("since") java.time.LocalDateTime since,
                                          @Param("limit") int limit);
}
