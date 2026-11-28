# Review quality recovery: the prompt that met the D1 gate

2026-11-28. ADR-0026 treats evidence anchoring as useful and the prompt that
introduced it as a regression. Increment C made the model's claims checkable,
but on the same 20 review cases it dropped score agreement from 0.85 to 0.70 and
severity agreement from 0.40 to 0.25. D1 asks a narrower question: can the
service prompt recover those verdicts while keeping evidence coverage, without
changing the request or response contract?

The answer for this model and this corpus is yes, with a caveat about length.
Prompt `c2992c58aed9` met every frozen D1 target. It is the committed
`benchmark/eval/baseline-2026-11-28-quality.json`.

## How to reproduce

The gateway was the native Ollama installation on this machine, not the
compose `ollama` container:

```powershell
$env:JAVA_HOME = 'E:\java\jdk21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

mvn -B -f ai-service/pom.xml test
mvn -B -q -f ai-service/pom.xml package -DskipTests

# The native server must be running on 11434 with qwen2.5:3b pulled.
java -jar ai-service\target\nexus-ai-service-1.0.0.jar `
  --server.port=18002 `
  --spring.profiles.active=prod `
  --ai-service.llm.base-url=http://localhost:11434 `
  --ai-service.llm.api-key=ollama `
  --ai-service.llm.model=qwen2.5:3b `
  --ai-service.llm.timeout=60s `
  --ai-service.llm.max-attempts=2 `
  --ai-service.review.evidence-required=false

python scripts/ai_eval.py `
  --base-url http://localhost:18002 `
  --key <service-key> `
  --only review `
  --write-baseline benchmark/eval/baseline-2026-11-28-quality.json
```

The model is the same `qwen2.5:3b` used by B and C, at temperature 0.2. This run
used the GPU-backed native Ollama, while B and C ran in the compose CPU
container. The agreement numbers are comparable because the model and cases are
the same; the latency numbers are not. The accepted run's p50 / p95 was 2.58 s /
4.47 s, against C's 22.4 s / 49.6 s on CPU.

## Runs

Only one run per prompt was used. v5 was the first run to meet every target; it
was not selected by rerunning v5 until a threshold was crossed.

| prompt | score | severity | cases with evidence | invalid | note |
|---|---|---|---|---|---|
| `0dd45c1e06d3` | 0.65 | 0.10 | 12/20 | 0.10 | long rubric; two 180 s timeouts |
| `540931a3e173` | 0.60 | 0.55 | 18/20 | 0.00 | severity examples helped; many low-risk snippets became `low/9` |
| `9dae4b15c632` | 0.60 | 0.55 | 17/20 | 0.00 | score bands helped severe cases but over-penalized clean snippets |
| `ba32804b7d82` | 0.75 | 0.70 | 15/20 | 0.00 | explicit checks and separate prompt schema; five misses |
| `c2992c58aed9` | **0.90** | **0.90** | **17/20** | **0.00** | accepted baseline; 18 rejected quotes, 0.25 repair rate |

## What changed in the accepted prompt

The prompt now gives the model a procedure rather than adjectives:

- Nine concrete checks: loop boundary, resource lifetime, swallowed error,
  chained null dereference, unsynchronized shared state, SQL/command and HTML
  injection, catastrophic regex, serial HTTP, and prompt injection in the META
  block.
- A score scale tied to the severity of the finding: 9-10 has no finding, 7-8
  is low-risk only, 5-6 has a medium finding, 3-4 has a high or several medium
  findings, and 0-2 is critical or broken. A medium finding caps score at 7; a
  high or critical finding caps it at 4.
- Short examples for the defect classes that the previous prompts either
  missed or over-penalized, including a clean stream, a typed immutable
  dataclass, and a validated parser.
- A prompt-specific severity description in `ReviewSchema.PROMPT_REVIEW`.
  `PROMPT_REVIEW` no longer inherits code-review examples such as RCE and XSS.
- The evidence instruction remains, but it now asks for one quote that
  demonstrates a non-low finding and tells the model to copy the source
  character-for-character.

The cost is real: the accepted run used 18,067 prompt tokens across 20 cases
(about 903 per case), against 6,274 in C. That did not hurt this GPU-backed run,
but the compose CPU provider needs its own latency measurement before the
prompt is called latency-safe.

## D1 acceptance

| metric | target | accepted run | C baseline |
|---|---|---|---|
| score agreement | >= 0.85 | 0.90 | 0.70 |
| severity agreement | >= 0.55 | 0.90 | 0.25 |
| validWithEvidence / cases | >= 0.75 | 17/20 | 15/20 |
| invalid rate | <= 0.05 | 0.00 | 0.05 |
| case regressions vs C | <= 1 | 1 | - |

The one regression is `validated-python-function`: C scored it 8 and this run
scored it 6, so `scoreOk` changed from true to false. The run still produced
`medium` for that case, so its severity was already outside the expected `low`
band in C. `formatless-prompt` is not a regression: C also scored it 2, outside
the 3-7 band. The accepted prompt rates it `high` rather than C's `low`, but
both miss the expected score.

Evidence coverage improved enough to clear D1, not enough to enforce it.
`AI_EVIDENCE_REQUIRED` stays false: 3 of 20 cases still have no accepted quote,
so turning enforcement on now would turn 15% of otherwise-valid verdicts into
`unknown`. That is D2's decision, not this prompt's.

## What this cannot prove

- One run of one prompt on one 3B model. Temperature 0.2 still has variance; a
  second run of `c2992c58aed9` was not used to choose the baseline.
- The GPU result says nothing about CPU latency or token throughput in the
  compose stack.
- The 20-case corpus is a regression set, not a distribution. A 0.90 agreement
  does not estimate how often the reviewer is right on unseen posts.
- Evidence counts are coverage, not relevance. An accepted quote is text that
  exists in the submission; it is not proof that the quote supports the
  verdict.
- `repairRate` is 0.25, so five responses needed the JSON repair pass. The
  repair path is covered by existing tests, but the prompt's malformed-output
  rate is a second cost that this quality table does not price.
- D0's live container acceptance is separate. The compose rendering is in the
  ticket; Docker Desktop on this machine could not start, so the sidecar was
  not observed running.
