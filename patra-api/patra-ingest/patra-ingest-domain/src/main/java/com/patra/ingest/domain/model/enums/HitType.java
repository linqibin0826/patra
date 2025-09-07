package com.patra.ingest.domain.model.enums;

/**
 * 命中类型枚举
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum HitType {
    
    /**
     * 新增命中
     */
    NEW,
    
    /**
     * 更新命中
     */
    UPDATE,
    
    /**
     * 删除命中
     */
    DELETE,
    
    /**
     * 跳过（已存在且未变更）
     */
    SKIP
}
