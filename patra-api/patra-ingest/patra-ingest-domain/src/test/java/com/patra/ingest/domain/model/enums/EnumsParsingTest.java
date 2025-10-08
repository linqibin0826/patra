package com.patra.ingest.domain.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 各枚举解析方法的健壮性测试。
 */
class EnumsParsingTest {

    @Test
    @DisplayName("OperationCode.fromCode 空/未知值抛出异常；忽略大小写与空白")
    void operationCodeFromCode() {
        assertThrows(IllegalArgumentException.class, () -> OperationCode.fromCode(null));
        assertEquals(OperationCode.UPDATE, OperationCode.fromCode(" update "));
        assertThrows(IllegalArgumentException.class, () -> OperationCode.fromCode("x"));
    }

    @Test
    @DisplayName("PlanStatus.fromCode 校验与解析")
    void planStatusFromCode() {
        assertThrows(IllegalArgumentException.class, () -> PlanStatus.fromCode(null));
        assertEquals(PlanStatus.DRAFT, PlanStatus.fromCode(" draft "));
        assertThrows(IllegalArgumentException.class, () -> PlanStatus.fromCode("x"));
    }

    @Test
    @DisplayName("TaskStatus.fromCode 校验与解析")
    void taskStatusFromCode() {
        assertThrows(IllegalArgumentException.class, () -> TaskStatus.fromCode(null));
        assertEquals(TaskStatus.QUEUED, TaskStatus.fromCode(" queued "));
        assertThrows(IllegalArgumentException.class, () -> TaskStatus.fromCode("x"));
    }
}
