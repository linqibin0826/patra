package com.patra.registry.domain.vo;

import lombok.Builder;
import lombok.Value;

/**
 * API参数映射值对象
 * docref: /docs/domain/model/vo/ApiParamMapping.txt
 */
@Value
@Builder
public class ApiParamMapping {
    
    /**
     * 操作名：search/fetch/lookup…
     */
    String operation;
    
    /**
     * 标准键（统一内部语义键）
     */
    String stdKey;
    
    /**
     * 供应商参数名（如 term/retmax/retstart）
     */
    String providerParam;
    
    /**
     * 可选转换函数（如 toExclusiveMinus1d）
     */
    String transform;
    
    /**
     * 备注/补充说明
     */
    String notes;
}
