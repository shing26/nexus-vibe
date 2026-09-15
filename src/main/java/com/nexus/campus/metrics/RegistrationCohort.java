package com.nexus.campus.metrics;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * One registration and the first post that author ever published, for the
 * activation ratio published by {@code FunnelAggregateTask}.
 *
 * <p>A read model, not a response: nothing here leaves the process. It exists
 * because the "within seven days" test cannot be written in SQL portably —
 * {@code DATEADD} is H2 and {@code DATE_ADD … INTERVAL} is MySQL, and this repo's
 * tests run on the first while its deployment runs on the second. The query
 * therefore returns two timestamps and Java compares them.</p>
 */
@Data
public class RegistrationCohort {

    private Long userId;

    private LocalDateTime registeredAt;

    /** Null when the author has never posted. */
    private LocalDateTime firstPostAt;
}
