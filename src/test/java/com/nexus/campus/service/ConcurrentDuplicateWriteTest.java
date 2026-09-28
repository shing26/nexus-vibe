package com.nexus.campus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.campus.dto.RegisterRequest;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.exception.BusinessException;
import com.nexus.campus.mapper.SysUserMapper;
import com.nexus.campus.mapper.VibePostMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The duplicate-write paths that a single-threaded test cannot see: two
 * requests racing on the same unique key.
 *
 * <p>Both mechanisms are database constraints rather than application locks, so
 * the test has to run the statements for real and concurrently. It runs on H2
 * in MySQL compatibility mode, <strong>not MySQL</strong>: the assertions are
 * about the shape of the guarantee (exactly one row survives, the loser gets a
 * translated conflict, no statement escapes as an unhandled 500), and H2 is
 * what {@code mvn test} has on this machine. The MySQL-side behaviour is
 * exercised by {@code benchmark/concurrency/like-concurrency.ps1} against the
 * running stack.</p>
 */
@SpringBootTest
@Sql({"/data.sql", "/test-users.sql"})
class ConcurrentDuplicateWriteTest {

    private static final int LIKE_THREADS = 32;
    private static final int REGISTER_THREADS = 16;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserService sysUserService;

    @Test
    @DisplayName("32 concurrent like-mirror writes to one (post,user) leave exactly one row and raise nothing")
    void concurrentLikeMirrorWrites_keepExactlyOneRow() throws Exception {
        long postId = 1L;
        long userId = 5L;
        // Start from a clean slate: data.sql seeds no likes, but be explicit so a
        // rerun of the class cannot inherit a row from an earlier test.
        vibePostMapper.deletePostLike(postId, userId);
        assertThat(vibePostMapper.countPostLike(postId, userId)).isZero();

        AtomicInteger inserted = new AtomicInteger();
        AtomicInteger ignored = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger maxRowsObserved = new AtomicInteger();

        runTogether(LIKE_THREADS, () -> {
            int rows = vibePostMapper.insertPostLike(postId, userId);
            if (rows > 0) {
                inserted.incrementAndGet();
            } else {
                // INSERT IGNORE swallows the duplicate; the caller sees 0 rows.
                ignored.incrementAndGet();
            }
            // Sample the row count from inside the race so a transient second row
            // (which the unique key is supposed to make impossible) is caught even
            // if the final state happens to look clean.
            maxRowsObserved.accumulateAndGet(
                    vibePostMapper.countPostLike(postId, userId), Math::max);
        }, errors);

        assertThat(errors).as("no statement may escape as an unhandled exception").hasValue(0);
        assertThat(inserted).as("exactly one INSERT IGNORE may report a written row").hasValue(1);
        assertThat(ignored).as("every other writer is ignored, not failed").hasValue(LIKE_THREADS - 1);
        assertThat(maxRowsObserved).as("the row count may never exceed one at any observation").hasValue(1);
        assertThat(vibePostMapper.countPostLike(postId, userId)).isEqualTo(1);
        assertThat(vibePostMapper.countPostLikes(postId)).isEqualTo(1);
    }

    @Test
    @DisplayName("16 concurrent registrations of one username yield one success, fifteen 409s, one row")
    void concurrentRegistration_sameUsername_oneWinnerRestConflict() throws Exception {
        String username = "race-user";
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger otherFailures = new AtomicInteger();

        runTogether(REGISTER_THREADS, () -> {
            RegisterRequest request = new RegisterRequest();
            request.setUsername(username);
            request.setEmail("race-user@example.invalid");
            request.setPassword("RacePass1");
            request.setNickname("Race User");
            try {
                sysUserService.register(request);
                success.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getStatus().value() == 409) {
                    conflict.incrementAndGet();
                } else {
                    otherFailures.incrementAndGet();
                }
            }
        }, errors);

        assertThat(errors).as("DuplicateKeyException must be translated, never surface raw").hasValue(0);
        assertThat(otherFailures).as("no failure may be anything other than the 409 conflict").hasValue(0);
        assertThat(success).as("exactly one registration may win the username").hasValue(1);
        assertThat(conflict).as("every loser is told the name is taken").hasValue(REGISTER_THREADS - 1);

        Long rows = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        assertThat(rows).as("sys_user may hold exactly one row for the contested username").isEqualTo(1L);
    }

    /**
     * Starts {@code threads} workers at the same instant, waits for all of them,
     * and counts any exception the worker body did not handle itself. The latch
     * is what makes this a race rather than a loop.
     */
    private static void runTogether(int threads, Runnable body, AtomicInteger errors) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        body.run();
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            startGate.countDown();
            assertThat(doneGate.await(60, TimeUnit.SECONDS))
                    .as("all %d workers must finish within 60s", threads)
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
