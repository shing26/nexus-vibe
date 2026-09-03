-- ============================================================================
-- Benchmark seed: 100k rows in a dedicated `nexus_bench` schema, mirroring
-- the vibe_post shape that selectFilteredPage (AI-curated listing) queries.
--
-- NOT mounted into docker-entrypoint-initdb.d — run manually against the
-- compose db container:
--   docker compose exec -T db mysql -u root -p"$DB_PASSWORD" < \
--     docker/mysql/benchmark/seed_bench_data.sql
--
-- Drop the whole schema when done (see the bottom of this file / DROP line).
-- ============================================================================

DROP DATABASE IF EXISTS nexus_bench;
CREATE DATABASE nexus_bench CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nexus_bench;

-- Same columns as the production vibe_post (init.sql), without indexes
-- beyond the PK: the point is to measure the ai-sort index in isolation.
CREATE TABLE vibe_post (
  id bigint NOT NULL PRIMARY KEY,
  user_id bigint NOT NULL,
  category_id int NOT NULL,
  title varchar(120) NOT NULL,
  content mediumtext NOT NULL,
  summary varchar(200) DEFAULT NULL,
  view_count int NOT NULL DEFAULT 0,
  like_count int NOT NULL DEFAULT 0,
  comment_count int NOT NULL DEFAULT 0,
  status tinyint NOT NULL DEFAULT 1,
  is_pinned tinyint NOT NULL DEFAULT 0,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  code_snippets text,
  ai_reviewed tinyint NOT NULL DEFAULT 0,
  ai_review_score int NOT NULL DEFAULT 0,
  token_count int NOT NULL DEFAULT 0,
  post_type varchar(10) NOT NULL DEFAULT 'post',
  prompt_metadata text,
  forked_from_id bigint
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Minimal stand-ins for the JOIN targets (only the columns the query reads).
CREATE TABLE sys_user (
  id bigint NOT NULL PRIMARY KEY,
  nickname varchar(50) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE vibe_channel (
  id int NOT NULL PRIMARY KEY,
  name varchar(50) NOT NULL
) ENGINE=InnoDB;

INSERT INTO sys_user (id, nickname)
SELECT n, CONCAT('bench_user_', n) FROM (
  SELECT (a.x + b.x * 10 + 1) AS n
  FROM (SELECT 0 x UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
        UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
       (SELECT 0 x UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
        UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
) t;
INSERT INTO vibe_channel (id, name) VALUES (1,'a'),(2,'b'),(3,'c'),(4,'d'),(5,'e'),(6,'f'),(7,'g');

-- 100k rows via cross-join doubling: 10 base rows x 4 x 4 x ... = 100k.
-- Deterministic pseudo-random columns from id arithmetic (RAND() would
-- differ per row anyway; arithmetic keeps re-runs comparable).
DELIMITER $$
CREATE PROCEDURE seed_posts()
BEGIN
  INSERT INTO vibe_post
    (id, user_id, category_id, title, content, summary,
     view_count, like_count, comment_count, status, is_pinned, create_time,
     code_snippets, ai_reviewed, ai_review_score, token_count, post_type)
  SELECT
    n,
    1 + (n MOD 50),
    1 + (n MOD 7),
    CONCAT('Bench post #', n),
    CONCAT('Body text for post ', n, '.\n```java\nint x = ', n MOD 97, ';\n```'),
    CONCAT('Summary ', n MOD 1000),
    n MOD 5000,
    -- long tail: 90% under 10 likes, 1% viral
    IF(n MOD 100 = 0, 100 + (n MOD 900), n MOD 10),
    n MOD 30,
    -- status: 96% active(1), 3% pending(2), 1% rejected(3)
    IF(n MOD 100 < 96, 1, IF(n MOD 100 < 99, 2, 3)),
    IF(n MOD 500 = 0, 1, 0),
    -- create_time spread over the last 30 days
    DATE_ADD(NOW(), INTERVAL -(n MOD 720) HOUR),
    CONCAT('[{"snippet":"int x = ', n MOD 97, ';"}]'),
    -- ai_reviewed: ~30% reviewed, score 0-100 skewed low
    IF(n MOD 10 < 3, 1, 0),
    IF(n MOD 10 < 3, (n * 7) MOD 101, 0),
    n MOD 800,
    IF(n MOD 9 = 0, 'prompt', 'post')
  FROM (
    SELECT (a.x + b.x * 10 + c.x * 100 + d.x * 1000 + e.x * 10000 + 1) AS n
    FROM (SELECT 0 x UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
         (SELECT 0 x UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
         (SELECT 0 x UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c,
         (SELECT 0 x UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) d,
         (SELECT 0 x UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) e
  ) nums;
END$$
DELIMITER ;

CALL seed_posts();
DROP PROCEDURE seed_posts;

SELECT COUNT(*) AS total_rows FROM vibe_post;
SELECT status, COUNT(*) AS cnt FROM vibe_post GROUP BY status;
SELECT ai_reviewed, COUNT(*) AS cnt FROM vibe_post GROUP BY ai_reviewed;

-- To tear down after the benchmark:
--   DROP DATABASE nexus_bench;
