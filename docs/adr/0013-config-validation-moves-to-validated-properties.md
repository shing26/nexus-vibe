# 0013 — The AI settings bind into one validated object, and the bespoke validator goes

The AI pipeline reads nine `campus.ai.*` settings at eight call sites, all through `@Value`, and
one `BeanFactoryPostProcessor` (`AiConfigValidator`) was drafted to refuse a deploy whose numbers
would silently degrade the pipeline — a zero timeout, a zero attempt budget, a negative lease. It
worked: an earlier round on this branch wrote it and tested it twelve ways.

**That validator is not in any commit.** Both rounds landed in the same squash, and this one
removed the class before anything was pushed, so `git log -S AiConfigValidator` finds nothing and
`framework-reinforcement.md`'s `Files:` list names two paths that do not exist in the tree. The
description below is of the drafted code, kept because it is what this decision was made against —
not because a reader can check it out.

This ADR reverses one line of that round's `Rejected` section, which had turned this shape down on
the grounds that `@Value` plus a validator was already doing the job.

Two things changed the answer, and neither of them is a defect in the old code.

The first is where the knowledge sits. With `@Value`, a setting's default lives in the annotation
at its call site, its rule lives in the validator, and its name lives in both — three places for one
fact. `campus.ai.review.lease-seconds` was bound in `AiReviewEventListener` with a default of `30`
while `application.yml` set `240`, so the effective value depended on whether the file was on the
classpath; nothing in either place said so. A `@ConfigurationProperties` class puts the field, the
default and the constraint on adjacent lines, and makes the difference visible the first time
someone reads it.

The second is that "a bespoke validator" is a thing a reader has to trust. The drafted
`AiConfigValidator` was correct and tested, but it is 150 lines of hand-rolled parsing — `DurationStyle.detectAndParse`,
`Integer.parseInt`, a URL check that deliberately uses `getRawAuthority()` because Compose service
names contain underscores. Every one of those is a place the validator and Spring's own binder can
disagree about what a valid value is. Binding through Spring means the same code path that reads
the property also judges it.

Decision: `campus.ai.*` binds into `CampusAiProperties`, annotated `@Validated`, and
`AiConfigValidator` and its test are removed (they only ever existed in the working tree). The eight consumers take the object instead of
the property. The validation rules are equivalent — endpoint not blank and an absolute http(s) URL,
model not blank, timeout greater than zero, and positive integers for lease-seconds, max-attempts,
max-context-tokens, stale-minutes, failure-threshold and open-seconds.

Two details worth recording, because both are deviations from the obvious plan.

**`@DurationMin` does not exist in this Spring Boot.** It arrived in 3.4; the project is on 3.3.5,
and upgrading the framework to get one annotation is not a trade this round makes. The duration
rule is therefore an `@AssertTrue` method whose message names the property
(`campus.ai.llm.timeout must be greater than zero`), so the operator still gets the property name
in the failure. When the framework moves to 3.4 the method can become the annotation.

**The guarantee is per property group, not per configuration.** Spring Boot 3.3 validates each
`@Valid` nested group as it finishes binding and throws on the first group that fails. The old
`BeanFactoryPostProcessor` collected every violation across the whole tree and reported them in one
message. So "startup names the property" survives, and "startup names *all* the properties"
becomes "startup names all the violations in the group that failed". `CampusAiPropertiesTest`
pins both halves: one test asserts every violation in a group is reported at once, and a second
asserts that two bad groups still produce one `BindValidationException` rather than a claim of
completeness. This is a real loss and it is the price of deleting the validator.

Alternatives rejected. Keeping both — the validator *and* the properties object — doubles the
rules and creates the failure this ADR is about: two judges that can disagree, with no test that
compares them. Flattening the properties into one class to restore whole-tree aggregation: the
prefix structure is the public configuration contract (`campus.ai.llm.breaker.open-seconds` is
documented in `application.yml` and in the runbooks), and flattening it to satisfy a validation
detail would change every documented key. Writing a custom `ConfigurationPropertiesBindHandlerAdvisor`
to aggregate across groups: possible, and it would be a bespoke mechanism again, with more surface
than the validator it replaced. Validating in an `@PostConstruct` on the properties bean: the bean
would exist and be injected into consumers before the check ran, which is exactly the ordering the
old post-processor was chosen for.

What this costs, stated plainly: one new class, eight edited constructors or fields, a deleted
validator and its twelve tests, a rewritten test that now depends on Spring's binder and a
`ValidationAutoConfiguration` context, and one guarantee given up as described above. The
configuration contract does not change — no property is renamed, no default moves — and
`docs/plans/environment-matrix.md` records the dev/prod/CI values so the next reader does not have
to reconstruct them from two YAML files.

Why this is worth reopening a frozen scope (ADR-0009) for: the artifact's argument is that an LLM
is an unreliable dependency, and the configuration that decides how the pipeline reacts to that
unreliability was the one part of it a reader could not verify from the file that declared it. The
replacement is the framework's own mechanism, which means a reviewer can check the claim by knowing
Spring rather than by reading this repository's parser.
