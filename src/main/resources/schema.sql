-- Nexus-Campus Database Schema

-- 1. User Core Table
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL PRIMARY KEY,
  `username` varchar(50) NOT NULL UNIQUE,
  `password` varchar(100) NOT NULL,
  `nickname` varchar(50) NOT NULL,
  `avatar` varchar(255) DEFAULT 'default_avatar.png',
  `bio` varchar(255) DEFAULT NULL,
  `role` varchar(20) NOT NULL DEFAULT 'USER',
  `core_power` int NOT NULL DEFAULT 0,
  `level` int NOT NULL DEFAULT 1,
  `status` tinyint NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Post Category Table
CREATE TABLE IF NOT EXISTS `vibe_channel` (
  `id` int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `name` varchar(50) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `slug` varchar(50) DEFAULT NULL UNIQUE,
  `sort_order` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Tag Table
CREATE TABLE IF NOT EXISTS `vibe_tag` (
  `id` int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `name` varchar(30) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Post Data Table
CREATE TABLE IF NOT EXISTS `vibe_post` (
  `id` bigint NOT NULL PRIMARY KEY,
  `user_id` bigint NOT NULL,
  `category_id` int NOT NULL,
  `title` varchar(150) NOT NULL,
  `content` longtext NOT NULL,
  `summary` varchar(255) DEFAULT NULL,
  `view_count` int NOT NULL DEFAULT 0,
  `like_count` int NOT NULL DEFAULT 0,
  `comment_count` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1-Active, 2-Pending Audit, 3-Rejected',
  `is_pinned` tinyint NOT NULL DEFAULT 0 COMMENT '0-Normal, 1-Pinned',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS `idx_post_user_id` ON `vibe_post`(`user_id`);
CREATE INDEX IF NOT EXISTS `idx_post_category_id` ON `vibe_post`(`category_id`);
CREATE INDEX IF NOT EXISTS `idx_post_status` ON `vibe_post`(`status`);

-- 5. Post-Tag Junction Table
CREATE TABLE IF NOT EXISTS `vibe_post_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `post_id` bigint NOT NULL,
  `tag_id` int NOT NULL
);

-- 5b. Post Like Table (unique per user, keeps MySQL fallback idempotent)
CREATE TABLE IF NOT EXISTS `vibe_post_like` (
  `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `uk_post_user` UNIQUE (`post_id`, `user_id`)
);
CREATE INDEX IF NOT EXISTS `idx_like_post_id` ON `vibe_post_like`(`post_id`);
CREATE INDEX IF NOT EXISTS `idx_like_user_id` ON `vibe_post_like`(`user_id`);

-- 6. Neuron Comment Table
CREATE TABLE IF NOT EXISTS `vibe_comment` (
  `id` bigint NOT NULL PRIMARY KEY,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `parent_id` bigint NOT NULL DEFAULT 0,
  `target_id` bigint NOT NULL DEFAULT 0,
  `content` text NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS `idx_comment_post_id` ON `vibe_comment`(`post_id`);
CREATE INDEX IF NOT EXISTS `idx_comment_parent_id` ON `vibe_comment`(`parent_id`);

-- 7. Message Table
CREATE TABLE IF NOT EXISTS `sys_message` (
  `id` bigint NOT NULL PRIMARY KEY,
  `from_user_id` bigint NOT NULL,
  `to_user_id` bigint NOT NULL,
  `content` text NOT NULL,
  `type` tinyint NOT NULL DEFAULT 1,
  `is_read` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS `idx_msg_to_user` ON `sys_message`(`to_user_id`);
CREATE INDEX IF NOT EXISTS `idx_msg_from_user` ON `sys_message`(`from_user_id`);

-- 8. AI Review Log Table
CREATE TABLE IF NOT EXISTS `ai_review_log` (
  `id` bigint NOT NULL PRIMARY KEY,
  `post_id` bigint NOT NULL,
  `reviewer` varchar(50) NOT NULL DEFAULT 'code-review-agent',
  `result_json` text,
  `severity` varchar(20),
  `is_approved` tinyint DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. AI Review columns for vibe_post
ALTER TABLE `vibe_post` ADD COLUMN IF NOT EXISTS `code_snippets` text;
ALTER TABLE `vibe_post` ADD COLUMN IF NOT EXISTS `ai_reviewed` tinyint NOT NULL DEFAULT 0;
ALTER TABLE `vibe_post` ADD COLUMN IF NOT EXISTS `ai_review_score` int NOT NULL DEFAULT 0;
ALTER TABLE `vibe_post` ADD COLUMN IF NOT EXISTS `token_count` int NOT NULL DEFAULT 0;
ALTER TABLE `vibe_post` ADD COLUMN IF NOT EXISTS `post_type` varchar(10) NOT NULL DEFAULT 'post';
ALTER TABLE `vibe_post` ADD COLUMN IF NOT EXISTS `prompt_metadata` text;
ALTER TABLE `vibe_post` ADD COLUMN IF NOT EXISTS `forked_from_id` bigint;

-- 10. Prompt template version history
CREATE TABLE IF NOT EXISTS `vibe_prompt_version` (
  `id` bigint NOT NULL PRIMARY KEY,
  `post_id` bigint NOT NULL,
  `version` int NOT NULL,
  `branch` varchar(50) NOT NULL DEFAULT 'main',
  `title` varchar(150) NOT NULL,
  `content` longtext NOT NULL,
  `prompt_metadata` text,
  `change_note` varchar(255),
  `created_by` bigint,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS `idx_prompt_version_post` ON `vibe_prompt_version`(`post_id`, `branch`, `version`);
