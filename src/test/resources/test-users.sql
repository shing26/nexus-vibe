-- Test fixture users; mirrors the demo accounts DataPreloader creates at startup.
INSERT INTO sys_user (id, username, password, nickname, avatar, role, core_power, level, status, create_time, update_time) VALUES
(1, 'admin', '$2a$10$zbKYjy8KI2ppVBBghmeQ0ueYEHFCm68xMe1RmecbFlsyuHTdEwaDK', 'System Admin', 'default_avatar.png', 'ADMIN', 99999, 8, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'shing', '$2a$10$zbKYjy8KI2ppVBBghmeQ0ueYEHFCm68xMe1RmecbFlsyuHTdEwaDK', 'shing', 'default_avatar.png', 'USER', 2280, 5, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'alice', '$2a$10$zbKYjy8KI2ppVBBghmeQ0ueYEHFCm68xMe1RmecbFlsyuHTdEwaDK', 'Alice', 'default_avatar.png', 'USER', 1560, 4, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'bob', '$2a$10$zbKYjy8KI2ppVBBghmeQ0ueYEHFCm68xMe1RmecbFlsyuHTdEwaDK', 'Bob', 'default_avatar.png', 'USER', 920, 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 'testuser', '$2a$10$YR6jgYrZv667md4VbDWYjuZ0Ya3kwc9uhYMzEqXX/cAieL2YzsMY.', 'Test User', 'default_avatar.png', 'USER', 50, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 'eve', '$2a$10$zbKYjy8KI2ppVBBghmeQ0ueYEHFCm68xMe1RmecbFlsyuHTdEwaDK', 'Eve', 'default_avatar.png', 'USER', 640, 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(7, 'charlie', '$2a$10$zbKYjy8KI2ppVBBghmeQ0ueYEHFCm68xMe1RmecbFlsyuHTdEwaDK', 'Charlie', 'default_avatar.png', 'USER', 120, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(999, 'AiAgent', 'NOLOGIN_AI_AGENT_ACCOUNT', 'AI 助手', 'robot_avatar.png', 'AI_AGENT', 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
