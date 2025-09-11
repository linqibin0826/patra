package com.patra.starter.core.error.codec;

/** RFC7807 字段 + 平台扩展字段常量与媒体类型。 */
public final class ProblemJson {
    private ProblemJson() {}

    /** 媒体类型 */
    public static final String MEDIA_TYPE = "application/problem+json";

    // --- 标准字段（RFC 7807） ---
    public static final String TYPE     = "type";
    public static final String TITLE    = "title";
    public static final String STATUS   = "status";
    public static final String DETAIL   = "detail";
    public static final String INSTANCE = "instance";

    // --- 平台扩展字段 ---
    public static final String CODE      = "code";
    public static final String SERVICE   = "service";
    public static final String TRACE_ID  = "traceId";
    public static final String TIMESTAMP = "timestamp";
    public static final String EXTRAS    = "extras";
}
