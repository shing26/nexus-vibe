package com.nexus.campus.config;

import com.nexus.campus.entity.SysUser;
import com.nexus.campus.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The seed switch has to decide accounts, not merely their passwords. Before this
 * the seven sample authors were inserted with a random password on every real
 * deployment, which is how production ended up with accounts nobody could use and
 * content nobody owned.
 */
@ExtendWith(MockitoExtension.class)
class DataPreloaderSeedGateTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DataPreloader preloader;

    @BeforeEach
    void wireFields() {
        // Redis is absent from the unit context, which is the path the preheat steps take
        // when the backend is unavailable; the two seed steps below are what is under test.
        preloader = new DataPreloader(null);
        ReflectionTestUtils.setField(preloader, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(preloader, "passwordEncoder", passwordEncoder);
    }

    private void configure(boolean seedEnabled, String demoPassword) {
        ReflectionTestUtils.setField(preloader, "demoSeedEnabled", seedEnabled);
        ReflectionTestUtils.setField(preloader, "demoPassword", demoPassword);
    }

    @Test
    @DisplayName("Seeding off: no sample accounts, but the AI agent row is still ensured")
    void productionStartCreatesNoDemoUsers() {
        configure(false, "");
        when(sysUserMapper.selectById(999L)).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("$2a$random");

        preloader.run();

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(SysUser::getId).containsExactly(999L);
        assertThat(captor.getAllValues()).extracting(SysUser::getRole).containsExactly("AI_AGENT");
        verify(sysUserMapper, never()).selectById(1L);
        verify(sysUserMapper, never()).selectById(7L);
        // Exactly one hash, and it is the agent's throwaway one: DEMO_PASSWORD is never
        // read on this path, so no account can be logged into with it.
        verify(passwordEncoder, times(1)).encode(any());
    }

    @Test
    @DisplayName("Seeding on: all seven sample accounts get the DEMO_PASSWORD hash")
    void demoStartSeedsAccounts() {
        configure(true, "DemoPass123");
        when(sysUserMapper.selectById(anyLong())).thenReturn(null);
        when(passwordEncoder.encode(any())).thenAnswer(invocation ->
                "DemoPass123".equals(invocation.getArgument(0)) ? "$2a$demo" : "$2a$agent-throwaway");

        preloader.run();

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper, times(8)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(SysUser::getId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 999L);
        assertThat(captor.getAllValues()).filteredOn(user -> user.getId() <= 7L)
                .allMatch(user -> "$2a$demo".equals(user.getPassword()));
    }

    @Test
    @DisplayName("Seeding on with a blank password still fails fast")
    void demoStartWithoutPasswordRefuses() {
        configure(true, "  ");
        when(sysUserMapper.selectById(999L)).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("$2a$random");

        assertThatThrownBy(() -> preloader.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_PASSWORD");
    }
}
