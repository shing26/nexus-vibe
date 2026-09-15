package com.nexus.campus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The gate matters more than the insert: the moved script is MySQL-only SQL, and a
 * dev or test context runs on H2 with the same demo flag switched on. If this class
 * ever applies the script there, the suite breaks in whichever order the contexts
 * happen to start in, which is the opposite of a gate.
 */
@ExtendWith(MockitoExtension.class)
class DemoContentSeederTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DemoContentSeeder seeder(boolean seedEnabled, String url) {
        return new DemoContentSeeder(jdbcTemplate, seedEnabled, url);
    }

    @Test
    @DisplayName("Seeding off touches the database not at all")
    void staysOutOfTheDatabaseWhenSeedingIsOff() {
        seeder(false, "jdbc:mysql://db:3306/nexus_campus").run();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("H2 gets its content from data.sql, never from the MySQL script")
    void staysOutOfTheDatabaseOnH2() {
        seeder(true, "jdbc:h2:mem:nexuscampus;MODE=MYSQL").run();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Empty MySQL database: the script is applied")
    void appliesScriptToAnEmptyMysqlDatabase() {
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vibe_post", Integer.class)).thenReturn(0);
        DemoContentSeeder seeder = spy(seeder(true, "jdbc:mysql://db:3306/nexus_campus"));
        doNothing().when(seeder).applyScript();

        seeder.run();

        verify(seeder).applyScript();
        verify(jdbcTemplate, times(1)).queryForObject("SELECT COUNT(*) FROM vibe_post", Integer.class);
    }

    @Test
    @DisplayName("Posts already exist: insert-only, nothing is re-applied or cleared")
    void neverReappliesToANonEmptyDatabase() {
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vibe_post", Integer.class)).thenReturn(12);
        // No doNothing() stub here: applyScript against a mocked JdbcTemplate is inert, and
        // leaving the stub in place would be an unused stubbing if the gate ever leaks.
        DemoContentSeeder seeder = spy(seeder(true, "jdbc:mysql://db:3306/nexus_campus"));

        seeder.run();

        verify(seeder, never()).applyScript();
    }

    @Test
    @DisplayName("The moved script is on the classpath and still holds the sample content")
    void scriptResourceIsPackaged() throws Exception {
        ClassPathResource resource = new ClassPathResource(DemoContentSeeder.CONTENT_SCRIPT);

        assertThat(resource.exists()).isTrue();
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        // The tables init.sql used to fill by hand, all still present after the move.
        assertThat(sql)
                .contains("INSERT INTO `vibe_post`")
                .contains("INSERT INTO `vibe_comment`")
                .contains("INSERT INTO `sys_message`")
                .contains("INSERT INTO `ai_review_log`")
                .contains("INSERT INTO `vibe_prompt_version`");
        // ...and the destructive clears are gone: this runs against a live database now.
        assertThat(sql).doesNotContain("DELETE FROM");
    }
}
