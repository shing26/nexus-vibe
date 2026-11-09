package com.nexus.campus.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * B7, as a config-reading test rather than a manual check.
 *
 * <p>The shipped edge config is read from disk and the matching nginx would perform is simulated
 * for the URLs this round cares about. That is the only way to answer "is the new path reachable
 * and is every other actuator path still closed" without a running nginx, and it fails on the
 * edit that would change the answer - which a documented manual check does not.</p>
 *
 * <p>The new endpoint needed no nginx change: it lives under {@code /api/}, the one prefix the
 * edge already proxies with the URI untouched, and carries no {@code /actuator} word. What this
 * test pins is exactly that: the new path resolves to the {@code /api/} proxy, {@code deps} still
 * resolves to the explicit 404, and the exact-match health URL is still the only actuator path
 * forwarded.</p>
 */
class NginxEdgeSurfaceTest {

    private static final String NEW_PATH = "/api/v1/status/capabilities";
    private static final Pattern LOCATION = Pattern.compile("^\\s*location\\s+(.+?)\\s*\\{");

    @Test
    @DisplayName("GET /api/v1/status/capabilities is proxied unchanged by the /api/ location")
    void newPathIsForwardedByTheApiProxy() {
        Location matched = match(NEW_PATH);

        assertThat(matched.pattern())
                .as("%s must be served by the generic API proxy", NEW_PATH)
                .isEqualTo("/api/");
        assertThat(matched.body())
                .as("with a variable and no URI part, nginx forwards the path unchanged")
                .contains("proxy_pass http://$nexus_app;")
                .doesNotContain("rewrite");
    }

    @Test
    @DisplayName("/actuator/health/deps still resolves to an explicit 404 at the edge")
    void depsGroupStaysClosed() {
        Location matched = match("/actuator/health/deps");

        assertThat(matched.pattern()).isEqualTo("/actuator/");
        assertThat(matched.body()).contains("return 404;");
    }

    @Test
    @DisplayName("The only forwarded actuator path is the exact health URL, onto servable")
    void onlyHealthIsForwarded() {
        List<Location> actuatorLocations = locations().stream()
                .filter(location -> location.pattern().contains("actuator"))
                .toList();

        assertThat(actuatorLocations).extracting(Location::pattern)
                .containsExactlyInAnyOrder("= /actuator/health", "/actuator/");

        Location health = actuatorLocations.stream()
                .filter(location -> location.pattern().startsWith("="))
                .findFirst()
                .orElseThrow();
        assertThat(health.body())
                .contains("rewrite ^ /actuator/health/servable break;")
                .contains("proxy_pass http://$nexus_app;");
    }

    @Test
    @DisplayName("No location invents a second status, dependency or metrics surface")
    void noExtraStatusSurface() {
        for (Location location : locations()) {
            assertThat(location.pattern().toLowerCase())
                    .as("an extra location is a new public surface; none may appear for this round")
                    .doesNotContain("deps")
                    .doesNotContain("/status")
                    .doesNotContain("metrics")
                    .doesNotContain("prometheus")
                    .doesNotContain("env");
        }
    }

    /**
     * The subset of nginx's algorithm these URLs need: exact match wins, then the longest literal
     * prefix. None of the URLs used here contains a dot or a file extension, so the two regex
     * locations in the file cannot win; that is asserted rather than assumed.
     */
    private static Location match(String uri) {
        assertThat(uri).as("regex locations must not be able to win for this fixture").doesNotContain(".");

        List<Location> locations = locations();
        for (Location location : locations) {
            if (location.pattern().startsWith("=")
                    && location.pattern().substring(1).trim().equals(uri)) {
                return location;
            }
        }

        Location best = null;
        int bestLength = -1;
        for (Location location : locations) {
            String pattern = location.pattern();
            if (pattern.startsWith("~")) {
                continue;
            }
            String prefix = pattern.startsWith("^~") ? pattern.substring(2).trim() : pattern;
            if (uri.startsWith(prefix) && prefix.length() > bestLength) {
                best = location;
                bestLength = prefix.length();
            }
        }
        if (best == null) {
            return fail("no location matches " + uri);
        }
        return best;
    }

    private static List<Location> locations() {
        List<String> lines = readConfig();
        List<Location> locations = new ArrayList<>();
        String pattern = null;
        StringBuilder body = new StringBuilder();
        int depth = 0;

        for (String raw : lines) {
            String line = stripComment(raw);
            if (pattern == null) {
                Matcher matcher = LOCATION.matcher(line);
                if (matcher.find()) {
                    pattern = matcher.group(1).trim();
                    body = new StringBuilder();
                    depth = braces(line);
                    if (depth <= 0) {
                        locations.add(new Location(pattern, ""));
                        pattern = null;
                    }
                }
                continue;
            }
            depth += braces(line);
            if (depth <= 0) {
                locations.add(new Location(pattern, body.toString()));
                pattern = null;
            } else {
                body.append(line).append('\n');
            }
        }

        assertThat(locations)
                .as("the parser has to see the edge's locations to say anything about them")
                .hasSizeGreaterThanOrEqualTo(6);
        return locations;
    }

    private static int braces(String line) {
        int net = 0;
        for (char c : line.toCharArray()) {
            if (c == '{') {
                net++;
            } else if (c == '}') {
                net--;
            }
        }
        return net;
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    private static List<String> readConfig() {
        Path relative = Path.of("docker", "nginx", "nginx.conf");
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readAllLines(candidate, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("could not read " + candidate, e);
                }
            }
        }
        return fail("no " + relative + " at or above " + Path.of("").toAbsolutePath());
    }

    private record Location(String pattern, String body) {
    }
}
