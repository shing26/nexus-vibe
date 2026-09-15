package com.nexus.campus.mapper;

import com.nexus.campus.metrics.RegistrationCohort;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The three reads behind the product-loop gauges. Every window is a bound
 * timestamp rather than a SQL date function, on purpose: these run against H2 in
 * tests and MySQL in the deployment, and the two spell "seven days ago"
 * differently enough that only one of them has ever been executed here.
 */
@Mapper
public interface FunnelMapper {

    /**
     * Registrations in the cohort window with each author's first post, which is
     * null when they never published. The seven-day comparison happens in Java.
     */
    @Select("SELECT u.id AS user_id, u.create_time AS registered_at, " +
            "(SELECT MIN(p.create_time) FROM vibe_post p WHERE p.user_id = u.id) AS first_post_at " +
            "FROM sys_user u WHERE u.create_time >= #{since}")
    List<RegistrationCohort> selectRegistrationCohort(@Param("since") LocalDateTime since);

    /**
     * Distinct authors with a post or a comment since the cutoff.
     *
     * <p>Content is the definition of "active". A {@code last_active_at} column
     * written on every authenticated request would measure browsing instead, at the
     * cost of a schema change and a hot-path write; that trade is recorded in the
     * ticket, and its price is that a visitor who reads and never posts is invisible
     * to this number.</p>
     */
    @Select("SELECT COUNT(*) FROM (" +
            "SELECT user_id FROM vibe_post WHERE create_time >= #{since} " +
            "UNION " +
            "SELECT user_id FROM vibe_comment WHERE create_time >= #{since}) content_authors")
    long countContentAuthorsSince(@Param("since") LocalDateTime since);

    /** Denominator for the active-content ratio: everybody who has ever registered. */
    @Select("SELECT COUNT(*) FROM sys_user")
    long countUsers();
}
