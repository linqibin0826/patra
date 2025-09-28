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

    // 注意：0xxx（HTTP 对齐段）请统一使用 HttpStdErrors.of("REG").* 工厂方法，不在本枚举维护。

    // ========================================
    // Business-specific codes (1xxx series)
    // ========================================

    // Dictionary operations (14xx series)

    /**
     * 字典类型未找到（映射：DictionaryNotFoundException，类型级）。
     */
    REG_1401("REG-1401", 404),

    /**
     * 字典项未找到（映射：DictionaryNotFoundException，项级）。
     */
    REG_1402("REG-1402", 404),

    /**
     * 字典项被禁用（映射：DictionaryItemDisabled）。
     */
    REG_1403("REG-1403", 422),

    /**
     * 字典类型已存在（映射：DictionaryTypeAlreadyExists）。
     */
    REG_1404("REG-1404", 409),

    /**
     * 字典项已存在（映射：DictionaryItemAlreadyExists）。
     */
    REG_1405("REG-1405", 409),

    /**
     * 字典类型被禁用（映射：DictionaryTypeDisabled）。
     */
    REG_1406("REG-1406", 422),

    /**
     * 字典校验失败（映射：DictionaryValidationException）。
     */
    REG_1407("REG-1407", 422),

    /**
     * 缺失默认项（映射：DictionaryDefaultItemMissing）。
     */
    REG_1408("REG-1408", 422),

    /**
     * 数据库/仓储层错误（映射：DictionaryRepositoryException）。
     */
    REG_1409("REG-1409", 500),

    // Registry general operations (15xx series)

    /**
     * 超出配额限制（映射：RegistryQuotaExceeded）。
     */
    REG_1501("REG-1501", 429);

    private final String code;
    private final int httpStatus;

    /**
     * 构造函数。
     */
    RegistryErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /**
     * 返回错误码字符串（REG-NNNN）。
     */
    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * 返回字符串表示。
     */
    @Override
    public String toString() {
        return code;
    }
}
