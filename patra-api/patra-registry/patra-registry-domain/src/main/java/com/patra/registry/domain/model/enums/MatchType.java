package com.patra.registry.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;



/**
 * 匹配策略枚举
 * docref: /docs/domain/enums.discovery.md#6-matchtype-匹配策略枚举
 */
@Getter
@RequiredArgsConstructor
public enum MatchType implements CodeEnum<String> {
    
    PHRASE("phrase", "短语匹配"),
    EXACT("exact", "精确匹配"),
    ANY("any", "任意匹配");
    
    private final String code;
    private final String description;
}
