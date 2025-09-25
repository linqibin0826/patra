package com.patra.registry.domain.model.vo.dictionary;

/**
 * 校验结果值对象（不可变）。
 *
 * <p>封装字典校验结果：包含是否通过与失败时的错误信息。</p>
 *
 * @param isValid 是否通过
 * @param errorMessage 失败时的错误信息（通过为 null）
 * @author linqibin
 * @since 0.1.0
 */
public record ValidationResult(
    boolean isValid,
    String errorMessage
) {
    
    /** 带参数校验的紧凑构造器。 */
    public ValidationResult {
        if (!isValid && (errorMessage == null || errorMessage.trim().isEmpty())) {
            throw new IllegalArgumentException("Error message is required when validation fails");
        }
        // Normalize error message
        errorMessage = errorMessage != null ? errorMessage.trim() : null;
    }
    
    /** 创建“校验成功”结果。 */
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }
    
    /** 创建“校验失败”结果（带错误信息）。 */
    public static ValidationResult failure(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Error message cannot be null or empty for failed validation");
        }
        return new ValidationResult(false, errorMessage);
    }
    
    /** 创建“未找到”失败结果。 */
    public static ValidationResult notFound(String typeCode, String itemCode) {
        String message = String.format("Dictionary item not found: type='%s', item='%s'", typeCode, itemCode);
        return new ValidationResult(false, message);
    }
    
    /** 创建“被禁用”失败结果。 */
    public static ValidationResult disabled(String typeCode, String itemCode) {
        String message = String.format("Dictionary item is disabled: type='%s', item='%s'", typeCode, itemCode);
        return new ValidationResult(false, message);
    }
    
    /** 创建“已删除”失败结果。 */
    public static ValidationResult deleted(String typeCode, String itemCode) {
        String message = String.format("Dictionary item is deleted: type='%s', item='%s'", typeCode, itemCode);
        return new ValidationResult(false, message);
    }
    
    /** 是否失败（!isValid）。 */
    public boolean isFailure() {
        return !isValid;
    }
    
    /** 获取错误信息（通过则为 null）。 */
    public String getErrorMessage() {
        return errorMessage;
    }
}
