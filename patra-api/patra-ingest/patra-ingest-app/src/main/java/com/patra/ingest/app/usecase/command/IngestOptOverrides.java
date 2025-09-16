// src/main/java/com/example/ingest/app/command/Overrides.java
package com.patra.ingest.app.usecase.command;

import java.util.Optional;

/**
 * 显式覆盖数据源配置的“字段名/参数名”等实现细节。
 * 一般情况下应为空，优先使用数据源配置；仅在临时/灰度需要时覆盖。
 */
public record IngestOptOverrides(
        Optional<String> timeFieldName
) {
    public IngestOptOverrides {
        timeFieldName = timeFieldName == null ? Optional.empty() : timeFieldName;
    }

    public static IngestOptOverrides empty() {
        return new IngestOptOverrides(Optional.empty());
    }
}
