package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 游标推进方向
 */
@Getter
public enum CursorAdvanceDirection implements CodeEnum<String> {
    FORWARD("forward", "增量前进"),
    BACKFILL("backfill", "历史回灌");

    private final String code;
    private final String description;

    CursorAdvanceDirection(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
