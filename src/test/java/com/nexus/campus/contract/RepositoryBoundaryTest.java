package com.nexus.campus.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The post application layer may know repository interfaces, not MyBatis
 * mappers. A source scan keeps the boundary visible even when a future change
 * is otherwise behaviourally correct.
 */
class RepositoryBoundaryTest {

    private static final Path REPOSITORY_ROOT = Paths.get(
            "src", "main", "java", "com", "nexus", "campus", "repository");

    private static final Path SERVICE = Paths.get(
            "src", "main", "java", "com", "nexus", "campus",
            "service", "impl", "VibePostServiceImpl.java");
    private static final Path CREATION_SERVICE = Paths.get(
            "src", "main", "java", "com", "nexus", "campus",
            "service", "impl", "PostCreationService.java");
    private static final Path EDIT_SERVICE = Paths.get(
            "src", "main", "java", "com", "nexus", "campus",
            "service", "impl", "PostEditService.java");
    private static final Path EVENT_PUBLISHER = Paths.get(
            "src", "main", "java", "com", "nexus", "campus",
            "service", "impl", "PostAgentEventPublisher.java");
    private static final Path AI_REVIEW_SERVICE = Paths.get(
            "src", "main", "java", "com", "nexus", "campus",
            "agent", "AiReviewService.java");

    private static final Pattern JACKSON_OBJECT_MAPPER =
            Pattern.compile("objectMapper", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("Post application services do not import or hold MyBatis mappers")
    void postServicesStopAtRepositoryInterfaces() throws IOException {
        for (Path path : new Path[]{SERVICE, CREATION_SERVICE, EDIT_SERVICE, EVENT_PUBLISHER,
                AI_REVIEW_SERVICE}) {
            assertThat(path).as(path.toString()).exists();
            String source = Files.readString(path, StandardCharsets.UTF_8);
            assertThat(source)
                    .as(path + " must not import the MyBatis mapper package")
                    .doesNotContain("com.nexus.campus.mapper");
            assertThat(mapperLines(source))
                    .as(path + " must not hold or name a MyBatis mapper")
                    .isEmpty();
        }

        assertThat(Files.readString(SERVICE, StandardCharsets.UTF_8))
                .contains("postCreationService.createPost")
                .contains("postEditService.updatePost");
    }

    /**
     * The other half of the seam: a repository interface is the application
     * layer's view of persistence, so it cannot hand back a MyBatis type. The
     * adapters under {@code repository/impl} are where framework types are
     * allowed to exist.
     */
    @Test
    @DisplayName("Repository interfaces expose no MyBatis types")
    void repositoryInterfacesAreFrameworkFree() throws IOException {
        List<Path> interfaces;
        try (Stream<Path> files = Files.list(REPOSITORY_ROOT)) {
            interfaces = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        assertThat(interfaces).as("repository interfaces should exist").isNotEmpty();
        for (Path path : interfaces) {
            assertThat(Files.readString(path, StandardCharsets.UTF_8))
                    .as(path + " is the application layer's view of persistence; "
                            + "MyBatis types belong in repository/impl")
                    .doesNotContain("com.baomidou");
        }
    }

    /**
     * Lines that name a mapper, minus Jackson's {@code ObjectMapper}, which is a
     * JSON codec and has nothing to do with persistence. The blunt
     * {@code "Mapper;"} substring rule this replaced flagged that import the
     * moment {@code AiReviewService} joined the scan, which is the wrong fix to
     * make in a service that legitimately parses JSON.
     */
    private static List<String> mapperLines(String source) {
        return source.lines()
                .filter(line -> line.contains("Mapper"))
                .filter(line -> !JACKSON_OBJECT_MAPPER.matcher(line).find())
                .collect(Collectors.toList());
    }
}
