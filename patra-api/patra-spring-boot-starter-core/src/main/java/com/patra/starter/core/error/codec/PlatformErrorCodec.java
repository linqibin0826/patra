package com.patra.starter.core.error.codec;


import com.patra.common.error.core.PlatformError;

import java.util.Map;

/**
 * PlatformError 与 Problem JSON 的编解码接口。
 * 既提供 JSON 文本/字节互转，也提供 Map 级别互转。
 */
public interface PlatformErrorCodec {

    /* ---------- 写出 ---------- */

    /**
     * 写出为 Map（键名遵循 RFC7807 + 扩展字段约定）
     */
    Map<String, Object> toProblemMap(PlatformError error);

    /**
     * 写出为 JSON 字符串（application/problem+json）
     */
    String toJson(PlatformError error);

    /**
     * 写出为 JSON 字节数组（UTF-8）
     */
    byte[] toBytes(PlatformError error);

    /* ---------- 读入 ---------- */

    /**
     * 从 Map 读入，容错解析并生成 PlatformError
     */
    PlatformError fromProblemMap(Map<String, ?> map);

    /**
     * 从 JSON 字符串读入
     */
    PlatformError fromJson(String json);

    /**
     * 从 JSON 字节数组读入（UTF-8）
     */
    PlatformError fromBytes(byte[] bytes);
}
