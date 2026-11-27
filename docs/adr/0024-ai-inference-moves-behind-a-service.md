# 0024 — AI inference moves behind an internal service

Status: **Accepted — 2026-11-27.** The frozen contract is
`plans/ai-service-extraction.md`, based on commit `c7bb34a`, on branch
`codex/ai-service-extraction`.

## Context

One process currently does two jobs. `AbstractLlmReviewer` assembles the prompt,
`LlmClient` performs the OpenAI-compatible call (hosted DeepSeek in production, local
Ollama in development), and `JsonRepairUtil`, the retry ladder and `ReviewValidity` all
run in Java. That arrangement works, and ADR-0004, ADR-0005 and ADR-0007 are built on it.

Three things make the boundary worth moving now.

1. **The model surface is growing.** Review, post safety, comment safety, agent mention
   and the deferred retrieval work all face the same provider, but each carries its own
   prompt and its own parsing. Provider quirks already leak into Java: `response_format`
   is `json_schema` for some models and `json_object` for DeepSeek, and DeepSeek's
   thinking mode has to be disabled explicitly. Every new AI surface adds another place
   where a provider detail can be wrong.
2. **The architecture claim is stronger with a real boundary.** "Java + AI" is currently
   an HTTP call inside a `@Component`. A typed contract between a Java policy owner and
   an inference service is a different, and more defensible, piece of engineering.
3. **The seam already exists.** ADR-0012 made `Reviewer` and `ReviewPolicy` replaceable,
   and `Reviewer`'s own javadoc says a replacement "produces data, not side effects".
   Extracting inference is therefore a supported move, not a rewrite.

The risk is equally clear. The project's subject is treating the LLM as an unreliable
dependency. If the extraction moves failure policy into the new service, the thesis is
diluted and the system becomes a two-process demo. The boundary therefore has to be drawn
so that Java still decides what a failure means.

## Decision

1. **Add `ai-service/`, a separate Java 21 / Spring Boot process, as the owner of
   model-facing inference concerns**: prompt templates (roles, schema, examples), provider
   adapters and their quirks, JSON repair, provider-level retries, and token/latency
   accounting. It uses Spring AI 1.1.x on the Boot 3.5 line for the provider client and
   structured output. It is an independent Maven project: the application's root build is
   not converted to a reactor. It is stateless and reachable only on the compose network.
2. **Java remains the policy owner**: trigger rules, the lease and attempt count,
   `ReviewValidity`, persistence, comment rendering, score writeback, fail-closed, the
   pending queue and reconciliation. `ServiceReviewer` implements the existing `Reviewer`
   seam; the two safety listeners move onto an equivalent `SafetyClassifier` seam. The
   application calls typed endpoints, not an OpenAI-compatible facade, so the service's
   structured output is the contract rather than a convention.
3. **The boundary is a typed JSON contract**, committed as OpenAPI 3.1 under
   `ai-service/openapi/`. Java validates every response again. The service is a
   dependency, not an authority: a well-formed but nonsensical verdict still fails
   `ReviewValidity` and is recorded as invalid. Each side runs its own contract test, and
   the service's JUnit suite validates the OpenAPI file itself, so a field rename cannot
   pass silently.
4. **One retry owner per hop.** Provider retries and the reinforced-prompt retry for
   invalid output live in the AI service; the lease, attempt ladder and reconciliation
   stay in Java. The application does not retry an HTTP call to the service. Nested retry
   ladders (3 attempts in Java times 3 in the service) are forbidden because they
   multiply both latency and spend.
5. **`campus.ai.mode` selects `direct` or `service`.** The code default is `direct`, so
   unit tests and a missing service stay safe; local compose defaults to `service`;
   production starts on `direct` and flips to `service` only after parity evidence and
   independent verification. The flip and the rollback are the same one-variable change
   plus restart, not a code change.
6. **P0 covers review and safety only.** Retrieval (RAG) is outside this ADR's scope; a
   future ADR and plan must justify it, starting from a measured recall@5 baseline. This
   ADR does not authorise an embedding index or a second datastore.
7. **The AI service is the only model caller in production service mode.** The model
   credential is injected into the `ai` container, not into the application container.
   Rollback to direct mode is an explicit compose change that re-injects the key and
   restarts the application, so it cannot happen silently at runtime. The one surface
   outside this boundary is the `@ai-agent` mention reply, which is chat: production
   sets `CAMPUS_AI_MENTION_ENABLED=false` on the flip until a separate plan adds a chat
   endpoint. The local/demo stack keeps it enabled against keyless Ollama.
8. **The service is internal-only and its health is split.** It publishes no host port
   and gets no nginx route. `/healthz` answers process liveness only; `/readyz` answers a
   cached provider probe. `AiServiceClient.isHealthy()` consumes `/readyz`, and the
   existing `LlmHealthCache` stays in front of it, so the publish gate and the reconcile
   task keep a single verdict in both modes.

## What deliberately does not move

- `PromptIsolation` and context truncation stay in Java. They are security and budget
  policy, not model mechanics; keeping one implementation avoids two places to get
  untrusted-input handling wrong.
- The `pending-llm` marker, `FAILED` transition and reconciliation sweep stay in Java.
  The AI service cannot write post status, and has no database credentials.
- Comment rendering stays in Java. The service returns fields and `resultJson`; a shared
  `VerdictRenderer` turns them into the comment body, so direct and service modes cannot
  drift in presentation.
- The user-facing API and its error semantics do not change. "Model unreachable" and
  "AI service unreachable" must produce the same observable state.

## Consequences

- A second JVM, CI job, health check and memory budget. The Docker VM is 7.65 GiB and
  Ollama already reserves 3 GiB, so the second JVM needs an explicit heap cap; no local
  model weights and no Python runtime in P0.
- Two Spring Boot minor versions in the stack (3.3.5 in the application, 3.5.x in the AI
  service). That is the price of keeping the frozen application's dependency graph
  untouched; Spring AI 2.x would force Boot 4 and is out of scope.
- Two independent builds and CI jobs. A shared contract module would remove the
  duplicated DTOs but would couple the two release cycles; it is deferred until the
  contract has survived a round unchanged.
- Two failure domains instead of one. The new one maps onto existing paths, but that
  mapping needs its own tests: service down, provider down, 200 with malformed JSON, and
  504 must each land in the state the old path produced.
- Contract drift becomes a real risk. The OpenAPI file plus a contract test in each
  service are mandatory, not optional.
- Prompt changes no longer require a redeploy of the application and become testable in
  the AI service's own suite. The cost is a parity obligation: the same fixture posts
  must produce the same structured verdict in both modes.
- Choosing a second JVM gives up Python's model ecosystem and the cross-language
  contract. It keeps one toolchain, one dependency style and one operational model. That
  is a deliberate trade for Java depth, not an oversight.
- In production service mode the model credential is removed from the application
  container. Rollback to direct then requires a compose change and restart, which is a
  visible operational step rather than a config toggle.
- A blank shared key is a startup failure: the AI service defaults to the `prod`
  profile, and `.env.example` ships a non-blank local/demo value that both containers
  share. A copied environment cannot expose an unauthenticated `/v1/*` on `nexus-net`.
- README's "one compose file, two seams" line and the test counts change in the same
  commit that lands P0.

## Alternatives considered

- **Spring AI or LangChain4j in the same JVM.** Fewer moving parts and no second process,
  but provider quirks stay in the application and the result demonstrates a library, not
  a service boundary.
- **A Python/FastAPI service.** The stronger model ecosystem and a genuine cross-language
  contract, at the cost of a second toolchain, CI runtime and dependency set. Rejected in
  favour of Java depth, not because the boundary would be weaker.
- **Spring AI 2.1.0.** The current major line, but its starters depend on Spring Boot
  4.2.0. That would turn a service extraction into an application-wide upgrade.
- **LangChain4j.** A credible alternative with a stronger agent/tool API, but its Spring
  Boot starter is still published as a beta and this round needs only structured review
  and safety calls.
- **An OpenAI-compatible facade in the AI service.** The cheapest integration: point
  `LLM_ENDPOINT` at the new service and change nothing in Java. But Java keeps prompt
  assembly and the contract stays untyped, so the architecture claim remains a claim.
- **Keep the direct path.** It works and is already resilient. The cost is that the
  project never demonstrates the boundary it describes.

## Relationship to earlier decisions

This ADR does not supersede ADR-0009, ADR-0016 or ADR-0022; it operates under ADR-0022's
reopened scope. It preserves ADR-0004's fail-closed rule, ADR-0005's lease, ADR-0007's
degraded-but-serving semantics, ADR-0010's replay-not-live rule and ADR-0012's two seams.
ADR-0006's hosted DeepSeek remains the production model; in service mode the AI service
becomes its only caller.
