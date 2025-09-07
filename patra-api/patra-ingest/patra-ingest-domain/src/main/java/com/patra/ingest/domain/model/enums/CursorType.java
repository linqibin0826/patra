package com.patra.ingest.domain.model.enums;

/**
 * 游标类型枚举
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum CursorType {
    
    /**
     * 分页游标
     */
    PAGE,
    
    /**
     * 令牌游标
     */
    TOKEN,
    
    /**
     * 时间戳游标
     */
    TIMESTAMP,
    
    /**
     * 自定义游标
     */
    CUSTOM
}
