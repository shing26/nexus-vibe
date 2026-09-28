package com.nexus.campus.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.VibePostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The defect B2 surfaced, pinned against a real database.
 *
 * <p>{@code benchmark/concurrency/like-concurrency.ps1} found that a post which
 * was liked and then fully unliked kept its old {@code like_count} forever: the
 * Redis set and the durable membership table both said zero, but the flush
 * refused to write the zero because it could not tell "legitimately empty" from
 * "the set was lost". The flush now treats the durable table as the authority
 * (see {@code docs/tickets/like-count-convergence.md}), and this test drives the
 * real mapper against H2 to prove the column actually converges — the pure
 * Mockito tests next door only prove the branch was taken.</p>
 *
 * <p>Runs on <strong>H2, not MySQL</strong>, and with a mocked
 * {@link RedisTemplate} because the test context has no Redis; the end-to-end
 * MySQL/Redis run is the concurrency script, which needs a rebuilt image.</p>
 */
@SpringBootTest
@Sql({"/data.sql", "/test-users.sql"})
class LikeCountConvergenceTest {

    private static final long POST_ID = 1L;
    private static final String LIKE_KEY = "post:like:" + POST_ID;
    private static final String DIRTY_KEY = "post:like:dirty";

    @Autowired
    private LikeSyncTask likeSyncTask;

    @Autowired
    private VibePostMapper vibePostMapper;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    private SetOperations<String, Object> setOps;

    @BeforeEach
    void setUp() {
        setOps = Mockito.mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        for (long userId = 1L; userId <= 7L; userId++) {
            vibePostMapper.deletePostLike(POST_ID, userId);
        }
    }

    @Test
    @DisplayName("A fully unliked post converges to zero instead of keeping the stale count")
    void unlikeToZeroConverges() {
        setStoredCount(50);
        stubFlushWithEmptyRedisSet();

        likeSyncTask.syncLikes();

        assertThat(storedCount())
                .as("Redis and the membership table both say zero; the column must follow")
                .isZero();
    }

    @Test
    @DisplayName("An empty Redis set with durable rows left rebuilds the set and keeps the durable count")
    void lostSetRebuildsFromDurableRows() {
        setStoredCount(50);
        vibePostMapper.insertPostLike(POST_ID, 1L);
        vibePostMapper.insertPostLike(POST_ID, 2L);
        vibePostMapper.insertPostLike(POST_ID, 3L);
        stubFlushWithEmptyRedisSet();

        likeSyncTask.syncLikes();

        assertThat(storedCount()).as("the durable membership is the authority").isEqualTo(3);
        verify(setOps).add(LIKE_KEY, "1");
        verify(setOps).add(LIKE_KEY, "2");
        verify(setOps).add(LIKE_KEY, "3");
        verify(setOps, never()).add(DIRTY_KEY, String.valueOf(POST_ID));
    }

    private void stubFlushWithEmptyRedisSet() {
        when(setOps.pop(DIRTY_KEY, LikeSyncTask.BATCH_SIZE)).thenReturn(List.of(String.valueOf(POST_ID)));
        when(setOps.size(LIKE_KEY)).thenReturn(0L);
    }

    private void setStoredCount(int count) {
        vibePostMapper.update(null, new LambdaUpdateWrapper<VibePost>()
                .eq(VibePost::getId, POST_ID)
                .set(VibePost::getLikeCount, count));
    }

    private int storedCount() {
        VibePost stored = vibePostMapper.selectById(POST_ID);
        assertThat(stored).as("post %d must exist in the fixture", POST_ID).isNotNull();
        return stored.getLikeCount();
    }
}
