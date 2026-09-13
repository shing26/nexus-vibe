package com.nexus.campus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Applies the sample posts, comments, messages and review logs that used to live
 * in {@code docker/mysql/init.sql}.
 *
 * <p>The image used to seed that content on the very first boot of the database
 * container, before {@link DataPreloader} had decided whether accounts were
 * wanted at all. Turning {@code DEMO_SEED_ENABLED} off therefore produced a
 * production database full of posts whose authors did not exist. Content now
 * follows accounts: it is applied only while demo seeding is on, only into MySQL,
 * and only while the post table is still empty.</p>
 *
 * <p>The H2 dev profile keeps getting its content from
 * {@code src/main/resources/data.sql}, which is why the database check exists:
 * the script below uses MySQL date functions that H2 would reject.</p>
 */
@Component
@Order(300)
public class DemoContentSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoContentSeeder.class);

    static final String CONTENT_SCRIPT = "db/mysql/demo-content.sql";

    private final JdbcTemplate jdbcTemplate;
    private final boolean seedEnabled;
    private final String datasourceUrl;

    public DemoContentSeeder(JdbcTemplate jdbcTemplate,
                             @Value("${campus.demo.seed-enabled:true}") boolean seedEnabled,
                             @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.seedEnabled = seedEnabled;
        this.datasourceUrl = datasourceUrl;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            log.debug("[PREHEAT] Demo seeding is off, sample content stays out of the database.");
            return;
        }
        if (!datasourceUrl.startsWith("jdbc:mysql")) {
            log.debug("[PREHEAT] Non-MySQL datasource ({}), sample content comes from data.sql.", datasourceUrl);
            return;
        }
        Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vibe_post", Integer.class);
        if (existing != null && existing > 0) {
            log.info("[PREHEAT] {} posts already present, sample content is not re-applied.", existing);
            return;
        }
        applyScript();
        log.info("[PREHEAT] Demo content applied to an empty MySQL database.");
    }

    /** Package-private so the gate can be tested without a database. */
    void applyScript() {
        EncodedResource script = new EncodedResource(
                new ClassPathResource(CONTENT_SCRIPT), StandardCharsets.UTF_8);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, script);
            return null;
        });
    }
}
