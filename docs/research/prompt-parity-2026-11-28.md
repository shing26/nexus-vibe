# Prompt-text drift: direct reviewers vs the AI service

2026-11-28. Acceptance criterion #3 in `plans/ai-service-extraction.md` asks for
verdict parity on a fixture set. A8 recorded what the parity test does *not* cover:
the two prompt builders — `AbstractLlmReviewer`'s subclasses in the application
and `PromptFactory` in the inference service — had never had their output
compared. This note is that measurement.

The question is narrow. If the two modes send the model very different text, a
verdict-parity test that stubs the response proves very little about the service
path. If they send the same text, the stubbed parity test is meaningful evidence
about the payload swap.

## How to reproduce

The fixtures are committed once, in `benchmark/parity/review-fixtures.json`
(eight inputs; the two oversized ones are expanded from `repeatText`/`repeatCount`
so the repository does not carry 200 KB of repeated lines). Each side renders its
own corpus from that file; per-request nonces are replaced with `NONCE` before
writing, because they are random by design.

```bash
mvn -B -q -Djacoco.skip=true -Dtest=PromptTextCorpusTest test
mvn -B -q -f ai-service/pom.xml -Dtest=PromptTextCorpusTest test
python scripts/prompt_parity.py
```

The first command writes `target/prompt-parity/direct/<fixture>.txt`, the second
`ai-service/target/prompt-parity/service/<fixture>.txt`, and the last compares
them against `benchmark/parity/prompt-drift-baseline.json`. The same three steps
run in `.github/workflows/prompt-parity.yml`; the job fails when any fixture
drops more than 0.005 below the recorded line ratio or token Jaccard.

The service-side test builds its request the way `ServiceReviewer` does —
neutralized title and content, fenced blocks sent as `snippets` — rather than
calling `PromptFactory` with a synthetic shape.

## Result

| fixture | lines (direct/service) | line similarity | token Jaccard | changed lines |
|---|---|---|---|---|
| buggy-code | 23/23 | 0.9565 (95.7%) | 0.8494 | 2 |
| clean-code | 23/23 | 0.9565 (95.7%) | 0.8344 | 2 |
| empty-title | 22/22 | 0.9545 (95.5%) | 0.8288 | 2 |
| injection | 25/25 | 0.9600 (96.0%) | 0.8476 | 2 |
| multi-block | 1826/2729 | 0.8013 (80.1%) | 0.6692 | 905 |
| oversized-code | 3841/3841 | 0.9997 (100.0%) | 0.9984 | 2 |
| oversized-prompt | 21/21 | 0.9524 (95.2%) | 0.9961 | 2 |
| prompt-post | 21/21 | 0.9524 (95.2%) | 0.8555 | 2 |

Mean line similarity: 0.9417.

## What the numbers say

**One system-prompt sentence differs, and it is the schema instruction.** Seven
of the eight fixtures differ by exactly two changed lines (a one-line
replacement):

```text
-Output your analysis as a JSON object matching the provided schema.
+Return ONLY a JSON object with exactly these fields: score (integer 0-10), severity
 (low|medium|high|critical), codeQuality (string), securityConcerns (string),
 optimizationSuggestions (string).
```

The service enumerates the fields where the direct path points at the JSON schema
it sends alongside the request. That is why the token Jaccard sits near 0.85 on
otherwise identical text: the replacement is longer than the line it replaces.

**The user message is byte-identical for those seven fixtures**, including the
injection fixture. Neutralization is applied twice on the service side (once by
`ServiceReviewer`, once by `PromptFactory`) and is idempotent, so the doubled hop
does not change the text.

**`multi-block` is the one semantic difference.** The fixture has three
14,400-character code blocks:

- Direct mode filters blocks against `maxContextTokens - 2,600` = 9,400 tokens.
  Two blocks fit (8,230 tokens); the third would need 12,345 and is dropped.
- Service mode truncates each block at `maxContextTokens * 3.5` = 42,000
  characters and sends all three, because `ServiceReviewer` forwards every
  extracted snippet and the service has a per-block cap, not a total cap.

The result is 1,826 lines against 2,729 and 905 changed lines. In aggregate the
service sends about 12,343 tokens of user text where direct mode sends about
8,230. Verdict parity (#3) is unaffected because that test stubs the response,
but the model does not see the same payload. This measurement pins the
difference rather than fixing it: making the service enforce a total budget is a
behaviour change with its own review, and it is not part of this ticket.

**The oversized fixtures do not drift.** Both sides truncate at 42,000
characters, so the user text matches; the only difference is the shared system
sentence.

## What this measurement cannot prove

- It never calls a model. It says nothing about verdict quality parity, only
  about the text each side would send.
- It covers the review path only. Safety prompts (`/v1/safety`) are not in the
  corpus.
- Nonces are normalized to `NONCE`, so this cannot detect nonce reuse,
  predictability, or a system/user nonce mismatch. Those are separate properties.
- Eight fixtures are a set, not a distribution. The corpus is chosen to cover the
  named cases in acceptance #3, not to estimate how often drift would matter in
  production.
- It records the `multi-block` difference; it does not establish that the
  difference is harmless for any particular model or context window.
