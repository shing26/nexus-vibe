package com.nexus.campus.service;

import com.nexus.campus.dto.JwtResponse;
import com.nexus.campus.dto.LoginRequest;
import com.nexus.campus.dto.RegisterRequest;
import com.nexus.campus.entity.SysUser;
import com.nexus.campus.mapper.SysUserMapper;
import com.nexus.campus.service.impl.SysUserServiceImpl;
import com.nexus.campus.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SysUserServiceImpl sysUserService;

    private SysUser seedUser;
    private final String rawPassword = "testPass123";
    private final String encryptedPassword = SysUserServiceImpl.encryptPassword(rawPassword);

    @BeforeEach
    void setUp() {
        seedUser = new SysUser();
        seedUser.setId(100L);
        seedUser.setUsername("testuser");
        seedUser.setPassword(encryptedPassword);
        seedUser.setNickname("Test User");
        seedUser.setRole("USER");
        seedUser.setCorePower(50);
        seedUser.setLevel(1);
        seedUser.setStatus(1);
        seedUser.setAvatar("default_avatar.png");
        when(passwordEncoder.matches(any(), anyString())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$mock-hash-for-tests");
    }

    @Test
    @DisplayName("login() with valid credentials should return JwtResponse")
    void loginSuccess() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword(rawPassword);

        when(sysUserMapper.selectOne(any())).thenReturn(seedUser);
        when(jwtUtil.generateToken(100L, "testuser", "USER")).thenReturn("mock-jwt-token");

        JwtResponse response = sysUserService.login(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals(100L, response.getUserId());
        assertEquals("testuser", response.getUsername());
        assertEquals("USER", response.getRole());
        assertEquals(50, response.getCorePower());
        assertEquals(1, response.getLevel());
    }

    @Test
    @DisplayName("login() with wrong password should throw RuntimeException")
    void loginWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrongpassword");

        when(sysUserMapper.selectOne(any())).thenReturn(seedUser);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> sysUserService.login(request));
        assertTrue(ex.getMessage().contains("Invalid username or password"));
    }

    @Test
    @DisplayName("login() for deactivated account should throw RuntimeException")
    void loginDeactivatedAccount() {
        seedUser.setStatus(0);
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword(rawPassword);

        when(sysUserMapper.selectOne(any())).thenReturn(seedUser);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> sysUserService.login(request));
        assertTrue(ex.getMessage().contains("deactivated"));
    }

    @Test
    @DisplayName("login() with nonexistent username should throw RuntimeException")
    void loginUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nobody");
        request.setPassword(rawPassword);

        when(sysUserMapper.selectOne(any())).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> sysUserService.login(request));
        assertTrue(ex.getMessage().contains("Invalid username or password"));
    }

    @Test
    @DisplayName("register() with new username should create user and return JwtResponse")
    void registerNewUser() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword(rawPassword);
        request.setNickname("New User");

        when(sysUserMapper.selectOne(any())).thenReturn(null);
        when(jwtUtil.generateToken(any(), eq("newuser"), eq("USER"))).thenReturn("mock-jwt-token");

        JwtResponse response = sysUserService.register(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals("newuser", response.getUsername());
        assertEquals("USER", response.getRole());
        assertEquals(0, response.getCorePower());
        assertEquals(1, response.getLevel());

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert((SysUser) captor.capture());
        SysUser inserted = captor.getValue();
        assertEquals("newuser", inserted.getUsername());
        assertEquals("New User", inserted.getNickname());
        assertEquals("USER", inserted.getRole());
        assertEquals(0, inserted.getCorePower());
        assertEquals(1, inserted.getLevel());
        assertEquals(1, inserted.getStatus());
    }

    @Test
    @DisplayName("register() with existing username should throw RuntimeException")
    void registerDuplicateUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword(rawPassword);
        request.setNickname("Duplicate");

        when(sysUserMapper.selectOne(any())).thenReturn(seedUser);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> sysUserService.register(request));
        assertTrue(ex.getMessage().contains("already exists"));
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("getUserById() should return the user when found")
    void getUserByIdFound() {
        when(sysUserMapper.selectById(100L)).thenReturn(seedUser);

        SysUser result = sysUserService.getUserById(100L);
        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("testuser", result.getUsername());
    }

    @Test
    @DisplayName("getUserById() should return null when not found")
    void getUserByIdNotFound() {
        when(sysUserMapper.selectById(999L)).thenReturn(null);

        SysUser result = sysUserService.getUserById(999L);
        assertNull(result);
    }

    @Test
    @DisplayName("getUserByUsername() should return the user when found")
    void getUserByUsernameFound() {
        when(sysUserMapper.selectOne(any())).thenReturn(seedUser);

        SysUser result = sysUserService.getUserByUsername("testuser");
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
    }

    @Test
    @DisplayName("getUserByUsername() should return null when not found")
    void getUserByUsernameNotFound() {
        when(sysUserMapper.selectOne(any())).thenReturn(null);

        SysUser result = sysUserService.getUserByUsername("nobody");
        assertNull(result);
    }

    @Test
    @DisplayName("addCorePower() should add points and upgrade level when crossing threshold")
    void addCorePowerWithLevelUp() {
        seedUser.setCorePower(95);
        when(sysUserMapper.selectById(100L)).thenReturn(seedUser);
        when(sysUserMapper.updateById(any(SysUser.class))).thenReturn(1);

        boolean result = sysUserService.addCorePower(100L, 10);

        assertTrue(result);
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        SysUser updated = captor.getValue();
        assertEquals(105, updated.getCorePower());
        assertEquals(2, updated.getLevel());
    }

    @Test
    @DisplayName("addCorePower() should add points without level upgrade when staying in same tier")
    void addCorePowerWithoutLevelUp() {
        seedUser.setCorePower(50);
        when(sysUserMapper.selectById(100L)).thenReturn(seedUser);
        when(sysUserMapper.updateById(any(SysUser.class))).thenReturn(1);

        boolean result = sysUserService.addCorePower(100L, 20);

        assertTrue(result);
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        SysUser updated = captor.getValue();
        assertEquals(70, updated.getCorePower());
        assertEquals(1, updated.getLevel());
    }

    @Test
    @DisplayName("addCorePower() should return false when user does not exist")
    void addCorePowerUserNotFound() {
        when(sysUserMapper.selectById(999L)).thenReturn(null);

        boolean result = sysUserService.addCorePower(999L, 10);
        assertFalse(result);
        verify(sysUserMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("addCorePower() should handle max level (level 8)")
    void addCorePowerMaxLevel() {
        seedUser.setCorePower(99999);
        seedUser.setLevel(7);
        when(sysUserMapper.selectById(100L)).thenReturn(seedUser);
        when(sysUserMapper.updateById(any(SysUser.class))).thenReturn(1);

        boolean result = sysUserService.addCorePower(100L, 1);

        assertTrue(result);
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertEquals(100000, captor.getValue().getCorePower());
        assertEquals(8, captor.getValue().getLevel());
    }
}
