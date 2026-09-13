package com.nexus.campus.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Gives a deployment without demo accounts exactly one way in.
 *
 * <p>With {@code DEMO_SEED_ENABLED=false} the platform used to come up with no
 * administrator at all: the admin panel, the audit log and the review queue are
 * all role-gated, so the first thing an operator had to do was invent an account
 * by hand in SQL. This runner creates the {@code admin} account from
 * {@code BOOTSTRAP_ADMIN_PASSWORD} instead, and only when no ADMIN exists, so a
 * restart never resurrects a deleted account and never overwrites a rotated
 * password.</p>
 *
 * <p>It stands aside while demo seeding is on: {@link DataPreloader} owns the
 * same username there, and two writers for one account is how you get a
 * password that changes depending on start order.</p>
 */
@Component
@Order(200)
public class BootstrapAdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_ROLE = "ADMIN";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;
    private final boolean demoSeedEnabled;

    public BootstrapAdminInitializer(SysUserMapper sysUserMapper,
                                     PasswordEncoder passwordEncoder,
                                     @Value("${campus.bootstrap.admin-password:}") String adminPassword,
                                     @Value("${campus.demo.seed-enabled:true}") boolean demoSeedEnabled) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
        this.demoSeedEnabled = demoSeedEnabled;
    }

    @Override
    public void run(String... args) {
        bootstrap();
    }

    void bootstrap() {
        if (demoSeedEnabled) {
            log.debug("[BOOTSTRAP] Demo seeding owns the admin account, skipping.");
            return;
        }

        SysUser named = findByName(ADMIN_USERNAME);
        if (named != null) {
            if (ADMIN_ROLE.equals(named.getRole())) {
                log.debug("[BOOTSTRAP] Administrator '{}' already exists, skipping.", ADMIN_USERNAME);
                return;
            }
            // Registration is public, so the username can legitimately be taken by a
            // normal account. Claiming it back would mean mutating someone else's row.
            log.warn("[BOOTSTRAP] Username '{}' belongs to a non-admin account (id={}); refusing to take it over. "
                            + "Grant ADMIN explicitly or bootstrap under a different name.",
                    ADMIN_USERNAME, named.getId());
            return;
        }
        if (hasAdmin()) {
            log.info("[BOOTSTRAP] An ADMIN already exists, no bootstrap account created.");
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("[BOOTSTRAP] No administrator exists and BOOTSTRAP_ADMIN_PASSWORD is not set. "
                    + "The admin area stays unreachable until one is bootstrapped.");
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername(ADMIN_USERNAME);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setNickname("System Admin");
        admin.setRole(ADMIN_ROLE);
        admin.setCorePower(0);
        admin.setLevel(1);
        admin.setStatus(1);
        sysUserMapper.insert(admin);
        log.warn("[BOOTSTRAP] Created administrator '{}' from BOOTSTRAP_ADMIN_PASSWORD. "
                + "Log in and rotate this password now — the value is still in the environment file.", ADMIN_USERNAME);
    }

    private SysUser findByName(String username) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }

    private boolean hasAdmin() {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, ADMIN_ROLE));
        return count != null && count > 0;
    }
}
