package com.patra.registry.api.error;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Registry 服务错误码目录（实现 ErrorCodeLike）。
 *
 * <p>统一采用 REG-NNNN 形式（REG 为上下文前缀）。</p>
 *
 * <p>分类：
 * - 0xxx：对齐 HTTP 的通用错误
 * - 1xxx：领域/业务特定错误
 * </p>
 *
 * <p>追加式维护：仅新增，不删除/修改既有错误码，确保 API 稳定性。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum RegistryErrorCode implements ErrorCodeLike {
    
    // ========================================
    // Common HTTP-aligned codes (0xxx series)
    // ========================================
    
    /** 错误请求（HTTP 400）。 */
    REG_0400("REG-0400"),
    
    /** 未认证（HTTP 401）。 */
    REG_0401("REG-0401"),
    
    /** 禁止访问（HTTP 403）。 */
    REG_0403("REG-0403"),
    
    /** 未找到（HTTP 404）。 */
    REG_0404("REG-0404"),
    
    /** 冲突（HTTP 409）。 */
    REG_0409("REG-0409"),
    
    /** 语义错误（HTTP 422）。 */
    REG_0422("REG-0422"),
    
    /** 请求过多/配额耗尽（HTTP 429）。 */
    REG_0429("REG-0429"),
    
    /** 服务器内部错误（HTTP 500）。 */
    REG_0500("REG-0500"),
    
    /** 服务不可用（HTTP 503）。 */
    REG_0503("REG-0503"),
    
    /** 网关超时（HTTP 504）。 */
    REG_0504("REG-0504"),
    
    // ========================================
    // Business-specific codes (1xxx series)
    // ========================================
    
    // Dictionary operations (14xx series)
    
    /** 字典类型未找到（映射：DictionaryNotFoundException，类型级）。 */
    REG_1401("REG-1401"),
    
    /** 字典项未找到（映射：DictionaryNotFoundException，项级）。 */
    REG_1402("REG-1402"),
    
    /** 字典项被禁用（映射：DictionaryItemDisabled）。 */
    REG_1403("REG-1403"),
    
    /** 字典类型已存在（映射：DictionaryTypeAlreadyExists）。 */
    REG_1404("REG-1404"),
    
    /** 字典项已存在（映射：DictionaryItemAlreadyExists）。 */
    REG_1405("REG-1405"),
    
    /** 字典类型被禁用（映射：DictionaryTypeDisabled）。 */
    REG_1406("REG-1406"),
    
    /** 字典校验失败（映射：DictionaryValidationException）。 */
    REG_1407("REG-1407"),
    
    /** 缺失默认项（映射：DictionaryDefaultItemMissing）。 */
    REG_1408("REG-1408"),
    
    /** 数据库/仓储层错误（映射：DictionaryRepositoryException）。 */
    REG_1409("REG-1409"),
    
    // Registry general operations (15xx series)
    
    /** 超出配额限制（映射：RegistryQuotaExceeded）。 */
    REG_1501("REG-1501");
    
    private final String code;
    
    /** 构造函数。 */
    RegistryErrorCode(String code) {
        this.code = code;
    }
    
    /** 返回错误码字符串（REG-NNNN）。 */
    @Override
    public String code() {
        return code;
    }
    
    /** 返回字符串表示。 */
    @Override
    public String toString() {
        return code;
    }
}
