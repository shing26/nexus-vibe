# Real-user trial — 2026-11 (A5)

Status: **waiting on participants.** Harness launched 2026-11-19; no
participant has completed the journey yet, and this file says so in those
words rather than implying otherwise. Plan source:
[portfolio-showcase-plan](../plans/portfolio-showcase-plan.md), item A5.
Ticket: [a5-real-user-trial](../tickets/a5-real-user-trial.md).

## The journey (what "completed" means)

1. Register an account on the live instance.
2. Publish one post containing a fenced code block.
3. Read the AI review of that post — score, severity and verdict visible on
   the post page.

Completion is checkable against the stack, not taken on trust: the
participant's `sys_user` row exists, their `vibe_post` row contains a fenced
block, and `ai_review_log` carries a `code-review-agent` row for it.

## Invitation copy (sent verbatim to every participant)

> 帮我测个东西，大概 10 分钟：
>
> 1. 打开 <https://qualifier-discuss-marry.ngrok-free.dev>，注册一个账号。
> 2. 发一篇帖子，正文里贴一段你自己写过的代码（什么语言都行，随便一段）。
> 3. 发完等一两分钟，回到帖子页面，看 AI 给它的评审——分数、严重度、逐条发现。
>
> 然后把这一路的感受用你自己的话发给我：卡住的地方、觉得奇怪的、觉得
> 好的、觉得多余的，都算。原话最好——我会一字不改地记下来，只用来改东西。

## Verbatim feedback (unedited)

Nothing yet. Quotes are appended here exactly as received, each with the
participant's date; no translation, no summarising, no softening.

| Participant | Date | Journey completed? | Verbatim words |
|---|---|---|---|
| — | — | — | — |

## Usage measurement (run on this machine, read-only)

Baseline: **not yet taken.** The first measurement below was attempted on
2026-11-19 but Docker Desktop's engine was unreachable from the shell
(HTTP 500 on the engine pipe), so no number exists yet — this file records
the attempt rather than inventing the count. Run the command at kickoff and
again whenever a participant completes, and paste the output here with the
date.

```powershell
docker compose exec -T db sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" nexus_campus -e "$0"' "
SELECT
  (SELECT COUNT(*) FROM sys_user) AS registered_users,
  (SELECT COUNT(*) FROM vibe_post p
     WHERE p.user_id <> 999 AND p.id <> 900000000000000001
       AND p.content LIKE '%\`\`\`%') AS human_code_posts,
  (SELECT COUNT(*) FROM ai_review_log l
     JOIN vibe_post p ON p.id = l.post_id
     WHERE l.reviewer = 'code-review-agent'
       AND p.user_id <> 999 AND p.id <> 900000000000000001) AS human_code_reviews;"
```

Exclusions: user 999 is the AI agent account; post `900000000000000001` is the
curated showcase recording. Everything else in these counts is a real
registration and a real post.

## What this trial cannot prove

- That the participants resemble the audience a hiring manager cares about —
  three friends are three friends; the record keeps their context only if
  they volunteer it.
- That a completed journey means the experience was good. The verbatim words
  carry that; if they are harsh, they are recorded harsh.
- Anything beyond the journey defined above: retention, repeat use, or
  "would come back" are different claims, and this round does not make them.
