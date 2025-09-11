package com.patra.registry.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;



/**
 * 文本匹配策略：短语、精确或任意。
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
