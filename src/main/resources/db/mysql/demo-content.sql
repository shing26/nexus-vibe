-- =============================================================================
-- Demo content for a MySQL deployment that turns demo seeding on
-- (DEMO_SEED_ENABLED=true). Applied by DemoContentSeeder: insert-only, and only
-- while vibe_post is still empty.
--
-- The H2 dev profile never runs this file: it keeps its own src/main/resources/data.sql
-- seed, and MySQL date functions do not run there.
-- Users are not inserted here: DataPreloader owns accounts, and the author ids
-- below are the demo accounts it creates when seeding is enabled.
--
-- B10 curation rule: these read like posts people actually wrote. Every post is
-- in one of the seven real channels, and nothing here announces itself as sample
-- data. Reviewed rows and their logs agree: post 2 has one recorded review (the
-- score-8 prompt post), post 3 is the showcase candidate and has two (the first
-- submission and the fixed version), so each voucher shows a real call count and
-- runtime. Point SHOWCASE_POST_ID at 3 when this seed is the demo corpus.
-- =============================================================================
INSERT INTO `vibe_post` (`id`, `title`, `content`, `user_id`, `category_id`, `status`, `like_count`, `comment_count`, `view_count`, `is_pinned`, `post_type`, `prompt_metadata`, `forked_from_id`, `ai_reviewed`, `ai_review_score`, `create_time`) VALUES
(1, '先读这里：本站的 AI 评审会失败，而且会明确告诉你',
 '很多人第一次看到 AI 评审卡，会以为它只是一个装饰。其实它是一条真实的流水线：帖子发布后领取评审租约，调用模型，解析结果，校验不通过就修复重试，仍然失败就把帖子标记为待重试。\n\n设计上只有一条底线：评审不可用时，内容不会被悄悄放行，也不会假装通过。你会看到失败状态和重试说明。这个频道会记录这些规则的变化，以及我们为什么这么选。',
 1, 1, 1, 46, 2, 860, 1, 'post', NULL, NULL, 0, 0, DATE_SUB(NOW(), INTERVAL 9 DAY)),
(2, '把 JSON 输出从「祈祷」变成「契约」的 System Prompt',
 '与其在 prompt 末尾写「请务必返回合法 JSON」，不如把 schema、失败示例和修复指令一起交给模型。下面这版我用了三个月，配合后端的 repair-parse，空结果和截断都显著变少。\n\n关键不是措辞更凶，而是让模型知道：字段名不能改、缺失字段要补全、解析失败时只修语法不改语义。\n\n```text\nYou are a JSON repair pass. Return ONLY a JSON object with exactly these keys:\n  score        integer 0-10\n  severity     one of: low, medium, high, critical\n  findings     array of strings\nDo not invent keys. Do not change values. Close unterminated strings.\n```',
 2, 2, 1, 128, 3, 2140, 0, 'prompt', '{"role":"You are a strict structured-output assistant. You return one JSON object that matches the provided schema and nothing else.","recommendedModel":"claude-sonnet","temperature":0.2,"variables":["schema","failureExample"]}', NULL, 1, 8, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(3, '把缓存从 HashMap 换成 Caffeine 之前，我先让它崩了一次',
 '这段代码原本只有五行，后来每个请求都往里面塞 key。没有上限、没有过期、没有并发保护，refreshAll 还会先 clear 再重建，正好制造了一个所有请求同时 miss 的窗口。\n\n压测到 200 并发时它没有报错，只是内存曲线一路向上。第二天凌晨 OOM。下面是我后来用来复现的最小片段，留作反面教材。\n\n```java\nMap<String, Value> cache = new HashMap<>();\n\nValue get(String key) {\n    Value hit = cache.get(key);\n    if (hit == null) {\n        hit = load(key);\n        cache.put(key, hit);\n    }\n    return hit;\n}\n\nvoid refreshAll() {\n    cache.clear();\n    for (String key : registry.keys()) {\n        cache.put(key, load(key));\n    }\n}\n```\n\n最后的修法不是「加个 synchronized」，而是换成有容量上限、写入过期和 single-flight 的缓存。评审卡里的两条记录，分别对应我第一次提交和改完之后的版本。',
 2, 3, 1, 231, 2, 3480, 0, 'post', NULL, NULL, 1, 7, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(4, '多 Agent 协作里最贵的不是 token，是没人负责的重试',
 '我们试过让三个 Agent 互相检查：写代码的、跑测试的、做安全审查的。真正拖垮一次任务的是第三轮之后没人知道该由谁重试。\n\n后来把职责写死：只有编排者能重试，子 Agent 只能返回结构化失败原因。token 成本反而降了，因为失败的调用不再被重复三次。',
 3, 4, 1, 97, 4, 1670, 0, 'post', NULL, NULL, 0, 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(5, '上下文窗口不是内存，是预算',
 '把整份代码库塞进上下文，短期看很爽，长期看是在用最贵的方式做检索。我的做法是先让模型列出它需要看的文件，再按需加载，并且把每次加载的 token 记在日志里。\n\n一个月后最明显的变化不是成本，而是回答开始稳定：模型不再被无关文件带偏。',
 4, 5, 1, 74, 1, 1320, 0, 'post', NULL, NULL, 0, 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(6, 'Redis 挂掉时，为什么我不让整个 Spring Boot 起不来',
 '一开始我把 Redis 健康检查写进了启动依赖，结果一次机房网络抖动让整个服务无法启动。后来改成：启动时只警告，运行期热点排序降级到 MySQL，健康检查返回 degraded 而不是 down。\n\ndegraded 的含义是「能力受损但仍在服务」，不是「假装健康」。这两种语义混在一起，值班的人就永远不知道该不该报警。',
 5, 6, 1, 63, 3, 980, 0, 'post', NULL, NULL, 0, 0, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(7, 'LLM 应用可观测性清单：日志、指标、追踪各要什么',
 '日志回答「这一次为什么失败」，指标回答「最近是不是更差了」，追踪回答「时间花在哪一段」。三样缺一不可，但不要用同一个系统硬塞。\n\n我们现在的做法：每次模型调用一条结构化日志，带 request id、模型名、耗时、重试次数；指标只看成功率、P95 延迟和 token 消耗；追踪只串「领取租约 → 调用 → 解析 → 落库」四段。',
 6, 7, 1, 88, 1, 1450, 0, 'post', NULL, NULL, 0, 0, DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(8, '让 Agent 自己写测试，然后在 CI 里拒绝它',
 'Agent 写的测试有一个典型问题：它会顺着实现写，于是测试永远通过。我们的处理是让另一个模型只读需求、不读实现，先写出期望行为，再交给实现方。\n\n第一次这样做时，两个 Agent 在命名上吵了三轮。最后留下的测试比人写的更啰嗦，但确实抓住了一个边界条件。',
 2, 4, 1, 52, 2, 760, 0, 'post', NULL, NULL, 0, 0, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(9, '把 17 个重复 prompt 收敛成一个模板，我删掉了 600 行',
 '重复的 prompt 最麻烦的地方不是占空间，而是改一处忘一处。我们把共同部分抽成 role 和输出契约，只把每个场景真正不同的三五行留在变量里。\n\n```diff\n- 请用 JSON 返回结果，字段包括 score、severity 和 findings。\n- 不要返回多余的解释。\n+ 输出契约见 {{schema}}；失败示例见 {{failureExample}}。\n+ 只返回 JSON，不要解释。\n```\n\n删完之后第一次评审通过率没有变化，但维护成本肉眼可见地降了。',
 7, 5, 1, 41, 0, 620, 0, 'post', NULL, NULL, 0, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(10, '线上 502 排查到凌晨，最后发现是 nginx 的 proxy_read_timeout',
 '后端日志一切正常，接口在本地也能跑通，但线上每隔几分钟就有一批 502。最后定位到 nginx 的 proxy_read_timeout 默认 60 秒，而批量评审接口偶尔会超过这个时间。\n\n把超时调到 300 秒并给慢接口单独开了路由之后，502 消失。记录一下，免得下次再从应用层查起。\n\n```nginx\nlocation /api/v1/agent-logs {\n    proxy_read_timeout 300s;\n    proxy_send_timeout 300s;\n    proxy_pass http://app:8080;\n}\n```',
 3, 6, 2, 0, 0, 12, 0, 'post', NULL, NULL, 0, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR));

INSERT INTO `vibe_post_tag` (`id`, `post_id`, `tag_id`) VALUES
(1, 1, 7),
(2, 2, 1), (3, 2, 2),
(4, 3, 2), (5, 3, 6),
(6, 4, 6),
(7, 5, 2),
(8, 6, 7),
(9, 7, 6),
(10, 8, 6), (11, 8, 7),
(12, 9, 1),
(13, 10, 7);

INSERT INTO `vibe_prompt_version` (`id`, `post_id`, `version`, `branch`, `title`, `content`, `prompt_metadata`, `change_note`, `created_by`, `create_time`) VALUES
(8001, 2, 1, 'main', '把 JSON 输出从「祈祷」变成「契约」的 System Prompt',
 '与其在 prompt 末尾写「请务必返回合法 JSON」，不如把 schema、失败示例和修复指令一起交给模型。',
 '{"role":"You are a strict structured-output assistant.","recommendedModel":"claude-sonnet","temperature":0.2,"variables":["schema","failureExample"]}', '第一版：只写了输出契约', 2, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(8002, 2, 2, 'main', '把 JSON 输出从「祈祷」变成「契约」的 System Prompt',
 '关键不是措辞更凶，而是让模型知道：字段名不能改、缺失字段要补全、解析失败时只修语法不改语义。',
 '{"role":"You are a strict structured-output assistant. You return one JSON object that matches the provided schema and nothing else.","recommendedModel":"claude-sonnet","temperature":0.2,"variables":["schema","failureExample"]}', '补上修复指令和失败示例', 2, DATE_SUB(NOW(), INTERVAL 5 DAY));

INSERT INTO `vibe_comment` (`id`, `post_id`, `user_id`, `parent_id`, `target_id`, `content`, `status`, `create_time`) VALUES
(1, 3, 3, 0, 0, 'clear 再重建这个窗口太真实了。我们后来用 build 新 map 再原子替换，读路径就再也没见过空缓存。', 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 3, 4, 0, 0, 'Caffeine 的 get(key, loader) 顺手把 single-flight 也解决了，建议直接上。', 1, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(3, 2, 5, 0, 0, '把失败示例一起给模型这点很有用，比单纯强调「必须合法」有效。', 1, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(4, 4, 2, 0, 0, '「只有编排者能重试」这条应该写进规范，我们也是踩过才明白。', 1, DATE_SUB(NOW(), INTERVAL 18 HOUR)),
(5, 6, 7, 0, 0, 'degraded 和 down 分开之后，值班群安静了很多。', 1, DATE_SUB(NOW(), INTERVAL 16 HOUR)),
(6, 10, 1, 0, 0, '这类问题最耗时间的地方是日志看起来完全正常。谢谢记录。', 1, DATE_SUB(NOW(), INTERVAL 1 HOUR));

INSERT INTO `sys_message` (`id`, `from_user_id`, `to_user_id`, `content`, `type`, `is_read`, `create_time`) VALUES
(1, 3, 2, 'Alice 评论了你的帖子：把缓存从 HashMap 换成 Caffeine 之前，我先让它崩了一次', 2, 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 4, 2, 'Bob 评论了你的帖子：把缓存从 HashMap 换成 Caffeine 之前，我先让它崩了一次', 2, 0, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(3, 2, 4, 'shing 评论了你的帖子：多 Agent 协作里最贵的不是 token，是没人负责的重试', 2, 0, DATE_SUB(NOW(), INTERVAL 18 HOUR));

-- Recorded reviews for every post whose ai_reviewed flag is 1. Post 2 has one
-- (score 8, approved) so its reviewed badge resolves to a real detail instead of
-- the "unavailable" state; post 3 has two (the first submission and the fixed
-- version). The voucher derives its call count from these rows, so a fresh
-- deployment shows a real count and runtime instead of a hand-waved blank.
INSERT INTO `ai_review_log` (`id`, `post_id`, `reviewer`, `result_json`, `severity`, `is_approved`, `created_at`) VALUES
(9000, 2, 'code-review-agent',
 '{"score":8,"severity":"low","codeQuality":"输出契约、失败示例和修复指令都写进了 prompt，变量边界清楚；文本块里的键名与后端解析字段一致，没有依赖模型自由发挥。","securityConcerns":"示例没有包含真实密钥或用户数据；修复指令只要求补全语法，不会诱导模型改写语义。","optimizationSuggestions":"可以把 schema 单独抽成一段可复用文本，并补一个截断输出的修复示例；当前版本已经覆盖空结果与非法 JSON 两类失败。","isApproved":true,"durationMs":1670}',
 'low', 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 5 DAY), INTERVAL 20 MINUTE)),
(9001, 3, 'code-review-agent',
 '{"score":5,"severity":"medium","codeQuality":"get 与 put 之间没有同步，且 HashMap 不是线程安全的；refreshAll 先 clear 再重建，所有并发请求会在同一个窗口里穿透到 load，形成缓存击穿。","securityConcerns":"缓存没有容量上限也没有过期策略，任何能控制 key 的调用方都可以把堆内存推高，构成拒绝服务风险。","optimizationSuggestions":"改用有容量上限和写入过期的缓存，并用 get(key, loader) 做单飞加载；refreshAll 改为在旁路构建新映射后原子替换，避免读者看到空缓存。","isApproved":false,"durationMs":2210}',
 'medium', 0, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(9002, 3, 'code-review-agent',
 '{"score":7,"severity":"low","codeQuality":"新的实现用 Caffeine 替换了裸 HashMap，容量上限、过期与并发语义都由缓存库负责；get 通过 loader 合并了并发未命中，refreshAll 不再清空读路径。","securityConcerns":"堆内存风险已由 maximumSize 约束；load 抛异常时不会写入缓存，避免把失败结果缓存成永久值。","optimizationSuggestions":"建议补一个 hit rate 指标，并在 load 失败时记录原因；如果上游允许，可以对热点 key 做 refreshAfterWrite，而不是整表刷新。","isApproved":true,"durationMs":1840}',
 'low', 1, DATE_SUB(NOW(), INTERVAL 2 DAY));
