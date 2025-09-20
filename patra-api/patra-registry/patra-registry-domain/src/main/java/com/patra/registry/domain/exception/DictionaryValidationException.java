package com.patra.registry.domain.exception;

import java.util.List;

/**
 * 字典校验因违反业务规则而失败时抛出。
 *
 * <p>表示字典数据或引用违反了领域约束（如项被禁用、缺失默认项、无效引用等）。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryValidationException extends RegistryRuleViolation {
    
    /** 关联的字典类型编码（如适用） */
    private final String typeCode;
    
    /** 关联的字典项编码（如适用） */
    private final String itemCode;
    
    /** 详细校验错误消息列表 */
    private final List<String> validationErrors;
    
    /**
     * 单条错误消息构造异常。
     */
    public DictionaryValidationException(String message, String typeCode, String itemCode) {
        super(message);
        this.typeCode = typeCode;
        this.itemCode = itemCode;
        this.validationErrors = List.of(message);
    }
    
    /**
     * 多条错误消息构造异常。
     */
    public DictionaryValidationException(List<String> validationErrors, String typeCode) {
        super(String.format("Dictionary validation failed for type %s: %s", 
                           typeCode, String.join(", ", validationErrors)));
        this.typeCode = typeCode;
        this.itemCode = null;
        this.validationErrors = List.copyOf(validationErrors);
    }
    
    /**
     * 多条错误消息 + 自定义主消息构造异常。
     */
    public DictionaryValidationException(String message, List<String> validationErrors, 
                                       String typeCode, String itemCode) {
        super(message);
        this.typeCode = typeCode;
        this.itemCode = itemCode;
        this.validationErrors = validationErrors != null ? List.copyOf(validationErrors) : List.of();
    }
    
    /** 获取类型编码（如有）。 */
    public String getTypeCode() {
        return typeCode;
    }
    
    /** 获取项编码（如有）。 */
    public String getItemCode() {
        return itemCode;
    }
    
    /** 获取校验错误消息列表（不可变）。 */
    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
