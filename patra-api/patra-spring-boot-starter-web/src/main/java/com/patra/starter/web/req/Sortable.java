package com.patra.starter.web.req;

import java.util.Set;

/**
 * Sorting contract. Implementations declare a whitelist used for validation and documentation.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface Sortable {

    /** Sorting expression formatted as {@code field,asc|desc;field2,desc}. */
    String getSort();

    /**
     * Allowed sort fields in lower camel case exposed to clients
     * (defaults to {@code id}, {@code createdAt}, {@code updatedAt}).
     */
    default Set<String> allowedSortFields() {
        return Set.of("id", "createdAt", "updatedAt");
    }

    /** Maximum number of sort fields (override when stricter limits are required). */
    default int maxSortFields() {
        return 3;
    }
}
