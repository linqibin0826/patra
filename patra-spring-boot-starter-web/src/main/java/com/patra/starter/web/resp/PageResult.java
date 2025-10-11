package com.patra.starter.web.resp;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * Generic pagination payload shared by REST endpoints.
 *
 * @param <T> element type contained in the page
 */
@Data
public class PageResult<T> {

    /** Total number of matching records across all pages. */
    private long total;

    /** Index of the current page (1-based to align with API contracts). */
    private long current;

    /** Requested page size. */
    private long size;

    /** Total number of pages derived from {@code total} and {@code size}. */
    private long pages;

    /** Records contained in the current page; empty when no data is available. */
    private List<T> records = Collections.emptyList();

    /**
     * Build a {@link PageResult} instance with the supplied metadata and records.
     *
     * @param total   total number of records
     * @param current current page index (1-based)
     * @param size    requested page size
     * @param records page records (nullable)
     * @param <T>     element type
     * @return populated {@link PageResult}
     */
    public static <T> PageResult<T> of(long total, long current, long size, List<T> records) {
        PageResult<T> r = new PageResult<>();
        r.total = total;
        r.current = current;
        r.size = size;
        r.pages = size <= 0 ? 0 : (total + size - 1) / size;
        r.records = (records == null ? Collections.emptyList() : records);
        return r;
    }

}
