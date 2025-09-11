package com.patra.registry.domain.model.vo;

import lombok.Builder;
import lombok.Value;

/**
 * API 参数映射值对象。
 * <p>用于把内部标准键（stdKey）映射到供应商 API 的具体参数名，并可指定转换函数。
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
