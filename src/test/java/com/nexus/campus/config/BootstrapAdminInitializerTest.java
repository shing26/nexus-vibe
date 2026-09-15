package com.nexus.campus.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the three startup states an operator can actually land in, with the real
 * encoder so a created account is provably loginable.
 */
@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {

    @Mock
    private SysUserMapper sysUserMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void captureLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(BootstrapAdminInitializer.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void releaseLogs() {
        ((Logger) LoggerFactory.getLogger(BootstrapAdminInitializer.class)).detachAppender(logAppender);
    }

    private BootstrapAdminInitializer initializer(String password, boolean demoSeedEnabled) {
        return new BootstrapAdminInitializer(sysUserMapper, passwordEncoder, password, demoSeedEnabled);
    }

    private boolean loggedAt(Level level, String fragment) {
        return logAppender.list.stream()
                .anyMatch(e -> e.getLevel() == level && e.getFormattedMessage().contains(fragment));
    }

    @Test
    @DisplayName("No ADMIN and a bootstrap password: creates admin with a loginable password")
    void createsAdminWhenPasswordProvided() {
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(sysUserMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        initializer("ChangeMe123", false).run();

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        SysUser admin = captor.getValue();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        assertThat(admin.getStatus()).isEqualTo(1);
        assertThat(passwordEncoder.matches("ChangeMe123", admin.getPassword())).isTrue();
        // The warning is the rotation nudge: the password is sitting in an env file.
        assertThat(loggedAt(Level.WARN, "rotate")).isTrue();
    }

    @Test
    @DisplayName("No ADMIN and no bootstrap password: creates nothing and warns once")
    void warnsAndSkipsWithoutPassword() {
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(sysUserMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        initializer("", false).run();

        verify(sysUserMapper, never()).insert(any(SysUser.class));
        assertThat(logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("BOOTSTRAP_ADMIN_PASSWORD"))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("An ADMIN already exists: idempotent skip, password untouched")
    void skipsWhenAdminExists() {
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(sysUserMapper.selectCount(any(Wrapper.class))).thenReturn(2L);

        initializer("ChangeMe123", false).run();

        verify(sysUserMapper, never()).insert(any(SysUser.class));
        assertThat(loggedAt(Level.WARN, "rotate")).isFalse();
    }

    @Test
    @DisplayName("The bootstrap account itself is already there: second start is silent")
    void skipsWhenAdminUsernameTakenByAdmin() {
        SysUser existing = new SysUser();
        existing.setId(42L);
        existing.setUsername("admin");
        existing.setRole("ADMIN");
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        initializer("ChangeMe123", false).run();

        verify(sysUserMapper, never()).insert(any(SysUser.class));
        verify(sysUserMapper, never()).updateById(any(SysUser.class));
        assertThat(loggedAt(Level.WARN, "BOOTSTRAP_ADMIN_PASSWORD")).isFalse();
    }

    @Test
    @DisplayName("A registered user holds the name admin: refuse to take the row over")
    void refusesToPromoteSomeoneElsesAccount() {
        SysUser squatter = new SysUser();
        squatter.setId(7L);
        squatter.setUsername("admin");
        squatter.setRole("USER");
        when(sysUserMapper.selectOne(any(Wrapper.class))).thenReturn(squatter);

        initializer("ChangeMe123", false).run();

        verify(sysUserMapper, never()).insert(any(SysUser.class));
        verify(sysUserMapper, never()).updateById(any(SysUser.class));
        assertThat(loggedAt(Level.WARN, "refusing to take it over")).isTrue();
    }

    @Test
    @DisplayName("Demo seeding owns the account: this runner stays out of the way")
    void standsAsideWhileDemoSeedingIsOn() {
        initializer("ChangeMe123", true).run();

        verify(sysUserMapper, never()).insert(any(SysUser.class));
        verify(sysUserMapper, never()).selectOne(any(Wrapper.class));
    }
}
