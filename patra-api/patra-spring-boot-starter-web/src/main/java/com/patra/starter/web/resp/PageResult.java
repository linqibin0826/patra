package com.patra.starter.web.resp;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class PageResult<T> {

    private long total;

    private long current;

    private long size;

    private long pages;

    private List<T> records = Collections.emptyList();

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
