-- =============================================================================
-- Nexus-Vibe - MySQL Initialization Script (fresh install)
-- Mirrors src/main/resources/schema.sql + data.sql for MySQL 8.0.
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
  INDEX `idx_post_user_id` (`user_id`),
  INDEX `idx_post_category_id` (`category_id`),
  INDEX `idx_post_status` (`status`)
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
-- Seed Data
-- Users are created by DataPreloader at app startup; passwords come from
-- DEMO_PASSWORD only when demo seeding is enabled.
-- -----------------------------------------------------------------------------

DELETE FROM `ai_review_log`;
DELETE FROM `vibe_prompt_version`;
DELETE FROM `sys_message`;
DELETE FROM `vibe_comment`;
DELETE FROM `vibe_post_tag`;
DELETE FROM `vibe_post_like`;
DELETE FROM `vibe_post`;
DELETE FROM `vibe_tag`;
DELETE FROM `vibe_channel`;

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

INSERT INTO `vibe_post` (`id`, `title`, `content`, `user_id`, `category_id`, `status`, `like_count`, `comment_count`, `view_count`, `is_pinned`, `post_type`, `create_time`) VALUES
(1, 'Building a RAG pipeline with LangChain and Claude 3',
 'A step-by-step guide to building a production-ready RAG pipeline: document chunking strategies, embedding selection, vector store optimization with pgvector, and prompt templates for Claude 3. Includes benchmark comparisons across different chunk sizes.',
 2, 4, 1, 85, 2, 320, 0, 'post', DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(2, 'Fine-tuning Llama 3 on domain-specific code: lessons learned',
 'Deep dive into fine-tuning Llama 3 8B on a custom Python code dataset. Covers LoRA rank selection, dataset preparation, QLoRA vs full fine-tuning tradeoffs, and evaluation benchmarks vs GPT-3.5.',
 3, 5, 1, 150, 1, 890, 0, 'post', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(3, 'Claude Computer Use: building autonomous browser agents',
 'Exploring Anthropic computer use capabilities: how to build agents that can browse, fill forms, extract data, and navigate complex web UIs autonomously. Includes safety guardrails and rate limiting strategies.',
 1, 4, 1, 620, 0, 4500, 0, 'post', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(4, 'Prompt patterns for reliable structured output from LLMs',
 'A collection of battle-tested prompt patterns for getting consistent JSON output: role-locked formatting, chain-of-thought with schema enforcement, XML-tagged responses, and few-shot template design. Benchmarked across GPT-4, Claude 3.5, and Gemini.',
 4, 1, 1, 230, 0, 1200, 0, 'post', DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(5, '[Pending Audit] AI-generated music with Stable Audio and Suno: a comparison',
 'Comparing AI music generation platforms: prompt engineering for music, genre adherence, audio quality, and commercial usage rights. Includes sample outputs and production workflow recommendations.',
 3, 7, 2, 0, 0, 10, 0, 'post', NOW());

INSERT INTO `vibe_post` (`id`, `title`, `content`, `user_id`, `category_id`, `status`, `like_count`, `comment_count`, `view_count`, `is_pinned`, `post_type`, `prompt_metadata`, `forked_from_id`, `create_time`) VALUES
(100, 'React Component Generator',
 'Create a React component named {{componentName}} that follows best practices. Define the props interface and include these features: {{features}}. The component should be type-safe with TypeScript, include proper JSDoc comments, and handle loading/error/empty states.',
 1, 2, 1, 42, 0, 560, 0, 'prompt', '{"role":"You are a senior React developer specializing in component architecture. Generate clean, composable, and well-documented React components with TypeScript. Follow SOLID principles and React best practices.","recommendedModel":"gpt-4o","temperature":0.7,"variables":["componentName","features"]}', NULL, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(101, 'Tailwind UI Prompt Architect',
 'Design a responsive {{layout}} using Tailwind CSS with a {{colorScheme}} color scheme. Specify the layout structure and any specific UI patterns you want to include. The generated code should be production-ready with proper responsive breakpoints and accessibility attributes.',
 2, 2, 1, 35, 0, 420, 0, 'prompt', '{"role":"You are a Tailwind CSS expert and UI designer. Create beautiful, responsive, and accessible layouts using Tailwind CSS utility classes. Prioritize mobile-first design and adhere to WCAG 2.1 AA standards.","recommendedModel":"gpt-4o","temperature":0.5,"variables":["layout","colorScheme"]}', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(102, 'React Component Generator',
 'Create a React component named {{componentName}} that follows best practices, with features {{features}} and {{accessibilityLevel}} accessibility. Forked from the React Component Generator template with an extra focus on keyboard accessibility and reduced motion support.',
 3, 2, 1, 9, 0, 140, 0, 'prompt', '{"role":"You are a senior React developer specializing in accessible component architecture. Generate clean, composable, and well-documented React components with TypeScript. Follow WCAG 2.1 AA standards and React best practices.","recommendedModel":"gpt-4o","temperature":0.6,"variables":["componentName","features","accessibilityLevel"]}', 100, DATE_SUB(NOW(), INTERVAL 8 HOUR));

INSERT INTO `vibe_post_tag` (`id`, `post_id`, `tag_id`) VALUES
(1, 1, 2), (2, 1, 4),
(3, 2, 5), (4, 2, 7),
(5, 3, 6), (6, 3, 2),
(7, 4, 1), (8, 5, 3),
(9, 100, 1), (10, 100, 6),
(11, 101, 2), (12, 101, 7);

INSERT INTO `vibe_prompt_version` (`id`, `post_id`, `version`, `branch`, `title`, `content`, `prompt_metadata`, `change_note`, `created_by`, `create_time`) VALUES
(8001, 100, 1, 'main', 'React Component Generator',
 'Create a React component named {{componentName}} that follows best practices. Define the props interface and include these features: {{features}}.',
 '{"role":"You are a senior React developer specializing in component architecture. Generate clean, composable, and well-documented React components with TypeScript.","recommendedModel":"gpt-4o","temperature":0.7,"variables":["componentName","features"]}', 'Initial version', 1, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(8002, 100, 2, 'main', 'React Component Generator',
 'Create a React component named {{componentName}} that follows best practices. Define the props interface and include these features: {{features}}. The component should be type-safe with TypeScript.',
 '{"role":"You are a senior React developer specializing in component architecture. Generate clean, composable, and well-documented React components with TypeScript. Follow SOLID principles.","recommendedModel":"gpt-4o","temperature":0.7,"variables":["componentName","features"]}', 'Add TypeScript safety guidance', 1, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(8003, 100, 3, 'main', 'React Component Generator',
 'Create a React component named {{componentName}} that follows best practices. Define the props interface and include these features: {{features}}. The component should be type-safe with TypeScript, include proper JSDoc comments, and handle loading/error/empty states.',
 '{"role":"You are a senior React developer specializing in component architecture. Generate clean, composable, and well-documented React components with TypeScript. Follow SOLID principles and React best practices.","recommendedModel":"gpt-4o","temperature":0.7,"variables":["componentName","features"]}', 'Add JSDoc and state handling', 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(8004, 101, 1, 'main', 'Tailwind UI Prompt Architect',
 'Design a responsive {{layout}} using Tailwind CSS with a {{colorScheme}} color scheme. Specify the layout structure and any specific UI patterns you want to include.',
 '{"role":"You are a Tailwind CSS expert and UI designer. Create beautiful, responsive, and accessible layouts using Tailwind CSS utility classes.","recommendedModel":"gpt-4o","temperature":0.5,"variables":["layout","colorScheme"]}', 'Initial version', 2, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(8005, 102, 1, 'main', 'React Component Generator',
 'Create a React component named {{componentName}} that follows best practices, with features {{features}} and {{accessibilityLevel}} accessibility. Forked from the React Component Generator template with an extra focus on keyboard accessibility and reduced motion support.',
 '{"role":"You are a senior React developer specializing in accessible component architecture. Generate clean, composable, and well-documented React components with TypeScript.","recommendedModel":"gpt-4o","temperature":0.6,"variables":["componentName","features","accessibilityLevel"]}', 'Forked from post 100', 3, DATE_SUB(NOW(), INTERVAL 8 HOUR));

INSERT INTO `vibe_comment` (`id`, `post_id`, `user_id`, `parent_id`, `target_id`, `content`, `status`, `create_time`) VALUES
(1, 1, 3, 0, 0, 'Great walkthrough! Which embedding model did you use for the Chinese documents?', 1, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(2, 1, 4, 0, 0, 'Have you tried using parent-child chunking for better retrieval?', 1, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(3, 2, 2, 0, 0, 'Thanks for sharing! The benchmark results are really insightful.', 1, DATE_SUB(NOW(), INTERVAL 18 HOUR));

INSERT INTO `sys_message` (`id`, `from_user_id`, `to_user_id`, `content`, `type`, `is_read`, `create_time`) VALUES
(1, 3, 2, 'Alice commented on your post: Building a RAG pipeline with LangChain and Claude 3', 2, 0, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(2, 4, 2, 'Bob commented on your post: Building a RAG pipeline with LangChain and Claude 3', 2, 0, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(3, 2, 1, 'shing commented on your post: Claude Computer Use: building autonomous browser agents', 2, 0, DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO `ai_review_log` (`id`, `post_id`, `reviewer`, `result_json`, `severity`, `is_approved`, `created_at`) VALUES
(9001, 100, 'code-review-agent', '{"score":9,"severity":"low"}', 'low', 1, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(9002, 101, 'code-review-agent', '{"score":7,"severity":"medium"}', 'medium', 1, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(9003, 1, 'safety-check-agent', 'Prompt injection', 'critical', 0, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(9004, 2, 'code-review-agent', '{"score":5,"severity":"unknown"}', 'unknown', 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9005, 100, 'code-review-agent', '{"score":4,"severity":"unknown"}', 'unknown', 0, DATE_SUB(NOW(), INTERVAL 2 DAY));
