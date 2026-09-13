package com.nexus.campus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The alerting layer has to be unable to fail silently, and that starts with it loading at all.
 *
 * <p>Grafana parses {@code rules.yaml} at startup and exits on anything it does not recognise, so
 * an unusable enum value does not degrade one rule -- it takes the dashboard and the notifier down
 * with it, in a restart loop that looks identical to "Grafana is still starting" from the outside.
 * That is not hypothetical: {@code noDataState: ALERTING} was in this file, and the only reason it
 * was caught is that the drill's {@code grafana-provisioning-loaded} step happened to be looking at
 * {@code /api/health} rather than reading the YAML. This test is the two-second version of that
 * step, so the next spelling mistake lands in CI rather than fifteen minutes into a drill.</p>
 *
 * <p>It pins three things the drill's file-reading steps cannot: that every value is one of the
 * four Grafana actually accepts, that no rule carries a state key Grafana would quietly drop, and
 * that the receiver the policy points at exists with an address the bridge really listens on.</p>
 */
class GrafanaAlertProvisioningTest {

    /**
     * The only spellings Grafana 11's provisioner resolves, verbatim. {@code ALERTING} and
     * {@code alerting} are both rejected with "unknown NoData state option", which makes this set
     * the whole point of the file.
     */
    private static final Set<String> ACCEPTED_STATES = Set.of("Alerting", "NoData", "OK", "KeepState");

    /**
     * Keys that look like state policies but are not in the provisioning schema. Grafana's parser is
     * not strict about them: it loads the rule and drops the key, which leaves a comment claiming a
     * policy the engine never applied.
     */
    private static final Set<String> INVENTED_STATE_KEYS = Set.of("errorState", "errorOrTimeoutState", "noData");

    /** Per-rule policy, and the reason each one is that way. Keep in step with rules.yaml. */
    private static final Map<String, String> EXPECTED_NO_DATA = Map.of(
            // Gauges registered when the process starts: no series means no app, no endpoint, no scrape.
            "nexus-llm-breaker-open", "Alerting",
            "nexus-ai-review-backlog", "Alerting",
            // The rule that asks whether anything is measuring at all, so it must not answer "healthy".
            "nexus-prometheus-scrape-failed", "Alerting",
            // Ratios behind a volume guard: an empty result usually means too quiet, not broken.
            "nexus-http-5xx-ratio", "OK",
            "nexus-rate-limit-spike", "OK",
            "nexus-availability-999-fast-burn", "OK");

    @Test
    @DisplayName("every rule's no-data and error state is a value Grafana accepts")
    void stateValuesAreSpellableByGrafana() {
        List<Map<String, Object>> rules = loadRules();
        assertThat(rules).as("rules.yaml should not load empty")
                .hasSizeGreaterThanOrEqualTo(EXPECTED_NO_DATA.size());

        List<String> problems = new ArrayList<>();
        for (Map<String, Object> rule : rules) {
            String uid = String.valueOf(rule.get("uid"));
            for (String key : List.of("noDataState", "execErrState")) {
                Object value = rule.get(key);
                if (value == null) {
                    problems.add(uid + " has no " + key);
                } else if (!ACCEPTED_STATES.contains(String.valueOf(value))) {
                    problems.add(uid + "." + key + "='" + value + "' is not one of " + ACCEPTED_STATES);
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("the per-rule no-data policy is the one the file's header argues for")
    void noDataPolicyIsPerRule() {
        List<String> drift = new ArrayList<>();
        for (Map<String, Object> rule : loadRules()) {
            String uid = String.valueOf(rule.get("uid"));
            String expected = EXPECTED_NO_DATA.get(uid);
            if (expected == null) {
                drift.add(uid + " is not described in this test -- state it here, with the reason");
            } else if (!expected.equals(String.valueOf(rule.get("noDataState")))) {
                drift.add(uid + " is noDataState=" + rule.get("noDataState") + ", expected " + expected);
            }
        }
        assertThat(drift).isEmpty();
    }

    /**
     * A rule that fires on a missing scrape is only blind-proof if the evaluation error goes the same
     * way; this is the one rule where "I could not run the query" is itself the incident.
     */
    @Test
    @DisplayName("the scrapability rule alerts on absent data and on a failed evaluation")
    void scrapabilityRuleFailsLoudOnBoth() {
        Map<String, Object> rule = ruleByUid("nexus-prometheus-scrape-failed");
        assertThat(rule.get("noDataState")).isEqualTo("Alerting");
        assertThat(rule.get("execErrState")).isEqualTo("Alerting");
    }

    @Test
    @DisplayName("no rule carries a state key Grafana would silently drop")
    void noInventedStateKeys() {
        List<String> dropped = new ArrayList<>();
        for (Map<String, Object> rule : loadRules()) {
            String uid = String.valueOf(rule.get("uid"));
            for (String key : INVENTED_STATE_KEYS) {
                if (rule.containsKey(key)) {
                    dropped.add(uid + " sets " + key + "= but provisioning ignores it");
                }
            }
        }
        assertThat(dropped).isEmpty();
    }

    /**
     * Every alert in the stack has to have somewhere to go, and that somewhere has to be the
     * container the bridge actually runs in. A typo in either file yields rules that fire into a
     * 404, which is the silent failure this round exists to remove.
     */
    @Test
    @DisplayName("the routing policy points at a contact point that addresses the bridge container")
    void policyRoutesToTheBridge() {
        Map<String, Object> policy = firstPolicy();
        String receiver = String.valueOf(policy.get("receiver"));

        List<Map<String, Object>> points = topLevelList("contact-points.yaml", "contactPoints");
        assertThat(points).extracting(point -> String.valueOf(point.get("name"))).contains(receiver);

        String url = null;
        for (Map<String, Object> point : points) {
            if (!receiver.equals(String.valueOf(point.get("name")))) {
                continue;
            }
            List<Map<String, Object>> receivers = list(point.get("receivers"));
            assertThat(receivers).as("%s has no receiver to send to", receiver).isNotEmpty();
            url = String.valueOf(map(receivers.get(0).get("settings")).get("url"));
        }
        // docker-compose.yml names the service alert-bridge and the drill reads this same URL back
        // out of the running API, so the three cannot disagree without one of them going red.
        assertThat(url).startsWith("http://alert-bridge:").endsWith("/notify");
    }

    @Test
    @DisplayName("a rules file Grafana cannot parse fails here before it crash loops a stack")
    void rulesFileParsesAsOneDocument() throws IOException {
        Path path = provisioningFile("rules.yaml");
        try (InputStream in = Files.newInputStream(path)) {
            Object document = new Yaml().load(in);
            assertThat(document).isInstanceOf(Map.class);
            assertThat(map(document)).containsKey("groups");
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    private List<Map<String, Object>> loadRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        for (Map<String, Object> group : topLevelList("rules.yaml", "groups")) {
            rules.addAll(list(group.get("rules")));
        }
        return rules;
    }

    private Map<String, Object> ruleByUid(String uid) {
        return loadRules().stream()
                .filter(rule -> uid.equals(String.valueOf(rule.get("uid"))))
                .findFirst()
                .orElseGet(() -> fail("no rule with uid " + uid + " in rules.yaml"));
    }

    private Map<String, Object> firstPolicy() {
        List<Map<String, Object>> policies = topLevelList("policies.yaml", "policies");
        assertThat(policies).as("policies.yaml must declare at least one routing policy").isNotEmpty();
        return policies.get(0);
    }

    /** Read one provisioning file from disk and return {@code key} as a list of maps. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> topLevelList(String file, String key) {
        Path path = provisioningFile(file);
        try (InputStream in = Files.newInputStream(path)) {
            Object root = new Yaml().load(in);
            Object node = map(root).get(key);
            if (node == null) {
                return fail(path + " has no " + key);
            }
            return (List<Map<String, Object>>) node;
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + path, e);
        }
    }

    /**
     * Resolve the shipped alerting files from wherever the build happens to be running: Maven uses
     * the repository root, an IDE may use the module directory. Failing loudly on a miss is on
     * purpose -- a skipped provisioning check reads exactly like a passing one.
     */
    private Path provisioningFile(String file) {
        Path relative = Path.of("docker", "observability", "grafana", "provisioning", "alerting");
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path probe = dir.resolve(relative).resolve(file);
            if (Files.isRegularFile(probe)) {
                return probe.normalize();
            }
        }
        return fail("no " + file + " under " + relative + " at or above " + Path.of("").toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object node) {
        return node instanceof Map ? (Map<String, Object>) node : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object node) {
        return node instanceof List ? (List<Map<String, Object>>) node : List.of();
    }
}
