-- Clear old data
DELETE FROM ai_review_log;
DELETE FROM vibe_prompt_version;
DELETE FROM sys_message;
DELETE FROM vibe_comment;
DELETE FROM vibe_post_tag;
DELETE FROM vibe_post;
DELETE FROM vibe_tag;
DELETE FROM vibe_channel;
DELETE FROM sys_user;

-- Channels (AI Community Channels with slugs)
INSERT INTO vibe_channel (id, name, description, slug, sort_order, status, create_time) VALUES
(1, '社区公告', '系统公告、更新日志（管理员只读）', 'announcements', 1, 1, CURRENT_TIMESTAMP),
(2, 'Prompt 工坊', 'System Prompt 设计、Chain-of-Thought、少样本技巧', 'prompts', 2, 1, CURRENT_TIMESTAMP),
(3, '作品展示', 'Vibe Coding 成品展示：网页、工具、自动化流程', 'showcase', 3, 1, CURRENT_TIMESTAMP),
(4, 'Agent 实战', 'Multi-Agent、工具调用、OpenClaw/Codex 使用心得', 'agents', 4, 1, CURRENT_TIMESTAMP),
(5, 'Vibe Coding 经验', '上下文控制、幻觉治理、架构设计的纯经验讨论', 'vibe-coding', 5, 1, CURRENT_TIMESTAMP),
(6, '代码急诊室', '贴报错上下文，社区或 AI Agent 协助分析', 'debug', 6, 1, CURRENT_TIMESTAMP),
(7, '资源聚合', '工具链推荐、API 评测、教程链接', 'resources', 7, 1, CURRENT_TIMESTAMP);

-- Tags
INSERT INTO vibe_tag (id, name, status, create_time) VALUES
(1, 'GPT-4', 1, CURRENT_TIMESTAMP),
(2, 'Claude', 1, CURRENT_TIMESTAMP),
(3, 'Stable Diffusion', 1, CURRENT_TIMESTAMP),
(4, 'RAG', 1, CURRENT_TIMESTAMP),
(5, 'Fine-tuning', 1, CURRENT_TIMESTAMP),
(6, 'Agents', 1, CURRENT_TIMESTAMP),
(7, 'Open Source', 1, CURRENT_TIMESTAMP);

-- Posts (varying timestamps for Gravity Decay demo)
INSERT INTO vibe_post (id, title, content, user_id, category_id, status, like_count, comment_count, view_count, is_pinned, post_type, create_time) VALUES
(1, 'Building a RAG pipeline with LangChain and Claude 3',
 'A step-by-step guide to building a production-ready RAG pipeline: document chunking strategies, embedding selection, vector store optimization with pgvector, and prompt templates for Claude 3. Includes benchmark comparisons across different chunk sizes.',
 2, 4, 1, 85, 2, 320, 0, 'post', DATEADD('HOUR', -2, CURRENT_TIMESTAMP)),
(2, 'Fine-tuning Llama 3 on domain-specific code: lessons learned',
 'Deep dive into fine-tuning Llama 3 8B on a custom Python code dataset. Covers LoRA rank selection, dataset preparation, QLoRA vs full fine-tuning tradeoffs, and evaluation benchmarks vs GPT-3.5.',
 3, 5, 1, 150, 1, 890, 0, 'post', DATEADD('DAY', -1, CURRENT_TIMESTAMP)),
(3, 'Claude Computer Use: building autonomous browser agents',
 'Exploring Anthropic computer use capabilities: how to build agents that can browse, fill forms, extract data, and navigate complex web UIs autonomously. Includes safety guardrails and rate limiting strategies.',
 1, 4, 1, 620, 0, 4500, 0, 'post', DATEADD('DAY', -7, CURRENT_TIMESTAMP)),
(4, 'Prompt patterns for reliable structured output from LLMs',
 'A collection of battle-tested prompt patterns for getting consistent JSON output: role-locked formatting, chain-of-thought with schema enforcement, XML-tagged responses, and few-shot template design. Benchmarked across GPT-4, Claude 3.5, and Gemini.',
 4, 1, 1, 230, 0, 1200, 0, 'post', DATEADD('HOUR', -3, CURRENT_TIMESTAMP)),
(5, '[Pending Audit] AI-generated music with Stable Audio and Suno: a comparison',
 'Comparing AI music generation platforms: prompt engineering for music, genre adherence, audio quality, and commercial usage rights. Includes sample outputs and production workflow recommendations.',
 3, 7, 2, 0, 0, 10, 0, 'post', CURRENT_TIMESTAMP);

-- Prompt Template Posts
INSERT INTO vibe_post (id, title, content, user_id, category_id, status, like_count, comment_count, view_count, is_pinned, post_type, prompt_metadata, forked_from_id, create_time) VALUES
(100, 'React Component Generator',
 'Create a React component named {{componentName}} that follows best practices. Define the props interface and include these features: {{features}}. The component should be type-safe with TypeScript, include proper JSDoc comments, and handle loading/error/empty states.

```tsx
interface ComponentProps {
  name: string;
  features: string[];
}
```',
 1, 2, 1, 42, 0, 560, 0, 'prompt', '{"role":"You are a senior React developer specializing in component architecture. Generate clean, composable, and well-documented React components with TypeScript. Follow SOLID principles and React best practices.","recommendedModel":"gpt-4o","temperature":0.7,"variables":["componentName","features"]}', NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
(101, 'Tailwind UI Prompt Architect',
 'Design a responsive {{layout}} using Tailwind CSS with a {{colorScheme}} color scheme. Specify the layout structure and any specific UI patterns you want to include. The generated code should be production-ready with proper responsive breakpoints and accessibility attributes.

```tsx
<main className="grid min-h-screen grid-cols-1 gap-4 p-4 md:grid-cols-12">
  <aside className="md:col-span-3">Sidebar</aside>
  <section className="md:col-span-9">Content</section>
</main>
```',
 2, 2, 1, 35, 0, 420, 0, 'prompt', '{"role":"You are a Tailwind CSS expert and UI designer. Create beautiful, responsive, and accessible layouts using Tailwind CSS utility classes. Prioritize mobile-first design and adhere to WCAG 2.1 AA standards.","recommendedModel":"gpt-4o","temperature":0.5,"variables":["layout","colorScheme"]}', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP)),
(102, 'React Component Generator',
 'Create a React component named {{componentName}} that follows best practices, with features {{features}} and {{accessibilityLevel}} accessibility. Forked from the React Component Generator template with an extra focus on keyboard accessibility and reduced motion support.',
 3, 2, 1, 9, 0, 140, 0, 'prompt', '{"role":"You are a senior React developer specializing in accessible component architecture. Generate clean, composable, and well-documented React components with TypeScript. Follow WCAG 2.1 AA standards and React best practices.","recommendedModel":"gpt-4o","temperature":0.6,"variables":["componentName","features","accessibilityLevel"]}', 100, DATEADD('HOUR', -8, CURRENT_TIMESTAMP));

-- Prompt version history
INSERT INTO vibe_prompt_version (id, post_id, version, branch, title, content, prompt_metadata, change_note, created_by, create_time) VALUES
(8001, 100, 1, 'main', 'React Component Generator',
 'Create a React component named {{componentName}} that follows best practices. Define the props interface and include these features: {{features}}.',
 '{"role":"You are a senior React developer specializing in component architecture. Generate clean, composable, and well-documented React components with TypeScript.","recommendedModel":"gpt-4o","temperature":0.7,"variables":["componentName","features"]}', 'Initial version', 1, DATEADD('DAY', -5, CURRENT_TIMESTAMP)),
(8002, 100, 2, 'main', 'React Component Generator',
 'Create a React component named {{componentName}} that follows best practices. Define the props interface and include these features: {{features}}. The component should be type-safe with TypeScript.',
 '{"role":"You are a senior React developer specializing in component architecture. Generate clean, composable, and well-documented React components with TypeScript. Follow SOLID principles.","recommendedModel":"gpt-4o","temperature":0.7,"variables":["componentName","features"]}', 'Add TypeScript safety guidance', 1, DATEADD('DAY', -4, CURRENT_TIMESTAMP)),
(8003, 100, 3, 'main', 'React Component Generator',
 'Create a React component named {{componentName}} that follows best practices. Define the props interface and include these features: {{features}}. The component should be type-safe with TypeScript, include proper JSDoc comments, and handle loading/error/empty states.',
 '{"role":"You are a senior React developer specializing in component architecture. Generate clean, composable, and well-documented React components with TypeScript. Follow SOLID principles and React best practices.","recommendedModel":"gpt-4o","temperature":0.7,"variables":["componentName","features"]}', 'Add JSDoc and state handling', 1, DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
(8004, 101, 1, 'main', 'Tailwind UI Prompt Architect',
 'Design a responsive {{layout}} using Tailwind CSS with a {{colorScheme}} color scheme. Specify the layout structure and any specific UI patterns you want to include.',
 '{"role":"You are a Tailwind CSS expert and UI designer. Create beautiful, responsive, and accessible layouts using Tailwind CSS utility classes.","recommendedModel":"gpt-4o","temperature":0.5,"variables":["layout","colorScheme"]}', 'Initial version', 2, DATEADD('DAY', -2, CURRENT_TIMESTAMP)),
(8005, 102, 1, 'main', 'React Component Generator',
 'Create a React component named {{componentName}} that follows best practices, with features {{features}} and {{accessibilityLevel}} accessibility. Forked from the React Component Generator template with an extra focus on keyboard accessibility and reduced motion support.',
 '{"role":"You are a senior React developer specializing in accessible component architecture. Generate clean, composable, and well-documented React components with TypeScript.","recommendedModel":"gpt-4o","temperature":0.6,"variables":["componentName","features","accessibilityLevel"]}', 'Forked from post 100', 3, DATEADD('HOUR', -8, CURRENT_TIMESTAMP));

-- Post-Tag associations
INSERT INTO vibe_post_tag (id, post_id, tag_id) VALUES
(1, 1, 2), (2, 1, 4),
(3, 2, 5), (4, 2, 7),
(5, 3, 6), (6, 3, 2),
(7, 4, 1), (8, 5, 3),
(9, 100, 1), (10, 100, 6),
(11, 101, 2), (12, 101, 7);

-- Comments
INSERT INTO vibe_comment (id, post_id, user_id, parent_id, target_id, content, status, create_time) VALUES
(1, 1, 3, 0, 0, 'Great walkthrough! Which embedding model did you use for the Chinese documents?', 1, DATEADD('HOUR', -1, CURRENT_TIMESTAMP)),
(2, 1, 4, 0, 0, 'Have you tried using parent-child chunking for better retrieval?', 1, DATEADD('MINUTE', -30, CURRENT_TIMESTAMP)),
(3, 2, 2, 0, 0, 'Thanks for sharing! The benchmark results are really insightful.', 1, DATEADD('HOUR', -18, CURRENT_TIMESTAMP));

-- System Messages
INSERT INTO sys_message (id, from_user_id, to_user_id, content, type, is_read, create_time) VALUES
(1, 3, 2, 'Alice commented on your post: Building a RAG pipeline with LangChain and Claude 3', 2, 0, DATEADD('HOUR', -1, CURRENT_TIMESTAMP)),
(2, 4, 2, 'Bob commented on your post: Building a RAG pipeline with LangChain and Claude 3', 2, 0, DATEADD('MINUTE', -30, CURRENT_TIMESTAMP)),
(3, 2, 1, 'shing commented on your post: Claude Computer Use: building autonomous browser agents', 2, 0, DATEADD('DAY', -1, CURRENT_TIMESTAMP));

-- AI Review Logs
INSERT INTO ai_review_log (id, post_id, reviewer, result_json, severity, is_approved, created_at) VALUES
(9001, 100, 'code-review-agent', '{"score":9,"severity":"low"}', 'low', 1, DATEADD('HOUR', -2, CURRENT_TIMESTAMP)),
(9002, 101, 'code-review-agent', '{"score":7,"severity":"medium"}', 'medium', 1, DATEADD('HOUR', -4, CURRENT_TIMESTAMP)),
(9003, 1, 'safety-check-agent', 'Prompt injection', 'critical', 0, DATEADD('HOUR', -6, CURRENT_TIMESTAMP)),
(9004, 2, 'code-review-agent', '{"score":5,"severity":"unknown"}', 'unknown', 1, DATEADD('DAY', -1, CURRENT_TIMESTAMP)),
(9005, 100, 'code-review-agent', '{"score":4,"severity":"unknown"}', 'unknown', 0, DATEADD('DAY', -2, CURRENT_TIMESTAMP));

-- Keep post review flags consistent with the seeded code-review logs.
UPDATE vibe_post SET ai_reviewed = 1, ai_review_score = 9 WHERE id = 100;
UPDATE vibe_post SET ai_reviewed = 1, ai_review_score = 7 WHERE id = 101;
UPDATE vibe_post SET ai_reviewed = 1, ai_review_score = 5 WHERE id = 2;
