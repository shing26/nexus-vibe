-- =============================================================================
-- Nexus-Vibe - MySQL Initialization Script (fresh install)
-- Mirrors src/main/resources/schema.sql for MySQL 8.0, plus the channels and
-- tags the dev profile seeds. Demo content is applied by the app, not here.
-- =============================================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `nexus_campus`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `nexus_campus`;

-- -----------------------------------------------------------------------------
-- Schema
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL PRIMARY KEY,
  `username` varchar(50) NOT NULL UNIQUE,
  `email` varchar(100) DEFAULT NULL UNIQUE,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vibe_channel` (
  `id` int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `name` varchar(50) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `slug` varchar(50) DEFAULT NULL UNIQUE,
  `sort_order` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vibe_tag` (
  `id` int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `name` varchar(30) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `code_snippets` text,
  `ai_reviewed` tinyint NOT NULL DEFAULT 0,
  `ai_review_score` int NOT NULL DEFAULT 0,
  `token_count` int NOT NULL DEFAULT 0,
  `post_type` varchar(10) NOT NULL DEFAULT 'post',
  `prompt_metadata` text,
  `forked_from_id` bigint,
  `review_lock_until` datetime DEFAULT NULL,
  `review_owner` varchar(64) DEFAULT NULL,
  `review_attempts` int NOT NULL DEFAULT 0,
  INDEX `idx_post_user_id` (`user_id`),
  INDEX `idx_post_category_id` (`category_id`),
  INDEX `idx_post_status` (`status`),
  INDEX `idx_post_ai_sort` (`status`, `ai_reviewed`, `ai_review_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vibe_post_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `post_id` bigint NOT NULL,
  `tag_id` int NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vibe_post_like` (
  `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `uk_post_user` UNIQUE (`post_id`, `user_id`),
  INDEX `idx_like_post_id` (`post_id`),
  INDEX `idx_like_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vibe_comment` (
  `id` bigint NOT NULL PRIMARY KEY,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `parent_id` bigint NOT NULL DEFAULT 0,
  `target_id` bigint NOT NULL DEFAULT 0,
  `content` text NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_comment_post_id` (`post_id`),
  INDEX `idx_comment_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_message` (
  `id` bigint NOT NULL PRIMARY KEY,
  `from_user_id` bigint NOT NULL,
  `to_user_id` bigint NOT NULL,
  `content` text NOT NULL,
  `type` tinyint NOT NULL DEFAULT 1,
  `is_read` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_msg_to_user` (`to_user_id`),
  INDEX `idx_msg_from_user` (`from_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_review_log` (
  `id` bigint NOT NULL PRIMARY KEY,
  `post_id` bigint NOT NULL,
  `reviewer` varchar(50) NOT NULL DEFAULT 'code-review-agent',
  `result_json` text,
  `severity` varchar(20),
  `is_approved` tinyint DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_pv_post_branch_version` (`post_id`, `branch`, `version`),
  INDEX `idx_prompt_version_post` (`post_id`, `branch`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Reference Data
-- Channels and tags only. Accounts come from DataPreloader, and demo posts,
-- comments, messages and review logs moved to
-- src/main/resources/db/mysql/demo-content.sql: an image that starts with this
-- file must not gain content pointing at authors it does not have.
-- -----------------------------------------------------------------------------

INSERT INTO `vibe_channel` (`id`, `name`, `description`, `slug`, `sort_order`, `status`, `create_time`) VALUES
(1, '社区公告', '系统公告、更新日志（管理员只读）', 'announcements', 1, 1, NOW()),
(2, 'Prompt 工坊', 'System Prompt 设计、Chain-of-Thought、少样本技巧', 'prompts', 2, 1, NOW()),
(3, '作品展示', 'Vibe Coding 成品展示：网页、工具、自动化流程', 'showcase', 3, 1, NOW()),
(4, 'Agent 实战', 'Multi-Agent、工具调用、OpenClaw/Codex 使用心得', 'agents', 4, 1, NOW()),
(5, 'Vibe Coding 经验', '上下文控制、幻觉治理、架构设计的纯经验讨论', 'vibe-coding', 5, 1, NOW()),
(6, '代码急诊室', '贴报错上下文，社区或 AI Agent 协助分析', 'debug', 6, 1, NOW()),
(7, '资源聚合', '工具链推荐、API 评测、教程链接', 'resources', 7, 1, NOW());

INSERT INTO `vibe_tag` (`id`, `name`, `status`, `create_time`) VALUES
(1, 'GPT-4', 1, NOW()),
(2, 'Claude', 1, NOW()),
(3, 'Stable Diffusion', 1, NOW()),
(4, 'RAG', 1, NOW()),
(5, 'Fine-tuning', 1, NOW()),
(6, 'Agents', 1, NOW()),
(7, 'Open Source', 1, NOW());
