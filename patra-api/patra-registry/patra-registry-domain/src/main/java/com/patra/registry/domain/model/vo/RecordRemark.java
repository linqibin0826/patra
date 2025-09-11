package com.patra.registry.domain.model.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 记录备注值对象（审计信息）。
 */
@Value
@Builder
public class RecordRemark {
    
    /**
     * 操作时间
     */
    LocalDateTime time;
    
    /**
     * 操作人
     */
    String by;
    
    /**
     * 备注内容
     */
    String note;
}
