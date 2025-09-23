package com.patra.common.domain;

import java.io.Serializable;
import java.time.Instant;

/**
 * 领域事件标记接口。
 * 建议事件对象为不可变（immutable），并包含发生时间与事件ID（可选）。
 */
public interface DomainEvent extends Serializable {

    /** 事件发生时间（用于排序/审计）。 */
    Instant occurredAt();
}
