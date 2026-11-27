# Evidence anchoring: first measured run

2026-11-28. Increment C of `plans/ai-service-depth.md` asks the model for
verbatim quotes and verifies them in Java. The point is not to make the model
more trustworthy; it is to make an unverifiable claim visible. A verdict can now
carry the exact source lines it claims to be about, and a quote that is not in
the submitted text is dropped and counted.

## How to reproduce

The service jar was built from `codex/ai-service-depth-c` and run against the
local Ollama exactly as in the B note, with the same `qwen2.5:3b` model:

```bash
mvn -B -q -f ai-service/pom.xml package -DskipTests
docker run -d --name nexus-ai-depth --network nexus-vibe_nexus-net \
  -p 18001:8000 -v "$PWD/ai-service/target:/jars:ro" \
  -e AI_LLM_ENDPOINT=http://ollama:11434 -e LLM_MODEL=qwen2.5:3b \
  -e AI_SERVICE_KEY=depth-key -e AI_LLM_TIMEOUT=60s -e AI_LLM_MAX_ATTEMPTS=2 \
  eclipse-temurin:21-jre java -jar /jars/nexus-ai-service-1.0.0.jar

python scripts/ai_eval.py --base-url http://localhost:18001 --key depth-key \
  --write-baseline benchmark/eval/baseline-2026-11-28-evidence.json
```

The run was taken on 2026-11-27 21:24–21:36 UTC (2026-11-28 05:24–05:36
Asia/Shanghai). Prompt version `eb5421e62edc` is the B version plus the evidence
instruction and schema field. `AI_EVIDENCE_REQUIRED` stayed at its default
`false`.

`EvidenceExtractorTest` covers the verifier without a model: a real quote on
line 2, a reflowed multi-line quote, source text returned instead of the model's
whitespace, a fabricated quote, blank/short/duplicate quotes, a snippet match,
the five-quote cap, and a missing/non-array `evidence` value.

## Result

| metric | value |
|---|---|
| review cases | 20 |
| cases with at least one accepted quote | 15 |
| accepted quotes | 27 |
| rejected quotes | 29 |
| cases with no evidence at all | 4 |
| invalid (HTTP failure) | 1 |
| score agreement | 0.70 |
| severity agreement | 0.25 |
| safety class agreement | 0.9167 |
| review p50 / p95 latency | 22.4 s / 49.6 s |
| safety p50 / p95 latency | 6.9 s / 21.8 s |

The one invalid case is `structured-prompt`: the provider hit the 60 s attempt
timeout, and a second 60 s attempt cannot fit in the request's 115 s budget, so
the service returned 504 `model_timeout` after 60.7 s. That is the retry ladder
working as designed, not a parse failure.

A live spot-check with `AI_EVIDENCE_REQUIRED=true` and a one-character post
(`content="x"`, so no quote can reach the 10-character minimum) returned
`severity="unknown"`, `approved=false`, `repaired=true`, an empty `evidence`
array and a `resultJson` whose severity was rewritten to `unknown`. The model
first answered `critical`; the policy, not the model, produced the invalid
verdict.

The four cases with an empty accepted set are `off-by-one-loop`,
`injection-inside-code` and `serial-http-in-loop` (the model chose to return no
quotes) and `mutable-default` (it returned three, all rejected). Across the
run, 29 of the 56 supplied quotes were rejected. The count does not separate
"not found", "too short" and "duplicate"; that is a deliberate simplification
for the first version and a limit of this measurement.

Two examples from the baseline:

- `hardcoded-secret` produced two quotes, `String password = "hunter2";` on
  line 2 and `DriverManager.getConnection(url, "admin", password);` on line 3.
- `null-dereference` produced two accepted and three rejected quotes. The eval
  report stores counts, not the accepted text (the call archive stores the raw
  model output), so this note can name the counts but not the spans.

The score and severity agreement fell from the B run's 0.85 / 0.40 to 0.70 /
0.25. The evidence instruction changes the output shape, and the model spent
more of its answer on quotes; it also scored `injection-inside-code` 0/critical
this time, outside the 1–5 band. This is a real prompt-version effect, which is
why the baseline is a separate file rather than an edit to the B baseline.

## What this cannot prove

- One run on one 3B model. A different provider, temperature or prompt version
  needs its own baseline; the gate does not compare across them.
- A rejected quote is counted, not explained. The response has one integer, so
  an operator cannot tell a paraphrase from a too-short quote without the
  archive.
- Accepted means "this text exists in the submission", not "this text supports
  the verdict". Relevance is still unmeasured.
- Snippet matches use line numbers local to that snippet; content matches use
  line numbers in the post body. The response does not carry which source was
  used, so a client must not assume every span indexes the post.
- The title is not searched. The contract says content or snippets, and the
  title is metadata the model may legitimately quote in prose.
- The committed baseline records `evidenceCount` and `evidenceRejected`, not
  the accepted quote text. Inspecting the spans requires the call archive or a
  fresh request; the report alone cannot show what was quoted.
- Evidence does not influence approval, status or the rendered comment body.
  It is attached to `resultJson` for persistence and study.
- Four of twenty cases would fail if `AI_EVIDENCE_REQUIRED=true` were enabled
  today. The default stays `false` until a run shows the local model can supply
  an accepted quote reliably.
