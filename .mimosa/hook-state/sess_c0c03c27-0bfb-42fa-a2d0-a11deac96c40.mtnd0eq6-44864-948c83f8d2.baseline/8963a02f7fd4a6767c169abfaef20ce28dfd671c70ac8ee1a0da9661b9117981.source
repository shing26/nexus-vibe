package com.nexus.campus.config;

import com.nexus.campus.service.PostRankingService;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Preheats the system on startup:
 * <ul>
 *   <li>Warms the Redis ZSet hot ranking by running the gravity-decay recalculation</li>
 *   <li>Seeds default sensitive words into Redis for hot reload</li>
 *   <li>Prints a startup banner with demo credentials</li>
 * </ul>
 *
 * <p>All Redis operations gracefully degrade if the backend is unavailable.</p>
 */
@Component
public class DataPreloader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataPreloader.class);

    private final PostRankingService postRankingService;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${campus.demo.password:123456}")
    private String demoPassword;

    @Value("${campus.demo.seed-enabled:true}")
    private boolean demoSeedEnabled;

    public DataPreloader(PostRankingService postRankingService) {
        this.postRankingService = postRankingService;
    }

    @Override
    public void run(String... args) {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║        NEXUS CAMPUS — SYSTEM INITIALIZATION            ║");
        log.info("╚══════════════════════════════════════════════════════════╝");
        log.info("");

        preheatHotRanking();
        seedSensitiveWords();
        ensureAiAgent();
        ensureDemoUsers();
        if (demoSeedEnabled) {
            printCredentials();
        }

        log.info("============================================================");
        log.info("  Nexus Campus is fully operational.");
        log.info("  Demo account — username: admin / password: from DEMO_PASSWORD");
        log.info("============================================================");
        log.info("");
    }

    private void preheatHotRanking() {
        if (stringRedisTemplate == null) {
            log.info("[PREHEAT] Redis not available — hot ranking will use MySQL fallback.");
            return;
        }
        try {
            stringRedisTemplate.getConnectionFactory().getConnection().ping();
            postRankingService.recalculateHotRanking();
            log.info("[PREHEAT] Redis hot ranking recalculated successfully.");
        } catch (Exception e) {
            log.info("[PREHEAT] Redis ping failed — hot ranking will use MySQL fallback.");
        }
    }

    private void seedSensitiveWords() {
        if (stringRedisTemplate == null) {
            log.info("[PREHEAT] Redis not available — skipping sensitive word seed.");
            return;
        }
        try {
            stringRedisTemplate.getConnectionFactory().getConnection().ping();
            List<String> defaultWords = Arrays.asList(
                "fuck", "shit", "asshole", "bitch", "damn",
                "赌博", "毒品", "暴力", "色情", "诈骗", "枪支"
            );
            for (String word : defaultWords) {
                stringRedisTemplate.opsForSet().add("sys:sensitive:words", word);
            }
            log.info("[PREHEAT] {} default sensitive words seeded into Redis.", defaultWords.size());
        } catch (Exception e) {
            log.info("[PREHEAT] Redis not available — skipping sensitive word seed.");
        }
    }

    private void ensureDemoUsers() {
        String encoded;
        if (demoSeedEnabled) {
            if (demoPassword == null || demoPassword.isBlank()) {
                throw new IllegalStateException(
                        "DEMO_PASSWORD must be set when demo user seeding is enabled; refusing to seed a blank password.");
            }
            encoded = passwordEncoder.encode(demoPassword);
            log.info("[PREHEAT] Demo accounts ensured (insert-only, password source: DEMO_PASSWORD).");
        } else {
            // Keep the seed users for sample content, but give them random,
            // unrecoverable passwords so they cannot be used to log in.
            encoded = passwordEncoder.encode(UUID.randomUUID().toString());
            log.info("[PREHEAT] Demo accounts ensured with random passwords (demo seeding disabled).");
        }
        ensureDemoUser(1L, "admin", encoded, "System Admin", "default_avatar.png", "ADMIN", 99999, 8);
        ensureDemoUser(2L, "shing", encoded, "shing", "default_avatar.png", "USER", 2280, 5);
        ensureDemoUser(3L, "alice", encoded, "Alice", "default_avatar.png", "USER", 1560, 4);
        ensureDemoUser(4L, "bob", encoded, "Bob", "default_avatar.png", "USER", 920, 3);
        ensureDemoUser(5L, "testuser", encoded, "Test User", "default_avatar.png", "USER", 50, 1);
        ensureDemoUser(6L, "eve", encoded, "Eve", "default_avatar.png", "USER", 640, 3);
        ensureDemoUser(7L, "charlie", encoded, "Charlie", "default_avatar.png", "USER", 120, 2);
    }

    private void ensureAiAgent() {
        if (sysUserMapper.selectById(999L) != null) {
            return;
        }
        SysUser agent = new SysUser();
        agent.setId(999L);
        agent.setUsername("AiAgent");
        agent.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        agent.setNickname("AI 助手");
        agent.setAvatar("robot_avatar.png");
        agent.setRole("AI_AGENT");
        agent.setCorePower(0);
        agent.setLevel(1);
        agent.setStatus(1);
        sysUserMapper.insert(agent);
        log.info("[PREHEAT] AiAgent account ensured.");
    }

    /**
     * Creates a demo account only when the id is not yet taken, so a production
     * restart never resets an existing user's password or profile.
     */
    private void ensureDemoUser(Long id, String username, String password, String nickname,
                                String avatar, String role, int corePower, int level) {
        if (sysUserMapper.selectById(id) != null) {
            log.debug("[PREHEAT] Demo account {} already exists, skipping.", username);
            return;
        }
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname(nickname);
        user.setAvatar(avatar);
        user.setRole(role);
        user.setCorePower(corePower);
        user.setLevel(level);
        user.setStatus(1);
        sysUserMapper.insert(user);
    }

    private void printCredentials() {
        log.info("┌──────────────────────────────────────────────────────────┐");
        log.info("│  Demo Accounts (password: from DEMO_PASSWORD)               │");
        log.info("├──────────────────────────────────────────────────────────┤");
        log.info("│  ADMIN  │ admin    │ Full admin access (audit, dashboard) │");
        log.info("│  USER   │ alice    │ Technical Exchange active poster     │");
        log.info("│  USER   │ bob      │ Life & Career section frequent user  │");
        log.info("│  USER   │ eve      │ Academic research contributor        │");
        log.info("│  USER   │ charlie  │ New user, creative space explorer    │");
        log.info("└──────────────────────────────────────────────────────────┘");
    }
}
