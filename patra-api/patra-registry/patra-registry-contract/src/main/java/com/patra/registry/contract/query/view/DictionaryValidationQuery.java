package com.patra.registry.contract.query.view;

/**
 * 字典校验查询对象（CQRS 查询侧）。
 *
 * <p>在 app 与 contract 模块间共享，承载校验结果；不可变，用于内部与对外 API。</p>
 *
 * @param typeCode 被校验的类型编码
 * @param itemCode 被校验的项编码
 * @param isValid 校验是否通过
 * @param errorMessage 失败时的错误信息（通过时为 null）
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryValidationQuery(
    String typeCode,
    String itemCode,
    boolean isValid,
    String errorMessage
) {
    
    /** 带参数校验的紧凑构造器。 */
    public DictionaryValidationQuery {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item code cannot be null or empty");
        }
        if (!isValid && (errorMessage == null || errorMessage.trim().isEmpty())) {
            throw new IllegalArgumentException("Error message is required when validation fails");
        }
        
        // Normalize the codes and error message
        typeCode = typeCode.trim();
        itemCode = itemCode.trim();
        errorMessage = errorMessage != null ? errorMessage.trim() : null;
    }
    
    /** 创建“校验成功”结果。 */
    public static DictionaryValidationQuery success(String typeCode, String itemCode) {
        return new DictionaryValidationQuery(typeCode, itemCode, true, null);
    }
    
    /** 创建“校验失败”结果（带错误信息）。 */
    public static DictionaryValidationQuery failure(String typeCode, String itemCode, String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Error message cannot be null or empty for failed validation");
        }
        return new DictionaryValidationQuery(typeCode, itemCode, false, errorMessage);
    }
    
    /** 创建“未找到”失败结果。 */
    public static DictionaryValidationQuery notFound(String typeCode, String itemCode) {
        String message = String.format("Dictionary item not found: type='%s', item='%s'", typeCode, itemCode);
        return new DictionaryValidationQuery(typeCode, itemCode, false, message);
    }
    
    /** 创建“被禁用”失败结果。 */
    public static DictionaryValidationQuery disabled(String typeCode, String itemCode) {
        String message = String.format("Dictionary item is disabled: type='%s', item='%s'", typeCode, itemCode);
        return new DictionaryValidationQuery(typeCode, itemCode, false, message);
    }
    
    /** 是否失败（!isValid）。 */
    public boolean isFailure() {
        return !isValid;
    }
    
    /** 获取引用字符串（typeCode:itemCode）。 */
    public String getReferenceString() {
        return typeCode + ":" + itemCode;
    }
    
    /** 获取错误信息（通过则为 null）。 */
    public String getErrorMessage() {
        return errorMessage;
    }
}
