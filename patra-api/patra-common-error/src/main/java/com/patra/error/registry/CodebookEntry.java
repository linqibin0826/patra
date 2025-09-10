package com.patra.error.registry;

import com.patra.error.core.ErrorCode;

import java.util.Map;
import java.util.Objects;

/**
 * 单条错误码登记信息（用于生成文档、校验、展示）
 */
public record CodebookEntry(
        ErrorCode code,
        String title,         // 人读标题
        Integer httpStatus,   // 建议 HTTP（可为 null）
        String doc,           // 文档链接（可为 null）
        String owner,         // 负责人或团队（可为 null）
        Map<String, Object> extras // 任意扩展信息（可为空 Map）
) {
    public CodebookEntry {
        Objects.requireNonNull(code, "code");
        // 允许 title/httpStatus/doc/owner/extras 为 null
    }
}
