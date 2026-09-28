package com.nexus.campus.repository;

import java.util.List;

/**
 * One page of a repository query: the records, plus the total row count the
 * caller needs to build its own pagination wrapper.
 *
 * <p>Deliberately not a MyBatis {@code Page}. The repository interface is the
 * application layer's view of persistence, so a service that pages through
 * posts must not have to construct or unwrap a framework type to do it. The
 * adapters under {@code repository/impl} translate.</p>
 */
public record PageSlice<T>(List<T> records, long total) {

    public PageSlice {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
