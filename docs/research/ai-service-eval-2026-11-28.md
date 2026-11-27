# AI service eval: first golden-set baseline

2026-11-28. Increment B of `plans/ai-service-depth.md` exists because the service
had no number that could regress. Parity tests compare shapes and prompts, not
quality; a prompt or provider change could lower every verdict and every gate
would stay green. This note records the first measurement and, just as
importantly, the cases where the local model disagrees with the expectations.

## How to reproduce

The corpora are committed: `benchmark/eval/review-cases.json` (20 cases) and
`benchmark/eval/safety-cases.json` (12 cases). Each review case carries an
expected score band, the severities that would be defensible, and a one-line
rationale; each safety case carries the expected class.

```bash
mvn -B -q -f ai-service/pom.xml package -DskipTests
docker run -d --name nexus-ai-depth --network nexus-vibe_nexus-net \
  -p 18001:8000 -v "$PWD/ai-service/target:/jars:ro" \
  -e AI_LLM_ENDPOINT=http://ollama:11434 -e LLM_MODEL=qwen2.5:3b \
  -e AI_SERVICE_KEY=depth-key -e AI_LLM_TIMEOUT=60s -e AI_LLM_MAX_ATTEMPTS=2 \
  eclipse-temurin:21-jre java -jar /jars/nexus-ai-service-1.0.0.jar

python scripts/ai_eval.py --base-url http://localhost:18001 --key depth-key \
  --write-baseline benchmark/eval/baseline-2026-11-28.json
```

The run was taken on 2026-11-27 21:00–21:07 UTC (2026-11-28 05:00–05:07
Asia/Shanghai) against the jar built from `codex/ai-service-depth-b`, provider
`ollama:11434`, model `qwen2.5:3b`, prompt version `4507b4663c31`. The version is
returned as the `X-Prompt-Version` response header on both endpoints and written
into archive records, so a baseline can be tied to the text that produced it.

`scripts/test_ai_eval.py` runs 29 tests without a model: corpus validation,
percentile and summary maths, the comparison gate in both directions, and an
end-to-end run against a stub HTTP server.

## Result

| metric | review (20 cases) | safety (12 cases) |
|---|---|---|
| invalid / HTTP failure | 0 | 0 |
| agreement | score 0.85, severity 0.40 | class 0.9167 |
| repair rate | 0 | n/a |
| p50 latency | 14,961.8 ms | 7,757.2 ms |
| p95 latency | 27,842.4 ms | 21,073.9 ms |
| tokens | 6,032 prompt / 2,171 completion | n/a |

The score band admits 17 of 20 cases. Three missed: `null-dereference` (7
against a 3–6 band), `racy-counter` (6 against 2–5), and `formatless-prompt`
(2 against 3–7). The first two are real severity *score* disagreements, not
parser failures: the verdict was well-formed, `repaired=false` throughout.

Severity agreement is the number worth reading. The model used only `medium`
(14 cases) and `low` (6 cases); it never returned `high` or `critical`, so every
band that expected a serious rating failed — including SQL concatenation,
a hardcoded secret, a racy counter and `innerHTML` XSS. Score agreement (0.85)
therefore overstates usefulness: a reviewer that calls a SQL injection
"medium, 3/10" lands inside a loose score band while still under-rating it.

Safety disagreement is one case: `injection-forged-delimiter` was classified
`safe` instead of `prompt_injection`. The other 11 cases matched.

The first review case measured 55 ms because the warm-up request before the run
used the same content and the deduplicator served it from cache. That is the
feature working, but it means p50 includes one cached sample; the remaining 19
provider calls are 10.7–31.6 s.

Latency percentiles are nearest-rank (`ceil(p * n)`), so the safety p95 is the
slowest of its 12 cases, the 21.1 s forged-delimiter miss.

## The gate

`--baseline <file>` fails when a higher-is-better metric (score, severity or
classification agreement) drops more than 0.05 below the baseline, or when
`invalidRate` rises more than 0.05 above it. `--tolerance` changes that window.
`--fail-below <ratio>` is the absolute alternative: any agreement metric below
the named ratio fails the run, with or without a baseline.
Aggregate tolerance alone would let a reviewer that always answers `4/medium`
pass, because the corpus contains many cases that accept that band. The gate
therefore also compares each baseline case: more than one case that passed now
failing is a regression (`--case-regressions` changes the allowance), and a
case missing from the run is always a failure. A section missing from either
the run or the baseline is an error, so a truncated baseline cannot switch a
gate off.
The comparison and both failure directions are unit-tested
(`CompareTest`, `EndToEndTest.test_regression_against_baseline_exits_non_zero`).

`.github/workflows/ai-eval.yml` runs the corpus and maths tests only. It does not
call a model.

## What this cannot prove

- Twenty review cases and twelve safety cases are a smoke set, not a benchmark.
  They cannot rank models or estimate accuracy on the forum's real traffic.
- There is one run on one model on one machine. The provider is sampled with
  temperature 0.2 (review) and default safety settings, so a repeat will move a
  few cases. The 0.05 tolerance is wider than one flip in 20 cases (0.05 exactly)
  but not wider than two.
- The expectations are hand-written, not adjudicated by a second reviewer. A
  case where the band is wrong will look like a model failure.
- Agreement is exact-match on a band and a class. It does not measure whether
  the rationale is correct, and it ignores the content of `quality`, `security`
  and `suggestions`.
- Deduplication means a repeated case is not a second model sample. That is
  correct for cost, but it makes the harness unsuitable for measuring variance
  without changing the request identity.
- The baseline is a local artefact. A hosted provider with a different model
  name and prompt behaviour needs its own run and its own file; the gate does
  not compare across models.
